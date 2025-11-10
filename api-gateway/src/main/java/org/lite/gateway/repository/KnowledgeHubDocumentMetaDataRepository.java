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
     * Find the most recent metadata extract for a document/team/collection combination
     */
    Mono<KnowledgeHubDocumentMetaData> findTopByDocumentIdAndTeamIdOrderByExtractedAtDesc(String documentId, String teamId);

    Mono<KnowledgeHubDocumentMetaData> findTopByDocumentIdAndTeamIdAndCollectionIdOrderByExtractedAtDesc(String documentId, String teamId, String collectionId);
    
    /**
     * Delete metadata extract by document ID
     */
    Mono<Void> deleteByDocumentId(String documentId);

    /**
     * Delete metadata entries by document, team, and collection.
     */
    Mono<Void> deleteByDocumentIdAndTeamIdAndCollectionId(String documentId, String teamId, String collectionId);
}

