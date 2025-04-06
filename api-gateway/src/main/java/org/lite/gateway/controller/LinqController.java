package org.lite.gateway.controller;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.SwaggerEndpointInfo;
import org.lite.gateway.dto.SwaggerSchemaInfo;
import org.lite.gateway.dto.LinqProtocolExample;
import org.lite.gateway.dto.ConvertToLinqProtocolRequest;
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
@RequestMapping("/api/v1/linq")
@Slf4j
@AllArgsConstructor
public class LinqController {

    private final LinqService linqService;
    private final ApiEndpointService apiEndpointService;

    @PostMapping
    public Mono<LinqResponse> handleLinqRequest(@RequestBody LinqRequest request) {
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
                                    // Create schema info for this endpoint
                                    Map<String, Object> schemaInfo = new HashMap<>();
                                    schemaInfo.put("requestSchema", schemas.get(endpointInfo.getPath() + "_request"));
                                    schemaInfo.put("responseSchema", schemas.get(endpointInfo.getPath() + "_response"));
                                    schemaInfo.put("summary", endpointInfo.getSummary());
                                    schemaInfo.put("parameters", endpointInfo.getParameters());
                                    
                                    // Convert to Linq format
                                    return linqService.convertToLinqProtocol(
                                        endpointInfo.getMethod(),
                                        endpointInfo.getPath(),
                                        schemaInfo,
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
