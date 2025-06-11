package org.lite.gateway.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqTool;
import org.lite.gateway.repository.LinqToolRepository;
import org.lite.gateway.service.LinqToolService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinqToolServiceImpl implements LinqToolService {

    @NonNull
    private final LinqToolRepository linqToolRepository;

    @NonNull
    private final WebClient.Builder webClientBuilder;

    @NonNull
    private final TeamContextService teamContextService;

    @Override
    public Mono<LinqTool> saveLinqTool(LinqTool linqTool) {
        // Validate required fields
        if (linqTool.getTarget() == null || linqTool.getTarget().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqTool target is required"));
        }
        if (linqTool.getTeam() == null || linqTool.getTeam().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqTool team ID is required"));
        }
        if (linqTool.getEndpoint() == null || linqTool.getEndpoint().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqTool endpoint is required"));
        }
        if (linqTool.getMethod() == null || linqTool.getMethod().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqTool method is required"));
        }

        log.info("Saving LinqTool with target: {} for team: {}", linqTool.getTarget(), linqTool.getTeam());
        
        return linqToolRepository.findByTargetAndTeam(linqTool.getTarget(), linqTool.getTeam())
            .<LinqTool>flatMap(existingTool -> {
                // Update existing tool
                existingTool.setEndpoint(linqTool.getEndpoint());
                existingTool.setMethod(linqTool.getMethod());
                existingTool.setHeaders(linqTool.getHeaders());
                existingTool.setAuthType(linqTool.getAuthType());
                existingTool.setApiKey(linqTool.getApiKey());
                existingTool.setSupportedIntents(linqTool.getSupportedIntents());
                
                log.info("Updating existing LinqTool with ID: {}", existingTool.getId());
                return linqToolRepository.save(existingTool)
                    .doOnSuccess(saved -> log.info("Updated LinqTool with ID: {}", saved.getId()))
                    .doOnError(error -> log.error("Failed to update LinqTool: {}", error.getMessage()));
            })
            .switchIfEmpty(Mono.<LinqTool>defer(() -> {
                // Create new tool
                log.info("Creating new LinqTool for target: {} and team: {}", linqTool.getTarget(), linqTool.getTeam());
                return linqToolRepository.save(linqTool)
                    .doOnSuccess(saved -> log.info("Created new LinqTool with ID: {}", saved.getId()))
                    .doOnError(error -> log.error("Failed to create LinqTool: {}", error.getMessage()));
            }));
    }

    @Override
    public Flux<LinqTool> findByTeamId(String teamId) {
        log.info("Finding LinqTool configurations for team: {}", teamId);
        return linqToolRepository.findByTeam(teamId)
                .doOnNext(tool -> log.info("Found LinqTool configuration for team {}: target={}", teamId, tool.getTarget()))
                .doOnComplete(() -> log.info("Completed fetching LinqTool configurations for team: {}", teamId))
                .doOnError(error -> log.error("Error finding LinqTool configurations for team {}: {}", teamId, error.getMessage()));
    }

    @Override
    public Mono<LinqTool> findByTargetAndTeam(String target, String teamId) {
        log.info("Finding LinqTool configuration for target: {} and team: {}", target, teamId);
        return linqToolRepository.findByTargetAndTeam(target, teamId)
                .doOnSuccess(tool -> {
                    if (tool != null) {
                        log.info("Found LinqTool configuration for target: {} and team: {}", target, teamId);
                    } else {
                        log.warn("No LinqTool configuration found for target: {} and team: {}", target, teamId);
                    }
                })
                .doOnError(error -> log.error("Error finding LinqTool configuration for target: {} and team: {}: {}", 
                    target, teamId, error.getMessage()));
    }

    @Override
    public Mono<LinqResponse> executeToolRequest(LinqRequest request, LinqTool tool) {
        String intent = request.getQuery().getIntent();
        if (!tool.getSupportedIntents().contains(intent)) {
            return Mono.error(new IllegalArgumentException("Intent not supported by tool: " + intent));
        }

        AtomicReference<String> url = new AtomicReference<>(buildToolUrl(tool, request));
        String method = tool.getMethod();
        Object payload = buildToolPayload(request, tool);

        return Mono.just(tool.getApiKey())
                .flatMap(apiKey -> {
                    Map<String, String> headers = new HashMap<>(tool.getHeaders());
                    String authType = tool.getAuthType() != null ? tool.getAuthType() : "none";

                    switch (authType) {
                        case "bearer":
                            headers.put("Authorization", "Bearer " + apiKey);
                            break;
                        case "api_key_query":
                            url.set(url.get() + (url.get().contains("?") ? "&" : "?") + "key=" + apiKey);
                            break;
                        case "none":
                        default:
                            break;
                    }

                    // Add cache-busting header
                    headers.put("Cache-Control", "no-cache, no-store, must-revalidate");
                    headers.put("Pragma", "no-cache");
                    headers.put("Expires", "0");

                    return invokeToolService(method, url.get(), payload, headers);
                })
                .map(result -> {
                    LinqResponse response = new LinqResponse();
                    response.setResult(result);
                    LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                    metadata.setSource(tool.getTarget());
                    metadata.setStatus("success");
                    metadata.setTeam(tool.getTeam());
                    metadata.setCacheHit(false);
                    response.setMetadata(metadata);
                    return response;
                });
    }

    private String buildToolUrl(LinqTool tool, LinqRequest request) {
        String endpoint = tool.getEndpoint();
        LinqRequest.Query.ToolConfig toolConfig = request.getQuery().getToolConfig();
        if (toolConfig != null && toolConfig.getModel() != null) {
            endpoint = endpoint.replace("{model}", toolConfig.getModel());
        }
        return endpoint;
    }

    private Object buildToolPayload(LinqRequest request, LinqTool tool) {
        Map<String, Object> payload = new HashMap<>();
        LinqRequest.Query.ToolConfig toolConfig = request.getQuery().getToolConfig();
        String target = tool.getTarget();

        switch (target) {
            case "openai":
            case "mistral":
                payload.put("model", toolConfig != null && toolConfig.getModel() != null ? toolConfig.getModel() : "default");
                payload.put("messages", request.getQuery().getPayload());
                if (toolConfig != null && toolConfig.getSettings() != null) {
                    payload.putAll(toolConfig.getSettings());
                }
                break;
            case "huggingface":
                payload.put("inputs", request.getQuery().getParams().getOrDefault("prompt", ""));
                payload.put("model", toolConfig != null && toolConfig.getModel() != null ? toolConfig.getModel() : "sentence-transformers/all-MiniLM-L6-v2");
                payload.put("parameters", toolConfig != null ? toolConfig.getSettings() : new HashMap<>());
                break;
            case "gemini":
                payload.put("contents", List.of(Map.of("parts", List.of(Map.of("text", request.getQuery().getParams().getOrDefault("prompt", ""))))));
                if (toolConfig != null && toolConfig.getSettings() != null) {
                    payload.put("generationConfig", toolConfig.getSettings());
                }
                break;
            case "openai-embed":
                payload.put("input", request.getQuery().getParams().getOrDefault("text", ""));
                payload.put("model", toolConfig != null && toolConfig.getModel() != null ? toolConfig.getModel() : "text-embedding-ada-002");
                break;
            case "gemini-embed":
                payload.put("content", Map.of("parts", List.of(Map.of("text", request.getQuery().getParams().getOrDefault("text", "")))));
                payload.put("model", toolConfig != null && toolConfig.getModel() != null ? toolConfig.getModel() : "embedding-001");
                break;
            default:
                return request.getQuery().getPayload(); // Fallback to raw payload
        }
        return payload;
    }

    private Mono<Object> invokeToolService(String method, String url, Object payload, Map<String, String> headers) {
        WebClient webClient = webClientBuilder.build();
        WebClient.RequestBodySpec requestSpec = webClient.method(HttpMethod.valueOf(method))
                .uri(url)
                .headers(httpHeaders -> headers.forEach(httpHeaders::add));

        log.info("Making {} request to {} with headers: {}", method, url, headers);
        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            log.info("Request payload: {}", payload);
            requestSpec.bodyValue(payload);
        }

        return requestSpec
                .retrieve()
                .bodyToMono(Object.class)
                .doOnNext(response -> log.info("Received response from {}: {}", url, response))
                .doOnError(error -> log.error("Error calling tool {}: {}", url, error.getMessage()))
                .onErrorResume(error -> {
                    log.error("Error details for {}: {}", url, error.getMessage());
                    return Mono.just(Map.of("error", error.getMessage()));
                });
    }
}
