package org.lite.gateway.service;

import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.List;

/**
 * Service for enriching AI Assistant context with Knowledge Graph information.
 * Extracts entities from user queries and enriches chat context with graph data.
 */
public interface KnowledgeHubGraphContextService {

    /**
     * Enrich chat context with Knowledge Graph information based on user query.
     * Extracts entities from the query and retrieves relevant graph data.
     * 
     * @param userMessage The user's message/query
     * @param teamId Team ID for multi-tenant isolation
     * @return Mono containing enriched context map with entities, relationships, and summaries
     */
    Mono<Map<String, Object>> enrichContextWithGraph(String userMessage, String teamId);

    /**
     * Extract entities mentioned in a user query.
     * Uses lightweight extraction to identify potential entity mentions.
     * 
     * @param query The user query
     * @param teamId Team ID for multi-tenant isolation
     * @return Mono containing list of entity mentions (type, name/id, confidence)
     */
    Mono<List<Map<String, Object>>> extractEntityMentions(String query, String teamId);

    /**
     * Query Knowledge Graph for entities matching the extracted mentions.
     * 
     * @param entityMentions List of entity mentions from query
     * @param teamId Team ID for multi-tenant isolation
     * @return Mono containing map of entity info (entities, relationships, related documents)
     */
    Mono<Map<String, Object>> queryGraphForEntities(List<Map<String, Object>> entityMentions, String teamId);
}

