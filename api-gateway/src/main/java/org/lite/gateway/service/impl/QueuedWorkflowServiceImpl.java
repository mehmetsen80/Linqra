package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.LinqResponse.QueuedWorkflowStep;
import org.lite.gateway.service.QueuedWorkflowService;
import org.lite.gateway.service.LinqToolService;
import org.lite.gateway.service.LinqMicroService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.repository.LinqToolRepository;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.springframework.stereotype.Service;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import reactor.core.publisher.Mono;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.domain.Sort;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import java.time.Duration;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueuedWorkflowServiceImpl implements QueuedWorkflowService {
    private static final String QUEUE_KEY = "async:step:queue";
    
    private final ReactiveRedisTemplate<String, String> asyncStepQueueRedisTemplate;
    private final ReactiveRedisTemplate<String, QueuedWorkflowStep> asyncStepStatusRedisTemplate;
    private final ObjectMapper objectMapper;
    private final LinqToolRepository linqToolRepository;
    private final LinqToolService linqToolService;
    private final LinqMicroService linqMicroService;
    private final TeamContextService teamContextService;
    private final LinqWorkflowExecutionRepository executionRepository;

    @Override
    public Mono<Void> queueAsyncStep(String workflowId, int stepNumber, LinqResponse.WorkflowStep step) {
        return teamContextService.getTeamFromContext()
                .flatMap(teamId -> {
                    try {
                        // Generate a unique execution ID for this step
                        String executionId = UUID.randomUUID().toString();
                        
                        // Set the execution ID in the step object
                        step.setExecutionId(executionId);
                        
                        // Create the step execution with the unique ID
                        QueuedWorkflowStep stepExecution = new QueuedWorkflowStep();
                        stepExecution.setWorkflowId(workflowId);  // Set the workflowId
                        stepExecution.setStepId(String.valueOf(stepNumber)); // Use step number as stepId
                        stepExecution.setStatus("queued");
                        stepExecution.setQueuedAt(LocalDateTime.now());
                        stepExecution.setExecutionId(executionId);  // Add the execution ID
                        
                        // Use workflowId:stepNumber:executionId as the Redis key
                        String redisKey = String.format("%s:%d:%s", workflowId, stepNumber, executionId);
                        
                        // Create the task to be queued
                        AsyncStepTask task = new AsyncStepTask(
                            workflowId,
                            String.valueOf(stepNumber), // Use step number as stepId
                            step,
                            step.getParams(),
                            step.getAction(),
                            step.getIntent(),
                            teamId
                        );
                        
                        // First store the status
                        return asyncStepStatusRedisTemplate.opsForValue()
                                .set(redisKey, stepExecution, Duration.ofHours(24))
                                .doOnSuccess(v -> log.info("Queued async step for workflow {} step {} with execution ID {}", 
                                    workflowId, stepNumber, executionId))
                                .doOnError(e -> log.error("Failed to queue async step for workflow {} step {}: {}", 
                                    workflowId, stepNumber, e.getMessage(), e))
                                // Then add the task to the queue
                                .then(asyncStepQueueRedisTemplate.opsForList()
                                    .rightPush(QUEUE_KEY, objectMapper.writeValueAsString(task))
                                    .doOnSuccess(v -> log.info("Added task to queue for workflow {} step {} with execution ID {}", 
                                        workflowId, stepNumber, executionId))
                                    .doOnError(e -> log.error("Failed to add task to queue for workflow {} step {}: {}", 
                                        workflowId, stepNumber, e.getMessage(), e)))
                                .then();
                    } catch (Exception e) {
                        log.error("Failed to queue async step: {}", e.getMessage(), e);
                        return Mono.error(e);
                    }
                });
    }

    @Override
    public Mono<QueuedWorkflowStep> getAsyncStepStatus(String workflowId, int stepNumber) {
        return teamContextService.getTeamFromContext()
                .flatMap(teamId -> {
                    // Get all keys matching the pattern workflowId:stepNumber:*
                    String pattern = String.format("%s:%d:*", workflowId, stepNumber);
                    return asyncStepStatusRedisTemplate.keys(pattern)
                            .collectList()
                            .flatMap(keys -> {
                                if (keys.isEmpty()) {
                                    return Mono.empty();
                                }
                                // Get the most recent execution (last key in the list)
                                String latestKey = keys.get(keys.size() - 1);
                                return asyncStepStatusRedisTemplate.opsForValue().get(latestKey);
                            });
                });
    }

    @Override
    public Mono<Void> cancelAsyncStep(String workflowId, int stepNumber) {
        return teamContextService.getTeamFromContext()
                .flatMap(teamId -> {
                    // Get all keys matching the pattern workflowId:stepNumber:*
                    String pattern = String.format("%s:%d:*", workflowId, stepNumber);
                    return asyncStepStatusRedisTemplate.keys(pattern)
                            .collectList()
                            .flatMap(keys -> {
                                if (keys.isEmpty()) {
                                    return Mono.error(new RuntimeException("No queued step found for workflow " + workflowId + " step " + stepNumber));
                                }
                                // Get the most recent execution (last key in the list)
                                String latestKey = keys.get(keys.size() - 1);
                                return asyncStepStatusRedisTemplate.opsForValue().get(latestKey)
                                        .flatMap(stepExecution -> {
                                            stepExecution.setStatus("cancelled");
                                            stepExecution.setCancelledAt(LocalDateTime.now());
                                            return asyncStepStatusRedisTemplate.opsForValue()
                                                    .set(latestKey, stepExecution, Duration.ofHours(24))
                                                    .then();
                                        });
                            });
                });
    }

    @Override
    public Mono<Map<String, QueuedWorkflowStep>> getAllAsyncSteps(String workflowId) {
        return teamContextService.getTeamFromContext()
                .flatMap(teamId -> {
                    String pattern = String.format("%s:*", workflowId);
                    return asyncStepStatusRedisTemplate.keys(pattern)
                            .collectList()
                            .flatMap(keys -> {
                                if (keys.isEmpty()) {
                                    return Mono.empty();
                                }
                                return asyncStepStatusRedisTemplate.opsForValue().multiGet(keys)
                                        .map(steps -> steps.stream()
                                            .collect(Collectors.toMap(
                                                QueuedWorkflowStep::getStepId,
                                                step -> step
                                            ))
                                        );
                            });
                });
    }

    private String getStatusKey(String workflowId, String stepId, String executionId) {
        return String.format("%s:%s:%s", workflowId, stepId, executionId);
    }

    protected Mono<Void> processAsyncStep(AsyncStepTask task) {
        // Get the execution ID from the step object
        String executionId = task.getStep().getExecutionId();
        if (executionId == null) {
            log.error("No execution ID found in step for workflow {} step {}", task.getWorkflowId(), task.getStepId());
            return Mono.error(new RuntimeException("No execution ID found in step"));
        }
        
        String statusKey = getStatusKey(task.getWorkflowId(), task.getStepId(), executionId);
        log.info("Starting async step processing for workflow {} step {} with team {} and execution ID {}", 
            task.getWorkflowId(), task.getStepId(), task.getTeamId(), executionId);
        
        // First check if the step is already being processed or completed
        return asyncStepStatusRedisTemplate.opsForValue().get(statusKey)
            .doOnNext(status -> log.info("Current status for workflow {} step {}: {}", 
                task.getWorkflowId(), task.getStepId(), status.getStatus()))
            .filter(status -> !status.getStatus().equals("processing") && !status.getStatus().equals("completed"))
            .doOnNext(status -> log.info("Step is not processing/completed, proceeding with execution"))
            .flatMap(status -> updateStatusToProcessing(statusKey, status))
            .doOnNext(status -> log.info("Updated status to processing"))
            .flatMap(status -> {
                log.info("Executing step with team {} for workflow {} step {}", 
                    task.getTeamId(), task.getWorkflowId(), task.getStepId());
                return executeStepWithTeam(task.getStep(), task.getParams(), task.getAction(), task.getIntent(), task.getTeamId());
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
                return updateStatusToCompleted(statusKey, response.getResult(), task.getWorkflowId(), task.getStepId());
            })
            .doOnNext(v -> log.info("Updated status to completed"))
            .onErrorResume(error -> {
                log.error("Error in async step processing: {}", error.getMessage(), error);
                return updateStatusToFailed(statusKey, error.getMessage(), task.getWorkflowId(), task.getStepId())
                    .then(Mono.error(error)); // Re-throw the error to ensure it's propagated
            })
            .then();
    }

    private Mono<QueuedWorkflowStep> updateStatusToProcessing(String statusKey, QueuedWorkflowStep status) {
        status.setStatus("processing");
        return asyncStepStatusRedisTemplate.opsForValue()
            .set(statusKey, status, Duration.ofHours(24))
            .thenReturn(status);
    }

    private Mono<LinqResponse> executeStepWithTeam(LinqResponse.WorkflowStep step, Map<String, Object> params, String action, String intent, String teamId) {
        LinqRequest stepRequest = createStepRequest(step, params, action, intent);
        
        return Mono.just(teamId)
            .flatMap(id -> {
                log.info("Searching for tool with target: {} and team: {}", step.getTarget(), id);
                return linqToolRepository.findByTargetAndTeamId(step.getTarget(), id)
                    .doOnNext(tool -> log.info("Found tool: {}", tool))
                    .doOnError(error -> log.error("Error finding tool: {}", error.getMessage()))
                    .doOnSuccess(tool -> {
                        if (tool == null) {
                            log.info("No tool found for target: {}", step.getTarget());
                        }
                    });
            })
            .doOnNext(tool -> log.info("About to execute tool request"))
            .flatMap(tool -> linqToolService.executeToolRequest(stepRequest, tool))
            .doOnNext(stepResponse -> log.info("Tool request executed successfully"))
            .switchIfEmpty(Mono.<LinqResponse>defer(() -> {
                log.info("No tool found, executing microservice request");
                return linqMicroService.execute(stepRequest);
            }));
    }

    private LinqRequest createStepRequest(LinqResponse.WorkflowStep step, Map<String, Object> params, String action, String intent) {
        LinqRequest stepRequest = new LinqRequest();
        LinqRequest.Link stepLink = new LinqRequest.Link();
        stepLink.setTarget(step.getTarget());
        stepLink.setAction(action);
        stepRequest.setLink(stepLink);
        
        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent(intent);
        query.setParams(params);
        stepRequest.setQuery(query);
        
        return stepRequest;
    }

    private Mono<Void> updateStatusToCompleted(String statusKey, Object result, String workflowId, String stepId) {
        return asyncStepStatusRedisTemplate.opsForValue().get(statusKey)
            .flatMap(status -> {
                status.setStatus("completed");
                status.setCompletedAt(LocalDateTime.now());
                status.setResult(result);
                return asyncStepStatusRedisTemplate.opsForValue()
                    .set(statusKey, status, Duration.ofHours(24))
                    .then();
            })
            .then(updateWorkflowExecution(workflowId, stepId, "completed", result));
    }

    private Mono<Void> updateStatusToFailed(String statusKey, String errorMessage, String workflowId, String stepId) {
        return asyncStepStatusRedisTemplate.opsForValue().get(statusKey)
            .flatMap(status -> {
                status.setStatus("failed");
                status.setError(errorMessage);
                status.setCompletedAt(LocalDateTime.now());
                return asyncStepStatusRedisTemplate.opsForValue()
                    .set(statusKey, status, Duration.ofHours(24))
                    .then();
            })
            .then(updateWorkflowExecution(workflowId, stepId, "failed", null));
    }

    private Mono<Void> updateWorkflowExecution(String workflowId, String stepId, String status, Object result) {
        return executionRepository.findByWorkflowId(workflowId, Sort.by(Sort.Direction.DESC, "executedAt"))
            .next()
            .flatMap(execution -> {
                // Update the step result and status in the workflow execution
                if (execution.getResponse() != null && 
                    execution.getResponse().getResult() instanceof LinqResponse.WorkflowResult workflowResult) {
                    
                    // Update step result
                    workflowResult.getSteps().stream()
                        .filter(step -> String.valueOf(step.getStep()).equals(stepId))
                        .findFirst()
                        .ifPresent(step -> {
                            step.setResult(result);
                        });
                    
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
                                    long startTime = meta.getExecutedAt().toInstant(ZoneOffset.UTC).toEpochMilli();
                                    long endTime = System.currentTimeMillis();
                                    meta.setDurationMs(endTime - startTime);
                                }
                            });
                    }

                    // Update overall workflow status if step failed
                    if (status.equals("failed")) {
                        execution.getResponse().getMetadata().setStatus("error");
                    }
                }
                
                return executionRepository.save(execution);
            })
            .then();
    }

    @Scheduled(fixedDelay = 5000) // Poll every 5 seconds
    public void processQueue() {
        log.info("Checking async step queue...");
        asyncStepQueueRedisTemplate.opsForList().leftPop(QUEUE_KEY)
            .doOnSubscribe(s -> log.info("Subscribing to queue processing..."))
            .doOnNext(message -> log.info("Found message in queue: {}", message))
            .flatMap(message -> {
                try {
                    AsyncStepTask task = objectMapper.readValue(message, AsyncStepTask.class);
                    log.info("Processing async step for workflow {} step {} with team {} and execution ID {}", 
                        task.getWorkflowId(), task.getStepId(), task.getTeamId(), task.getStep().getExecutionId());
                    return processAsyncStep(task);
                } catch (Exception e) {
                    log.error("Failed to process queued task: {}", e.getMessage(), e);
                    return Mono.error(e);
                }
            })
            .doOnError(error -> log.error("Error processing queue: {}", error.getMessage(), error))
            .subscribe(
                null,
                error -> log.error("Error in queue subscription: {}", error.getMessage(), error)
            );
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    @Getter
    private static class AsyncStepTask {
        @JsonProperty("workflowId")
        private final String workflowId;
        
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

        @JsonProperty("teamId")
        private final String teamId;

        @JsonCreator
        public AsyncStepTask(
            @JsonProperty("workflowId") String workflowId,
            @JsonProperty("stepId") String stepId,
            @JsonProperty("step") LinqResponse.WorkflowStep step,
            @JsonProperty("params") Map<String, Object> params,
            @JsonProperty("action") String action,
            @JsonProperty("intent") String intent,
            @JsonProperty("teamId") String teamId) {
            this.workflowId = workflowId;
            this.stepId = stepId;
            this.step = step;
            this.params = params;
            this.action = action;
            this.intent = intent;
            this.teamId = teamId;
        }
    }
} 