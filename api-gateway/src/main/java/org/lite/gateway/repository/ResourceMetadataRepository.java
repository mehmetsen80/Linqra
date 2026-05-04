package org.lite.gateway.repository;

import org.lite.gateway.entity.ResourceMetadata;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface ResourceMetadataRepository extends ReactiveMongoRepository<ResourceMetadata, String> {
    Mono<Boolean> existsByDomainAndCategoryAndResourceId(String domain, String category, String resourceId);

    Mono<ResourceMetadata> findByDomainAndCategoryAndResourceId(String domain, String category, String resourceId);
}
