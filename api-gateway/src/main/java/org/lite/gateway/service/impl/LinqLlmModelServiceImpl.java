package org.lite.gateway.service.impl;

import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqLlmModel;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.repository.LlmModelRepository;
import org.lite.gateway.service.LinqLlmModelService;
import org.lite.gateway.util.AuditLogHelper;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.enums.AuditResultType;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class LinqLlmModelServiceImpl implements LinqLlmModelService {

    @NonNull
    private final LinqLlmModelRepository linqLlmModelRepository;

    @NonNull
    private final LlmModelRepository llmModelRepository;

    @NonNull
    private final WebClient.Builder webClientBuilder;

    @NonNull
    private final AuditLogHelper auditLogHelper;

    // Priority is now stored per-model in the database (LinqLlmModel.priority
    // field)
    // Lower priority number = higher priority (tried first), null = unassigned

    private Mono<LinqLlmModel> enrichWithModelMetadata(LinqLlmModel linqLlmModel) {
        if (linqLlmModel == null) {
            return Mono.empty();
        }
        if (!StringUtils.hasText(linqLlmModel.getModelName())) {
            return Mono.just(linqLlmModel);
        }

        return llmModelRepository.findByModelName(linqLlmModel.getModelName())
                .map(llmModel -> {
                    linqLlmModel.setEmbeddingDimension(llmModel.getDimensions());
                    linqLlmModel.setInputPricePer1M(llmModel.getInputPricePer1M());
                    linqLlmModel.setOutputPricePer1M(llmModel.getOutputPricePer1M());
                    linqLlmModel.setContextWindowTokens(llmModel.getContextWindowTokens());
                    return linqLlmModel;
                })
                .defaultIfEmpty(linqLlmModel);
    }

    @Override
    public Mono<LinqLlmModel> saveLinqLlmModel(LinqLlmModel linqLlmModel) {
        // Validate required fields
        if (linqLlmModel.getModelCategory() == null || linqLlmModel.getModelCategory().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqLlmModel modelCategory is required"));
        }
        if (linqLlmModel.getTeamId() == null || linqLlmModel.getTeamId().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqLlmModel team ID is required"));
        }
        if (linqLlmModel.getEndpoint() == null || linqLlmModel.getEndpoint().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqLlmModel endpoint is required"));
        }
        if (linqLlmModel.getMethod() == null || linqLlmModel.getMethod().isEmpty()) {
            return Mono.error(new IllegalArgumentException("LinqLlmModel method is required"));
        }

        log.info("Saving LinqLlmModel with modelCategory: {}, modelName: {} for team: {}",
                linqLlmModel.getModelCategory(), linqLlmModel.getModelName(), linqLlmModel.getTeamId());

        return linqLlmModelRepository.findByModelCategoryAndModelNameAndTeamId(
                linqLlmModel.getModelCategory(), linqLlmModel.getModelName(), linqLlmModel.getTeamId())
                .<LinqLlmModel>flatMap(existingLlmModel -> {
                    // Update existing linq llm model
                    existingLlmModel.setEndpoint(linqLlmModel.getEndpoint());
                    existingLlmModel.setProvider(linqLlmModel.getProvider());
                    existingLlmModel.setMethod(linqLlmModel.getMethod());
                    existingLlmModel.setHeaders(linqLlmModel.getHeaders());
                    existingLlmModel.setAuthType(linqLlmModel.getAuthType());
                    existingLlmModel.setApiKey(linqLlmModel.getApiKey());
                    existingLlmModel.setSupportedIntents(linqLlmModel.getSupportedIntents());

                    log.info("Updating existing LinqLlmModel with ID: {}", existingLlmModel.getId());
                    return linqLlmModelRepository.save(existingLlmModel)
                            .doOnSuccess(saved -> log.info("Updated LinqLlmModel with ID: {}", saved.getId()))
                            .doOnError(error -> log.error("Failed to update LinqLlmModel: {}", error.getMessage()));
                })
                .switchIfEmpty(Mono.<LinqLlmModel>defer(() -> {
                    // Create new linq llm model
                    log.info("Creating new LinqLlmModel for modelCategory: {}, modelName: {} and team: {}",
                            linqLlmModel.getModelCategory(), linqLlmModel.getModelName(), linqLlmModel.getTeamId());

                    // Auto-assign priority if not set - find max priority for team and add 1
                    if (linqLlmModel.getPriority() == null) {
                        return linqLlmModelRepository.findByTeamId(linqLlmModel.getTeamId())
                                .map(LinqLlmModel::getPriority)
                                .filter(p -> p != null)
                                .reduce(0, (a, b) -> Math.max(a, b))
                                .defaultIfEmpty(0)
                                .flatMap(maxPriority -> {
                                    linqLlmModel.setPriority(maxPriority + 1);
                                    log.info("Auto-assigned priority {} to new model {}", maxPriority + 1,
                                            linqLlmModel.getModelName());
                                    return linqLlmModelRepository.save(linqLlmModel);
                                })
                                .doOnSuccess(saved -> log.info("Created new LinqLlmModel with ID: {} and priority: {}",
                                        saved.getId(), saved.getPriority()))
                                .doOnError(error -> log.error("Failed to create LinqLlmModel: {}", error.getMessage()));
                    }

                    return linqLlmModelRepository.save(linqLlmModel)
                            .doOnSuccess(saved -> log.info("Created new LinqLlmModel with ID: {} and priority: {}",
                                    saved.getId(), saved.getPriority()))
                            .doOnError(error -> log.error("Failed to create LinqLlmModel: {}", error.getMessage()));
                }));
    }

    @Override
    public Mono<Void> deleteLinqLlmModel(String id) {
        log.info("Deleting LinqLlmModel with ID: {}", id);
        return linqLlmModelRepository.findById(id)
                .flatMap(llmModel -> {
                    log.info("Found LinqLlmModel to delete: modelCategory={}, modelName={}",
                            llmModel.getModelCategory(), llmModel.getModelName());
                    return linqLlmModelRepository.deleteById(id)
                            .doOnSuccess(v -> log.info("Deleted LinqLlmModel with ID: {}", id))
                            .doOnError(error -> log.error("Failed to delete LinqLlmModel with ID {}: {}", id,
                                    error.getMessage()));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("LinqLlmModel not found with ID: {} (may have been already deleted)", id);
                    return Mono.empty();
                }));
    }

    @Override
    public Mono<LinqLlmModel> findById(String id) {
        log.info("Finding LLM model configuration by ID: {}", id);
        return linqLlmModelRepository.findById(id)
                .flatMap(this::enrichWithModelMetadata)
                .doOnSuccess(llmModel -> {
                    if (llmModel != null) {
                        log.info("✅ Found LLM model configuration: id={}, modelCategory={}, modelName={}",
                                id, llmModel.getModelCategory(), llmModel.getModelName());
                    } else {
                        log.warn("⚠️ No LLM model configuration found for ID: {}", id);
                    }
                })
                .doOnError(error -> log.error("❌ Error finding LLM model configuration for ID {}: {}",
                        id, error.getMessage()));
    }

    @Override
    public Flux<LinqLlmModel> findByTeamId(String teamId) {
        log.info("Finding LLM model configurations for team: {}", teamId);
        return linqLlmModelRepository.findByTeamId(teamId)
                .flatMap(this::enrichWithModelMetadata)
                .doOnNext(llmModel -> log.info(
                        "Found LLM model configuration for team {}: modelCategory={}, modelName={}",
                        teamId, llmModel.getModelCategory(), llmModel.getModelName()))
                .doOnComplete(() -> log.info("Completed fetching LLM model configurations for team: {}", teamId))
                .doOnError(error -> log.error("Error finding LLM model configurations for team {}: {}", teamId,
                        error.getMessage()));
    }

    @Override
    public Flux<LinqLlmModel> findByModelCategoryAndTeamId(String modelCategory, String teamId) {
        log.info("Finding LLM model configurations for modelCategory: {} and team: {}", modelCategory, teamId);
        return linqLlmModelRepository.findByModelCategoryAndTeamId(modelCategory, teamId)
                .flatMap(this::enrichWithModelMetadata)
                .doOnNext(llmModel -> log.info("Found LLM model configuration: modelCategory={}, modelName={}",
                        modelCategory, llmModel.getModelName()))
                .doOnComplete(
                        () -> log.info("Completed fetching LLM model configurations for modelCategory: {} and team: {}",
                                modelCategory, teamId))
                .doOnError(error -> log.error(
                        "Error finding LLM model configurations for modelCategory: {} and team: {}: {}",
                        modelCategory, teamId, error.getMessage()));
    }

    @Override
    public Flux<LinqLlmModel> findByModelCategoriesAndTeamId(java.util.List<String> modelCategories, String teamId) {
        log.info("Finding LLM model configurations for modelCategories: {} and team: {}", modelCategories, teamId);
        return Flux.fromIterable(modelCategories)
                .flatMap(modelCategory -> linqLlmModelRepository.findByModelCategoryAndTeamId(modelCategory, teamId))
                .flatMap(this::enrichWithModelMetadata)
                .doOnNext(llmModel -> log.info("Found LLM model configuration: modelCategory={}, modelName={}",
                        llmModel.getModelCategory(), llmModel.getModelName()))
                .doOnComplete(() -> log.info(
                        "Completed fetching LLM model configurations for modelCategories: {} and team: {}",
                        modelCategories, teamId))
                .doOnError(error -> log.error(
                        "Error finding LLM model configurations for modelCategories: {} and team: {}: {}",
                        modelCategories, teamId, error.getMessage()));
    }

    @Override
    public Mono<LinqLlmModel> findByModelCategoryAndModelNameAndTeamId(String modelCategory, String modelName,
            String teamId) {
        log.info("Finding LLM model configuration for modelCategory: {}, modelName: {} and team: {}", modelCategory,
                modelName, teamId);
        return linqLlmModelRepository.findByModelCategoryAndModelNameAndTeamId(modelCategory, modelName, teamId)
                .flatMap(this::enrichWithModelMetadata)
                .doOnSuccess(llmModel -> {
                    if (llmModel != null) {
                        log.info("✅ Found LLM model configuration: modelCategory={}, modelName={}, endpoint={}",
                                modelCategory, modelName, llmModel.getEndpoint());
                    } else {
                        log.warn(
                                "⚠️ No LLM model configuration found for modelCategory: {}, modelName: {} and team: {}",
                                modelCategory, modelName, teamId);
                    }
                })
                .doOnError(error -> log.error(
                        "❌ Error finding LLM model configuration for modelCategory: {}, modelName: {} and team: {}: {}",
                        modelCategory, modelName, teamId, error.getMessage()));
    }

    @Override
    public Mono<LinqLlmModel> findCheapestAvailableModel(List<String> modelCategories, String teamId,
            long estimatedPromptTokens, long estimatedCompletionTokens) {
        log.debug("Finding cheapest available model from categories: {} for team: {}", modelCategories, teamId);

        // Fetch all available models for all specified categories and enrich with
        // pricing
        return Flux.fromIterable(modelCategories)
                .flatMap(category -> findByModelCategoryAndTeamId(category, teamId))
                .collectList()
                .flatMap(models -> {
                    if (models.isEmpty()) {
                        return Mono.error(new RuntimeException(
                                "No chat model found from categories: " + modelCategories
                                        + ". Please configure a chat model for team: " + teamId));
                    }

                    log.debug("Found {} available models from {} categories", models.size(), modelCategories.size());

                    // Log all available models for debugging
                    log.info("🔍 Available models for selection:");
                    for (LinqLlmModel model : models) {
                        double cost = calculateEstimatedCost(model, estimatedPromptTokens, estimatedCompletionTokens);
                        log.info("  - {} (provider: {}, category: {}, priority: {}, estimated cost: ${})",
                                model.getModelName(),
                                model.getProvider(),
                                model.getModelCategory(),
                                model.getPriority() != null ? model.getPriority() : "N/A",
                                String.format("%.6f", cost));
                    }

                    // Sort models by database priority (lower = higher priority), then by cost
                    List<LinqLlmModel> sortedModels = models.stream()
                            .sorted((m1, m2) -> {
                                // Get priority from database (null treated as lowest priority)
                                int priority1 = m1.getPriority() != null ? m1.getPriority() : Integer.MAX_VALUE;
                                int priority2 = m2.getPriority() != null ? m2.getPriority() : Integer.MAX_VALUE;

                                // First sort by database priority
                                if (priority1 != priority2) {
                                    return Integer.compare(priority1, priority2);
                                }

                                // Then sort by estimated cost per request
                                double cost1 = calculateEstimatedCost(m1, estimatedPromptTokens,
                                        estimatedCompletionTokens);
                                double cost2 = calculateEstimatedCost(m2, estimatedPromptTokens,
                                        estimatedCompletionTokens);
                                return Double.compare(cost1, cost2);
                            })
                            .collect(Collectors.toList());

                    // Log sorted order for debugging
                    log.info("🔍 Models sorted by priority and cost:");
                    for (int i = 0; i < sortedModels.size(); i++) {
                        LinqLlmModel model = sortedModels.get(i);
                        double cost = calculateEstimatedCost(model, estimatedPromptTokens, estimatedCompletionTokens);
                        log.info("  {}. {} (provider: {}, priority: {}, cost: ${})",
                                i + 1,
                                model.getModelName(),
                                model.getProvider(),
                                model.getPriority() != null ? model.getPriority() : "N/A",
                                String.format("%.6f", cost));
                    }

                    LinqLlmModel cheapest = sortedModels.get(0);
                    double estimatedCost = calculateEstimatedCost(cheapest, estimatedPromptTokens,
                            estimatedCompletionTokens);
                    log.info(
                            "💰 Selected cheapest model: {} / {} (provider: {}, category: {}, estimated cost: ${} per request)",
                            cheapest.getModelName(),
                            cheapest.getModelCategory(),
                            cheapest.getProvider(),
                            cheapest.getModelCategory(),
                            String.format("%.6f", estimatedCost));

                    return Mono.just(cheapest);
                });
    }

    /**
     * Calculate estimated cost per request for a model
     * Uses estimated token counts to compare models
     * 
     * @param model                     The LinqLlmModel to calculate cost for
     * @param estimatedPromptTokens     Estimated prompt tokens for the request
     * @param estimatedCompletionTokens Estimated completion tokens for the request
     * @return Estimated cost in USD
     */
    private double calculateEstimatedCost(LinqLlmModel model, long estimatedPromptTokens,
            long estimatedCompletionTokens) {
        Double inputPrice = model.getInputPricePer1M();
        Double outputPrice = model.getOutputPricePer1M();

        // If pricing is not available, return a high value to deprioritize
        if (inputPrice == null || outputPrice == null) {
            log.debug("Model {} does not have pricing metadata, using fallback cost", model.getModelName());
            return 999999.0; // High value to deprioritize models without pricing
        }

        // Calculate estimated cost: (promptTokens / 1M) * inputPrice +
        // (completionTokens / 1M) * outputPrice
        double promptCost = (estimatedPromptTokens / 1_000_000.0) * inputPrice;
        double completionCost = (estimatedCompletionTokens / 1_000_000.0) * outputPrice;
        return promptCost + completionCost;
    }

    @Override
    public Mono<LinqResponse> executeLlmRequest(LinqRequest request, LinqLlmModel llmModel) {
        LocalDateTime startTime = LocalDateTime.now();
        String intent = request.getQuery().getIntent();

        if (!llmModel.getSupportedIntents().contains(intent)) {
            String errorMsg = "Intent '" + intent + "' not supported by " + llmModel.getModelCategory() + " "
                    + llmModel.getModelName();

            // Log failed validation
            Map<String, Object> errorContext = new HashMap<>();
            errorContext.put("modelCategory", llmModel.getModelCategory());
            errorContext.put("modelName", llmModel.getModelName());
            errorContext.put("teamId", llmModel.getTeamId());
            errorContext.put("intent", intent);
            errorContext.put("supportedIntents", llmModel.getSupportedIntents());
            errorContext.put("error", errorMsg);
            errorContext.put("durationMs", java.time.Duration.between(startTime, LocalDateTime.now()).toMillis());

            if (request.getExecutedBy() != null) {
                errorContext.put("executedBy", request.getExecutedBy());
            }

            return auditLogHelper.logDetailedEvent(
                    AuditEventType.LLM_REQUEST_FAILED,
                    AuditActionType.READ,
                    AuditResourceType.LLM_MODEL,
                    llmModel.getModelCategory() + "/" + llmModel.getModelName(),
                    String.format("LLM request validation failed: %s", errorMsg),
                    errorContext,
                    null,
                    null,
                    AuditResultType.FAILED)
                    .doOnError(auditError -> log.error("Failed to log audit event (validation failed): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty())
                    .then(Mono.error(new IllegalArgumentException(errorMsg)));
        }

        AtomicReference<String> url = new AtomicReference<>(buildLlmUrl(llmModel, request));
        String method = llmModel.getMethod();
        Object payload = buildLlmPayload(request, llmModel);

        String resourceId = llmModel.getModelCategory() + "/" + llmModel.getModelName();
        String workflowId = request.getQuery() != null && request.getQuery().getWorkflowId() != null
                ? request.getQuery().getWorkflowId()
                : null;
        String executedBy = request.getExecutedBy();

        log.info("🚀 Executing LLM request - modelCategory: {}, modelName: {}, URL: {}",
                llmModel.getModelCategory(), llmModel.getModelName(), url.get());

        // Log LLM request started
        Map<String, Object> startContext = new HashMap<>();
        startContext.put("modelCategory", llmModel.getModelCategory());
        startContext.put("modelName", llmModel.getModelName());
        startContext.put("teamId", llmModel.getTeamId());
        startContext.put("intent", intent);
        startContext.put("method", method);
        startContext.put("url", url.get());
        startContext.put("startTimestamp", startTime.toString());
        if (executedBy != null) {
            startContext.put("executedBy", executedBy);
        }
        if (workflowId != null) {
            startContext.put("workflowId", workflowId);
        }

        return auditLogHelper.logDetailedEvent(
                AuditEventType.LLM_REQUEST_STARTED,
                AuditActionType.READ,
                AuditResourceType.LLM_MODEL,
                resourceId,
                String.format("LLM request started for %s/%s with intent '%s'", llmModel.getModelCategory(),
                        llmModel.getModelName(), intent),
                startContext,
                null,
                null)
                .doOnError(auditError -> log.error("Failed to log audit event (LLM request started): {}",
                        auditError.getMessage(), auditError))
                .onErrorResume(auditError -> Mono.empty())
                .then(Mono.just(llmModel.getApiKey()))
                .flatMap(apiKey -> {
                    Map<String, String> headers = new HashMap<>(llmModel.getHeaders());
                    String authType = llmModel.getAuthType() != null ? llmModel.getAuthType() : "none";

                    switch (authType) {
                        case "bearer":
                            headers.put("Authorization", "Bearer " + apiKey);
                            log.debug("Using Bearer token authentication for {}", llmModel.getModelCategory());
                            break;
                        case "api_key":
                            headers.put("x-api-key", apiKey);
                            log.debug("Using x-api-key header authentication for {}", llmModel.getModelCategory());
                            break;
                        case "api_key_query":
                            url.set(url.get() + (url.get().contains("?") ? "&" : "?") + "key=" + apiKey);
                            log.debug("Using API key query parameter authentication for {}",
                                    llmModel.getModelCategory());
                            break;
                        case "none":
                        default:
                            log.debug("No authentication required for {}", llmModel.getModelCategory());
                            break;
                    }

                    // Add cache-busting headers
                    headers.put("Cache-Control", "no-cache, no-store, must-revalidate");
                    headers.put("Pragma", "no-cache");
                    headers.put("Expires", "0");

                    // DEBUG LOGGING
                    log.error(">>> PREPARING LLM CALL <<<");
                    log.error("URL: {}", url.get());
                    log.error("Method: {}", method);
                    log.error("Auth Type: {}", authType);
                    log.error("Headers Keys: {}", headers.keySet());
                    // Be careful not to log full keys in prod, but for debug:
                    headers.forEach((k, v) -> log.error("Header '{}': '{}'", k,
                            v.length() > 10 ? v.substring(0, 5) + "..." : v));

                    return invokeLlmService(method, url.get(), payload, headers);
                })
                .flatMap(result -> {
                    // Check if result is an error response BEFORE processing
                    if (result instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> resultMap = (Map<String, Object>) result;
                        if (resultMap.containsKey("error")) {
                            // This is an error response - extract error details and throw exception
                            Object errorObj = resultMap.get("error");
                            String errorMessage = "LLM API returned an error";

                            if (errorObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                                if (errorMap.containsKey("message")) {
                                    errorMessage = (String) errorMap.get("message");
                                }
                            } else if (errorObj instanceof String) {
                                errorMessage = (String) errorObj;
                            }

                            // Check for HTTP status code
                            Integer httpStatus = resultMap.containsKey("httpStatus")
                                    ? (Integer) resultMap.get("httpStatus")
                                    : null;

                            // Create proper error message
                            String fullErrorMessage = httpStatus != null
                                    ? String.format("HTTP %d: %s", httpStatus, errorMessage)
                                    : errorMessage;

                            // Log and throw error
                            log.error("❌ LLM request failed for {}/{}: {}",
                                    llmModel.getModelCategory(), llmModel.getModelName(), fullErrorMessage);

                            // Audit log the failure
                            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                            Map<String, Object> errorContext = new HashMap<>();
                            errorContext.put("modelCategory", llmModel.getModelCategory());
                            errorContext.put("modelName", llmModel.getModelName());
                            errorContext.put("teamId", llmModel.getTeamId());
                            errorContext.put("intent", intent);
                            errorContext.put("method", method);
                            errorContext.put("url", url.get());
                            errorContext.put("error", fullErrorMessage);
                            errorContext.put("httpStatus", httpStatus);
                            errorContext.put("durationMs", durationMs);
                            errorContext.put("failureTimestamp", LocalDateTime.now().toString());
                            if (executedBy != null) {
                                errorContext.put("executedBy", executedBy);
                            }
                            if (workflowId != null) {
                                errorContext.put("workflowId", workflowId);
                            }

                            return auditLogHelper.logDetailedEvent(
                                    AuditEventType.LLM_REQUEST_FAILED,
                                    AuditActionType.READ,
                                    AuditResourceType.LLM_MODEL,
                                    resourceId,
                                    String.format("LLM request failed for %s/%s: %s",
                                            llmModel.getModelCategory(), llmModel.getModelName(), fullErrorMessage),
                                    errorContext,
                                    null,
                                    null,
                                    AuditResultType.FAILED)
                                    .doOnError(auditError -> log.error(
                                            "Failed to log audit event (LLM request failed): {}",
                                            auditError.getMessage(), auditError))
                                    .onErrorResume(auditError -> Mono.empty())
                                    .then(Mono.error(new RuntimeException(fullErrorMessage)));
                        }
                    }

                    // Success path - result is valid
                    LinqResponse response = new LinqResponse();
                    response.setResult(result);
                    LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                    metadata.setSource(llmModel.getModelCategory());
                    metadata.setStatus("success");
                    metadata.setTeamId(llmModel.getTeamId());
                    metadata.setCacheHit(false);
                    response.setMetadata(metadata);
                    log.info("✅ LLM request completed successfully for {}/{}", llmModel.getModelCategory(),
                            llmModel.getModelName());

                    // Log successful completion
                    long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                    Map<String, Object> successContext = new HashMap<>();
                    successContext.put("modelCategory", llmModel.getModelCategory());
                    successContext.put("modelName", llmModel.getModelName());
                    successContext.put("teamId", llmModel.getTeamId());
                    successContext.put("intent", intent);
                    successContext.put("method", method);
                    successContext.put("url", url.get());
                    successContext.put("durationMs", durationMs);
                    successContext.put("completionTimestamp", LocalDateTime.now().toString());
                    if (executedBy != null) {
                        successContext.put("executedBy", executedBy);
                    }
                    if (workflowId != null) {
                        successContext.put("workflowId", workflowId);
                    }

                    return auditLogHelper.logDetailedEvent(
                            AuditEventType.LLM_REQUEST_COMPLETED,
                            AuditActionType.READ,
                            AuditResourceType.LLM_MODEL,
                            resourceId,
                            String.format("LLM request completed successfully for %s/%s with intent '%s'",
                                    llmModel.getModelCategory(), llmModel.getModelName(), intent),
                            successContext,
                            null,
                            null,
                            AuditResultType.SUCCESS)
                            .doOnError(auditError -> log.error("Failed to log audit event (LLM request completed): {}",
                                    auditError.getMessage(), auditError))
                            .onErrorResume(auditError -> Mono.empty())
                            .thenReturn(response);
                })
                .onErrorResume(error -> {
                    // Log failure
                    long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                    Map<String, Object> errorContext = new HashMap<>();
                    errorContext.put("modelCategory", llmModel.getModelCategory());
                    errorContext.put("modelName", llmModel.getModelName());
                    errorContext.put("teamId", llmModel.getTeamId());
                    errorContext.put("intent", intent);
                    errorContext.put("method", method);
                    errorContext.put("url", url.get());
                    errorContext.put("error", error.getMessage());
                    errorContext.put("errorType", error.getClass().getSimpleName());
                    errorContext.put("durationMs", durationMs);
                    errorContext.put("failureTimestamp", LocalDateTime.now().toString());
                    if (executedBy != null) {
                        errorContext.put("executedBy", executedBy);
                    }
                    if (workflowId != null) {
                        errorContext.put("workflowId", workflowId);
                    }

                    return auditLogHelper.logDetailedEvent(
                            AuditEventType.LLM_REQUEST_FAILED,
                            AuditActionType.READ,
                            AuditResourceType.LLM_MODEL,
                            resourceId,
                            String.format("LLM request failed for %s/%s: %s", llmModel.getModelCategory(),
                                    llmModel.getModelName(), error.getMessage()),
                            errorContext,
                            null,
                            null,
                            AuditResultType.FAILED)
                            .doOnError(auditError -> log.error("Failed to log audit event (LLM request failed): {}",
                                    auditError.getMessage(), auditError))
                            .onErrorResume(auditError -> Mono.empty())
                            .then(Mono.error(error));
                });
    }

    private String buildLlmUrl(LinqLlmModel llmModel, LinqRequest request) {
        String endpoint = llmModel.getEndpoint();
        LinqRequest.Query.LlmConfig llmConfig = request.getQuery().getLlmConfig();
        if (llmConfig != null && llmConfig.getModel() != null) {
            endpoint = endpoint.replace("{model}", llmConfig.getModel());
            log.debug("Resolved LLM endpoint for model '{}' to URL: {}", llmConfig.getModel(), endpoint);
        } else {
            log.debug("Using static LLM endpoint (no {model} placeholder to replace): {}", endpoint);
        }
        return endpoint;
    }

    private Object buildLlmPayload(LinqRequest request, LinqLlmModel llmModel) {
        Map<String, Object> payload = new HashMap<>();
        LinqRequest.Query.LlmConfig llmConfig = request.getQuery().getLlmConfig();
        String modelCategory = llmModel.getModelCategory();

        switch (modelCategory) {
            case "openai-chat":
            case "mistral-chat":
                payload.put("model",
                        llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel() : "default");
                payload.put("messages", request.getQuery().getPayload());
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    // Convert max.tokens to max_tokens for OpenAI API
                    Map<String, Object> settings = new HashMap<>(llmConfig.getSettings());
                    if (settings.containsKey("max.tokens")) {
                        Object value = settings.remove("max.tokens");
                        settings.put("max_tokens", value);
                    }
                    payload.putAll(settings);
                }
                break;
            case "huggingface-chat":
                payload.put("inputs", request.getQuery().getParams().getOrDefault("prompt", ""));
                payload.put("model", llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel()
                        : "sentence-transformers/all-MiniLM-L6-v2");
                payload.put("parameters", llmConfig != null ? llmConfig.getSettings() : new HashMap<>());
                break;
            case "gemini-chat":
                // Gemini Chat API format - handle messages array
                List<Map<String, Object>> geminiContents = new ArrayList<>();
                if (request.getQuery().getPayload() instanceof List) {
                    // Convert messages array to Gemini contents format
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> messages = (List<Map<String, Object>>) request.getQuery().getPayload();
                    for (Map<String, Object> message : messages) {
                        String role = (String) message.get("role");
                        Object contentObj = message.get("content");
                        String content = contentObj != null ? contentObj.toString() : "";

                        // Gemini uses "user" and "model" roles (not "assistant" or "system")
                        String geminiRole = "user".equals(role) ? "user"
                                : ("assistant".equals(role) || "system".equals(role)) ? "model" : "user";

                        Map<String, Object> part = new HashMap<>();
                        part.put("text", content);

                        Map<String, Object> geminiContent = new HashMap<>();
                        geminiContent.put("role", geminiRole);
                        geminiContent.put("parts", List.of(part));

                        geminiContents.add(geminiContent);
                    }
                } else {
                    // Fallback to prompt from params
                    String prompt = request.getQuery().getParams().getOrDefault("prompt", "").toString();
                    Map<String, Object> part = new HashMap<>();
                    part.put("text", prompt);
                    Map<String, Object> geminiContent = new HashMap<>();
                    geminiContent.put("role", "user");
                    geminiContent.put("parts", List.of(part));
                    geminiContents.add(geminiContent);
                }
                payload.put("contents", geminiContents);

                // Convert max_tokens to maxOutputTokens for Gemini
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    Map<String, Object> settings = new HashMap<>(llmConfig.getSettings());
                    if (settings.containsKey("max_tokens")) {
                        Object value = settings.remove("max_tokens");
                        settings.put("maxOutputTokens", value);
                    }
                    // Also handle max.tokens if present
                    if (settings.containsKey("max.tokens")) {
                        Object value = settings.remove("max.tokens");
                        settings.put("maxOutputTokens", value);
                    }
                    payload.put("generationConfig", settings);
                }
                break;
            case "claude-chat":
                // Claude API format - requires system message to be top-level, not in messages
                // array
                String claudeModel = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel()
                        : "claude-sonnet-4-5";
                payload.put("model", claudeModel);

                // Handle messages array from payload
                List<Map<String, Object>> claudeMessages = new ArrayList<>();
                String systemMessage = null;

                if (request.getQuery().getPayload() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> messages = (List<Map<String, Object>>) request.getQuery().getPayload();

                    // Extract system message and separate user/assistant messages
                    for (Map<String, Object> message : messages) {
                        String role = (String) message.get("role");
                        Object contentObj = message.get("content");
                        String content = contentObj != null ? contentObj.toString() : "";

                        if ("system".equals(role)) {
                            // Claude requires system message as top-level parameter
                            systemMessage = content;
                        } else if ("user".equals(role) || "assistant".equals(role)) {
                            // Add user and assistant messages to messages array
                            Map<String, Object> claudeMsg = new HashMap<>();
                            claudeMsg.put("role", role);
                            claudeMsg.put("content", content);
                            claudeMessages.add(claudeMsg);
                        }
                    }
                } else if (request.getQuery().getPayload() instanceof Map) {
                    // If payload is a Map, convert to messages format
                    Map<String, Object> payloadMap = (Map<String, Object>) request.getQuery().getPayload();
                    if (payloadMap.containsKey("messages")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> messages = (List<Map<String, Object>>) payloadMap.get("messages");
                        for (Map<String, Object> message : messages) {
                            String role = (String) message.get("role");
                            Object contentObj = message.get("content");
                            String content = contentObj != null ? contentObj.toString() : "";

                            if ("system".equals(role)) {
                                systemMessage = content;
                            } else if ("user".equals(role) || "assistant".equals(role)) {
                                Map<String, Object> claudeMsg = new HashMap<>();
                                claudeMsg.put("role", role);
                                claudeMsg.put("content", content);
                                claudeMessages.add(claudeMsg);
                            }
                        }
                    } else {
                        // Create a single user message from the payload
                        String content = payloadMap.getOrDefault("content", "").toString();
                        Map<String, Object> userMsg = new HashMap<>();
                        userMsg.put("role", "user");
                        userMsg.put("content", content);
                        claudeMessages.add(userMsg);
                    }
                }

                // Set system message as top-level parameter if present
                if (systemMessage != null && !systemMessage.trim().isEmpty()) {
                    payload.put("system", systemMessage);
                }

                // Set messages array (only user and assistant messages)
                payload.put("messages", claudeMessages);

                // Add settings from llmConfig (e.g., max_tokens)
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    payload.putAll(llmConfig.getSettings());
                }
                log.info("Building Claude payload for model: {} with {} messages", claudeModel, claudeMessages.size());
                break;
            case "openai-embed":
                Object textParam = request.getQuery().getParams().getOrDefault("text", "");
                String text = textParam != null ? textParam.toString() : "";
                String model = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel()
                        : "text-embedding-ada-002";
                payload.put("input", text);
                payload.put("model", model);
                log.info("Building OpenAI embedding payload - text: {}, model: {}", text, model);
                break;
            case "gemini-embed":
                Object geminiTextParam = request.getQuery().getParams().getOrDefault("text", "");
                String geminiText = geminiTextParam != null ? geminiTextParam.toString() : "";
                String geminiModel = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel()
                        : "text-embedding-004";
                // Gemini embedContent API format
                payload.put("content", Map.of("parts", List.of(Map.of("text", geminiText))));
                log.info("Building Gemini embedding payload - text: {}, model: {}", geminiText, geminiModel);
                break;
            case "cohere-chat":
                // Cohere V2 Chat API format - requires messages array
                String cohereModel = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel()
                        : "command-r-08-2024";
                payload.put("model", cohereModel);

                List<Map<String, Object>> cohereMessages = new ArrayList<>();

                if (request.getQuery().getPayload() instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> messages = (List<Map<String, Object>>) request.getQuery().getPayload();

                    for (Map<String, Object> message : messages) {
                        String role = (String) message.get("role");
                        Object contentObj = message.get("content");
                        String content = contentObj != null ? contentObj.toString() : "";

                        if (content.trim().isEmpty()) {
                            continue;
                        }

                        // Map roles to Cohere V2 roles
                        Map<String, Object> cohereMsg = new HashMap<>();
                        if ("system".equalsIgnoreCase(role)) {
                            cohereMsg.put("role", "system");
                        } else if ("assistant".equalsIgnoreCase(role)) {
                            cohereMsg.put("role", "assistant");
                        } else {
                            cohereMsg.put("role", "user");
                        }
                        cohereMsg.put("content", content);
                        cohereMessages.add(cohereMsg);
                    }
                } else if (request.getQuery().getPayload() instanceof Map) {
                    // Handle Map payload
                    Map<String, Object> coherePayload = (Map<String, Object>) request.getQuery().getPayload();

                    if (coherePayload.containsKey("messages")) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> messages = (List<Map<String, Object>>) coherePayload.get("messages");
                        for (Map<String, Object> message : messages) {
                            String role = (String) message.get("role");
                            Object contentObj = message.get("content");
                            String content = contentObj != null ? contentObj.toString() : "";

                            if (content.trim().isEmpty())
                                continue;

                            Map<String, Object> cohereMsg = new HashMap<>();
                            if ("system".equalsIgnoreCase(role)) {
                                cohereMsg.put("role", "system");
                            } else if ("assistant".equalsIgnoreCase(role)) {
                                cohereMsg.put("role", "assistant");
                            } else {
                                cohereMsg.put("role", "user");
                            }
                            cohereMsg.put("content", content);
                            cohereMessages.add(cohereMsg);
                        }
                    } else if (coherePayload.containsKey("message")) {
                        // Backwards compatibility for single message
                        Object contentObj = coherePayload.get("message");
                        String content = contentObj != null ? contentObj.toString() : "";

                        if (!content.trim().isEmpty()) {
                            Map<String, Object> cohereMsg = new HashMap<>();
                            cohereMsg.put("role", "user");
                            cohereMsg.put("content", content);
                            cohereMessages.add(cohereMsg);
                        }
                    }

                    // Add any other fields (excluding message/preamble/messages)
                    coherePayload.forEach((key, value) -> {
                        if (!key.equals("message") && !key.equals("preamble") && !key.equals("messages")) {
                            payload.put(key, value);
                        }
                    });
                }

                // Fallback: If no messages yet, check for 'prompt' parameter
                if (cohereMessages.isEmpty()) {
                    String prompt = request.getQuery().getParams().getOrDefault("prompt", "").toString();
                    if (!prompt.trim().isEmpty()) {
                        Map<String, Object> cohereMsg = new HashMap<>();
                        cohereMsg.put("role", "user");
                        cohereMsg.put("content", prompt);
                        cohereMessages.add(cohereMsg);
                        log.debug("Using fallback prompt for Cohere payload");
                    }
                }

                // Final validation
                if (cohereMessages.isEmpty()) {
                    throw new IllegalArgumentException(
                            "Cohere Chat API requires at least one non-empty message in the payload");
                }

                payload.put("messages", cohereMessages);

                // Add settings from llmConfig
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    Map<String, Object> settings = new HashMap<>(llmConfig.getSettings());

                    // Map max.tokens to max_tokens
                    if (settings.containsKey("max.tokens")) {
                        Object val = settings.remove("max.tokens");
                        settings.put("max_tokens", val);
                    }

                    payload.putAll(settings);
                }
                log.info("Building Cohere payload for model: {} with {} messages", cohereModel, cohereMessages.size());
                break;
            case "cohere-embed":
                // Cohere Embed API format
                Object cohereTextParam = request.getQuery().getParams().getOrDefault("text", "");
                String cohereText = cohereTextParam != null ? cohereTextParam.toString() : "";
                String cohereEmbedModel = llmConfig != null && llmConfig.getModel() != null ? llmConfig.getModel()
                        : "embed-english-v3.0";
                payload.put("texts", List.of(cohereText));
                payload.put("model", cohereEmbedModel);
                payload.put("input_type", "search_document");
                log.info("Building Cohere embedding payload - text: {}, model: {}", cohereText, cohereEmbedModel);
                break;
            default:
                payload.putAll(request.getQuery().getParams());
                break;
        }

        log.info("📦 Built {} payload for {}: {}", modelCategory, llmModel.getModelName(), payload);
        return payload;
    }

    private Mono<Object> invokeLlmService(String method, String url, Object payload,
            Map<String, String> headers) {
        return Mono.defer(() -> {
            try {
                WebClient webClient = webClientBuilder.build();
                WebClient.RequestHeadersSpec<?> requestSpec = switch (method) {
                    case "GET" -> webClient.get().uri(url);
                    case "POST" -> webClient.post().uri(url)
                            .bodyValue(payload);
                    case "PUT" -> webClient.put().uri(url)
                            .bodyValue(payload);
                    case "PATCH" -> webClient.patch().uri(url)
                            .bodyValue(payload);
                    default -> throw new IllegalArgumentException("Method not supported: " + method);
                };

                // Add headers
                // Add User-Agent to avoid Cloudflare blocks (often blocks default ReactorNetty
                // agent)
                requestSpec = requestSpec.header("User-Agent",
                        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");

                for (Map.Entry<String, String> entry : headers.entrySet()) {
                    requestSpec = requestSpec.header(entry.getKey(), entry.getValue());
                }

                log.info("\uD83C\uDF10 Making {} request to LLM service: {}", method, url);

                return requestSpec
                        .exchangeToMono(response -> {
                            if (response.statusCode().is2xxSuccessful()) {
                                return response.bodyToMono(Object.class);
                            } else {
                                return response.bodyToMono(String.class)
                                        .flatMap(body -> {
                                            log.error("❌ Error calling LLM service {}: {} - Response body: {}", url,
                                                    response.statusCode(), body);

                                            // Parse error message from response body
                                            String errorMessage = body;
                                            try {
                                                // Try to parse JSON error body to extract error message
                                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                                @SuppressWarnings("unchecked")
                                                Map<String, Object> parsedBody = mapper.readValue(body, Map.class);
                                                if (parsedBody.containsKey("error")) {
                                                    Object errorObj = parsedBody.get("error");
                                                    if (errorObj instanceof Map) {
                                                        @SuppressWarnings("unchecked")
                                                        Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                                                        if (errorMap.containsKey("message")) {
                                                            errorMessage = (String) errorMap.get("message");
                                                        }
                                                    }
                                                }
                                            } catch (Exception e) {
                                                // If parsing fails, use body as-is
                                            }

                                            // Return Mono.error with exception
                                            return Mono.error(new RuntimeException(
                                                    String.format("HTTP %d: %s",
                                                            response.statusCode().value(),
                                                            errorMessage)));
                                        });
                            }
                        })
                        .timeout(Duration.ofSeconds(90)) // Per-request timeout to fail fast on hanging connections
                        .retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofSeconds(5))
                                .filter(throwable -> {
                                    // Retry on HTTP 429 (Rate Limit), HTTP 5xx (Server Error), or Timeout
                                    String msg = throwable.getMessage();
                                    return (msg != null && (msg.contains("HTTP 429") || msg.contains("rate limit") ||
                                            msg.contains("HTTP 5")))
                                            || throwable instanceof java.util.concurrent.TimeoutException;
                                })
                                .doBeforeRetry(retrySignal -> log.warn(
                                        "⚠️ Retrying LLM service call {} due to error: {} (Attempt {}/3)",
                                        url, retrySignal.failure().getMessage(), retrySignal.totalRetries() + 1))
                                .onRetryExhaustedThrow((spec, signal) -> signal.failure()))
                        .timeout(Duration.ofMinutes(5)) // Global timeout (including retries) increased to 5 minutes
                        .doOnNext(response -> log.info("✅ Received response from LLM service: {}", url))
                        .doOnError(error -> log.error("❌ Error calling LLM service {}: {}", url, error.getMessage(),
                                error));

            } catch (Exception e) {
                return Mono.error(e);
            }
        });
    }

    @Override
    public Mono<Void> updatePriorities(String teamId, Map<String, Integer> priorityUpdates) {
        log.info("Updating priorities for team: {} - {} models", teamId, priorityUpdates.size());

        return Flux.fromIterable(priorityUpdates.entrySet())
                .flatMap(entry -> {
                    String modelId = entry.getKey();
                    Integer newPriority = entry.getValue();

                    return linqLlmModelRepository.findById(modelId)
                            .flatMap(model -> {
                                // Verify model belongs to the team
                                if (!teamId.equals(model.getTeamId())) {
                                    log.warn("Model {} does not belong to team {}", modelId, teamId);
                                    return Mono.empty();
                                }

                                model.setPriority(newPriority);
                                log.debug("Updating model {} priority to {}", model.getModelName(), newPriority);
                                return linqLlmModelRepository.save(model);
                            });
                })
                .then()
                .doOnSuccess(v -> log.info("✅ Successfully updated priorities for team: {}", teamId))
                .doOnError(
                        error -> log.error("❌ Error updating priorities for team {}: {}", teamId, error.getMessage()));
    }
}
