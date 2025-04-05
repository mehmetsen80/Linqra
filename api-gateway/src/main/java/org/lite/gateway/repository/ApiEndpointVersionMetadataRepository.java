package org.lite.gateway.repository;

import org.lite.gateway.entity.ApiEndpointVersionMetadata;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApiEndpointVersionMetadataRepository extends ReactiveMongoRepository<ApiEndpointVersionMetadata, String> {
    Flux<ApiEndpointVersionMetadata> findByEndpointIdOrderByVersionDesc(String endpointId);
    Flux<ApiEndpointVersionMetadata> findByRouteIdentifierOrderByVersionDesc(String routeIdentifier);
    Mono<ApiEndpointVersionMetadata> findByEndpointIdAndVersion(String endpointId, Integer version);
} 