package org.lite.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ApiEndpoint;
import org.lite.gateway.service.ApiEndpointService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.lite.gateway.dto.SwaggerEndpointInfo;
import org.lite.gateway.dto.SwaggerSchemaInfo;

@Slf4j
@RestController
@RequestMapping("/api/endpoints")
@RequiredArgsConstructor
@Tag(name = "API Endpoints", description = "API Endpoint management operations")
public class ApiEndpointController {

    private final ApiEndpointService endpointService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ApiEndpoint> createEndpoint(@RequestBody ApiEndpoint endpoint) {
        // Generate name before validation
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("MM-dd-yyyy-HH-mm-ss"));
        endpoint.setName("Endpoint-" + timestamp);
        
        log.info("Creating endpoint with name: {} and routeIdentifier: {}", endpoint.getName(), endpoint.getRouteIdentifier());
        log.info("Swagger JSON length: {}", endpoint.getSwaggerJson() != null ? endpoint.getSwaggerJson().length() : "null");
        return endpointService.createEndpoint(endpoint);
    }

    @GetMapping("/{id}")
    public Mono<ApiEndpoint> getEndpoint(@PathVariable String id) {
        return endpointService.getEndpoint(id);
    }

    @GetMapping("/route/{routeIdentifier}")
    public Flux<ApiEndpoint> getEndpointsByRoute(@PathVariable String routeIdentifier) {
        return endpointService.getEndpointsByRouteIdentifier(routeIdentifier);
    }

    @GetMapping("/route/{routeIdentifier}/version/{version}")
    public Mono<ApiEndpoint> getEndpointVersion(
            @PathVariable String routeIdentifier,
            @PathVariable Integer version) {
        return endpointService.getEndpointVersion(routeIdentifier, version);
    }

    @PutMapping("/{id}")
    public Mono<ApiEndpoint> updateEndpoint(
            @PathVariable String id,
            @RequestBody ApiEndpoint endpoint) {
        if (endpoint.getSwaggerJson() == null || endpoint.getSwaggerJson().trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Swagger JSON is required"));
        }
        return endpointService.updateEndpoint(id, endpoint);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteEndpoint(@PathVariable String id) {
        return endpointService.deleteEndpoint(id);
    }

    @PostMapping("/{id}/versions")
    public Mono<ApiEndpoint> createNewVersion(
            @PathVariable String id,
            @RequestBody ApiEndpoint endpoint) {
        // Don't check if trim().isEmpty() since swagger JSON can be very long
        if (endpoint == null || endpoint.getSwaggerJson() == null) {
            return Mono.error(new IllegalArgumentException("Swagger JSON is required"));
        }
        
        log.debug("Creating new version for endpoint {}: {}", id, endpoint.getSwaggerJson().substring(0, 50) + "...");
        return endpointService.createNewVersion(id, endpoint);
    }

    @PostMapping("/validate")
    public Mono<Boolean> validateSwaggerJson(@RequestBody String swaggerJson) {
        return endpointService.validateSwaggerJson(swaggerJson);
    }

    @PostMapping("/extract-swagger")
    public Mono<Map<String, Object>> extractSwaggerInfo(@RequestBody String swaggerJson) {
        Mono<Map<String, List<SwaggerEndpointInfo>>> endpointsMono = endpointService.extractEndpointsByTag(swaggerJson)
            .onErrorResume(e -> {
                log.error("Error extracting endpoints: {}", e.getMessage());
                return Mono.just(Collections.emptyMap());
            });

        Mono<Map<String, SwaggerSchemaInfo>> schemasMono = endpointService.extractSchemas(swaggerJson)
            .onErrorResume(e -> {
                log.error("Error extracting schemas: {}", e.getMessage());
                return Mono.just(Collections.emptyMap());
            });

        return Mono.zip(endpointsMono, schemasMono)
            .map(tuple -> {
                Map<String, Object> result = new HashMap<>();
                result.put("endpointsByTag", tuple.getT1());
                result.put("schemas", tuple.getT2());
                return result;
            });
    }

    @GetMapping("/route/{routeIdentifier}/versions")
    @Operation(summary = "Get all versions of an endpoint by route identifier")
    public Mono<List<ApiEndpoint>> getEndpointVersions(@PathVariable String routeIdentifier) {
        return endpointService.getEndpointVersionsByRoute(routeIdentifier)
            .collectList();
    }
} 