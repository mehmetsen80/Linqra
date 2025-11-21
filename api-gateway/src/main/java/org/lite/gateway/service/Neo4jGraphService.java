package org.lite.gateway.service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Service for Knowledge Graph operations using Neo4j
 */
public interface Neo4jGraphService {

    /**
     * Create or update an entity node in the graph
     * @param entityType Type/label of the entity (e.g., "Document", "Form", "Person", "Organization")
     * @param entityId Unique identifier for the entity
     * @param properties Map of properties for the entity
     * @param teamId Team ID for multi-tenant isolation
     * @return Mono with the created/updated entity ID
     */
    Mono<String> upsertEntity(String entityType, String entityId, Map<String, Object> properties, String teamId);

    /**
     * Create or update a relationship between two entities
     * @param fromEntityType Type of the source entity
     * @param fromEntityId ID of the source entity
     * @param relationshipType Type of the relationship (e.g., "MENTIONS", "REQUIRES", "RELATED_TO")
     * @param toEntityType Type of the target entity
     * @param toEntityId ID of the target entity
     * @param properties Optional properties for the relationship
     * @param teamId Team ID for multi-tenant isolation
     * @return Mono with success status
     */
    Mono<Boolean> upsertRelationship(
            String fromEntityType, String fromEntityId,
            String relationshipType,
            String toEntityType, String toEntityId,
            Map<String, Object> properties,
            String teamId
    );

    /**
     * Find entities by type and optional filters
     * @param entityType Type of entities to find
     * @param filters Optional property filters
     * @param teamId Team ID for multi-tenant isolation
     * @return Flux of entity maps
     */
    Flux<Map<String, Object>> findEntities(String entityType, Map<String, Object> filters, String teamId);

    /**
     * Find related entities for a given entity
     * @param entityType Type of the source entity
     * @param entityId ID of the source entity
     * @param relationshipType Optional relationship type filter (null for any relationship)
     * @param maxDepth Maximum depth to traverse (default 1)
     * @param teamId Team ID for multi-tenant isolation
     * @return Flux of related entity maps with relationship info
     */
    Flux<Map<String, Object>> findRelatedEntities(
            String entityType, String entityId,
            String relationshipType,
            int maxDepth,
            String teamId
    );

    /**
     * Execute a custom Cypher query
     * @param cypherQuery Cypher query string
     * @param parameters Query parameters
     * @return Flux of result maps
     */
    Flux<Map<String, Object>> executeQuery(String cypherQuery, Map<String, Object> parameters);

    /**
     * Delete an entity and all its relationships
     * @param entityType Type of the entity
     * @param entityId ID of the entity
     * @param teamId Team ID for multi-tenant isolation
     * @return Mono with success status
     */
    Mono<Boolean> deleteEntity(String entityType, String entityId, String teamId);

    /**
     * Get graph statistics for a team
     * @param teamId Team ID
     * @return Mono with statistics map (entity counts, relationship counts, etc.)
     */
    Mono<Map<String, Object>> getGraphStatistics(String teamId);
}

