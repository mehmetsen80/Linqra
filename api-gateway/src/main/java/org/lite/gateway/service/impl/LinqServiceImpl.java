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
import org.lite.gateway.repository.LinqToolRepository;
import org.lite.gateway.repository.TeamRouteRepository;
import org.lite.gateway.service.LinqService;
import org.lite.gateway.service.WorkflowService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.LinqToolService;
import org.lite.gateway.service.LinqMicroService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.stream.Collectors;

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

    @NonNull
    private final LinqToolRepository linqToolRepository;

    @NonNull
    private final WorkflowService workflowService;

    @NonNull
    private final TeamContextService teamContextService;

    @NonNull
    private final LinqToolService linqToolService;

    @NonNull
    private final LinqMicroService linqMicroService;

    @Value("${server.host:localhost}")
    private String gatewayHost;

    @Value("${server.port:7777}")
    private int gatewayPort;

    @Value("${server.ssl.enabled:true}")
    private boolean sslEnabled;

    @Override
    public Mono<LinqResponse> processLinqRequest(LinqRequest request) {
        log.info("Starting processLinqRequest for target: {}", request.getLink().getTarget());
        return validateRoutePermission(request)
                .then(Mono.<LinqResponse>defer(() -> {
                    log.info("After validateRoutePermission");
                    if (request.getQuery() == null || request.getQuery().getIntent().isEmpty()) {
                        return Mono.error(new IllegalArgumentException("Invalid LINQ request"));
                    }

                    // Handle workflow requests
                    if (request.getQuery().getWorkflow() != null && !request.getQuery().getWorkflow().isEmpty()) {
                        log.info("Processing workflow request with {} steps", request.getQuery().getWorkflow().size());
                        return workflowService.executeWorkflow(request);
                    }

                    // Existing logic for single requests
                    return teamContextService.getTeamFromContext()
                            .doOnNext(team -> log.info("Got team from context: {}", team))
                            .flatMap(team -> {
                                log.info("Searching for tool with target: {} and team: {}", request.getLink().getTarget(), team);
                                return linqToolRepository.findByTargetAndTeam(request.getLink().getTarget(), team)
                                        .doOnNext(tool -> log.info("Found tool: {}", tool))
                                        .doOnError(error -> log.error("Error finding tool: {}", error.getMessage()))
                                        .doOnSuccess(tool -> {
                                            if (tool == null) {
                                                log.info("No tool found for target: {}", request.getLink().getTarget());
                                            }
                                        });
                            })
                            .doOnNext(tool -> log.info("About to execute tool request"))
                            .flatMap(tool -> linqToolService.executeToolRequest(request, tool))
                            .doOnNext(response -> log.info("Tool request executed successfully"))
                            .switchIfEmpty(Mono.<LinqResponse>defer(() -> {
                                log.info("No tool found, executing microservice request");
                                return linqMicroService.execute(request);
                            }));
                }));
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
                    return statusCode >= 200 && statusCode < 300;
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

    private Mono<Void> validateRoutePermission(LinqRequest request) {
        String routeIdentifier = request.getLink().getTarget();

        // List of AI service targets that should bypass permission checks
        Set<String> bypassTargets = Set.of("openai", "mistral", "huggingface", "gemini", "workflow");

        // If the target is in our bypass list, return immediately
        if (bypassTargets.contains(routeIdentifier)) {
            return Mono.empty();
        }

        // Otherwise, proceed with normal permission check
        return teamContextService.getTeamFromContext()
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
                                    boolean hasUsePermission = teamRoute.getPermissions().contains(RoutePermission.USE);
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
}