package org.lite.gateway.repository;

import org.lite.gateway.entity.KnowledgeHubDocumentVersion;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface KnowledgeHubDocumentVersionRepository
        extends ReactiveMongoRepository<KnowledgeHubDocumentVersion, String> {

    Flux<KnowledgeHubDocumentVersion> findAllByDocumentIdOrderByVersionNumberDesc(String documentId);

    Mono<KnowledgeHubDocumentVersion> findByDocumentIdAndVersionNumber(String documentId, Integer versionNumber);

    Mono<Void> deleteAllByDocumentId(String documentId);
}
