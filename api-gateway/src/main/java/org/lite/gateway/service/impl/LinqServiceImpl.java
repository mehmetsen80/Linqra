package org.lite.gateway.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.RoutePermission;
import org.lite.gateway.repository.ApiRouteRepository;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.repository.TeamRouteRepository;
import org.lite.gateway.service.LinqService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.LinqLlmModelService;
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
    private final LinqLlmModelRepository linqLlmModelRepository;

    @NonNull
    private final TeamContextService teamContextService;

    @NonNull
    private final LinqLlmModelService linqLlmModelService;

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
                                // Try to get modelType from llmConfig first, then fallback to target-only search
                                final String modelType = (request.getQuery() != null && request.getQuery().getLlmConfig() != null && request.getQuery().getLlmConfig().getModel() != null) 
                                    ? request.getQuery().getLlmConfig().getModel() : null;
                                
                                if (modelType != null) {
                                    log.info("🔍 Searching for LLM model configuration: target={}, modelType={}, teamId={}", 
                                        request.getLink().getTarget(), modelType, team);
                                    return linqLlmModelRepository.findByTargetAndModelTypeAndTeamId(request.getLink().getTarget(), modelType, team)
                                            .doOnNext(llmModel -> log.info("✅ Found LLM model configuration: target={}, modelType={}", 
                                                llmModel.getTarget(), llmModel.getModelType()))
                                            .doOnError(error -> log.error("❌ Error finding LLM model for target {} with modelType {}: {}", 
                                                request.getLink().getTarget(), modelType, error.getMessage()))
                                            .switchIfEmpty(Mono.defer(() -> {
                                                log.warn("⚠️ No LLM model found with modelType {}, falling back to target-only search", modelType);
                                                return linqLlmModelRepository.findByTargetAndTeamId(request.getLink().getTarget(), team)
                                                        .doOnNext(llmModel -> log.info("✅ Found LLM model configuration (fallback): target={}, modelType={}", 
                                                            llmModel.getTarget(), llmModel.getModelType()));
                                            }));
                                } else {
                                    log.info("🔍 Searching for LLM model configuration: target={}, teamId={}", request.getLink().getTarget(), team);
                                    return linqLlmModelRepository.findByTargetAndTeamId(request.getLink().getTarget(), team)
                                            .doOnNext(llmModel -> log.info("✅ Found LLM model configuration: target={}, modelType={}", 
                                                llmModel.getTarget(), llmModel.getModelType()))
                                            .doOnError(error -> log.error("❌ Error finding LLM model for target {}: {}", 
                                                request.getLink().getTarget(), error.getMessage()));
                                }
                            })
                            .doOnSuccess(llmModel -> {
                                if (llmModel == null) {
                                    log.warn("⚠️ No LLM model configuration found for target: {}, will try microservice", 
                                        request.getLink().getTarget());
                                }
                            })
                            .doOnNext(llmModel -> log.info("🚀 About to execute LLM request for target: {}", request.getLink().getTarget()))
                            .flatMap(llmModel -> linqLlmModelService.executeLlmRequest(request, llmModel))
                            .doOnNext(response -> log.info("✅ LLM request executed successfully"))
                            .switchIfEmpty(Mono.<LinqResponse>defer(() -> {
                                log.info("📡 No LLM model found, executing microservice request for target: {}", request.getLink().getTarget());
                                return linqMicroService.execute(request);
                            }));
                }));
    }

    private Mono<Void> validateRoutePermission(LinqRequest request) {
        String routeIdentifier = request.getLink().getTarget();

        // List of AI service targets that should bypass permission checks
        Set<String> bypassTargets = Set.of("openai", "mistral", "huggingface", "gemini", "workflow", "openai-embed",  "gemini-embed", "api-gateway");

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