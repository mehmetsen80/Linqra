package org.lite.gateway.service;

import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

/**
 * Service for extracting entities from Knowledge Hub documents
 */
public interface KnowledgeHubGraphEntityExtractionService {
    
    /**
     * Extract entities from document chunks using LLM
     * @param documentId The document ID to extract entities from
     * @param teamId The team ID for access control
     * @return Mono indicating completion with count of extracted entities
     */
    Mono<Integer> extractEntitiesFromDocument(String documentId, String teamId);
    
    /**
     * Extract entities from text content using LLM
     * @param text The text content to analyze
     * @param documentId The document ID this text belongs to
     * @param teamId The team ID for access control
     * @return Mono with list of extracted entities (each entity is a map with type, id, properties)
     */
    Mono<List<Map<String, Object>>> extractEntitiesFromText(String text, String documentId, String teamId);
}

