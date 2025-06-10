package org.lite.gateway.service;

import org.lite.gateway.dto.SwaggerEndpointInfo;
import org.lite.gateway.dto.SwaggerSchemaInfo;
import org.lite.gateway.entity.ApiEndpoint;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

public interface ApiEndpointService {
    // Create new endpoint
    Mono<ApiEndpoint> createEndpoint(ApiEndpoint endpoint);

    // Get endpoint by ID
    Mono<ApiEndpoint> getEndpoint(String id);

    // Get all endpoints for a route
    Flux<ApiEndpoint> getEndpointsByRouteIdentifier(String routeIdentifier);

    // Get specific version of an endpoint
    Mono<ApiEndpoint> getEndpointVersion(String routeIdentifier, Integer version);

    // Update endpoint signature
    Mono<ApiEndpoint> updateEndpoint(String id, ApiEndpoint endpoint);

    // Delete endpoint
    Mono<Void> deleteEndpoint(String id);

    // Create new version - simplified signature
    Mono<ApiEndpoint> createNewVersion(String id, ApiEndpoint endpoint);

    // Validate OpenAPI/Swagger JSON
    Mono<Boolean> validateSwaggerJson(String swaggerJson);

    // Extract endpoints by tag
    Mono<Map<String, List<SwaggerEndpointInfo>>> extractEndpointsByTag(String swaggerJson);

    // Extract schemas
    Mono<Map<String, SwaggerSchemaInfo>> extractSchemas(String swaggerJson);

    // Get endpoint versions by route
    Flux<ApiEndpoint> getEndpointVersionsByRoute(String routeIdentifier);
} 