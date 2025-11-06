package org.lite.gateway.repository;

import org.lite.gateway.entity.KnowledgeHubDocumentMetaData;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface KnowledgeHubDocumentMetaDataRepository extends ReactiveMongoRepository<KnowledgeHubDocumentMetaData, String> {
    
    /**
     * Find metadata extract by document ID
     */
    Mono<KnowledgeHubDocumentMetaData> findByDocumentId(String documentId);
    
    /**
     * Find metadata extract by document ID and team ID (with access control)
     */
    Mono<KnowledgeHubDocumentMetaData> findByDocumentIdAndTeamId(String documentId, String teamId);
    
    /**
     * Delete metadata extract by document ID
     */
    Mono<Void> deleteByDocumentId(String documentId);
}

