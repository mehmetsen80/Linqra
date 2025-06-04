package org.lite.gateway.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.SwaggerEndpointInfo;
import org.lite.gateway.dto.SwaggerSchemaInfo;
import org.lite.gateway.dto.LinqProtocolExample;
import org.lite.gateway.dto.ConvertToLinqProtocolRequest;
import org.lite.gateway.service.LinqProtocolService;
import org.lite.gateway.service.LinqService;
import org.lite.gateway.service.ApiEndpointService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/linq")
@Slf4j
@AllArgsConstructor
public class LinqController {

    private final LinqService linqService;
    private final LinqProtocolService linqProtocolService;
    private final ApiEndpointService apiEndpointService;

    @PostMapping
    public Mono<LinqResponse> handleLinqRequest(@RequestBody LinqRequest request) {
        log.info("Received /linq request: {}", request);
        log.info("Request link: {}", request.getLink());
        log.info("Request query: {}", request.getQuery());
        if (request.getQuery() != null && request.getQuery().getWorkflow() != null) {
            log.info("Workflow steps: {}", request.getQuery().getWorkflow());
            if (request.getQuery().getWorkflow().size() > 1) {
                log.info("Step 2 toolConfig: {}", request.getQuery().getWorkflow().get(1).getToolConfig());
            }
        }
        return linqService.processLinqRequest(request);
    }

    @PostMapping("/convert")
    public Mono<Map<String, Object>> convertToLinqProtocol(@RequestBody ConvertToLinqProtocolRequest request) {
        return apiEndpointService.getEndpointsByRouteIdentifier(request.getRouteIdentifier())
            .filter(endpoint -> endpoint.getSwaggerJson() != null)
            .next()
            .flatMap(endpoint -> {
                String swaggerJson = endpoint.getSwaggerJson();
                
                // Get endpoints by tag
                Mono<Map<String, List<SwaggerEndpointInfo>>> endpointsMono = 
                    apiEndpointService.extractEndpointsByTag(swaggerJson)
                        .onErrorResume(e -> {
                            log.error("Error extracting endpoints: {}", e.getMessage());
                            return Mono.just(Collections.emptyMap());
                        });

                // Get schemas
                Mono<Map<String, SwaggerSchemaInfo>> schemasMono = 
                    apiEndpointService.extractSchemas(swaggerJson)
                        .onErrorResume(e -> {
                            log.error("Error extracting schemas: {}", e.getMessage());
                            return Mono.just(Collections.emptyMap());
                        });

                // Combine and convert to Linq format
                return Mono.zip(endpointsMono, schemasMono)
                    .flatMap(tuple -> {
                        Map<String, List<SwaggerEndpointInfo>> endpointsByTag = tuple.getT1();
                        Map<String, SwaggerSchemaInfo> schemas = tuple.getT2();
                        
                        // Convert each endpoint to Linq format
                        Map<String, List<LinqProtocolExample>> linqEndpointsByTag = new HashMap<>();
                        
                        endpointsByTag.forEach((tag, endpoints) -> {
                            List<LinqProtocolExample> linqEndpoints = endpoints.stream()
                                .map(endpointInfo -> {
                                    // Just pass the endpointInfo and routeIdentifier
                                    return linqProtocolService.convertToLinqProtocol(
                                        endpointInfo,
                                        request.getRouteIdentifier()
                                    ).block();
                                })
                                .collect(Collectors.toList());
                                
                            linqEndpointsByTag.put(tag, linqEndpoints);
                        });

                        Map<String, Object> result = new HashMap<>();
                        result.put("endpointsByTag", linqEndpointsByTag);
                        result.put("schemas", schemas);
                        
                        return Mono.just(result);
                    });
            });
    }
}
