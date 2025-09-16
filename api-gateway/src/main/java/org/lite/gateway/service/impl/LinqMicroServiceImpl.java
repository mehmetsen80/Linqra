package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ApiKeyPair;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.service.LinqMicroService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.ApiKeyContextService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinqMicroServiceImpl implements LinqMicroService {

    @NonNull
    private final WebClient.Builder webClientBuilder;

    @NonNull
    private final TeamContextService teamContextService;

    @NonNull
    private final RedisTemplate<String, String> redisTemplate;

    @NonNull
    private final ObjectMapper objectMapper;

    @NonNull
    private final ApiKeyContextService apiKeyContextService;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    @Value("${server.host:localhost}")
    private String gatewayHost;

    @Value("${server.port:7777}")
    private int gatewayPort;

    @Override
    public Mono<LinqResponse> execute(LinqRequest request) {
        String action = request.getLink().getAction().toLowerCase();
        if ("delete".equalsIgnoreCase(action)) {
            // Invalidate cache for DELETE
            LinqRequest getFetchRequest = new LinqRequest();
            getFetchRequest.setLink(new LinqRequest.Link());
            getFetchRequest.getLink().setTarget(request.getLink().getTarget());
            getFetchRequest.getLink().setAction("fetch");
            getFetchRequest.setQuery(request.getQuery());
            String cacheKey = generateCacheKey(getFetchRequest);
            redisTemplate.delete(cacheKey);
            return executeLinqRequest(request);
        }

        // Check if this is a fetch action
        if ("fetch".equalsIgnoreCase(action)) {
            // First check if this is part of a workflow step
            if (request.getQuery() != null && request.getQuery().getWorkflow() != null && !request.getQuery().getWorkflow().isEmpty()) {
                // Find the current step
                LinqRequest.Query.WorkflowStep currentStep = request.getQuery().getWorkflow().stream()
                    .filter(step -> step.getTarget().equals(request.getLink().getTarget()) &&
                                  step.getAction().equals(request.getLink().getAction()))
                    .findFirst()
                    .orElse(null);

                // Check if caching is enabled for this step
                if (currentStep != null && 
                    currentStep.getCacheConfig() != null && 
                    currentStep.getCacheConfig().isEnabled()) {
                    
                    // Use custom cache key if provided, otherwise generate one
                    String cacheKey = currentStep.getCacheConfig().getKey() != null ? 
                        currentStep.getCacheConfig().getKey() : 
                        generateCacheKey(request);
                    
                    log.info("Checking cache for workflow step with key: {}", cacheKey);
                    
                    return Mono.fromCallable(() -> redisTemplate.opsForValue().get(cacheKey))
                        .filter(Objects::nonNull)
                        .map(value -> {
                            try {
                                log.info("Cache hit for workflow step with key: {}", cacheKey);
                                LinqResponse response = objectMapper.readValue(value, LinqResponse.class);
                                response.getMetadata().setCacheHit(true);
                                return response;
                            } catch (Exception e) {
                                log.error("Failed to deserialize cached LinqResponse", e);
                                throw new RuntimeException("Failed to deserialize cached LinqResponse", e);
                            }
                        })
                        .switchIfEmpty(
                            executeLinqRequest(request)
                                .doOnNext(response -> {
                                    try {
                                        log.info("Cache miss for workflow step with key: {}, storing in cache", cacheKey);
                                        String jsonResponse = objectMapper.writeValueAsString(response);
                                        Duration ttl = currentStep.getCacheConfig().getTtlAsDuration();
                                        redisTemplate.opsForValue().set(cacheKey, jsonResponse, ttl);
                                    } catch (Exception e) {
                                        log.error("Failed to serialize LinqResponse for cache", e);
                                        throw new RuntimeException("Failed to serialize LinqResponse for cache", e);
                                    }
                                })
                        );
                }
            }

            // If caching is not explicitly enabled, execute without caching
            log.info("Caching not enabled for request, executing directly");
            return executeLinqRequest(request);
        }

        return executeLinqRequest(request);
    }

    private Mono<LinqResponse> executeLinqRequest(LinqRequest request) {
        String target = request.getLink().getTarget();
        String intent = request.getQuery().getIntent();
        String url = buildUrl(target, intent, request.getQuery().getParams());
        String action = request.getLink().getAction().toLowerCase();
        String method = switch (action) {
            case "fetch" -> "GET";
            case "create" -> "POST";
            case "update" -> "PUT";
            case "delete" -> "DELETE";
            case "patch" -> "PATCH";
            case "options" -> "OPTIONS";
            case "head" -> "HEAD";
            default -> throw new IllegalArgumentException("Unsupported action: " + action);
        };

        return invokeService(method, url, request)
                .flatMap(result ->
                        teamContextService.getTeamFromContext()
                                .map(teamId -> {
                                    LinqResponse response = new LinqResponse();
                                    response.setResult(result);
                                    LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                                    metadata.setSource(target);
                                    metadata.setStatus("success");
                                    metadata.setTeamId(teamId);
                                    metadata.setCacheHit(false);
                                    response.setMetadata(metadata);
                                    return response;
                                })
                );
    }

    private String buildUrl(String target, String intent, Map<String, Object> params) {
        String protocol = sslEnabled ? "https" : "http";
        String baseUrl = String.format("%s://%s:%d", protocol, gatewayHost, gatewayPort);

        // Handle path variables first
        String path = intent;
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", entry.getValue().toString());
            }
        }

        // Special handling for api-gateway target - call API directly without /r/ prefix
        String url;
        if ("api-gateway".equals(target)) {
            url = baseUrl + "/" + path;
        } else {
            url = baseUrl + "/r/" + target + "/" + path;
        }
        
        log.info("Linq url: {}", url);

        // Add remaining params as query parameters
        Map<String, String> queryParams = params != null ?
                params.entrySet().stream()
                        .filter(e -> !intent.contains("{" + e.getKey() + "}"))
                        .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString()))
                : Map.of();

        if (!queryParams.isEmpty()) {
            url += "?" + queryParams.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining("&"));
        }

        return url;
    }

    private Mono<Object> invokeService(String method, String url, LinqRequest request) {
        log.info("invokeService called for URL: {}", url);
        return apiKeyContextService.getApiKeyFromContext()
                .doOnNext(apiKeyPair -> log.info("API key from context: {}", apiKeyPair != null ? "present" : "null"))
                .doOnNext(apiKeyPair -> {
                    if (apiKeyPair != null) {
                        log.info("API key pair type: {}", apiKeyPair.getClass().getSimpleName());
                        if (apiKeyPair instanceof ApiKeyPair) {
                            ApiKeyPair keyPair = (ApiKeyPair) apiKeyPair;
                            log.info("API key name: {}", keyPair.getName());
                            log.info("API key key: {}", keyPair.getKey() != null ? "present" : "null");
                        }
                    }
                })
                .flatMap(apiKeyPair -> {
                    ApiKeyPair keyPair = (ApiKeyPair) apiKeyPair;
                    log.debug("Making {} request to {} with API key present", method, url);
                    WebClient webClient = webClientBuilder.build();

                    WebClient.RequestHeadersSpec<?> requestSpec = switch (method) {
                        case "GET" -> webClient.get().uri(url);
                        case "POST" -> webClient.post().uri(url)
                                .bodyValue(request.getQuery().getPayload());
                        case "PUT" -> webClient.put().uri(url)
                                .bodyValue(request.getQuery().getPayload());
                        case "DELETE" -> webClient.delete().uri(url);
                        case "PATCH" -> webClient.patch().uri(url)
                                .bodyValue(request.getQuery().getPayload());
                        case "OPTIONS" -> webClient.options().uri(url);
                        case "HEAD" -> webClient.head().uri(url);
                        default -> throw new IllegalArgumentException("Method not supported: " + method);
                    };

                    // Add API key header
                    requestSpec = requestSpec
                            .header("x-api-key", keyPair.getKey())
                            .header("x-api-key-name", keyPair.getName());
                    
                    // Add executedBy header if present (for user context in workflow steps)
                    if (request.getExecutedBy() != null) {
                        requestSpec = requestSpec.header("X-Executed-By", request.getExecutedBy());
                        log.debug("Added X-Executed-By header: {}", request.getExecutedBy());
                    }

                    return requestSpec
                            .exchangeToMono(response -> {
                                if (response.statusCode().is2xxSuccessful()) {
                                    return response.bodyToMono(Object.class)
                                            .switchIfEmpty(Mono.just(Map.of(
                                                    "message", method.equals("DELETE") ?
                                                            "Resource successfully deleted" :
                                                            "Success but no content"
                                            )));
                                } else {
                                    return response.bodyToMono(String.class)
                                            .flatMap(error -> Mono.error(new RuntimeException(
                                                    "Service returned " + response.statusCode() + ": " + error)));
                                }
                            });
                })
                .doOnError(error -> log.error("Error calling service {}: {}", url, error.getMessage()))
                .onErrorResume(error -> Mono.just(Map.of("error", error.getMessage())));
    }

    private String generateCacheKey(LinqRequest request) {
        // Build the actual URL path that would be cached
        String path = request.getQuery().getIntent();
        Map<String, Object> params = request.getQuery().getParams();

        // Replace path variables
        if (params != null) {
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                path = path.replace("{" + entry.getKey() + "}", entry.getValue().toString());
            }
        }

        // Create cache key using target and resolved path
        return "linq:" + request.getLink().getTarget() + ":" + path;
    }
}
