package org.lite.gateway.service;

import reactor.core.publisher.Mono;

public interface KnowledgeHubDocumentEmbeddingService {

    /**
     * Trigger embedding workflow for a document. Validates prerequisites, updates document status,
     * generates embeddings, stores them in Milvus and updates document metadata.
     *
     * @param documentId Document identifier (KnowledgeHubDocument.documentId)
     * @param teamId Team requesting the embedding (used for access control)
     * @return Mono that completes once embeddings are stored and document status updated
     */
    Mono<Void> embedDocument(String documentId, String teamId);
}

