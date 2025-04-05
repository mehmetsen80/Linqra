package org.lite.gateway.repository;

import org.lite.gateway.entity.ApiEndpointVersion;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ApiEndpointVersionRepository extends ReactiveMongoRepository<ApiEndpointVersion, String> {
    Mono<ApiEndpointVersion> findByEndpointIdAndVersion(String endpointId, Integer version);
    Flux<ApiEndpointVersion> findByRouteIdentifier(String routeIdentifier);
    Mono<ApiEndpointVersion> findByRouteIdentifierAndVersion(String routeIdentifier, Integer version);
} 