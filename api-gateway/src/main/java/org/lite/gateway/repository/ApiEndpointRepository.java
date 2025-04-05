package org.lite.gateway.repository;

import org.lite.gateway.entity.ApiEndpoint;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ApiEndpointRepository extends ReactiveMongoRepository<ApiEndpoint, String> {
    Flux<ApiEndpoint> findByRouteIdentifier(String routeIdentifier);
    Mono<ApiEndpoint> findByRouteIdentifierAndVersion(String routeIdentifier, Integer version);
    Mono<Boolean> existsByRouteIdentifier(String routeIdentifier);
} 