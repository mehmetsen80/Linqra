package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.Neo4jGraphService;
import org.neo4j.driver.Driver;
import org.neo4j.driver.Result;
import org.neo4j.driver.Session;
import org.neo4j.driver.exceptions.Neo4jException;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class Neo4jGraphServiceImpl implements Neo4jGraphService {

    private final Driver neo4jDriver;
    private final ChunkEncryptionService chunkEncryptionService;

    /**
     * Sensitive property keys that should be encrypted when storing entities in
     * Neo4j.
     * These properties contain confidential information that should not be visible
     * to database administrators.
     */
    private static final Set<String> SENSITIVE_PROPERTY_KEYS = Set.of(
            "name", "description", "address", "phone", "email", "website",
            "contactInfo", "title", "role", "affiliation",
            "street", "city", "state", "zipCode", "country", "coordinates",
            "documentType", "documentNumber", "issuingAuthority",
            "requiredFields", "filingInstructions", "purpose");

    @Override
    public Mono<String> upsertEntity(String entityType, String entityId, Map<String, Object> properties,
            String teamId) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                // Build MERGE query with teamId for multi-tenant isolation
                Map<String, Object> params = new HashMap<>(properties);
                params.put("entityId", entityId);
                params.put("teamId", teamId);

                // Remove createdAt/updatedAt from properties to avoid conflicts with our
                // timestamp logic
                // Also filter out nested Maps/Collections as Neo4j only supports primitive
                // types
                Map<String, Object> cleanProperties = sanitizeProperties(properties);
                cleanProperties.remove("createdAt");
                cleanProperties.remove("updatedAt");

                // Log entity type and properties before encryption (for debugging)
                log.debug("Processing {} entity {} for team {}. Properties before encryption: {}", entityType, entityId,
                        teamId, cleanProperties.keySet());

                // Encrypt sensitive properties before storing
                String encryptionKeyVersion = chunkEncryptionService.getCurrentKeyVersion(teamId).block();
                cleanProperties = encryptSensitiveProperties(cleanProperties, teamId, encryptionKeyVersion, entityType,
                        entityId);
                // Store entity-level encryption version for tracking
                cleanProperties.put("encryptionKeyVersion", encryptionKeyVersion);

                StringBuilder cypher = new StringBuilder();
                cypher.append("MERGE (e:").append(entityType).append(" {id: $entityId, teamId: $teamId}) ");
                // ON CREATE and ON MATCH must come immediately after MERGE
                cypher.append("ON CREATE SET e.createdAt = timestamp() ");
                cypher.append("ON MATCH SET e.updatedAt = timestamp() ");
                // First, remove all sensitive properties that might have old encryption markers
                // This ensures we don't leave orphaned encryption_version properties
                for (String sensitiveKey : SENSITIVE_PROPERTY_KEYS) {
                    cypher.append("REMOVE e.").append(sensitiveKey).append("_encryption_version ");
                }
                // Now set all properties - this will fully overwrite existing properties
                // including plaintext ones
                if (!cleanProperties.isEmpty()) {
                    cypher.append("SET e += $properties ");
                }
                cypher.append("RETURN e.id as id");

                params.put("properties", cleanProperties);

                Result result = session.run(cypher.toString(), params);
                if (result.hasNext()) {
                    // Use next() instead of single() to handle cases where multiple records might
                    // exist
                    // (e.g., duplicate nodes with same id but different labels)
                    String id = result.next().get("id").asString();
                    log.info("Upserted entity {}:{} for team {}", entityType, id, teamId);
                    // If there are more records, log a warning (shouldn't happen with MERGE, but
                    // defensive)
                    if (result.hasNext()) {
                        log.warn("Multiple records returned for entity {}:{} in team {}. Using first result.",
                                entityType, entityId, teamId);
                    }
                    return id;
                }
                return entityId;
            } catch (Neo4jException e) {
                log.error("Error upserting entity {}:{} for team {}: {}", entityType, entityId, teamId, e.getMessage());
                throw new RuntimeException("Failed to upsert entity", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Boolean> upsertRelationship(
            String fromEntityType, String fromEntityId,
            String relationshipType,
            String toEntityType, String toEntityId,
            Map<String, Object> properties,
            String teamId) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                Map<String, Object> params = new HashMap<>();
                params.put("fromId", fromEntityId);
                params.put("toId", toEntityId);
                params.put("teamId", teamId);

                // Sanitize properties to remove nested Maps/Collections
                Map<String, Object> sanitizedProps = properties != null ? sanitizeProperties(properties) : Map.of();

                StringBuilder cypher = new StringBuilder();
                cypher.append("MATCH (from:").append(fromEntityType).append(" {id: $fromId, teamId: $teamId}) ");
                cypher.append("MATCH (to:").append(toEntityType).append(" {id: $toId, teamId: $teamId}) ");
                cypher.append("MERGE (from)-[r:").append(relationshipType).append("]->(to) ");

                // ON CREATE and ON MATCH must come immediately after MERGE
                if (sanitizedProps != null && !sanitizedProps.isEmpty()) {
                    params.put("relProps", sanitizedProps);
                    cypher.append("ON CREATE SET r.createdAt = timestamp(), r += $relProps ");
                    cypher.append("ON MATCH SET r.updatedAt = timestamp(), r += $relProps ");
                } else {
                    cypher.append("ON CREATE SET r.createdAt = timestamp() ");
                    cypher.append("ON MATCH SET r.updatedAt = timestamp() ");
                }
                cypher.append("RETURN r");

                Result result = session.run(cypher.toString(), params);
                boolean success = result.hasNext();
                if (success) {
                    log.debug("Upserted relationship {} from {}:{} to {}:{} for team {}",
                            relationshipType, fromEntityType, fromEntityId, toEntityType, toEntityId, teamId);
                }
                return success;
            } catch (Neo4jException e) {
                log.error("Error upserting relationship {} from {}:{} to {}:{} for team {}: {}",
                        relationshipType, fromEntityType, fromEntityId, toEntityType, toEntityId, teamId,
                        e.getMessage());
                throw new RuntimeException("Failed to upsert relationship", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Map<String, Object>> findEntities(String entityType, Map<String, Object> filters, String teamId) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                if (teamId == null || teamId.trim().isEmpty()) {
                    log.error("teamId is null or empty when finding entities of type {}", entityType);
                    throw new IllegalArgumentException("teamId is required for finding entities");
                }

                Map<String, Object> params = new HashMap<>();
                params.put("teamId", teamId);

                // Remove teamId from filters if present (we always enforce it separately)
                Map<String, Object> cleanFilters = filters != null ? new HashMap<>(filters) : new HashMap<>();
                cleanFilters.remove("teamId");

                StringBuilder cypher = new StringBuilder();
                cypher.append("MATCH (e:").append(entityType).append(") ");
                cypher.append("WHERE e.teamId = $teamId "); // Always enforce teamId filtering

                if (!cleanFilters.isEmpty()) {
                    int index = 0;
                    for (Map.Entry<String, Object> filter : cleanFilters.entrySet()) {
                        cypher.append("AND e.").append(filter.getKey()).append(" = $filter").append(index).append(" ");
                        params.put("filter" + index, filter.getValue());
                        index++;
                    }
                }

                cypher.append("RETURN e");

                log.debug("Executing Cypher query for team {}: {}", teamId, cypher.toString());

                Result result = session.run(cypher.toString(), params);
                return result.stream()
                        .map(record -> {
                            var node = record.get("e").asNode();
                            Map<String, Object> map = new HashMap<>();
                            map.put("id", node.get("id").asString());
                            map.put("type", entityType);
                            node.asMap().forEach((key, value) -> {
                                if (value != null) {
                                    map.put(key, value);
                                }
                            });
                            // Decrypt sensitive properties before returning
                            return decryptSensitiveProperties(map, teamId);
                        })
                        .collect(Collectors.toList());
            } catch (Neo4jException e) {
                log.error("Error finding entities {} for team {}: {}", entityType, teamId, e.getMessage());
                throw new RuntimeException("Failed to find entities", e);
            }
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<Map<String, Object>> findRelatedEntities(
            String entityType, String entityId,
            String relationshipType,
            int maxDepth,
            String teamId) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                Map<String, Object> params = new HashMap<>();
                params.put("entityId", entityId);
                params.put("teamId", teamId);

                // For direct relationships (maxDepth=1), use a simpler query
                if (maxDepth <= 1) {
                    StringBuilder cypher = new StringBuilder();
                    cypher.append("MATCH (start:").append(entityType).append(" {id: $entityId, teamId: $teamId})-[r");

                    if (relationshipType != null && !relationshipType.isEmpty()) {
                        cypher.append(":").append(relationshipType);
                    }

                    cypher.append("]->(related) ");
                    cypher.append("WHERE related.teamId = $teamId ");
                    cypher.append("RETURN related, type(r) as relationshipType, properties(r) as relProperties ");
                    cypher.append("LIMIT 100");

                    Result result = session.run(cypher.toString(), params);
                    return result.stream()
                            .map(record -> {
                                var node = record.get("related").asNode();
                                Map<String, Object> map = new HashMap<>();
                                map.put("id", node.get("id").asString());
                                map.put("type", node.labels().iterator().next());
                                node.asMap().forEach((key, value) -> {
                                    if (value != null) {
                                        map.put(key, value);
                                    }
                                });

                                // Add direct relationship info
                                String relType = record.get("relationshipType").asString();
                                Map<String, Object> relProps = record.get("relProperties").asMap();
                                map.put("relationshipType", relType);
                                map.put("relationshipProperties", relProps);

                                // Decrypt sensitive properties before returning
                                return decryptSensitiveProperties(map, teamId);
                            })
                            .collect(Collectors.toList());
                } else {
                    // For multi-hop paths (maxDepth > 1), use path traversal
                    params.put("maxDepth", maxDepth);
                    StringBuilder cypher = new StringBuilder();
                    cypher.append("MATCH path = (start:").append(entityType)
                            .append(" {id: $entityId, teamId: $teamId})");

                    if (relationshipType != null && !relationshipType.isEmpty()) {
                        cypher.append("-[r:").append(relationshipType).append("*1..").append(maxDepth).append("]-");
                    } else {
                        cypher.append("-[*1..").append(maxDepth).append("]-");
                    }

                    cypher.append("(related) ");
                    cypher.append("WHERE related.teamId = $teamId ");
                    cypher.append("RETURN DISTINCT related, relationships(path) as rels ");
                    cypher.append("LIMIT 100");

                    Result result = session.run(cypher.toString(), params);
                    return result.stream()
                            .map(record -> {
                                var node = record.get("related").asNode();
                                Map<String, Object> map = new HashMap<>();
                                map.put("id", node.get("id").asString());
                                map.put("type", node.labels().iterator().next());
                                node.asMap().forEach((key, value) -> {
                                    if (value != null) {
                                        map.put(key, value);
                                    }
                                });

                                // Add relationship info from path
                                var rels = record.get("rels");
                                if (rels != null && !rels.isNull()) {
                                    map.put("relationships", rels.asList());
                                }

                                // Decrypt sensitive properties before returning
                                return decryptSensitiveProperties(map, teamId);
                            })
                            .collect(Collectors.toList());
                }
            } catch (Neo4jException e) {
                log.error("Error finding related entities for {}:{} for team {}: {}",
                        entityType, entityId, teamId, e.getMessage());
                throw new RuntimeException("Failed to find related entities", e);
            }
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Flux<Map<String, Object>> executeQuery(String cypherQuery, Map<String, Object> parameters) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                Map<String, Object> params = parameters != null ? parameters : Collections.emptyMap();
                Result result = session.run(cypherQuery, params);
                return result.stream()
                        .map(record -> {
                            Map<String, Object> map = new HashMap<>();
                            record.keys().forEach(key -> {
                                var value = record.get(key);
                                map.put(key, value.isNull() ? null : value.asObject());
                            });
                            return map;
                        })
                        .collect(Collectors.toList());
            } catch (Neo4jException e) {
                log.error("Error executing Cypher query: {}", e.getMessage());
                throw new RuntimeException("Failed to execute query", e);
            }
        }).subscribeOn(Schedulers.boundedElastic())
                .flatMapMany(Flux::fromIterable);
    }

    @Override
    public Mono<Boolean> deleteEntity(String entityType, String entityId, String teamId) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                Map<String, Object> params = new HashMap<>();
                params.put("entityId", entityId);
                params.put("teamId", teamId);

                String cypher = "MATCH (e:" + entityType + " {id: $entityId, teamId: $teamId}) " +
                        "DETACH DELETE e " +
                        "RETURN count(e) as deleted";

                Result result = session.run(cypher, params);
                if (result.hasNext()) {
                    long deleted = result.single().get("deleted").asLong();
                    log.debug("Deleted entity {}:{} for team {}, count: {}", entityType, entityId, teamId, deleted);
                    return deleted > 0;
                }
                return false;
            } catch (Neo4jException e) {
                log.error("Error deleting entity {}:{} for team {}: {}", entityType, entityId, teamId, e.getMessage());
                throw new RuntimeException("Failed to delete entity", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Long> deleteAllEntitiesByType(String entityType, String teamId) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                Map<String, Object> params = new HashMap<>();
                params.put("teamId", teamId);

                // Use DETACH DELETE to remove entities and all their relationships
                String cypher = "MATCH (e:" + entityType + " {teamId: $teamId}) " +
                        "DETACH DELETE e " +
                        "RETURN count(e) as deleted";

                Result result = session.run(cypher, params);
                if (result.hasNext()) {
                    long deleted = result.single().get("deleted").asLong();
                    log.info("Deleted {} entities of type {} for team {}", deleted, entityType, teamId);
                    return deleted;
                }
                log.warn("No entities of type {} found for team {} to delete", entityType, teamId);
                return 0L;
            } catch (Neo4jException e) {
                log.error("Error deleting all entities of type {} for team {}: {}", entityType, teamId, e.getMessage());
                throw new RuntimeException("Failed to delete entities", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Mono<Map<String, Object>> getGraphStatistics(String teamId) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                Map<String, Object> params = new HashMap<>();
                params.put("teamId", teamId);

                // Get entity counts by type
                String entityCountQuery = "MATCH (n {teamId: $teamId}) " +
                        "RETURN labels(n)[0] as type, count(n) as count " +
                        "ORDER BY count DESC";

                // Get relationship counts by type
                // Note: teamId is stored on nodes, not relationships, so filter by from.teamId
                // and to.teamId
                String relCountQuery = "MATCH (from)-[r]->(to) " +
                        "WHERE from.teamId = $teamId AND to.teamId = $teamId " +
                        "RETURN type(r) as type, count(r) as count " +
                        "ORDER BY count DESC";

                Result entityResult = session.run(entityCountQuery, params);
                Result relResult = session.run(relCountQuery, params);

                Map<String, Object> stats = new HashMap<>();

                Map<String, Long> entityCounts = new HashMap<>();
                entityResult.forEachRemaining(record -> {
                    String type = record.get("type").asString();
                    long count = record.get("count").asLong();
                    entityCounts.put(type, count);
                });
                stats.put("entityCounts", entityCounts);
                stats.put("totalEntities", entityCounts.values().stream().mapToLong(Long::longValue).sum());

                Map<String, Long> relCounts = new HashMap<>();
                relResult.forEachRemaining(record -> {
                    String type = record.get("type").asString();
                    long count = record.get("count").asLong();
                    relCounts.put(type, count);
                });
                stats.put("relationshipCounts", relCounts);
                stats.put("totalRelationships", relCounts.values().stream().mapToLong(Long::longValue).sum());

                return stats;
            } catch (Neo4jException e) {
                log.error("Error getting graph statistics for team {}: {}", teamId, e.getMessage());
                throw new RuntimeException("Failed to get graph statistics", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * Sanitize properties by removing nested Maps and Collections.
     * Neo4j only supports primitive types (String, Number, Boolean) and arrays of
     * primitives.
     * This method recursively filters out nested structures, keeping only
     * primitives.
     */
    private Map<String, Object> sanitizeProperties(Map<String, Object> properties) {
        Map<String, Object> sanitized = new HashMap<>();
        for (Map.Entry<String, Object> entry : properties.entrySet()) {
            Object value = entry.getValue();
            // Keep only primitive types and arrays of primitives
            if (value == null) {
                sanitized.put(entry.getKey(), null);
            } else if (value instanceof String || value instanceof Number || value instanceof Boolean) {
                sanitized.put(entry.getKey(), value);
            } else if (value instanceof List) {
                // Check if list contains only primitives
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                List<Object> primitiveList = list.stream()
                        .filter(item -> item == null || item instanceof String || item instanceof Number
                                || item instanceof Boolean)
                        .collect(Collectors.toList());
                if (!primitiveList.isEmpty() || list.isEmpty()) {
                    sanitized.put(entry.getKey(), primitiveList);
                }
                // If list contains non-primitives, skip it
            } else if (value instanceof Map) {
                // Skip nested Maps - Neo4j doesn't support them
                log.debug("Skipping nested Map property '{}' for entity (Neo4j only supports primitives)",
                        entry.getKey());
            } else {
                // For other types (e.g., custom objects), try to convert to String or skip
                log.debug("Skipping non-primitive property '{}' of type {} for entity (Neo4j only supports primitives)",
                        entry.getKey(), value.getClass().getSimpleName());
            }
        }
        return sanitized;
    }

    /**
     * Encrypt sensitive properties before storing in Neo4j.
     * 
     * @param properties           The entity properties
     * @param teamId               The team ID for key derivation
     * @param encryptionKeyVersion The encryption key version to use
     * @param entityType           Optional entity type for logging (can be null)
     * @param entityId             Optional entity ID for logging (can be null)
     * @return Properties map with sensitive fields encrypted
     */
    private Map<String, Object> encryptSensitiveProperties(Map<String, Object> properties, String teamId,
            String encryptionKeyVersion, String entityType, String entityId) {
        if (chunkEncryptionService == null) {
            log.error(
                    "ChunkEncryptionService is null! Cannot encrypt entity properties. This should not happen - check service initialization.");
            throw new IllegalStateException(
                    "ChunkEncryptionService is not available. Cannot encrypt sensitive entity properties.");
        }

        Map<String, Object> encryptedProperties = new HashMap<>(properties);
        int encryptedCount = 0;
        int failedCount = 0;

        // Log all properties for debugging
        String entityInfo = (entityType != null && entityId != null)
                ? String.format("%s:%s", entityType, entityId)
                : "entity";
        log.info("Encrypting {} properties. Available properties: {}", entityInfo, properties.keySet());

        for (String key : SENSITIVE_PROPERTY_KEYS) {
            Object value = encryptedProperties.get(key);
            if (value instanceof String && !((String) value).isEmpty()) {
                try {
                    // Encrypt the property value with current key version
                    String encryptedValue = chunkEncryptionService.encryptChunkText(
                            (String) value,
                            teamId,
                            encryptionKeyVersion).block();
                    encryptedProperties.put(key, encryptedValue);
                    // Store key version per property (for decryption later)
                    encryptedProperties.put(key + "_encryption_version", encryptionKeyVersion);
                    encryptedCount++;
                    log.info("Encrypted property '{}' for {} (team: {}, key version: {})", key, entityInfo, teamId,
                            encryptionKeyVersion);
                } catch (Exception e) {
                    log.error(
                            "Failed to encrypt property '{}' for team {}: {}. This is a critical error - entity will be stored with unencrypted sensitive data!",
                            key, teamId, e.getMessage(), e);
                    failedCount++;
                    // Continue with plaintext if encryption fails (better than losing data)
                }
            } else if (encryptedProperties.containsKey(key)) {
                log.info(
                        "Property '{}' exists but is not a non-empty String (value: {}, type: {}). Skipping encryption.",
                        key, value, value != null ? value.getClass().getSimpleName() : "null");
            }
        }

        if (encryptedCount > 0) {
            log.info("Encrypted {} sensitive properties for {} (team: {}, key version: {})", encryptedCount, entityInfo,
                    teamId, encryptionKeyVersion);
        } else {
            log.warn(
                    "No sensitive properties were encrypted for {} (team: {}). Available properties: {}. SENSITIVE_PROPERTY_KEYS: {}",
                    entityInfo, teamId, properties.keySet(), SENSITIVE_PROPERTY_KEYS);
        }
        if (failedCount > 0) {
            log.error(
                    "Failed to encrypt {} sensitive properties for {} (team: {}). Entity contains unencrypted sensitive data!",
                    failedCount, entityInfo, teamId);
        }

        return encryptedProperties;
    }

    /**
     * Decrypt sensitive properties when retrieving from Neo4j.
     * 
     * @param entity The entity map with properties
     * @param teamId The team ID for key derivation
     * @return Entity map with sensitive fields decrypted
     */
    private Map<String, Object> decryptSensitiveProperties(Map<String, Object> entity, String teamId) {
        Map<String, Object> decryptedEntity = new HashMap<>(entity);

        // Get entity-level encryption version (fallback if property-level version
        // missing)
        String entityEncryptionVersion = (String) decryptedEntity.get("encryptionKeyVersion");

        for (String key : SENSITIVE_PROPERTY_KEYS) {
            Object value = decryptedEntity.get(key);
            if (value instanceof String && !((String) value).isEmpty()) {
                // Check if this property has an encryption version marker
                String propertyKeyVersion = (String) decryptedEntity.get(key + "_encryption_version");

                // Only decrypt if we have an encryption version marker (property-level or
                // entity-level)
                // If no version marker exists, assume it's legacy plaintext data
                if (propertyKeyVersion == null || propertyKeyVersion.isEmpty()) {
                    if (entityEncryptionVersion == null || entityEncryptionVersion.isEmpty()) {
                        // No encryption version marker - legacy plaintext data, skip decryption
                        decryptedEntity.remove(key + "_encryption_version");
                        continue;
                    }
                    // Use entity-level version
                    propertyKeyVersion = entityEncryptionVersion;
                }

                try {
                    // Decrypt the property value using the detected key version
                    String decryptedValue = chunkEncryptionService.decryptChunkText(
                            (String) value,
                            teamId,
                            propertyKeyVersion).block();
                    decryptedEntity.put(key, decryptedValue);
                } catch (Exception e) {
                    log.warn(
                            "Failed to decrypt property '{}' for team {} with key version '{}': {}. Returning as-is (may be legacy plaintext).",
                            key, teamId, propertyKeyVersion, e.getMessage());
                    // Continue with original value if decryption fails (may be legacy plaintext)
                }

                // Remove encryption version metadata from returned entity
                decryptedEntity.remove(key + "_encryption_version");
            } else {
                // Remove encryption version metadata even if property is null/empty
                decryptedEntity.remove(key + "_encryption_version");
            }
        }

        // Remove entity-level encryption version from returned entity (internal
        // metadata)
        decryptedEntity.remove("encryptionKeyVersion");

        return decryptedEntity;
    }

    @Override
    public Mono<Map<String, Object>> decryptProperties(Map<String, Object> properties, String teamId) {
        return Mono.fromCallable(() -> {
            if (properties == null || properties.isEmpty()) {
                return properties;
            }
            return decryptSensitiveProperties(properties, teamId);
        }).subscribeOn(Schedulers.boundedElastic());
    }
}
