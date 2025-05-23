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
        return linqToolRepository.save(linqTool)
                .doOnSuccess(saved -> log.info("Saved LinqTool with ID: {}", saved.getId()))
                .doOnError(error -> log.error("Failed to save LinqTool: {}", error.getMessage()));
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

                    return invokeToolService(method, url.get(), payload, headers);
                })
                .flatMap(result ->
                        teamContextService.getTeamFromContext()
                                .map(team -> {
                                    LinqResponse response = new LinqResponse();
                                    response.setResult(result);
                                    LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                                    metadata.setSource(tool.getTarget());
                                    metadata.setStatus("success");
                                    metadata.setTeam(team);
                                    metadata.setCacheHit(false);
                                    response.setMetadata(metadata);
                                    return response;
                                })
                );
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
                payload.put("parameters", toolConfig != null ? toolConfig.getSettings() : new HashMap<>());
                break;
            case "gemini":
                payload.put("contents", List.of(Map.of("parts", List.of(Map.of("text", request.getQuery().getParams().getOrDefault("prompt", ""))))));
                if (toolConfig != null && toolConfig.getSettings() != null) {
                    payload.put("generationConfig", toolConfig.getSettings());
                }
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

        if ("POST".equals(method) || "PUT".equals(method) || "PATCH".equals(method)) {
            requestSpec.bodyValue(payload);
        }

        return requestSpec
                .retrieve()
                .bodyToMono(Object.class)
                .doOnError(error -> log.error("Error calling tool {}: {}", url, error.getMessage()))
                .onErrorResume(error -> Mono.just(Map.of("error", error.getMessage())));
    }
}
