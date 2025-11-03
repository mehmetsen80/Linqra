package org.lite.gateway.repository;

import org.lite.gateway.entity.KnowledgeHubDocument;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface DocumentRepository extends ReactiveMongoRepository<KnowledgeHubDocument, String> {
    
    Mono<KnowledgeHubDocument> findByDocumentId(String documentId);
    
    Flux<KnowledgeHubDocument> findByStatus(String status);
    
    Flux<KnowledgeHubDocument> findByTeamId(String teamId);
    
    Flux<KnowledgeHubDocument> findByTeamId(Sort sort, String teamId);
    
    Flux<KnowledgeHubDocument> findByCollectionId(String collectionId);
    
    Flux<KnowledgeHubDocument> findByTeamIdAndCollectionId(String teamId, String collectionId);
    
    Flux<KnowledgeHubDocument> findByTeamIdAndStatus(String teamId, String status);
    
    Flux<KnowledgeHubDocument> findByCollectionIdAndStatus(String collectionId, String status);
}

