package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.lite.gateway.dto.ExecutionProgressUpdate;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.LinqResponse.QueuedWorkflowStep;
import org.lite.gateway.model.ExecutionStatus;
import org.lite.gateway.service.QueuedWorkflowService;
import org.lite.gateway.service.LinqLlmModelService;
import org.lite.gateway.service.LinqMicroService;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.springframework.stereotype.Service;
import org.lite.gateway.service.CacheService;
import org.lite.gateway.service.ExecutionMonitoringService;

import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueuedWorkflowServiceImpl implements QueuedWorkflowService {
    private static final String QUEUE_KEY = "async:step:queue";

    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final LinqLlmModelRepository linqLlmModelRepository;
    private final LinqLlmModelService linqLlmModelService;
    private final LinqMicroService linqMicroService;
    private final LinqWorkflowExecutionRepository executionRepository;
    private final ExecutionMonitoringService executionMonitoringService;

    @Override
    public Mono<Void> queueAsyncStep(String workflowId, String executionId, int stepNumber,
            LinqResponse.WorkflowStep step, String teamId) {
        try {
            // Generate a unique internal execution ID for this specific step attempt
            String stepExecutionId = UUID.randomUUID().toString();

            // Set the execution ID in the step object
            step.setExecutionId(stepExecutionId);

            // Create the step execution with the unique ID
            QueuedWorkflowStep stepExecution = new QueuedWorkflowStep();
            stepExecution.setWorkflowId(workflowId); // Set the workflowId (template)
            stepExecution.setStepId(String.valueOf(stepNumber)); // Use step number as stepId
            stepExecution.setStatus("queued");
            stepExecution.setQueuedAt(LocalDateTime.now());
            stepExecution.setExecutionId(stepExecutionId); // Add the internal step execution ID

            if (executionId == null) {
                log.warn(
                        "⚠️ Queuing async step for workflow {} step {} without executionId. Status tracking may be unreliable.",
                        workflowId, stepNumber);
            }
            // Use executionId:stepNumber:stepExecutionId as the Redis key (use executionId
            // if available for uniqueness)
            String redisKey = getStatusKey(workflowId, executionId, String.valueOf(stepNumber), stepExecutionId);

            // Create the task to be queued
            AsyncStepTask task = new AsyncStepTask(
                    workflowId,
                    executionId,
                    String.valueOf(stepNumber), // Use step number as stepId
                    step,
                    step.getParams(),
                    step.getAction(),
                    step.getIntent(),
                    step.getPayload(),
                    teamId);

            // First store the status (serialize to JSON)
            String stepJson = objectMapper.writeValueAsString(stepExecution);
            return cacheService.set(redisKey, stepJson, Duration.ofHours(24))
                    .doOnSuccess(v -> log.info("Queued async step for workflow {} step {} with execution ID {}",
                            workflowId, stepNumber, executionId))
                    .doOnError(e -> log.error("Failed to queue async step for workflow {} step {}: {}",
                            workflowId, stepNumber, e.getMessage(), e))
                    // Then add the task to the queue
                    .then(cacheService.rightPush(QUEUE_KEY, objectMapper.writeValueAsString(task))
                            .doOnSuccess(
                                    v -> log.info("Added task to queue for workflow {} step {} with execution ID {}",
                                            workflowId, stepNumber, executionId))
                            .doOnError(e -> log.error("Failed to add task to queue for workflow {} step {}: {}",
                                    workflowId, stepNumber, e.getMessage(), e)))
                    .then();
        } catch (Exception e) {
            log.error("Failed to queue async step: {}", e.getMessage(), e);
            return Mono.error(e);
        }
    }

    @Override
    public Mono<QueuedWorkflowStep> getAsyncStepStatus(String workflowId, int stepNumber) {
        // Get all keys matching the pattern workflowId:*:stepNumber:*
        String pattern = String.format("%s:*:%d:*", workflowId, stepNumber);
        return cacheService.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.empty();
                    }
                    // Get the most recent execution (last key in the list)
                    String latestKey = keys.get(keys.size() - 1);
                    return cacheService.get(latestKey)
                            .map(json -> {
                                try {
                                    return objectMapper.readValue(json, QueuedWorkflowStep.class);
                                } catch (Exception e) {
                                    throw new RuntimeException("Failed to deserialize QueuedWorkflowStep", e);
                                }
                            });
                });
    }

    @Override
    public Mono<Void> cancelAsyncStep(String workflowId, int stepNumber) {
        // Get all keys matching the pattern workflowId:*:stepNumber:*
        String pattern = String.format("%s:*:%d:*", workflowId, stepNumber);
        return cacheService.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.error(new RuntimeException(
                                "No queued step found for workflow " + workflowId + " step " + stepNumber));
                    }
                    // Get the most recent execution (last key in the list)
                    String latestKey = keys.get(keys.size() - 1);
                    return cacheService.get(latestKey)
                            .flatMap(json -> {
                                try {
                                    QueuedWorkflowStep stepExecution = objectMapper.readValue(json,
                                            QueuedWorkflowStep.class);
                                    stepExecution.setStatus("cancelled");
                                    stepExecution.setCancelledAt(LocalDateTime.now());
                                    String updatedJson = objectMapper.writeValueAsString(stepExecution);
                                    return cacheService.set(latestKey, updatedJson, Duration.ofHours(24));
                                } catch (Exception e) {
                                    return Mono.error(new RuntimeException("Failed to update step status", e));
                                }
                            });
                });
    }

    @Override
    public Mono<Map<String, QueuedWorkflowStep>> getAllAsyncSteps(String workflowId) {
        // Get all keys matching the pattern workflowId:*
        String pattern = String.format("%s:*", workflowId);
        return cacheService.keys(pattern)
                .collectList()
                .flatMap(keys -> {
                    if (keys.isEmpty()) {
                        return Mono.empty();
                    }
                    return reactor.core.publisher.Flux.fromIterable(keys)
                            .flatMap(key -> cacheService.get(key)
                                    .map(json -> {
                                        try {
                                            return objectMapper.readValue(json, QueuedWorkflowStep.class);
                                        } catch (Exception e) {
                                            throw new RuntimeException("Failed to deserialize QueuedWorkflowStep", e);
                                        }
                                    }))
                            .collectList()
                            .map(steps -> steps.stream()
                                    .collect(Collectors.toMap(
                                            QueuedWorkflowStep::getStepId,
                                            step -> step)));
                });
    }

    private String getStatusKey(String workflowId, String executionId, String stepId, String stepExecutionId) {
        return String.format("%s:%s:%s:%s",
                workflowId != null ? workflowId : "unknown",
                executionId != null ? executionId : "unknown",
                stepId,
                stepExecutionId);
    }

    protected Mono<Void> processAsyncStep(AsyncStepTask task) {
        // Get the execution ID from the step object
        String stepExecutionId = task.getStep().getExecutionId();
        if (stepExecutionId == null) {
            log.error("No execution ID found in step for workflow {} step {}", task.getWorkflowId(), task.getStepId());
            return Mono.error(new RuntimeException("No execution ID found in step"));
        }

        String statusKey = getStatusKey(task.getWorkflowId(), task.getExecutionId(), task.getStepId(), stepExecutionId);
        log.info("🚀 [ASYNC] Processing workflow={}, step={}, execution={}, statusKey={}",
                task.getWorkflowId(), task.getStepId(), task.getExecutionId(), statusKey);

        // First check if the step is already being processed or completed
        return cacheService.get(statusKey)
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("⚠️ [ASYNC] Status key NOT FOUND in Redis: {}. This task might be stale or from a different environment.", statusKey);
                    return Mono.empty();
                }))
                .map(json -> {
                    try {
                        return objectMapper.readValue(json, QueuedWorkflowStep.class);
                    } catch (Exception e) {
                        log.error("❌ [ASYNC] Failed to deserialize status for key {}: {}", statusKey, e.getMessage());
                        throw new RuntimeException("Failed to deserialize QueuedWorkflowStep", e);
                    }
                })
                .doOnNext(status -> log.info("📊 [ASYNC] Current status for {}: {}", statusKey, status.getStatus()))
                .filter(status -> !status.getStatus().equals("processing") && !status.getStatus().equals("completed"))
                .flatMap(status -> updateStatusToProcessing(statusKey, status))
                .doOnNext(status -> log.info("Updated status to processing"))
                .flatMap(status -> {
                    log.info("Executing step with team {} for workflow {} step {}",
                            task.getTeamId(), task.getWorkflowId(), task.getStepId());
                    return executeStepWithTeam(task.getStep(), task.getParams(), task.getAction(), task.getIntent(),
                            task.getTeamId());
                })
                .doOnNext(response -> {
                    // Check if the response contains an error
                    if (response.getResult() instanceof Map<?, ?> resultMap && resultMap.containsKey("error")) {
                        throw new RuntimeException((String) resultMap.get("error"));
                    }
                    log.info("Step execution completed with result: {}", response.getResult());
                })
                .flatMap(response -> {
                    log.info("Updating status to completed for workflow {} step {}",
                            task.getWorkflowId(), task.getStepId());
                    return updateStatusToCompleted(statusKey, response.getResult(), task.getWorkflowId(),
                            task.getExecutionId(), task.getStepId());
                })
                .doOnNext(v -> log.info("Updated status to completed"))
                .onErrorResume(error -> {
                    log.error("Error in async step processing: {}", error.getMessage(), error);
                    return updateStatusToFailed(statusKey, error.getMessage(), task.getWorkflowId(),
                            task.getExecutionId(), task.getStepId())
                            .then(Mono.error(error)); // Re-throw the error to ensure it's propagated
                })
                .then();
    }

    private Mono<QueuedWorkflowStep> updateStatusToProcessing(String statusKey, QueuedWorkflowStep status) {
        status.setStatus("processing");
        try {
            String json = objectMapper.writeValueAsString(status);
            return cacheService.set(statusKey, json, Duration.ofHours(24))
                    .thenReturn(status);
        } catch (Exception e) {
            return Mono.error(new RuntimeException("Failed to serialize QueuedWorkflowStep", e));
        }
    }

    private Mono<LinqResponse> executeStepWithTeam(LinqResponse.WorkflowStep step, Map<String, Object> params,
            String action, String intent, String teamId) {
        LinqRequest stepRequest = createStepRequest(step, params, action, intent, teamId);

        return Mono.just(teamId)
                .flatMap(id -> {
                    // Try to get modelName from llmConfig
                    final String modelName = (step.getLlmConfig() != null && step.getLlmConfig().getModel() != null)
                            ? step.getLlmConfig().getModel()
                            : null;

                    if (modelName != null) {
                        log.info(
                                "🔍 Searching for LLM model configuration for async step {}: modelCategory={}, modelName={}, teamId={}",
                                step.getStep(), step.getTarget(), modelName, id);
                        return linqLlmModelRepository
                                .findByModelCategoryAndModelNameAndTeamId(step.getTarget(), modelName, id)
                                .doOnNext(llmModel -> log.info(
                                        "✅ Found LLM model configuration for async step {}: modelCategory={}, modelName={}",
                                        step.getStep(), llmModel.getModelCategory(), llmModel.getModelName()))
                                .doOnError(error -> log.error(
                                        "❌ Error finding LLM model for modelCategory {} with modelName {}: {}",
                                        step.getTarget(), modelName, error.getMessage()));
                    } else {
                        // If no modelName is specified, we cannot find the correct configuration
                        // since modelCategory alone can return multiple records (chat vs embed)
                        log.warn(
                                "⚠️ No modelName specified for async step {}, cannot determine LLM model configuration",
                                step.getStep());
                        return Mono.empty();
                    }
                })
                .doOnSuccess(llmModel -> {
                    if (llmModel == null) {
                        log.warn("⚠️ No LLM model configuration found for modelCategory: {}, will try microservice",
                                step.getTarget());
                    }
                })
                .doOnNext(llmModel -> log.info("🚀 About to execute async LLM request for step {}", step.getStep()))
                .flatMap(llmModel -> linqLlmModelService.executeLlmRequest(stepRequest, llmModel))
                .doOnNext(stepResponse -> log.info("✅ Async LLM request executed successfully for step {}",
                        step.getStep()))
                .switchIfEmpty(Mono.<LinqResponse>defer(() -> {
                    log.info(
                            "📡 No LLM model found for async step {}, executing microservice request for modelCategory: {}",
                            step.getStep(), step.getTarget());
                    return linqMicroService.execute(stepRequest);
                }));
    }

    private LinqRequest createStepRequest(LinqResponse.WorkflowStep step, Map<String, Object> params, String action,
            String intent, String teamId) {
        LinqRequest stepRequest = new LinqRequest();
        LinqRequest.Link stepLink = new LinqRequest.Link();
        stepLink.setTarget(step.getTarget());
        stepLink.setAction(action);
        stepRequest.setLink(stepLink);

        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent(intent);

        // Ensure teamId is in params for microservice routing and security
        Map<String, Object> finalParams = params != null ? new java.util.HashMap<>(params) : new java.util.HashMap<>();
        if (teamId != null && !finalParams.containsKey("teamId")) {
            finalParams.put("teamId", teamId);
        }
        query.setParams(finalParams);
        query.setPayload(step.getPayload());
        stepRequest.setQuery(query);

        return stepRequest;
    }

    private Mono<Void> updateStatusToCompleted(String statusKey, Object result, String workflowId, String executionId,
            String stepId) {
        return cacheService.get(statusKey)
                .flatMap(json -> {
                    try {
                        QueuedWorkflowStep status = objectMapper.readValue(json, QueuedWorkflowStep.class);
                        status.setStatus("completed");
                        status.setCompletedAt(LocalDateTime.now());
                        status.setResult(result);
                        String updatedJson = objectMapper.writeValueAsString(status);
                        return cacheService.set(statusKey, updatedJson, Duration.ofHours(24));
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Failed to update status", e));
                    }
                })
                .then(updateWorkflowExecution(workflowId, executionId, stepId, "completed", result));
    }

    private Mono<Void> updateStatusToFailed(String statusKey, String errorMessage, String workflowId,
            String executionId, String stepId) {
        return cacheService.get(statusKey)
                .flatMap(json -> {
                    try {
                        QueuedWorkflowStep status = objectMapper.readValue(json, QueuedWorkflowStep.class);
                        status.setStatus("failed");
                        status.setError(errorMessage);
                        status.setCompletedAt(LocalDateTime.now());
                        String updatedJson = objectMapper.writeValueAsString(status);
                        return cacheService.set(statusKey, updatedJson, Duration.ofHours(24));
                    } catch (Exception e) {
                        return Mono.error(new RuntimeException("Failed to update status", e));
                    }
                })
                .then(updateWorkflowExecution(workflowId, executionId, stepId, "failed", null));
    }

    private Mono<Void> updateWorkflowExecution(String workflowId, String executionId, String stepId, String status,
            Object result) {
        Mono<org.lite.gateway.entity.LinqWorkflowExecution> executionMono;
        if (executionId != null && !executionId.equals("unknown")) {
            executionMono = executionRepository.findById(executionId);
        } else {
            executionMono = executionRepository.findByWorkflowId(workflowId, Sort.by(Sort.Direction.DESC, "executedAt"))
                    .next();
        }

        return executionMono
                .<Void>flatMap(execution -> {
                    // Update the step result and status in the workflow execution
                    if (execution.getResponse() != null) {
                        
                        LinqResponse.WorkflowResult workflowResult = null;
                        if (execution.getResponse().getResult() instanceof LinqResponse.WorkflowResult wr) {
                            workflowResult = wr;
                        } else if (execution.getResponse().getResult() instanceof Map) {
                            try {
                                workflowResult = objectMapper.convertValue(execution.getResponse().getResult(), LinqResponse.WorkflowResult.class);
                                // Save it back so it's serialized correctly!
                                execution.getResponse().setResult(workflowResult);
                            } catch (Exception e) {
                                log.error("❌ [ASYNC] Failed to convert result map to WorkflowResult", e);
                            }
                        }

                        if (workflowResult != null) {
                            // Update step result
                            workflowResult.getSteps().stream()
                                    .filter(step -> String.valueOf(step.getStep()).equals(stepId))
                                    .findFirst()
                                    .ifPresent(step -> {
                                        step.setResult(result);
                                        step.setStatus(status);
                                    });
                        }

                        // Update step metadata
                        if (execution.getResponse().getMetadata() != null &&
                                execution.getResponse().getMetadata().getWorkflowMetadata() != null) {

                            execution.getResponse().getMetadata().getWorkflowMetadata().stream()
                                    .filter(meta -> String.valueOf(meta.getStep()).equals(stepId))
                                    .findFirst()
                                    .ifPresent(meta -> {
                                        meta.setStatus(status);
                                        if (status.equals("completed")) {
                                            // Calculate duration in milliseconds
                                            long startTime = meta.getExecutedAt() != null ? 
                                                    meta.getExecutedAt().toInstant(ZoneOffset.UTC).toEpochMilli() : 
                                                    System.currentTimeMillis();
                                            long endTime = System.currentTimeMillis();
                                            meta.setDurationMs(endTime - startTime);
                                        }
                                    });
                        }

                        // Update overall workflow status if step failed
                        if (status.equals("failed")) {
                            if (execution.getResponse().getMetadata() != null) {
                                execution.getResponse().getMetadata().setStatus("error");
                            }
                            execution.setStatus(ExecutionStatus.FAILED);
                        } else if (status.equals("completed") && 
                                execution.getResponse().getMetadata() != null && 
                                execution.getResponse().getMetadata().getWorkflowMetadata() != null) {
                            // Check if all steps are now completed/skipped/failed
                            boolean allStepsFinished = execution.getResponse().getMetadata().getWorkflowMetadata()
                                    .stream()
                                    .allMatch(meta -> {
                                        String s = meta.getStatus();
                                        return "success".equals(s) || "completed".equals(s) || "failed".equals(s)
                                                || "error".equals(s) || "skipped".equals(s) || "cancelled".equals(s);
                                    });

                            if (allStepsFinished) {
                                boolean anyFailed = execution.getResponse().getMetadata().getWorkflowMetadata().stream()
                                        .anyMatch(meta -> "failed".equals(meta.getStatus())
                                                || "error".equals(meta.getStatus()));

                                execution.setStatus(anyFailed ? ExecutionStatus.FAILED : ExecutionStatus.SUCCESS);
                                execution.getResponse().getMetadata().setStatus(anyFailed ? "error" : "success");
                                log.info("🏁 Overall workflow execution {} completed with status {}", execution.getId(), execution.getStatus());
                            }
                        }
                    }

                    return executionRepository.save(execution)
                            .<Void>flatMap(savedExecution -> {
                                // Send progress update via WebSocket
                                try {
                                    // Use agentExecutionId if available, fallback to workflow execution ID
                                    String wsExecutionId = (savedExecution.getAgentExecutionId() != null
                                            && !savedExecution.getAgentExecutionId().isEmpty())
                                                    ? savedExecution.getAgentExecutionId()
                                                    : savedExecution.getId();

                                    ExecutionProgressUpdate update = ExecutionProgressUpdate
                                            .builder()
                                            .executionId(wsExecutionId)
                                            .teamId(savedExecution.getTeamId())
                                            .agentId(savedExecution.getAgentId())
                                            .agentName(savedExecution.getAgentName())
                                            .taskId(savedExecution.getAgentTaskId())
                                            .taskName(savedExecution.getAgentTaskName())
                                            .status(savedExecution.getStatus().name())
                                            .currentStep(Integer.parseInt(stepId))
                                            .totalSteps(savedExecution.getResponse().getMetadata().getWorkflowMetadata()
                                                    .size())
                                            .startedAt(savedExecution.getExecutedAt())
                                            .stepResults(convertWorkflowResults(savedExecution.getResponse()))
                                            .build();

                                    if (savedExecution.getStatus() == ExecutionStatus.SUCCESS) {
                                        return executionMonitoringService.sendExecutionCompleted(update);
                                    } else if (savedExecution.getStatus() == ExecutionStatus.FAILED) {
                                        return executionMonitoringService.sendExecutionFailed(update,
                                                "Step execution failed");
                                    } else {
                                        return executionMonitoringService.sendStepProgress(update);
                                    }
                                } catch (Exception e) {
                                    log.error("Failed to send WebSocket update: {}", e.getMessage());
                                    return Mono.<Void>empty();
                                }
                            });
                })
                .then();
    }

    private Map<String, Object> convertWorkflowResults(LinqResponse response) {
        if (response == null || response.getResult() == null) {
            return new java.util.HashMap<>();
        }

        if (response.getResult() instanceof LinqResponse.WorkflowResult wr) {
            return wr.getSteps().stream()
                    .collect(Collectors.toMap(
                            s -> String.valueOf(s.getStep()),
                            s -> s.getResult() != null ? s.getResult() : new java.util.HashMap<>()));
        } else if (response.getResult() instanceof Map) {
            // Handle case where it's already a map (deserialization behavior can vary)
            Map<?, ?> map = (Map<?, ?>) response.getResult();
            Object steps = map.get("steps");
            if (steps instanceof List) {
                List<?> stepList = (List<?>) steps;
                Map<String, Object> results = new java.util.HashMap<>();
                for (Object stepObj : stepList) {
                    if (stepObj instanceof Map) {
                        Map<?, ?> stepMap = (Map<?, ?>) stepObj;
                        Object stepNum = stepMap.get("step");
                        Object stepResult = stepMap.get("result");
                        if (stepNum != null) {
                            results.put(String.valueOf(stepNum),
                                    stepResult != null ? stepResult : new java.util.HashMap<>());
                        }
                    }
                }
                return results;
            }
        }

        return new java.util.HashMap<>();
    }

    @Value("${app.redis.listener.enabled:true}")
    private boolean redisEnabled;

    @Scheduled(fixedDelay = 5000) // Poll every 5 seconds
    public void processQueue() {
        if (!redisEnabled) {
            return;
        }

        cacheService.leftPop(QUEUE_KEY)
                .flatMap(message -> {
                    try {
                        AsyncStepTask task = objectMapper.readValue(message, AsyncStepTask.class);
                        log.info("📥 [QUEUE] Found task: workflow={}, execution={}, step={}",
                                task.getWorkflowId(), task.getExecutionId(), task.getStepId());
                        return processAsyncStep(task);
                    } catch (Exception e) {
                        log.error("❌ [QUEUE] Failed to deserialize or process task: {}", e.getMessage(), e);
                        return Mono.error(e);
                    }
                })
                .subscribe(
                        v -> {},
                        error -> log.error("❌ [QUEUE] Error in queue subscription: {}", error.getMessage(), error));
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Getter
    private static class AsyncStepTask {
        @JsonProperty("workflowId")
        private final String workflowId;

        @JsonProperty("executionId")
        private final String executionId;

        @JsonProperty("stepId")
        private final String stepId;

        @JsonProperty("step")
        private final LinqResponse.WorkflowStep step;

        @JsonProperty("params")
        private final Map<String, Object> params;

        @JsonProperty("action")
        private final String action;

        @JsonProperty("intent")
        private final String intent;

        @JsonProperty("payload")
        private final Object payload;

        @JsonProperty("teamId")
        private final String teamId;

        @JsonCreator
        public AsyncStepTask(
                @JsonProperty("workflowId") String workflowId,
                @JsonProperty("executionId") String executionId,
                @JsonProperty("stepId") String stepId,
                @JsonProperty("step") LinqResponse.WorkflowStep step,
                @JsonProperty("params") Map<String, Object> params,
                @JsonProperty("action") String action,
                @JsonProperty("intent") String intent,
                @JsonProperty("payload") Object payload,
                @JsonProperty("teamId") String teamId) {
            this.workflowId = workflowId;
            this.executionId = executionId;
            this.stepId = stepId;
            this.step = step;
            this.params = params;
            this.action = action;
            this.intent = intent;
            this.payload = payload;
            this.teamId = teamId;
        }
    }
}