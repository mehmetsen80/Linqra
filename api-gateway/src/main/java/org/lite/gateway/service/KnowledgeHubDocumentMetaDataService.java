package org.lite.gateway.service;

import org.lite.gateway.entity.KnowledgeHubDocumentMetaData;
import reactor.core.publisher.Mono;

public interface KnowledgeHubDocumentMetaDataService {
    
    /**
     * Extract metadata from a processed document
     * This will read the processed JSON and extract metadata from it
     */
    Mono<KnowledgeHubDocumentMetaData> extractMetadata(String documentId, String teamId);
    
    /**
     * Get metadata extract by document ID with team access control
     */
    Mono<KnowledgeHubDocumentMetaData> getMetadataExtract(String documentId, String teamId);
    
    /**
     * Delete metadata extract by document ID
     */
    Mono<Void> deleteMetadataExtract(String documentId);
}

