package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.lite.gateway.dto.LinqProtocolExample;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.SwaggerEndpointInfo;
import org.lite.gateway.dto.SwaggerMediaType;
import org.lite.gateway.dto.SwaggerResponse;
import org.lite.gateway.entity.RoutePermission;
import org.lite.gateway.repository.ApiRouteRepository; 
import org.lite.gateway.repository.TeamRouteRepository;
import org.lite.gateway.service.LinqService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.HashMap;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinqServiceImpl implements LinqService {

    @NonNull
    private final WebClient.Builder webClientBuilder;

    @NonNull
    private final RedisTemplate<String, String> redisTemplate;

    @NonNull
    private final ObjectMapper objectMapper;

    @NonNull
    private final ApiRouteRepository apiRouteRepository;

    @NonNull
    private final TeamRouteRepository teamRouteRepository;

    @Value("${server.host:localhost}")
    private String gatewayHost;

    @Value("${server.port:7777}")
    private int gatewayPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    @Override
    public Mono<LinqResponse> processLinqRequest(LinqRequest request) {
        return validateRoutePermission(request)
            .then(Mono.defer(() -> {
                if (request.getQuery() == null || request.getQuery().getIntent().isEmpty()) {
                    return Mono.error(new IllegalArgumentException("Invalid LINQ request"));
                }

                // Handle DELETE - invalidate related cache first
                if ("delete".equalsIgnoreCase(request.getLink().getAction())) {
                    // Create a corresponding GET request to find its cache key
                    LinqRequest getFetchRequest = new LinqRequest();
                    getFetchRequest.setLink(new LinqRequest.Link());
                    getFetchRequest.getLink().setTarget(request.getLink().getTarget());
                    getFetchRequest.getLink().setAction("fetch");
                    getFetchRequest.setQuery(request.getQuery());
                    
                    String cacheKey = generateCacheKey(getFetchRequest);
                    // Delete the cache entry
                    redisTemplate.delete(cacheKey);
                    
                    return executeLinqRequest(request);
                }

                // Handle GET/fetch with caching
                if ("fetch".equalsIgnoreCase(request.getLink().getAction())) {
                    String cacheKey = generateCacheKey(request);
                    
                    return Mono.fromCallable(() -> redisTemplate.opsForValue().get(cacheKey))
                            .filter(Objects::nonNull)
                            .map(value -> {
                                try {
                                    LinqResponse response = objectMapper.readValue(value, LinqResponse.class);
                                    response.getMetadata().setCacheHit(true);
                                    return response;
                                } catch (Exception e) {
                                    throw new RuntimeException("Failed to deserialize cached LinqResponse", e);
                                }
                            })
                            .switchIfEmpty(
                                executeLinqRequest(request)
                                    .doOnNext(response -> {
                                        try {
                                            String jsonResponse = objectMapper.writeValueAsString(response);
                                            redisTemplate.opsForValue().set(cacheKey, jsonResponse, Duration.ofMinutes(5));
                                        } catch (Exception e) {
                                            throw new RuntimeException("Failed to serialize LinqResponse for cache", e);
                                        }
                                    })
                            );
                }

                // Don't cache other requests (create, update)
                return executeLinqRequest(request);
            }));
    }

    private Mono<Void> validateRoutePermission(LinqRequest request) {
        String routeIdentifier = request.getLink().getTarget();
        
        return getTeamFromContext()
            .flatMap(teamId -> {
                String permissionCacheKey = String.format("permission:%s:%s", teamId, routeIdentifier);
                
                return Mono.fromCallable(() -> redisTemplate.opsForValue().get(permissionCacheKey))
                    .filter(Objects::nonNull)
                    .switchIfEmpty(checkAndCachePermission(routeIdentifier, permissionCacheKey, teamId))
                    .filter(Boolean::parseBoolean)
                    .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.FORBIDDEN, 
                        "Team does not have USE permission for " + routeIdentifier)))
                    .then();
            });
    }

    private Mono<String> checkAndCachePermission(String routeIdentifier, String cacheKey, String teamId) {
        return apiRouteRepository.findByRouteIdentifier(routeIdentifier)
            .flatMap(apiRoute -> 
                teamRouteRepository.findByTeamIdAndRouteId(teamId, apiRoute.getId())
                    .map(teamRoute -> {
                        boolean hasUsePermission = 
                            teamRoute.getPermissions().contains(RoutePermission.USE);
                        redisTemplate.opsForValue().set(cacheKey, 
                            String.valueOf(hasUsePermission), 
                            Duration.ofMinutes(5));
                        return String.valueOf(hasUsePermission);
                    })
            )
            .switchIfEmpty(Mono.just("false"));
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
                getTeamFromContext()
                    .map(team -> {
                        LinqResponse response = new LinqResponse();
                        response.setResult(result);
                        LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                        metadata.setSource(target);
                        metadata.setStatus("success");
                        metadata.setTeam(team);
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
        
        String url = baseUrl + "/r/" + target + "/" + path;
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
        return getApiKeyFromContext()
            .flatMap(apiKey -> {
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

                return requestSpec
                    .header("X-API-Key", apiKey)
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

    private Mono<String> getApiKeyFromContext() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .filter(auth -> auth instanceof UsernamePasswordAuthenticationToken)
            .map(auth -> {
                Object credentials = auth.getCredentials();
                if (credentials instanceof String) {
                    log.debug("Found API key in security context");
                    return (String) credentials;
                }
                log.error("Invalid credentials type in security context: {}", 
                    credentials != null ? credentials.getClass() : "null");
                throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED, "Invalid API key format");
            })
            .switchIfEmpty(Mono.error(new ResponseStatusException(
                HttpStatus.UNAUTHORIZED, "No valid API key authentication found")));
    }

    private Mono<String> getTeamFromContext() {
        return ReactiveSecurityContextHolder.getContext()
            .map(SecurityContext::getAuthentication)
            .map(Authentication::getPrincipal)
            .cast(String.class);
    }

    @Override
    public Mono<LinqProtocolExample> convertToLinqProtocol(SwaggerEndpointInfo endpointInfo, String routeIdentifier) {
        return Mono.fromCallable(() -> {
            LinqProtocolExample example = new LinqProtocolExample();
            example.setSummary(endpointInfo.getSummary());
            
            // Create request example
            LinqRequest linqRequest = createExampleRequest(endpointInfo, routeIdentifier);
            example.setRequest(linqRequest);

            // Create response example
            LinqResponse response = createExampleResponse(endpointInfo, routeIdentifier);
            example.setResponse(response);

            return example;
        });
    }

    private LinqRequest createExampleRequest(SwaggerEndpointInfo endpointInfo, String routeIdentifier) {
        LinqRequest linqRequest = new LinqRequest();
        
        // Set target as routeIdentifier
        LinqRequest.Link link = new LinqRequest.Link();
        link.setTarget(routeIdentifier);
        
        // Convert HTTP method to Linq action
        switch (endpointInfo.getMethod().toUpperCase()) {
            case "GET" -> link.setAction("fetch");
            case "POST" -> link.setAction("create");
            case "PUT" -> link.setAction("update");
            case "DELETE" -> link.setAction("delete");
            case "PATCH" -> link.setAction("patch");
            case "OPTIONS" -> link.setAction("options");
            case "HEAD" -> link.setAction("head");
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + endpointInfo.getMethod());
        }
        linqRequest.setLink(link);
        
        // Set up query with parameters
        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent(endpointInfo.getPath());
        
        // Extract parameters
        Map<String, Object> params = new HashMap<>();
        if (endpointInfo.getParameters() != null) {
            endpointInfo.getParameters().forEach(param -> {
                params.put(param.getName(), param.getSchema());
            });
        }
        query.setParams(params);
        
        // Handle request body
        if (endpointInfo.getRequestBody() != null) {
            SwaggerMediaType mediaType = endpointInfo.getRequestBody().getContent().get("application/json");
            if (mediaType != null && mediaType.getExample() != null) {
                query.setPayload(mediaType.getExample());
            }
        }
        
        linqRequest.setQuery(query);
        return linqRequest;
    }

    private LinqResponse createExampleResponse(SwaggerEndpointInfo endpointInfo, String routeIdentifier) {
        LinqResponse response = new LinqResponse();
        
        // Set up metadata
        LinqResponse.Metadata metadata = new LinqResponse.Metadata();
        metadata.setSource(routeIdentifier);
        metadata.setStatus("success");
        metadata.setTeam("67d0aeb17172416c411d419e");
        metadata.setCacheHit(false);
        response.setMetadata(metadata);

        // Handle special cases first
        switch (endpointInfo.getMethod().toUpperCase()) {
            case "DELETE" -> {
                response.setResult(Map.of("message", "Resource successfully deleted"));
                return response;
            }
            case "OPTIONS" -> {
                response.setResult(Map.of("methods", List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS", "HEAD")));
                return response;
            }
            case "HEAD" -> {
                // Get the corresponding GET response to generate appropriate headers
                SwaggerResponse getResponse = endpointInfo.getResponses().entrySet().stream()
                    .filter(entry -> {
                        int statusCode = Integer.parseInt(entry.getKey());
                        return statusCode >= 200 && statusCode < 300;
                    })
                    .findFirst()
                    .map(Map.Entry::getValue)
                    .orElse(null);

                Map<String, Object> headers = new HashMap<>();
                if (getResponse != null && getResponse.getContent() != null) {
                    SwaggerMediaType mediaType = getResponse.getContent().get("application/json");
                    if (mediaType != null && mediaType.getExample() != null) {
                        // Use the example from the response directly
                        @SuppressWarnings("unchecked")
                        Map<String, Object> example = (Map<String, Object>) mediaType.getExample();
                        example.forEach((key, value) -> {
                            String headerName = "X-" + key.substring(0, 1).toUpperCase() + key.substring(1);
                            headers.put(headerName, String.valueOf(value));
                        });
                    }
                }
                
                if (headers.isEmpty()) {
                    // Fallback if no example is available
                    headers.put("X-Resource-Found", "true");
                }
                
                response.setResult(headers);
                return response;
            }
        }

        // Get the appropriate success response based on the defined responses
        Map<String, SwaggerResponse> responses = endpointInfo.getResponses();
        SwaggerResponse successResponse = responses.entrySet().stream()
            .filter(entry -> {
                int statusCode = Integer.parseInt(entry.getKey());
                return statusCode >= 200 && statusCode < 300; // Any 2xx status code
            })
            .findFirst()
            .map(Map.Entry::getValue)
            .orElse(null);

        if (successResponse != null && successResponse.getContent() != null) {
            SwaggerMediaType mediaType = successResponse.getContent().get("application/json");
            if (mediaType != null && mediaType.getExample() != null) {
                response.setResult(mediaType.getExample());
            }
        }

        return response;
    }
}