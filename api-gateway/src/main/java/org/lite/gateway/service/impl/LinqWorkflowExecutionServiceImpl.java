package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.model.ExecutionStatus;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.service.LinqLlmModelService;
import org.lite.gateway.service.LinqMicroService;
import org.lite.gateway.service.QueuedWorkflowService;
import org.lite.gateway.service.WorkflowExecutionContext;
import org.lite.gateway.service.LlmCostService;
import org.lite.gateway.service.ExecutionMonitoringService;
import org.lite.gateway.dto.ExecutionProgressUpdate;
import org.lite.gateway.util.AuditLogHelper;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.enums.AuditResultType;

import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqWorkflowExecutionServiceImpl implements LinqWorkflowExecutionService {
    private final LinqWorkflowExecutionRepository executionRepository;
    private final LinqLlmModelRepository linqLlmModelRepository;
    private final LinqLlmModelService linqLlmModelService;
    private final LinqMicroService linqMicroService;
    private final QueuedWorkflowService queuedWorkflowService;
    private final LlmCostService llmCostService;
    private final ExecutionMonitoringService executionMonitoringService;
    private final AuditLogHelper auditLogHelper;

    @Override
    public Mono<LinqResponse> executeWorkflow(LinqRequest request) {
        LocalDateTime startTime = LocalDateTime.now();
        List<LinqRequest.Query.WorkflowStep> steps = request.getQuery().getWorkflow();
        Map<Integer, Object> stepResults = new HashMap<>();
        List<LinqResponse.WorkflowStepMetadata> stepMetadata = new ArrayList<>();
        Map<String, Object> globalParams = request.getQuery().getParams();
        WorkflowExecutionContext context = new WorkflowExecutionContext(stepResults, globalParams);

        // Extract teamId from params (must be present from controller/executor)
        String teamId;
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object val = request.getQuery().getParams().get("teamId");
            if (val != null) {
                teamId = String.valueOf(val);
            } else {
                return Mono.error(new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Team ID must be provided in params"));
            }
        } else {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Team ID must be provided in params"));
        }
        String finalTeamId = teamId; // Make effectively final for use in lambdas

        String workflowId = request.getQuery() != null && request.getQuery().getWorkflowId() != null
                ? request.getQuery().getWorkflowId()
                : "unknown";
        String executedBy = request.getExecutedBy();

        // Log workflow execution started
        Map<String, Object> startContext = new HashMap<>();
        startContext.put("workflowId", workflowId);
        startContext.put("teamId", finalTeamId);
        startContext.put("stepCount", steps != null ? steps.size() : 0);
        startContext.put("startTimestamp", startTime.toString());
        if (executedBy != null) {
            startContext.put("executedBy", executedBy);
        }

        Mono<Void> startAuditLog = auditLogHelper.logDetailedEvent(
                AuditEventType.WORKFLOW_EXECUTION_STARTED,
                AuditActionType.READ,
                AuditResourceType.WORKFLOW,
                workflowId,
                String.format("Workflow execution started with %d steps", steps != null ? steps.size() : 0),
                startContext,
                null,
                null)
                .doOnError(auditError -> log.error("Failed to log audit event (workflow execution started): {}",
                        auditError.getMessage(), auditError))
                .onErrorResume(auditError -> Mono.empty());

        // Chain start audit log, then execute workflow
        return startAuditLog.then(Mono.defer(() -> {
            // Execute steps synchronously or asynchronously based on configuration
            Mono<LinqResponse> workflowMono = Mono.just(new LinqResponse());
            for (LinqRequest.Query.WorkflowStep step : steps) {
                workflowMono = workflowMono.flatMap(response -> {
                    Instant start = Instant.now();

                    // Send step progress update
                    return sendStepProgressUpdate(request, step, steps.size())
                            .then(Mono.defer(() -> {

                                // Check if step should be executed asynchronously
                                if (step.getAsync() != null && step.getAsync()) {
                                    // Create a WorkflowStep for async execution
                                    LinqResponse.WorkflowStep asyncStep = new LinqResponse.WorkflowStep();
                                    asyncStep.setStep(step.getStep());
                                    asyncStep.setTarget(step.getTarget());
                                    asyncStep.setParams(step.getParams());
                                    asyncStep.setAction(step.getAction());
                                    asyncStep.setIntent(step.getIntent());
                                    asyncStep.setAsync(true);
                                    asyncStep.setLlmConfig(step.getLlmConfig()); // Preserve llmConfig for async
                                                                                 // execution

                                    // Queue the step for async execution
                                    return queuedWorkflowService
                                            .queueAsyncStep(request.getQuery().getWorkflowId(), step.getStep(),
                                                    asyncStep, finalTeamId)
                                            .doOnSuccess(v -> log.info(
                                                    "Async step queued successfully for workflow {} step {}",
                                                    request.getQuery().getWorkflowId(), step.getStep()))
                                            .doOnError(e -> {
                                                log.error("Failed to queue async step for workflow {} step {}: {}",
                                                        request.getQuery().getWorkflowId(), step.getStep(),
                                                        e.getMessage(), e);
                                                // Create error metadata for failed queueing
                                                LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                                                meta.setStep(step.getStep());
                                                meta.setStatus("error");
                                                meta.setDurationMs(0);
                                                meta.setTarget(step.getTarget());
                                                meta.setExecutedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                                                meta.setAsync(true);
                                                stepMetadata.add(meta);
                                            })
                                            .then(Mono.defer(() -> {
                                                // Create metadata for async step
                                                LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                                                meta.setStep(step.getStep());
                                                meta.setStatus("queued");
                                                meta.setDurationMs(0);
                                                meta.setTarget(step.getTarget());
                                                meta.setExecutedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                                                meta.setAsync(true); // Set isAsync to true for async steps
                                                stepMetadata.add(meta);

                                                return Mono.just(response);
                                            }));
                                }

                                // Create a single-step LinqRequest
                                LinqRequest stepRequest = new LinqRequest();
                                LinqRequest.Link stepLink = new LinqRequest.Link();
                                stepLink.setTarget(step.getTarget());
                                stepLink.setAction(step.getAction());
                                stepRequest.setLink(stepLink);

                                LinqRequest.Query stepQuery = new LinqRequest.Query();
                                stepQuery.setIntent(step.getIntent());
                                stepQuery.setParams(resolvePlaceholdersForMap(step.getParams(), context));
                                stepQuery.setPayload(resolvePlaceholders(step.getPayload(), context));
                                stepQuery.setLlmConfig(step.getLlmConfig());

                                // Merge global params (e.g., teamId, userId) so downstream services see them
                                Map<String, Object> mergedParams = new HashMap<>();
                                if (globalParams != null) {
                                    mergedParams.putAll(globalParams);
                                }
                                if (stepQuery.getParams() != null) {
                                    mergedParams.putAll(stepQuery.getParams());
                                }
                                stepQuery.setParams(mergedParams);

                                // Set the workflow steps in the query to enable caching
                                stepQuery.setWorkflow(steps);
                                stepRequest.setQuery(stepQuery);

                                // Copy the executedBy field to maintain user context
                                stepRequest.setExecutedBy(request.getExecutedBy());

                                // Execute the step
                                // Try to get modelName from llmConfig first, then fallback to target-only
                                // search
                                final String modelName = (step.getLlmConfig() != null
                                        && step.getLlmConfig().getModel() != null)
                                                ? step.getLlmConfig().getModel()
                                                : null;

                                Mono<org.lite.gateway.entity.LinqLlmModel> llmModelMono;
                                if (modelName != null) {
                                    log.info(
                                            "🔍 Searching for LLM model configuration: modelCategory={}, modelName={}, teamId={}",
                                            step.getTarget(), modelName, finalTeamId);
                                    llmModelMono = linqLlmModelRepository
                                            .findByModelCategoryAndModelNameAndTeamId(step.getTarget(), modelName,
                                                    finalTeamId)
                                            .doOnNext(llmModel -> log.info(
                                                    "✅ Found LLM model configuration: modelCategory={}, modelName={}",
                                                    llmModel.getModelCategory(), llmModel.getModelName()))
                                            .doOnError(error -> log.error(
                                                    "❌ Error finding LLM model for modelCategory {} with modelName {}: {}",
                                                    step.getTarget(), modelName, error.getMessage()))
                                            .switchIfEmpty(Mono.defer(() -> {
                                                log.warn(
                                                        "⚠️ No LLM model found with modelName {}, falling back to target-only search",
                                                        modelName);
                                                return linqLlmModelRepository
                                                        .findByModelCategoryAndTeamId(step.getTarget(), finalTeamId)
                                                        .next() // Take the first result
                                                        .doOnNext(llmModel -> log.info(
                                                                "✅ Found LLM model configuration (fallback): modelCategory={}, modelName={}",
                                                                llmModel.getModelCategory(), llmModel.getModelName()));
                                            }));
                                } else {
                                    log.info("🔍 Searching for LLM model configuration: modelCategory={}, teamId={}",
                                            step.getTarget(), finalTeamId);
                                    llmModelMono = linqLlmModelRepository
                                            .findByModelCategoryAndTeamId(step.getTarget(), finalTeamId)
                                            .next() // Take the first result
                                            .doOnNext(llmModel -> log.info(
                                                    "✅ Found LLM model configuration: modelCategory={}, modelName={}",
                                                    llmModel.getModelCategory(), llmModel.getModelName()))
                                            .doOnError(error -> log.error(
                                                    "❌ Error finding LLM model for modelCategory {}: {}",
                                                    step.getTarget(), error.getMessage()));
                                }

                                return llmModelMono
                                        .doOnSuccess(llmModel -> {
                                            if (llmModel == null) {
                                                log.warn(
                                                        "⚠️ No LLM model configuration found for modelCategory: {}, will try microservice",
                                                        step.getTarget());
                                            }
                                        })
                                        .doOnNext(llmModel -> log.info("🚀 About to execute LLM request for step {}",
                                                step.getStep()))
                                        .flatMap(llmModel -> linqLlmModelService.executeLlmRequest(stepRequest,
                                                llmModel))
                                        .doOnNext(stepResponse -> log.info(
                                                "✅ LLM request executed successfully for step {}", step.getStep()))
                                        .switchIfEmpty(Mono.<LinqResponse>defer(() -> {
                                            log.info(
                                                    "📡 No LLM model found, executing microservice request for modelCategory: {}",
                                                    step.getTarget());
                                            return linqMicroService.execute(stepRequest);
                                        }))
                                        .flatMap(stepResponse -> {
                                            // Check if the result contains an error
                                            if (stepResponse.getResult() instanceof Map<?, ?> resultMap &&
                                                    resultMap.containsKey("error")) {
                                                return Mono.error(new ResponseStatusException(
                                                        HttpStatus.INTERNAL_SERVER_ERROR,
                                                        String.format("Workflow step %d failed: %s",
                                                                step.getStep(),
                                                                resultMap.get("error"))));
                                            }

                                            stepResults.put(step.getStep(), stepResponse.getResult());
                                            long durationMs = Duration.between(start, Instant.now()).toMillis();
                                            LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                                            meta.setStep(step.getStep());
                                            meta.setStatus("success");
                                            meta.setDurationMs(durationMs);
                                            meta.setTarget(step.getTarget());
                                            meta.setExecutedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                                            stepMetadata.add(meta);
                                            return Mono.just(response);
                                        })
                                        .onErrorResume(error -> {
                                            long durationMs = Duration.between(start, Instant.now()).toMillis();
                                            LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                                            meta.setStep(step.getStep());
                                            meta.setStatus("error");
                                            meta.setDurationMs(durationMs);
                                            meta.setTarget(step.getTarget());
                                            meta.setExecutedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                                            stepMetadata.add(meta);
                                            log.error("Error in workflow step {}: {}", step.getStep(),
                                                    error.getMessage());
                                            return Mono.error(new ResponseStatusException(
                                                    HttpStatus.INTERNAL_SERVER_ERROR,
                                                    String.format("Workflow step %d failed: %s", step.getStep(),
                                                            error.getMessage())));
                                        });
                            }));
                });
            }

            return workflowMono.map(response -> {
                // Build WorkflowResult
                LinqResponse.WorkflowResult workflowResult = new LinqResponse.WorkflowResult();
                List<LinqResponse.WorkflowStep> stepResultList = steps.stream()
                        .map(step -> {
                            LinqResponse.WorkflowStep stepResult = new LinqResponse.WorkflowStep();
                            stepResult.setStep(step.getStep());
                            stepResult.setTarget(step.getTarget());
                            stepResult.setResult(stepResults.get(step.getStep()));
                            return stepResult;
                        })
                        .collect(Collectors.toList());
                workflowResult.setSteps(stepResultList);

                // Set final result (from last step)
                Object lastResult = stepResults.get(steps.getLast().getStep());
                workflowResult.setFinalResult(extractFinalResult(lastResult));

                // Set response
                response.setResult(workflowResult);
                LinqResponse.Metadata metadata = new LinqResponse.Metadata();

                // Check if any step has failed
                boolean hasFailedStep = stepMetadata.stream()
                        .anyMatch(meta -> "error".equals(meta.getStatus()) || "failed".equals(meta.getStatus()));
                metadata.setStatus(hasFailedStep ? "error" : "success");

                metadata.setTeamId(finalTeamId);
                metadata.setCacheHit(false);
                metadata.setWorkflowMetadata(stepMetadata);
                response.setMetadata(metadata);
                return response;
            })
                    .flatMap(response -> {
                        // Log workflow execution completed/failed
                        long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
                        boolean hasFailedStep = stepMetadata.stream()
                                .anyMatch(
                                        meta -> "error".equals(meta.getStatus()) || "failed".equals(meta.getStatus()));

                        Map<String, Object> completionContext = new HashMap<>();
                        completionContext.put("workflowId", workflowId);
                        completionContext.put("teamId", finalTeamId);
                        completionContext.put("stepCount", steps != null ? steps.size() : 0);
                        completionContext.put("durationMs", durationMs);
                        completionContext.put("completionTimestamp", LocalDateTime.now().toString());
                        completionContext.put("hasFailedStep", hasFailedStep);
                        completionContext.put("stepMetadata", stepMetadata);
                        if (executedBy != null) {
                            completionContext.put("executedBy", executedBy);
                        }

                        return auditLogHelper.logDetailedEvent(
                                hasFailedStep ? AuditEventType.WORKFLOW_EXECUTION_FAILED
                                        : AuditEventType.WORKFLOW_EXECUTION_COMPLETED,
                                AuditActionType.READ,
                                AuditResourceType.WORKFLOW,
                                workflowId,
                                hasFailedStep ? String.format("Workflow execution failed after %d ms", durationMs)
                                        : String.format("Workflow execution completed successfully in %d ms",
                                                durationMs),
                                completionContext,
                                null,
                                null,
                                hasFailedStep ? AuditResultType.FAILED : AuditResultType.SUCCESS)
                                .doOnError(auditError -> log.error(
                                        "Failed to log audit event (workflow execution completed): {}",
                                        auditError.getMessage(), auditError))
                                .onErrorResume(auditError -> Mono.empty())
                                .thenReturn(response);
                    })
                    .doFinally(signalType -> {
                        // Clean up memory-intensive data structures after workflow completion
                        // Note: stepResults and globalParams are copied to response, so they can be
                        // safely cleared
                        // stepMetadata is directly referenced in response, so we don't clear it
                        stepResults.clear();
                        if (globalParams != null) {
                            globalParams.clear();
                        }
                        // Force garbage collection hint for memory cleanup
                        log.info("🧹 Starting post-execution cleanup for workflow");
                        System.gc();
                        log.info("🧹 Completed post-execution cleanup for workflow");
                    })
                    .onErrorResume(error -> {
                        // Log workflow execution failure
                        long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();

                        Map<String, Object> errorContext = new HashMap<>();
                        errorContext.put("workflowId", workflowId);
                        errorContext.put("teamId", finalTeamId);
                        errorContext.put("stepCount", steps != null ? steps.size() : 0);
                        errorContext.put("durationMs", durationMs);
                        errorContext.put("error", error.getMessage());
                        errorContext.put("errorType", error.getClass().getSimpleName());
                        errorContext.put("failureTimestamp", LocalDateTime.now().toString());
                        errorContext.put("stepMetadata", stepMetadata);
                        if (executedBy != null) {
                            errorContext.put("executedBy", executedBy);
                        }

                        Mono<Void> failureAuditLog = auditLogHelper.logDetailedEvent(
                                AuditEventType.WORKFLOW_EXECUTION_FAILED,
                                AuditActionType.READ,
                                AuditResourceType.WORKFLOW,
                                workflowId,
                                String.format("Workflow execution failed after %d ms: %s", durationMs,
                                        error.getMessage()),
                                errorContext,
                                null,
                                null,
                                AuditResultType.FAILED)
                                .doOnError(auditError -> log.error(
                                        "Failed to log audit event (workflow execution failed): {}",
                                        auditError.getMessage(), auditError))
                                .onErrorResume(auditError -> Mono.empty());

                        return failureAuditLog.then(Mono.defer(() -> {
                            // Create error response
                            LinqResponse errorResponse = new LinqResponse();
                            LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                            metadata.setSource("workflow");
                            metadata.setStatus("error");
                            // Try to use teamId from params on error path as well
                            String fallbackTeamId = null;
                            if (request.getQuery() != null && request.getQuery().getParams() != null) {
                                Object val = request.getQuery().getParams().get("teamId");
                                if (val != null)
                                    fallbackTeamId = String.valueOf(val);
                            }
                            if (fallbackTeamId == null || fallbackTeamId.isBlank()) {
                                fallbackTeamId = "unknown-team"; // Use fallback instead of blocking
                            }
                            metadata.setTeamId(fallbackTeamId);
                            metadata.setCacheHit(false);
                            metadata.setWorkflowMetadata(stepMetadata);
                            errorResponse.setMetadata(metadata);

                            // Set error result
                            LinqResponse.WorkflowResult errorResult = new LinqResponse.WorkflowResult();
                            errorResult.setSteps(steps.stream()
                                    .map(step -> {
                                        LinqResponse.WorkflowStep stepResult = new LinqResponse.WorkflowStep();
                                        stepResult.setStep(step.getStep());
                                        stepResult.setTarget(step.getTarget());
                                        stepResult.setResult(stepResults.get(step.getStep()));
                                        return stepResult;
                                    })
                                    .collect(Collectors.toList()));
                            errorResult.setFinalResult(error.getMessage());
                            errorResponse.setResult(errorResult);

                            return Mono.just(errorResponse);
                        }));
                    });
        }));
    }

    @Override
    public Mono<LinqWorkflowExecution> trackExecution(LinqRequest request, LinqResponse response) {
        return trackExecution(request, response, null);
    }

    @Override
    public Mono<LinqWorkflowExecution> trackExecutionWithAgentContext(LinqRequest request, LinqResponse response,
            Map<String, Object> agentContext) {
        return trackExecution(request, response, agentContext);
    }

    @Override
    public Mono<LinqWorkflowExecution> initializeExecution(LinqRequest request) {
        return initializeExecutionInternal(request, null);
    }

    @Override
    public Mono<LinqWorkflowExecution> initializeExecutionWithAgentContext(LinqRequest request,
            Map<String, Object> agentContext) {
        return initializeExecutionInternal(request, agentContext);
    }

    /**
     * Internal method that handles both regular and agent context tracking
     */
    private Mono<LinqWorkflowExecution> trackExecution(LinqRequest request, LinqResponse response,
            Map<String, Object> agentContext) {
        ExecutionStatus finalStatus = "success".equals(response.getMetadata().getStatus())
                ? ExecutionStatus.SUCCESS
                : ExecutionStatus.FAILED;

        long computedDuration = 0L;
        if (response.getMetadata() != null && response.getMetadata().getWorkflowMetadata() != null) {
            computedDuration = response.getMetadata().getWorkflowMetadata().stream()
                    .mapToLong(LinqResponse.WorkflowStepMetadata::getDurationMs)
                    .sum();

            response.getMetadata().getWorkflowMetadata().forEach(stepMetadata -> {
                if (stepMetadata.getTarget().equals("openai-chat") || stepMetadata.getTarget().equals("gemini-chat")
                        || stepMetadata.getTarget().equals("cohere-chat")
                        || stepMetadata.getTarget().equals("claude-chat")
                        || stepMetadata.getTarget().equals("openai-embed")
                        || stepMetadata.getTarget().equals("gemini-embed")
                        || stepMetadata.getTarget().equals("cohere-embed")) {
                    if (response.getResult() instanceof LinqResponse.WorkflowResult workflowResult) {
                        workflowResult.getSteps().stream()
                                .filter(step -> step.getStep() == stepMetadata.getStep())
                                .findFirst()
                                .ifPresent(step -> {
                                    if (step.getResult() instanceof Map<?, ?> resultMap) {
                                        LinqResponse.WorkflowStepMetadata.TokenUsage tokenUsage = new LinqResponse.WorkflowStepMetadata.TokenUsage();
                                        boolean hasTokenUsage = false;

                                        if ((stepMetadata.getTarget().equals("openai-chat")
                                                || stepMetadata.getTarget().equals("openai-embed"))
                                                && resultMap.containsKey("usage")) {
                                            Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
                                            long promptTokens = ((Number) usage.get("prompt_tokens")).longValue();
                                            long completionTokens = usage.containsKey("completion_tokens")
                                                    ? ((Number) usage.get("completion_tokens")).longValue()
                                                    : 0;
                                            long totalTokens = ((Number) usage.get("total_tokens")).longValue();

                                            tokenUsage.setPromptTokens(promptTokens);
                                            tokenUsage.setCompletionTokens(completionTokens);
                                            tokenUsage.setTotalTokens(totalTokens);

                                            String model = (String) resultMap.get("model");
                                            stepMetadata.setModel(model);

                                            double cost = llmCostService.calculateCost(model, promptTokens,
                                                    completionTokens);
                                            tokenUsage.setCostUsd(cost);
                                            log.debug("Calculated cost for {} ({}p/{}c tokens): ${}",
                                                    model, promptTokens, completionTokens, String.format("%.6f", cost));
                                            hasTokenUsage = true;

                                        } else if (stepMetadata.getTarget().equals("gemini-chat")
                                                && resultMap.containsKey("usageMetadata")) {
                                            Map<?, ?> usageMetadata = (Map<?, ?>) resultMap.get("usageMetadata");
                                            long promptTokens = ((Number) usageMetadata.get("promptTokenCount"))
                                                    .longValue();
                                            long completionTokens = usageMetadata.containsKey("candidatesTokenCount")
                                                    ? ((Number) usageMetadata.get("candidatesTokenCount")).longValue()
                                                    : 0;
                                            long totalTokens = ((Number) usageMetadata.get("totalTokenCount"))
                                                    .longValue();

                                            tokenUsage.setPromptTokens(promptTokens);
                                            tokenUsage.setCompletionTokens(completionTokens);
                                            tokenUsage.setTotalTokens(totalTokens);

                                            String model = (String) resultMap.get("modelVersion");
                                            stepMetadata.setModel(model);

                                            double cost = llmCostService.calculateCost(model, promptTokens,
                                                    completionTokens);
                                            tokenUsage.setCostUsd(cost);
                                            log.debug("Calculated cost for {} ({}p/{}c tokens): ${}",
                                                    model, promptTokens, completionTokens, String.format("%.6f", cost));
                                            hasTokenUsage = true;

                                        } else if (stepMetadata.getTarget().equals("gemini-embed")) {
                                            String inputText = "";
                                            if (request.getQuery() != null
                                                    && request.getQuery().getWorkflow() != null) {
                                                for (LinqRequest.Query.WorkflowStep ws : request.getQuery()
                                                        .getWorkflow()) {
                                                    if (ws.getStep() == stepMetadata.getStep()) {
                                                        if (ws.getParams() != null
                                                                && ws.getParams().containsKey("text")) {
                                                            Object textObj = ws.getParams().get("text");
                                                            if (textObj instanceof String) {
                                                                inputText = (String) textObj;
                                                            }
                                                        }
                                                        break;
                                                    }
                                                }
                                            }

                                            long estimatedTokens = Math.max(1, inputText.length() / 4);

                                            tokenUsage.setPromptTokens(estimatedTokens);
                                            tokenUsage.setCompletionTokens(0);
                                            tokenUsage.setTotalTokens(estimatedTokens);

                                            String model = "text-embedding-004";
                                            if (request.getQuery() != null
                                                    && request.getQuery().getWorkflow() != null) {
                                                request.getQuery().getWorkflow().stream()
                                                        .filter(ws -> ws.getStep() == stepMetadata.getStep())
                                                        .findFirst()
                                                        .ifPresent(ws -> {
                                                            if (ws.getLlmConfig() != null
                                                                    && ws.getLlmConfig().getModel() != null) {
                                                                stepMetadata.setModel(ws.getLlmConfig().getModel());
                                                            }
                                                        });
                                            }
                                            model = stepMetadata.getModel() != null ? stepMetadata.getModel() : model;

                                            double cost = llmCostService.calculateCost(model, estimatedTokens, 0);
                                            tokenUsage.setCostUsd(cost);
                                            log.info(
                                                    "⚠️ Gemini embedding - estimated {} tokens from {} chars (API doesn't return usage), cost: ${}",
                                                    estimatedTokens, inputText.length(), String.format("%.6f", cost));
                                            hasTokenUsage = true;

                                        } else if ((stepMetadata.getTarget().equals("cohere-chat")
                                                || stepMetadata.getTarget().equals("cohere-embed"))
                                                && resultMap.containsKey("meta")) {
                                            Map<?, ?> meta = (Map<?, ?>) resultMap.get("meta");
                                            if (meta.containsKey("billed_units")) {
                                                Map<?, ?> billedUnits = (Map<?, ?>) meta.get("billed_units");
                                                long promptTokens = billedUnits.containsKey("input_tokens")
                                                        ? ((Number) billedUnits.get("input_tokens")).longValue()
                                                        : 0;
                                                long completionTokens = billedUnits.containsKey("output_tokens")
                                                        ? ((Number) billedUnits.get("output_tokens")).longValue()
                                                        : 0;
                                                long totalTokens = promptTokens + completionTokens;

                                                tokenUsage.setPromptTokens(promptTokens);
                                                tokenUsage.setCompletionTokens(completionTokens);
                                                tokenUsage.setTotalTokens(totalTokens);

                                                String model = "command-r-08-2024";
                                                if (request.getQuery() != null
                                                        && request.getQuery().getWorkflow() != null) {
                                                    request.getQuery().getWorkflow().stream()
                                                            .filter(ws -> ws.getStep() == stepMetadata.getStep())
                                                            .findFirst()
                                                            .ifPresent(ws -> {
                                                                if (ws.getLlmConfig() != null
                                                                        && ws.getLlmConfig().getModel() != null) {
                                                                    stepMetadata.setModel(ws.getLlmConfig().getModel());
                                                                }
                                                            });
                                                }
                                                model = stepMetadata.getModel() != null ? stepMetadata.getModel()
                                                        : model;

                                                double cost = llmCostService.calculateCost(model, promptTokens,
                                                        completionTokens);
                                                tokenUsage.setCostUsd(cost);
                                                log.debug("Calculated cost for {} ({}p/{}c tokens): ${}",
                                                        model, promptTokens, completionTokens,
                                                        String.format("%.6f", cost));
                                                hasTokenUsage = true;
                                            }
                                        } else if (stepMetadata.getTarget().equals("claude-chat")
                                                && resultMap.containsKey("usage")) {
                                            Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
                                            long promptTokens = usage.containsKey("input_tokens")
                                                    ? ((Number) usage.get("input_tokens")).longValue()
                                                    : 0;
                                            long completionTokens = usage.containsKey("output_tokens")
                                                    ? ((Number) usage.get("output_tokens")).longValue()
                                                    : 0;
                                            long totalTokens = promptTokens + completionTokens;

                                            tokenUsage.setPromptTokens(promptTokens);
                                            tokenUsage.setCompletionTokens(completionTokens);
                                            tokenUsage.setTotalTokens(totalTokens);

                                            String model = "claude-sonnet-4-5";
                                            if (request.getQuery() != null
                                                    && request.getQuery().getWorkflow() != null) {
                                                request.getQuery().getWorkflow().stream()
                                                        .filter(ws -> ws.getStep() == stepMetadata.getStep())
                                                        .findFirst()
                                                        .ifPresent(ws -> {
                                                            if (ws.getLlmConfig() != null
                                                                    && ws.getLlmConfig().getModel() != null) {
                                                                stepMetadata.setModel(ws.getLlmConfig().getModel());
                                                            }
                                                        });
                                            }
                                            model = stepMetadata.getModel() != null ? stepMetadata.getModel() : model;

                                            double cost = llmCostService.calculateCost(model, promptTokens,
                                                    completionTokens);
                                            tokenUsage.setCostUsd(cost);
                                            log.debug("Calculated cost for {} ({}p/{}c tokens): ${}",
                                                    model, promptTokens, completionTokens, String.format("%.6f", cost));
                                            hasTokenUsage = true;
                                        }

                                        if (hasTokenUsage) {
                                            stepMetadata.setTokenUsage(tokenUsage);
                                        }
                                    }
                                });
                    }
                }
            });
        }
        final long totalDuration = computedDuration;

        String executionIdParam = null;
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object executionIdObj = request.getQuery().getParams().get("_executionId");
            if (executionIdObj != null) {
                executionIdParam = String.valueOf(executionIdObj);
            }
        }

        String agentExecutionId = agentContext != null ? (String) agentContext.get("agentExecutionId") : null;

        Mono<LinqWorkflowExecution> existingExecutionMono = Mono.empty();
        if (agentExecutionId != null && !agentExecutionId.isBlank()) {
            existingExecutionMono = executionRepository.findByAgentExecutionId(agentExecutionId);
        }
        if (executionIdParam != null && !executionIdParam.isBlank()) {
            existingExecutionMono = existingExecutionMono.switchIfEmpty(executionRepository.findById(executionIdParam));
        }

        return existingExecutionMono.flatMap(existing -> {
            existing.setTeamId(response.getMetadata().getTeamId());
            if (request.getQuery() != null && request.getQuery().getWorkflowId() != null) {
                existing.setWorkflowId(request.getQuery().getWorkflowId());
            }
            existing.setRequest(request);
            existing.setResponse(response);
            existing.setStatus(finalStatus);
            existing.setDurationMs(totalDuration);
            existing.setExecutedBy(request.getExecutedBy());
            if (existing.getExecutedAt() == null) {
                existing.setExecutedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
            }

            if (agentContext != null) {
                existing.setAgentId((String) agentContext.get("agentId"));
                existing.setAgentName((String) agentContext.get("agentName"));
                existing.setAgentTaskId((String) agentContext.get("agentTaskId"));
                existing.setAgentTaskName((String) agentContext.get("agentTaskName"));
                existing.setExecutionSource(
                        (String) agentContext.getOrDefault("executionSource", existing.getExecutionSource()));
                existing.setAgentExecutionId((String) agentContext.get("agentExecutionId"));
            }

            log.info("💾 Updating workflow execution {}", existing.getId());
            return executionRepository.save(existing)
                    .doOnSuccess(
                            e -> log.info("✅ Tracked workflow execution: {} with status: {}", e.getId(), e.getStatus()))
                    .doOnError(error -> log.error("❌ Error tracking workflow execution: {}", error.getMessage()));
        }).switchIfEmpty(Mono.defer(() -> {
            LinqWorkflowExecution execution = new LinqWorkflowExecution();
            execution.setTeamId(response.getMetadata().getTeamId());
            execution.setWorkflowId(request.getQuery() != null ? request.getQuery().getWorkflowId() : null);
            execution.setRequest(request);
            execution.setResponse(response);
            execution.setExecutedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
            execution.setExecutedBy(request.getExecutedBy());
            execution.setStatus(finalStatus);
            execution.setDurationMs(totalDuration);

            if (agentContext != null) {
                execution.setAgentId((String) agentContext.get("agentId"));
                execution.setAgentName((String) agentContext.get("agentName"));
                execution.setAgentTaskId((String) agentContext.get("agentTaskId"));
                execution.setAgentTaskName((String) agentContext.get("agentTaskName"));
                execution.setExecutionSource((String) agentContext.get("executionSource"));
                execution.setAgentExecutionId((String) agentContext.get("agentExecutionId"));
            }

            log.info("💾 Saving workflow execution");
            return executionRepository.save(execution)
                    .doOnSuccess(
                            e -> log.info("✅ Tracked workflow execution: {} with status: {}", e.getId(), e.getStatus()))
                    .doOnError(error -> log.error("❌ Error tracking workflow execution: {}", error.getMessage()));
        }));
    }

    private Mono<LinqWorkflowExecution> initializeExecutionInternal(LinqRequest request,
            Map<String, Object> agentContext) {
        if (request.getQuery() == null) {
            request.setQuery(new LinqRequest.Query());
        }
        if (request.getQuery().getParams() == null) {
            request.getQuery().setParams(new HashMap<>());
        }

        Object teamIdObj = request.getQuery().getParams().get("teamId");
        if (teamIdObj == null) {
            return Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Team ID must be provided in params"));
        }
        String teamId = String.valueOf(teamIdObj);

        LinqWorkflowExecution execution = new LinqWorkflowExecution();
        execution.setTeamId(teamId);
        execution.setWorkflowId(request.getQuery().getWorkflowId());
        execution.setRequest(request);
        execution.setExecutedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
        execution.setExecutedBy(request.getExecutedBy());
        execution.setStatus(ExecutionStatus.IN_PROGRESS);
        execution.setExecutionSource(agentContext != null
                ? (String) agentContext.getOrDefault("executionSource", "agent")
                : "manual");

        if (agentContext != null) {
            execution.setAgentId((String) agentContext.get("agentId"));
            execution.setAgentName((String) agentContext.get("agentName"));
            execution.setAgentTaskId((String) agentContext.get("agentTaskId"));
            execution.setAgentTaskName((String) agentContext.get("agentTaskName"));
            execution.setAgentExecutionId((String) agentContext.get("agentExecutionId"));
        }

        log.info("📝 Initializing workflow execution record");
        return executionRepository.save(execution)
                .doOnSuccess(saved -> {
                    request.getQuery().getParams().put("_executionId", saved.getId());
                    log.info("✅ Initialized workflow execution {} with status {}", saved.getId(), saved.getStatus());
                })
                .doOnError(error -> log.error("❌ Error initializing workflow execution: {}", error.getMessage()));
    }

    @Override
    public Flux<LinqWorkflowExecution> getWorkflowExecutions(String workflowId) {
        return executionRepository.findByWorkflowId(workflowId, Sort.by(Sort.Direction.DESC, "executedAt"))
                .doOnError(error -> log.error("Error fetching workflow executions: {}", error.getMessage()));
    }

    @Override
    public Flux<LinqWorkflowExecution> getTeamExecutions(String teamId) {
        return executionRepository.findByTeamId(teamId, Sort.by(Sort.Direction.DESC, "executedAt"))
                .doOnError(error -> log.error("Error fetching team executions: {}", error.getMessage()));
    }

    @Override
    public Mono<LinqWorkflowExecution> getExecution(String executionId) {
        return executionRepository.findById(executionId)
                .doOnError(error -> log.error("Error fetching execution: {}", error.getMessage()))
                .switchIfEmpty(Mono.error(new RuntimeException("Execution not found")));
    }

    public Mono<LinqWorkflowExecution> getExecutionByAgentExecutionId(String agentExecutionId) {
        log.info("Fetching execution by agentExecutionId: {}", agentExecutionId);
        return executionRepository.findByAgentExecutionId(agentExecutionId)
                .doOnSuccess(exec -> {
                    if (exec != null) {
                        log.info("Found execution with _id: {} for agentExecutionId: {}", exec.getId(),
                                agentExecutionId);
                    } else {
                        log.warn("No execution found for agentExecutionId: {}", agentExecutionId);
                    }
                })
                .doOnError(error -> log.error("Error fetching execution by agentExecutionId: {}", error.getMessage()))
                .switchIfEmpty(Mono
                        .error(new RuntimeException("Execution not found for agentExecutionId: " + agentExecutionId)));
    }

    @Override
    public Mono<Void> deleteExecution(String executionId, String teamId) {
        return executionRepository.findById(executionId)
                .filter(execution -> execution.getTeamId().equals(teamId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Execution not found or access denied")))
                .flatMap(execution -> executionRepository.delete(execution)
                        .doOnSuccess(v -> log.info("Deleted execution: {}", executionId))
                        .doOnError(error -> log.error("Error deleting execution: {}", error.getMessage())));
    }

    private Map<String, Object> resolvePlaceholdersForMap(Map<String, Object> input, WorkflowExecutionContext context) {
        if (input == null)
            return new HashMap<>();
        Map<String, Object> resolved = new HashMap<>();
        input.forEach((key, value) -> resolved.put(key, resolvePlaceholders(value, context)));
        return resolved;
    }

    private Object resolvePlaceholders(Object input, WorkflowExecutionContext context) {
        if (input == null)
            return null;
        if (input instanceof String stringInput) {
            // Check if the entire string is EXACTLY a single placeholder
            // If so, return the actual object instead of converting to string
            Pattern singlePlaceholderPattern = Pattern
                    .compile("^\\{\\{(step\\d+\\.result(?:\\.[^}]+)?|params\\.[\\w.]+)\\}\\}$");
            Matcher singleMatcher = singlePlaceholderPattern.matcher(stringInput);
            if (singleMatcher.matches()) {
                String placeholderContent = singleMatcher.group(1);
                Object resolvedObject = extractObjectValue(placeholderContent, context);
                if (resolvedObject != null) {
                    log.debug("🔍 Resolved placeholder {{{}}} as object type: {}", placeholderContent,
                            resolvedObject.getClass().getSimpleName());
                    return resolvedObject;
                }
            }
            // Otherwise, resolve as string (for string interpolation cases)
            return resolvePlaceholder(stringInput, context);
        }
        if (input instanceof List<?> list) {
            return list.stream()
                    .map(item -> resolvePlaceholders(item, context))
                    .collect(Collectors.toList());
        }
        if (input instanceof Map<?, ?> map) {
            return resolvePlaceholdersForMap(map.entrySet().stream()
                    .filter(entry -> entry.getKey() != null && entry.getValue() != null) // Filter out null keys and
                                                                                         // values
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            Map.Entry::getValue)),
                    context);
        }
        return input;
    }

    private Object extractObjectValue(String placeholderContent, WorkflowExecutionContext context) {
        // Handle step result placeholders
        Pattern stepPattern = Pattern.compile("^step(\\d+)\\.result(?:\\.([^}]+))?$");
        Matcher stepMatcher = stepPattern.matcher(placeholderContent);
        if (stepMatcher.matches()) {
            int stepNum = Integer.parseInt(stepMatcher.group(1));
            String path = stepMatcher.group(2);
            Object stepResult = context.getStepResults().get(stepNum);
            if (stepResult != null) {
                return path != null ? extractObjectFromPath(stepResult, path) : stepResult;
            }
        }

        // Handle params placeholders
        Pattern paramsPattern = Pattern.compile("^params\\.([\\w.]+)$");
        Matcher paramsMatcher = paramsPattern.matcher(placeholderContent);
        if (paramsMatcher.matches()) {
            String paramPath = paramsMatcher.group(1);
            if (context.getGlobalParams() != null) {
                return extractObjectFromPath(context.getGlobalParams(), paramPath);
            }
        }

        return null;
    }

    private Object extractObjectFromPath(Object obj, String path) {
        String[] parts = path.split("\\.");
        Object current = obj;
        for (String part : parts) {
            if (current == null)
                return null;

            // Check if this part contains array access (e.g., "embeddings[0]")
            if (part.contains("[")) {
                String arrayName = part.substring(0, part.indexOf("["));
                String indexStr = part.substring(part.indexOf("[") + 1, part.indexOf("]"));

                if (current instanceof Map<?, ?> map) {
                    current = map.get(arrayName);
                    if (current instanceof List<?> list && indexStr.matches("\\d+")) {
                        int index = Integer.parseInt(indexStr);
                        if (index >= 0 && index < list.size()) {
                            current = list.get(index);
                        } else {
                            return null;
                        }
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            } else {
                // Regular property access
                if (current instanceof Map<?, ?> map) {
                    current = map.get(part);
                } else if (current instanceof List<?> list && part.matches("\\d+")) {
                    int index = Integer.parseInt(part);
                    if (index >= 0 && index < list.size()) {
                        current = list.get(index);
                    } else {
                        return null;
                    }
                } else {
                    return null;
                }
            }
        }
        return current;
    }

    private String resolvePlaceholder(String value, WorkflowExecutionContext context) {
        String result = value;
        // Step result pattern - updated to handle complex JSON paths including arrays
        // and nested objects
        Pattern stepPattern = Pattern.compile("\\{\\{step(\\d+)\\.result(?:\\.([^}]+))?\\}\\}");
        Matcher stepMatcher = stepPattern.matcher(value);
        while (stepMatcher.find()) {
            int stepNum = Integer.parseInt(stepMatcher.group(1));
            String path = stepMatcher.group(2);
            Object stepResult = context.getStepResults().get(stepNum);
            String replacement = "";
            if (stepResult != null) {
                replacement = path != null ? extractValue(stepResult, path) : String.valueOf(stepResult);
            }
            result = result.replace(stepMatcher.group(0), replacement);
        }
        // Global params pattern
        Pattern paramsPattern = Pattern.compile("\\{\\{params\\.([\\w.]+)\\}\\}");
        Matcher paramsMatcher = paramsPattern.matcher(result);
        while (paramsMatcher.find()) {
            String paramPath = paramsMatcher.group(1);
            String replacement = "";
            if (context.getGlobalParams() != null) {
                replacement = extractValue(context.getGlobalParams(), paramPath);
            }
            result = result.replace(paramsMatcher.group(0), replacement);
        }
        return result;
    }

    private String extractValue(Object obj, String path) {
        Object result = extractObjectFromPath(obj, path);
        return result != null ? String.valueOf(result) : "";
    }

    private String extractFinalResult(Object result) {
        if (result instanceof Map<?, ?> resultMap) {
            Object choices = resultMap.get("choices");
            if (choices instanceof List<?> choiceList && !choiceList.isEmpty()) {
                Object firstChoice = choiceList.getFirst();
                if (firstChoice instanceof Map<?, ?> choiceMap) {
                    Object message = choiceMap.get("message");
                    if (message instanceof Map<?, ?> messageMap) {
                        Object content = messageMap.get("content");
                        return content != null ? content.toString() : "";
                    }
                }
            }
        }
        return result != null ? result.toString() : "";
    }

    /**
     * Send step progress update for execution monitoring
     */
    private Mono<Void> sendStepProgressUpdate(LinqRequest request, LinqRequest.Query.WorkflowStep step,
            int totalSteps) {
        return Mono.defer(() -> {
            try {
                // Extract execution context from request
                String executionId = extractExecutionId(request);
                String agentId = extractAgentId(request);
                String agentName = extractAgentName(request);
                String taskId = extractTaskId(request);
                String taskName = extractTaskName(request);
                String teamId = extractTeamId(request);

                log.info(
                        "🔍 Execution monitoring context - executionId: {}, agentId: {}, agentName: {}, taskId: {}, taskName: {}, teamId: {}",
                        executionId, agentId, agentName, taskId, taskName, teamId);

                if (executionId == null) {
                    // Not an agent execution, skip monitoring
                    log.info("⏭️ Skipping execution monitoring - no executionId found");
                    return Mono.empty();
                }

                // Get the actual execution start time and calculate duration
                java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
                java.time.LocalDateTime startedAt = now; // Default to now if we can't get the actual start time
                long durationMs = 0;

                // Try to get the actual execution start time from the AgentExecution
                if (executionId != null) {
                    try {
                        // Skip fetching AgentExecution to avoid blocking in reactive context
                        // Use current time as approximation
                        log.info(
                                "📊 Skipping AgentExecution fetch to avoid blocking in reactive context for executionId: {}",
                                executionId);
                        startedAt = now;
                        durationMs = 0; // No duration calculation to avoid blocking
                    } catch (Exception e) {
                        log.warn("Failed to fetch AgentExecution for duration calculation: {}", e.getMessage());
                        startedAt = now;
                        durationMs = 0; // No duration if we can't fetch execution
                    }
                }

                log.info("📊 About to build ExecutionProgressUpdate with durationMs: {}", durationMs);

                ExecutionProgressUpdate update = ExecutionProgressUpdate.builder()
                        .executionId(executionId)
                        .agentId(agentId)
                        .agentName(agentName)
                        .taskId(taskId)
                        .taskName(taskName)
                        .teamId(teamId)
                        .status("RUNNING")
                        .currentStep(step.getStep())
                        .totalSteps(totalSteps)
                        .currentStepName(generateStepName(step))
                        .currentStepTarget(step.getTarget())
                        .currentStepAction(step.getAction())
                        .startedAt(startedAt)
                        .lastUpdatedAt(now)
                        .executionDurationMs(durationMs)
                        .build();

                log.info("📊 Built ExecutionProgressUpdate - executionDurationMs: {}", update.getExecutionDurationMs());
                log.info(
                        "📊 Built ExecutionProgressUpdate - all fields: executionId={}, agentId={}, status={}, currentStep={}, totalSteps={}, startedAt={}, lastUpdatedAt={}, executionDurationMs={}",
                        update.getExecutionId(), update.getAgentId(), update.getStatus(), update.getCurrentStep(),
                        update.getTotalSteps(),
                        update.getStartedAt(), update.getLastUpdatedAt(), update.getExecutionDurationMs());

                log.info("📊 Sending ExecutionProgressUpdate with durationMs: {} for execution: {}", durationMs,
                        executionId);

                return executionMonitoringService.sendStepProgress(update)
                        .doOnSubscribe(subscription -> log.info("📊 Subscribed to sendStepProgress for execution: {}",
                                executionId))
                        .doOnSuccess(result -> log.info("📊 sendStepProgress completed successfully for execution: {}",
                                executionId))
                        .doOnError(error -> log.error("📊 sendStepProgress failed for execution: {} with error: {}",
                                executionId, error.getMessage()))
                        .doOnCancel(() -> log.warn("📊 sendStepProgress cancelled for execution: {}", executionId));
            } catch (Exception e) {
                log.warn("Failed to send step progress update: {}", e.getMessage());
                return Mono.empty();
            }
        });
    }

    private String extractExecutionId(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object executionId = request.getQuery().getParams().get("agentExecutionId");
            log.debug("🔍 Extracting executionId from params: {}", executionId);
            return executionId != null ? executionId.toString() : null;
        }
        log.debug("🔍 No query or params found in request");
        return null;
    }

    private String extractAgentId(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object agentId = request.getQuery().getParams().get("agentId");
            return agentId != null ? agentId.toString() : null;
        }
        return null;
    }

    private String extractAgentName(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object agentName = request.getQuery().getParams().get("agentName");
            log.debug("🔍 Extracting agentName from params: {}", agentName);
            return agentName != null ? agentName.toString() : null;
        }
        log.debug("🔍 No query or params found in request for agentName");
        return null;
    }

    private String extractTaskId(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object taskId = request.getQuery().getParams().get("agentTaskId");
            return taskId != null ? taskId.toString() : null;
        }
        return null;
    }

    private String extractTaskName(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object taskName = request.getQuery().getParams().get("agentTaskName");
            return taskName != null ? taskName.toString() : null;
        }
        return null;
    }

    private String extractTeamId(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object teamId = request.getQuery().getParams().get("teamId");
            return teamId != null ? teamId.toString() : null;
        }
        return null;
    }

    /**
     * Generate a descriptive step name from step configuration
     */
    private String generateStepName(LinqRequest.Query.WorkflowStep step) {
        // Use description if available (most user-friendly)
        if (step.getDescription() != null && !step.getDescription().trim().isEmpty()) {
            return step.getDescription();
        }

        String target = step.getTarget();
        String action = step.getAction();
        String intent = step.getIntent();

        // Handle common target/action combinations for better descriptions
        if (target != null && action != null) {
            // Knowledge Graph/Milvus operations
            if (target.equals("api-gateway") && intent != null && intent.contains("/api/milvus/")) {
                if (intent.contains("/search")) {
                    return "Searching knowledge base...";
                } else if (intent.contains("/records")) {
                    return "Saving to knowledge base...";
                }
            }

            // LLM/AI operations
            if (target != null && (target.contains("openai") || target.contains("gemini") ||
                    target.contains("claude") || target.contains("cohere"))) {
                if (action.equals("generate")) {
                    return "Generating answer using AI...";
                }
            }

            // Build descriptive name from target and action
            String targetDisplay = target != null ? target.replace("-", " ") : "service";
            String actionDisplay = action != null ? capitalizeFirst(action) : "Processing";

            // Special cases for common actions
            if (action.equals("create") && target.equals("api-gateway")) {
                return "Processing with knowledge base...";
            } else if (action.equals("fetch")) {
                return "Fetching data from " + targetDisplay + "...";
            } else if (action.equals("generate")) {
                return "Generating response...";
            } else if (action.equals("search")) {
                return "Searching " + targetDisplay + "...";
            } else {
                return actionDisplay + " " + targetDisplay + "...";
            }
        }

        // Fallback to step number
        return "Step " + step.getStep();
    }

    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
}