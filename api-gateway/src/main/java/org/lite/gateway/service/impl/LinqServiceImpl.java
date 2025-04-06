package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.lite.gateway.dto.LinqProtocolExample;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.SwaggerParameter;
import org.lite.gateway.dto.SwaggerSchema;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Value("${spring.server.host:localhost}")
    private String gatewayHost;

    @Value("${spring.server.port:7777}")
    private int gatewayPort;

    @Value("${spring.server.ssl.enabled:true}")
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
        
        String url = baseUrl + "/" + target + "/" + path;
        
        // Add remaining params as query parameters
        Map<String, String> queryParams = params != null ? 
            params.entrySet().stream()
                .filter(e -> !intent.contains("{" + e.getKey() + "}"))
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()))
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

    private boolean isCollectionEndpoint(String path) {
        String[] segments = path.split("/");
        String lastSegment = segments[segments.length - 1];
        return !lastSegment.startsWith("{") && !lastSegment.endsWith("}");
    }

    @Override
    public Mono<LinqProtocolExample> convertToLinqProtocol(String method, String path, Object schema, String routeIdentifier) {
        return Mono.fromCallable(() -> {
            LinqProtocolExample example = new LinqProtocolExample();
            
            // Get the endpoint info
            Map<String, Object> endpointInfo = (Map<String, Object>) schema;
            example.setSummary((String) endpointInfo.get("summary"));
            
            // Create request example
            LinqRequest linqRequest = createExampleRequest(method, path, schema, routeIdentifier);
            example.setRequest(linqRequest);

            // Create response example
            LinqResponse response = createExampleResponse(method, path, schema, routeIdentifier);
            example.setResponse(response);

            return example;
        });
    }

    private LinqRequest createExampleRequest(String method, String path, Object schema, String routeIdentifier) {
        LinqRequest linqRequest = new LinqRequest();
        Map<String, Object> schemaMap = (Map<String, Object>) schema;
        
        // Set target as routeIdentifier
        LinqRequest.Link link = new LinqRequest.Link();
        link.setTarget(routeIdentifier);
        
        // Convert HTTP method to Linq action
        switch (method.toUpperCase()) {
            case "GET" -> link.setAction("fetch");
            case "POST" -> link.setAction("create");
            case "PUT" -> link.setAction("update");
            case "DELETE" -> link.setAction("delete");
            case "PATCH" -> link.setAction("patch");
            case "OPTIONS" -> link.setAction("options");
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        }
        linqRequest.setLink(link);
        
        // Set up query with parameters
        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent(path);
        
        // Extract parameters from the endpoint info
        Map<String, Object> params = new HashMap<>();
        List<SwaggerParameter> parameters = (List<SwaggerParameter>) schemaMap.get("parameters");
        if (parameters != null) {
            parameters.forEach(param -> {
                String name = param.getName();
                Map<String, Object> paramSchema = param.getSchema();
                // Just use the schema information directly
                params.put(name, paramSchema);
            });
        }
        query.setParams(params);
        
        // Handle request body for POST/PUT/PATCH
        if ((method.equalsIgnoreCase("POST") || method.equalsIgnoreCase("PUT") || method.equalsIgnoreCase("PATCH")) 
            && schemaMap.get("requestSchema") != null) {
            Map<String, Object> requestSchema = (Map<String, Object>) schemaMap.get("requestSchema");
            Map<String, Object> properties = (Map<String, Object>) requestSchema.get("properties");
            if (properties != null) {
                Map<String, Object> examplePayload = createExampleItem(properties, 1);
                query.setPayload(examplePayload);
            }
        }
        
        linqRequest.setQuery(query);
        return linqRequest;
    }

    private LinqResponse createExampleResponse(String method, String path, Object schema, String routeIdentifier) {
        LinqResponse response = new LinqResponse();
        Map<String, Object> schemaMap = (Map<String, Object>) schema;
        
        // Set up metadata
        LinqResponse.Metadata metadata = new LinqResponse.Metadata();
        metadata.setSource(routeIdentifier);
        metadata.setStatus("success");
        metadata.setTeam("67d0aeb17172416c411d419e");
        metadata.setCacheHit(false);
        response.setMetadata(metadata);

        if (method.equalsIgnoreCase("DELETE")) {
            response.setResult(Map.of("message", "Resource successfully deleted"));
            return response;
        }

        if (method.equalsIgnoreCase("OPTIONS")) {
            response.setResult(Map.of("methods", List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")));
            return response;
        }

        // Get the schema for the response
        Map<String, Object> responseSchema = (Map<String, Object>) schemaMap.get("responseSchema");
        if (responseSchema != null) {
            // Handle schema reference if present
            Map<String, Object> schemaContent = responseSchema;
            if (responseSchema.containsKey("$ref")) {
                String schemaName = ((String) responseSchema.get("$ref")).replace("#/components/schemas/", "");
                schemaContent = (Map<String, Object>) ((Map<String, Object>) schemaMap.get("schemas")).get(schemaName);
            }

            Map<String, Object> properties = (Map<String, Object>) schemaContent.get("properties");
            if (properties != null) {
                if (isCollectionEndpoint(path)) {
                    // For collection endpoints, return an array
                    List<Map<String, Object>> items = new ArrayList<>();
                    items.add(createExampleItem(properties, 1));
                    items.add(createExampleItem(properties, 2));
                    response.setResult(items);
                } else {
                    // For single item endpoints, return one item
                    response.setResult(createExampleItem(properties, 1));
                }
            }
        }

        return response;
    }

    private Map<String, Object> createExampleItem(Map<String, Object> properties, int index) {
        Map<String, Object> item = new HashMap<>();
        
        properties.forEach((propName, propDetails) -> {
            Map<String, Object> details = (Map<String, Object>) propDetails;
            String type = (String) details.get("type");
            String format = (String) details.getOrDefault("format", null);
            
            Object value = generateExampleValue(type, format, propName, index);
            item.put(propName, value);
        });
        
        return item;
    }

    private Object generateExampleValue(String type, String format, String name, int index) {
        switch (type) {
            case "integer":
                return format != null && format.equals("int64") ? 
                    Long.valueOf(index) : Integer.valueOf(index);
            case "number":
                return format != null && format.equals("float") ? 
                    Float.valueOf(index) : Double.valueOf(index);
            case "boolean":
                return true;
            case "string":
                return name + "_" + index;
            default:
                return index;
        }
    }
}