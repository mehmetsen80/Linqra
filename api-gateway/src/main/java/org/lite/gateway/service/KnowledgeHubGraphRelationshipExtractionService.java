package org.lite.gateway.service;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Service for extracting relationships between entities in the Knowledge Graph
 */
public interface KnowledgeHubGraphRelationshipExtractionService {
    
    /**
     * Extract relationships for entities in a document
     * @param documentId The document ID to extract relationships for
     * @param teamId The team ID for access control
     * @return Mono indicating completion with count of extracted relationships
     */
    Mono<Integer> extractRelationshipsFromDocument(String documentId, String teamId);
    
    /**
     * Extract relationships for entities in a document (with force option)
     * @param documentId The document ID to extract relationships for
     * @param teamId The team ID for access control
     * @param force Force re-extraction even if already extracted
     * @return Mono indicating completion with count of extracted relationships
     */
    Mono<Integer> extractRelationshipsFromDocument(String documentId, String teamId, boolean force);
    
    /**
     * Extract relationships between specific entities using LLM
     * @param entities List of entities to analyze for relationships
     * @param documentId The document ID these entities belong to
     * @param teamId The team ID for access control
     * @return Mono with list of relationships (each relationship is a map with from, to, type, properties)
     */
    Mono<List<Map<String, Object>>> extractRelationshipsFromEntities(
            List<Map<String, Object>> entities, String documentId, String teamId);
}

