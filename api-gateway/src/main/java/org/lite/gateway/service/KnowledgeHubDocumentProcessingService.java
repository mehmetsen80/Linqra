package org.lite.gateway.service;

import reactor.core.publisher.Mono;

/**
 * Service for processing uploaded documents
 */
public interface KnowledgeHubDocumentProcessingService {
    
    /**
     * Process a document: download, parse, chunk, and store
     * @param documentId The document ID to process
     * @param teamId The team ID for access control
     * @return Mono that completes when processing is done
     */
    Mono<Void> processDocument(String documentId, String teamId);
}

