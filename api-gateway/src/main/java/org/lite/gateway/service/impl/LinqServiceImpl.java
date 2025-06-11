package org.lite.gateway.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.RoutePermission;
import org.lite.gateway.repository.ApiRouteRepository;
import org.lite.gateway.repository.LinqToolRepository;
import org.lite.gateway.repository.TeamRouteRepository;
import org.lite.gateway.service.LinqService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.LinqToolService;
import org.lite.gateway.service.LinqMicroService;
import org.lite.gateway.service.LinqWorkflowService;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinqServiceImpl implements LinqService {

    @NonNull
    private final RedisTemplate<String, String> redisTemplate;

    @NonNull
    private final ApiRouteRepository apiRouteRepository;

    @NonNull
    private final TeamRouteRepository teamRouteRepository;

    @NonNull
    private final LinqToolRepository linqToolRepository;

    @NonNull
    private final TeamContextService teamContextService;

    @NonNull
    private final LinqToolService linqToolService;

    @NonNull
    private final LinqMicroService linqMicroService;

    @NonNull
    private final LinqWorkflowService linqWorkflowService;

    @NonNull
    private final LinqWorkflowExecutionService workflowExecutionService;

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
                        return workflowExecutionService.executeWorkflow(request)
                            .flatMap(response -> 
                                // Track the execution
                                workflowExecutionService.trackExecution(request, response)
                                    .thenReturn(response)
                            );
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

    private Mono<Void> validateRoutePermission(LinqRequest request) {
        String routeIdentifier = request.getLink().getTarget();

        // List of AI service targets that should bypass permission checks
        Set<String> bypassTargets = Set.of("openai", "mistral", "huggingface", "gemini", "workflow", "openai-embed",  "gemini-embed");

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
}