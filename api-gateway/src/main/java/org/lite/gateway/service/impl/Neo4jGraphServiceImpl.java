package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public Mono<String> upsertEntity(String entityType, String entityId, Map<String, Object> properties, String teamId) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                // Build MERGE query with teamId for multi-tenant isolation
                Map<String, Object> params = new HashMap<>(properties);
                params.put("entityId", entityId);
                params.put("teamId", teamId);
                
                // Remove createdAt/updatedAt from properties to avoid conflicts with our timestamp logic
                // Also filter out nested Maps/Collections as Neo4j only supports primitive types
                Map<String, Object> cleanProperties = sanitizeProperties(properties);
                cleanProperties.remove("createdAt");
                cleanProperties.remove("updatedAt");
                
                StringBuilder cypher = new StringBuilder();
                cypher.append("MERGE (e:").append(entityType).append(" {id: $entityId, teamId: $teamId}) ");
                // ON CREATE and ON MATCH must come immediately after MERGE
                cypher.append("ON CREATE SET e.createdAt = timestamp(), e += $properties ");
                cypher.append("ON MATCH SET e.updatedAt = timestamp(), e += $properties ");
                cypher.append("RETURN e.id as id");
                
                params.put("properties", cleanProperties);
                
                Result result = session.run(cypher.toString(), params);
                if (result.hasNext()) {
                    // Use next() instead of single() to handle cases where multiple records might exist
                    // (e.g., duplicate nodes with same id but different labels)
                    String id = result.next().get("id").asString();
                    log.info("Upserted entity {}:{} for team {}", entityType, id, teamId);
                    // If there are more records, log a warning (shouldn't happen with MERGE, but defensive)
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
                    relationshipType, fromEntityType, fromEntityId, toEntityType, toEntityId, teamId, e.getMessage());
                throw new RuntimeException("Failed to upsert relationship", e);
            }
        }).subscribeOn(Schedulers.boundedElastic());
    }

    @Override
    public Flux<Map<String, Object>> findEntities(String entityType, Map<String, Object> filters, String teamId) {
        return Mono.fromCallable(() -> {
            try (Session session = neo4jDriver.session()) {
                Map<String, Object> params = new HashMap<>();
                params.put("teamId", teamId);
                
                StringBuilder cypher = new StringBuilder();
                cypher.append("MATCH (e:").append(entityType).append(" {teamId: $teamId}) ");
                
                if (filters != null && !filters.isEmpty()) {
                    cypher.append("WHERE ");
                    int index = 0;
                    boolean first = true;
                    for (Map.Entry<String, Object> filter : filters.entrySet()) {
                        if (!first) {
                            cypher.append("AND ");
                        }
                        String paramName = "filter" + index++;
                        cypher.append("e.").append(filter.getKey()).append(" = $").append(paramName).append(" ");
                        params.put(paramName, filter.getValue());
                        first = false;
                    }
                }
                
                cypher.append("RETURN e");
                
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
                        return map;
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
                            
                            return map;
                        })
                        .collect(Collectors.toList());
                } else {
                    // For multi-hop paths (maxDepth > 1), use path traversal
                    params.put("maxDepth", maxDepth);
                    StringBuilder cypher = new StringBuilder();
                    cypher.append("MATCH path = (start:").append(entityType).append(" {id: $entityId, teamId: $teamId})");
                    
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
                            return map;
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
                // Note: teamId is stored on nodes, not relationships, so filter by from.teamId and to.teamId
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
     * Neo4j only supports primitive types (String, Number, Boolean) and arrays of primitives.
     * This method recursively filters out nested structures, keeping only primitives.
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
                        .filter(item -> item == null || item instanceof String || item instanceof Number || item instanceof Boolean)
                        .collect(Collectors.toList());
                if (!primitiveList.isEmpty() || list.isEmpty()) {
                    sanitized.put(entry.getKey(), primitiveList);
                }
                // If list contains non-primitives, skip it
            } else if (value instanceof Map) {
                // Skip nested Maps - Neo4j doesn't support them
                log.debug("Skipping nested Map property '{}' for entity (Neo4j only supports primitives)", entry.getKey());
            } else {
                // For other types (e.g., custom objects), try to convert to String or skip
                log.debug("Skipping non-primitive property '{}' of type {} for entity (Neo4j only supports primitives)", 
                        entry.getKey(), value.getClass().getSimpleName());
            }
        }
        return sanitized;
    }
}

