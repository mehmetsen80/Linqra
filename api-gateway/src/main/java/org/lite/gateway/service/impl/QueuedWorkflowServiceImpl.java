package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class QueuedWorkflowServiceImpl implements QueuedWorkflowService {
    private static final String QUEUE_KEY = "async:step:queue";
    
    private final ReactiveRedisTemplate<String, String> asyncStepQueueRedisTemplate;
    private final ReactiveRedisTemplate<String, LinqResponse.AsyncStepStatus> asyncStepStatusRedisTemplate;
    private final ObjectMapper objectMapper;
    private final LinqToolRepository linqToolRepository;
    private final LinqToolService linqToolService;
    private final LinqMicroService linqMicroService;
    private final TeamContextService teamContextService;
    private final LinqWorkflowExecutionRepository executionRepository;

    @Override
    public Mono<Void> queueAsyncStep(String workflowId, String stepId, LinqResponse.WorkflowStep step) {
        String statusKey = getStatusKey(workflowId, stepId);
        
        // Initialize status
        LinqResponse.AsyncStepStatus status = new LinqResponse.AsyncStepStatus();
        status.setStepId(stepId);
        status.setStatus("queued");
        status.setQueuedAt(LocalDateTime.now());
        
        // Get team context first
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> {
                try {
                    // Create task message with step details
                    AsyncStepTask task = new AsyncStepTask(
                        workflowId, 
                        stepId, 
                        step,
                        step.getParams(),
                        step.getAction(),
                        step.getIntent(),
                        teamId  // Add teamId to the task
                    );
                    String message = objectMapper.writeValueAsString(task);
                    
                    log.info("Queueing async step for workflow {} step {} with team {}: {}", workflowId, stepId, teamId, message);
                    
                    // Store status and queue task in Redis
                    return Mono.defer(() -> {
                        log.info("Starting Redis operations for workflow {} step {}", workflowId, stepId);
                        return asyncStepStatusRedisTemplate.opsForValue().set(statusKey, status)
                            .doOnSuccess(v -> log.info("Status stored in Redis for workflow {} step {}", workflowId, stepId))
                            .doOnError(e -> log.error("Failed to store status in Redis for workflow {} step {}: {}", workflowId, stepId, e.getMessage(), e))
                            .then(asyncStepQueueRedisTemplate.opsForList().rightPush(QUEUE_KEY, message))
                            .doOnSuccess(v -> log.info("Task queued in Redis for workflow {} step {}", workflowId, stepId))
                            .doOnError(e -> log.error("Failed to queue task in Redis for workflow {} step {}: {}", workflowId, stepId, e.getMessage(), e))
                            .then();
                    });
                } catch (Exception e) {
                    log.error("Failed to queue async step for workflow {} step {}: {}", workflowId, stepId, e.getMessage(), e);
                    return Mono.error(e);
                }
            });
    }

    protected Mono<Void> processAsyncStep(AsyncStepTask task) {
        String statusKey = getStatusKey(task.getWorkflowId(), task.getStepId());
        log.info("Starting async step processing for workflow {} step {} with team {}", task.getWorkflowId(), task.getStepId(), task.getTeamId());
        
        // First check if the step is already being processed or completed
        return asyncStepStatusRedisTemplate.opsForValue().get(statusKey)
            .doOnNext(status -> log.info("Current status for workflow {} step {}: {}", task.getWorkflowId(), task.getStepId(), status.getStatus()))
            .filter(status -> !status.getStatus().equals("processing") && !status.getStatus().equals("completed"))
            .doOnNext(status -> log.info("Step is not processing/completed, proceeding with execution"))
            .flatMap(status -> updateStatusToProcessing(statusKey, status))
            .doOnNext(status -> log.info("Updated status to processing"))
            .flatMap(status -> {
                log.info("Executing step with team {} for workflow {} step {}", task.getTeamId(), task.getWorkflowId(), task.getStepId());
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
                log.info("Updating status to completed for workflow {} step {}", task.getWorkflowId(), task.getStepId());
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

    private Mono<LinqResponse.AsyncStepStatus> updateStatusToProcessing(String statusKey, LinqResponse.AsyncStepStatus status) {
        status.setStatus("processing");
        return asyncStepStatusRedisTemplate.opsForValue().set(statusKey, status)
            .thenReturn(status);
    }

    private Mono<LinqResponse> executeStepWithTeam(LinqResponse.WorkflowStep step, Map<String, Object> params, String action, String intent, String teamId) {
        LinqRequest stepRequest = createStepRequest(step, params, action, intent);
        
        return Mono.just(teamId)
            .flatMap(id -> {
                log.info("Searching for tool with target: {} and team: {}", step.getTarget(), id);
                return linqToolRepository.findByTargetAndTeam(step.getTarget(), id)
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
                return asyncStepStatusRedisTemplate.opsForValue().set(statusKey, status)
                    .thenReturn(status);
            })
            .then(updateWorkflowExecution(workflowId, stepId, "completed", result));
    }

    private Mono<Void> updateStatusToFailed(String statusKey, String errorMessage, String workflowId, String stepId) {
        return asyncStepStatusRedisTemplate.opsForValue().get(statusKey)
            .flatMap(status -> {
                status.setStatus("failed");
                status.setError(errorMessage);
                return asyncStepStatusRedisTemplate.opsForValue().set(statusKey, status)
                    .thenReturn(status);
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
                            .filter(meta -> meta.getStep() == Integer.parseInt(stepId))
                            .findFirst()
                            .ifPresent(meta -> {
                                meta.setStatus(status);
                                if (status.equals("completed")) {
                                    meta.setDurationMs(System.currentTimeMillis() - meta.getExecutedAt().toInstant(ZoneOffset.UTC).toEpochMilli());
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

    @Override
    public Mono<LinqResponse.AsyncStepStatus> getStepStatus(String workflowId, String stepId) {
        return asyncStepStatusRedisTemplate.opsForValue().get(getStatusKey(workflowId, stepId));
    }

    @Override
    public Mono<Map<String, LinqResponse.AsyncStepStatus>> getAllAsyncSteps(String workflowId) {
        return asyncStepStatusRedisTemplate.keys(workflowId + ":*")
            .flatMap(key -> asyncStepStatusRedisTemplate.opsForValue().get(key)
                .map(status -> Map.entry(key, status)))
            .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    @Override
    public Mono<Void> cancelAsyncStep(String workflowId, String stepId) {
        String statusKey = getStatusKey(workflowId, stepId);
        return asyncStepStatusRedisTemplate.opsForValue().get(statusKey)
            .flatMap(status -> {
                if (!status.getStatus().equals("completed")) {
                    status.setStatus("cancelled");
                    status.setCancelledAt(LocalDateTime.now());
                    return asyncStepStatusRedisTemplate.opsForValue().set(statusKey, status);
                }
                return Mono.empty();
            })
            .then();
    }

    private String getStatusKey(String workflowId, String stepId) {
        return workflowId + ":" + stepId;
    }

    @Scheduled(fixedDelay = 1000) // Poll every second
    public void processQueue() {
        log.info("Checking async step queue...");
        asyncStepQueueRedisTemplate.opsForList().leftPop(QUEUE_KEY)
            .doOnSubscribe(s -> log.info("Subscribing to queue processing..."))
            .doOnNext(message -> log.info("Found message in queue: {}", message))
            .flatMap(message -> {
                try {
                    AsyncStepTask task = objectMapper.readValue(message, AsyncStepTask.class);
                    log.info("Processing async step for workflow {} step {} with team {}", task.getWorkflowId(), task.getStepId(), task.getTeamId());
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