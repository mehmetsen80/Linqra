package org.lite.gateway.repository;

import org.lite.gateway.entity.KnowledgeHubChunk;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface KnowledgeHubChunkRepository extends ReactiveMongoRepository<KnowledgeHubChunk, String> {
    
    /**
     * Find all chunks for a specific document
     */
    Flux<KnowledgeHubChunk> findByDocumentId(String documentId);
    
    /**
     * Find all chunks for a specific document and team (with access control)
     */
    Flux<KnowledgeHubChunk> findByDocumentIdAndTeamId(String documentId, String teamId);
    
    /**
     * Count chunks for a specific document
     */
    Mono<Long> countByDocumentId(String documentId);
    
    /**
     * Delete all chunks for a specific document
     */
    Mono<Void> deleteAllByDocumentId(String documentId);
    
    /**
     * Find all chunks for multiple documents
     */
    Flux<KnowledgeHubChunk> findByDocumentIdIn(Iterable<String> documentIds);
}

