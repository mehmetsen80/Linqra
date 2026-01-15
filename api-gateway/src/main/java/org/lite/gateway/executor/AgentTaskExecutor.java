package org.lite.gateway.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.service.LinqWorkflowService;
import org.lite.gateway.service.ExecutionMonitoringService;
import org.lite.gateway.dto.ExecutionProgressUpdate;

import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Map;

/**
 * Abstract base class for task executors
 * Each task type should have its own executor implementation
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AgentTaskExecutor {

    protected final LinqWorkflowService linqWorkflowService;
    protected final LinqWorkflowExecutionService workflowExecutionService;
    protected final AgentRepository agentRepository;
    protected final AgentTaskRepository agentTaskRepository;
    protected final AgentExecutionRepository agentExecutionRepository;
    protected final ObjectMapper objectMapper;
    protected final ExecutionMonitoringService executionMonitoringService;

    /**
     * Execute a task
     * 
     * @param execution The agent execution context
     * @param task      The task to execute
     * @param agent     The agent executing the task
     * @return A Mono that completes when the task execution is finished
     */
    public abstract Mono<Void> executeTask(AgentExecution execution, AgentTask task, Agent agent);

    /**
     * Get timeout duration based on task type
     */
    protected Duration getTimeoutForTaskType(AgentTask task) {
        int baseTimeoutMinutes = task.getTimeoutMinutes();

        return switch (task.getTaskType()) {
            case WORKFLOW_EMBEDDED, WORKFLOW_TRIGGER, WORKFLOW_EMBEDDED_ADHOC ->
                Duration.ofMinutes(baseTimeoutMinutes);
            /*
             * // Future timeout adjustments for other task types (commented out for now)
             * case LLM_ANALYSIS -> Duration.ofMinutes((long) (baseTimeoutMinutes * 1.5));
             * case VECTOR_OPERATIONS -> Duration.ofMinutes((long) (baseTimeoutMinutes *
             * 1.3));
             * case DATA_PROCESSING -> Duration.ofMinutes((long) (baseTimeoutMinutes *
             * 1.2));
             * case CUSTOM_SCRIPT -> Duration.ofMinutes((long) baseTimeoutMinutes * 2);
             * case API_CALL -> Duration.ofMinutes((long) (baseTimeoutMinutes * 0.8));
             * case NOTIFICATION -> Duration.ofMinutes((long) (baseTimeoutMinutes * 0.5));
             * case DATA_SYNC, MONITORING, REPORTING ->
             * Duration.ofMinutes(baseTimeoutMinutes);
             */
        };
    }

    /**
     * Update execution status
     */
    protected Mono<Void> updateExecutionStatus(AgentExecution execution, ExecutionStatus status, String message,
            String workflowExecutionId) {
        // Use the proper methods to set both status and result correctly
        switch (status) {
            case COMPLETED -> execution.markAsCompleted();
            case FAILED -> execution.markAsFailed(message, "WORKFLOW_EXECUTION_FAILED");
            case TIMEOUT -> execution.markAsTimeout();
            default -> {
                // For other statuses, set manually
                execution.setStatus(status);
                execution.setErrorMessage(message);
            }
        }

        if (workflowExecutionId != null) {
            execution.setWorkflowExecutionId(workflowExecutionId);
        }

        return agentExecutionRepository.save(execution).then();
    }

    /**
     * Prepare workflow parameters from task and agent context
     */
    protected Map<String, Object> prepareWorkflowParameters(AgentExecution execution, AgentTask task, Agent agent) {
        Map<String, Object> mergedParams = new java.util.HashMap<>();
        Map<String, Object> linqConfig = task.getLinqConfig();
        if (linqConfig != null && linqConfig.containsKey("query")) {
            Object queryObj = linqConfig.get("query");
            if (queryObj instanceof Map) {
                Map<?, ?> queryMap = (Map<?, ?>) queryObj;
                Object paramsObj = queryMap.get("params");
                if (paramsObj instanceof Map) {
                    Map<?, ?> paramsMap = (Map<?, ?>) paramsObj;
                    paramsMap.forEach((key, value) -> {
                        if (key != null) {
                            mergedParams.put(String.valueOf(key), value);
                        }
                    });
                }
            }
        }
        if (execution.getInputData() != null && !execution.getInputData().isEmpty()) {
            mergedParams.putAll(execution.getInputData());
        }
        return mergedParams;
    }

    /**
     * Extract workflow ID from task configuration
     */
    protected String extractWorkflowId(AgentTask task) {
        Map<String, Object> linqConfig = task.getLinqConfig();
        if (linqConfig != null && linqConfig.containsKey("query")) {
            Object queryObj = linqConfig.get("query");
            if (queryObj instanceof Map) {
                Map<?, ?> queryMap = (Map<?, ?>) queryObj;
                Object workflowId = queryMap.get("workflowId");
                return workflowId != null ? workflowId.toString() : null;
            }
        }
        return null;
    }

    /**
     * Convert a Map to LinqRequest object using ObjectMapper
     */
    protected LinqRequest convertMapToLinqRequest(Map<String, Object> map) {
        try {
            return objectMapper.convertValue(map, LinqRequest.class);
        } catch (Exception e) {
            log.error("Failed to convert map to LinqRequest: {}", e.getMessage());
            throw new RuntimeException("Invalid LinqRequest structure in linq_config", e);
        }
    }

    /**
     * Monitor workflow completion and update AgentExecution status accordingly
     */
    protected Mono<Void> monitorWorkflowCompletion(String workflowExecutionId, AgentExecution execution) {
        log.info("Starting to monitor workflow completion for execution: {} (workflow: {})",
                execution.getExecutionId(), workflowExecutionId);

        return workflowExecutionService.getExecution(workflowExecutionId)
                .flatMap(workflowExecution -> {
                    org.lite.gateway.model.ExecutionStatus status = workflowExecution.getStatus();
                    log.info("Workflow {} status: {} for AgentExecution: {}", workflowExecutionId, status,
                            execution.getExecutionId());

                    if (org.lite.gateway.model.ExecutionStatus.SUCCESS.equals(status)) {
                        log.info(
                                "Workflow {} completed successfully, marking AgentExecution {} as COMPLETED with SUCCESS result",
                                workflowExecutionId, execution.getExecutionId());

                        // Extract results from workflow execution
                        Map<String, Object> outputData = new java.util.HashMap<>();
                        if (workflowExecution.getResponse() != null) {
                            outputData.put("workflowResponse", workflowExecution.getResponse());
                            if (workflowExecution.getResponse().getResult() != null) {
                                outputData.put("fullResult", workflowExecution.getResponse().getResult());

                                // Extract final result if available (common pattern in workflows)
                                Object resultObj = workflowExecution.getResponse().getResult();
                                if (resultObj instanceof Map) {
                                    Map<?, ?> resultMap = (Map<?, ?>) resultObj;
                                    if (resultMap.containsKey("finalResult")) {
                                        outputData.put("finalResult", resultMap.get("finalResult"));
                                    }
                                } else if (resultObj instanceof LinqResponse.WorkflowResult) {
                                    LinqResponse.WorkflowResult workflowResult = (LinqResponse.WorkflowResult) resultObj;
                                    if (workflowResult.getFinalResult() != null) {
                                        outputData.put("finalResult", workflowResult.getFinalResult());
                                    }
                                } else {
                                    try {
                                        @SuppressWarnings("unchecked")
                                        Map<String, Object> resultMap = objectMapper.convertValue(resultObj, Map.class);
                                        if (resultMap.containsKey("finalResult")) {
                                            outputData.put("finalResult", resultMap.get("finalResult"));
                                        }
                                    } catch (Exception e) {
                                        // Ignore, resultObj might be primitive or incompatible
                                    }
                                }
                            }
                        }
                        execution.setOutputData(outputData);

                        return updateExecutionStatus(execution, ExecutionStatus.COMPLETED,
                                "Agent task completed successfully", workflowExecutionId)
                                .then(sendCompletionUpdate(execution, workflowExecutionId));
                    } else if (org.lite.gateway.model.ExecutionStatus.FAILED.equals(status)) {
                        log.error("Workflow {} failed, marking AgentExecution {} as FAILED", workflowExecutionId,
                                execution.getExecutionId());
                        String errorMsg = "Workflow execution failed";
                        // Try to get error from metadata status
                        if (workflowExecution.getResponse() != null &&
                                workflowExecution.getResponse().getMetadata() != null &&
                                "error".equals(workflowExecution.getResponse().getMetadata().getStatus())) {
                            errorMsg = "Workflow failed - check execution logs";
                        }
                        return updateExecutionStatus(execution, ExecutionStatus.FAILED,
                                errorMsg, workflowExecutionId)
                                .then(sendFailedUpdate(execution, errorMsg, workflowExecutionId));
                    } else if (org.lite.gateway.model.ExecutionStatus.IN_PROGRESS.equals(status)) {
                        // Still running, wait and check again
                        log.info("Workflow {} still in progress, will check again in 5 seconds", workflowExecutionId);
                        return Mono.delay(Duration.ofSeconds(5))
                                .then(monitorWorkflowCompletion(workflowExecutionId, execution));
                    } else {
                        log.error("Unknown workflow status: {} for workflow: {}. Status type: {}. Marking as FAILED.",
                                status, workflowExecutionId, status != null ? status.getClass().getName() : "null");
                        // If we can't determine status, mark as failed for safety
                        return updateExecutionStatus(execution, ExecutionStatus.FAILED,
                                "Workflow monitoring failed: unknown status " + status, workflowExecutionId);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Error monitoring workflow completion for {}: {}", workflowExecutionId,
                            error.getMessage());
                    return updateExecutionStatus(execution, ExecutionStatus.FAILED,
                            "Error monitoring workflow: " + error.getMessage(), workflowExecutionId);
                });
    }

    protected Mono<Void> sendCompletionUpdate(AgentExecution execution, String workflowExecutionId) {
        if (executionMonitoringService == null)
            return Mono.empty();

        return workflowExecutionService.getExecution(workflowExecutionId)
                .flatMap(workflow -> {
                    int totalSteps = 0;
                    if (workflow.getRequest() != null && workflow.getRequest().getQuery() != null
                            && workflow.getRequest().getQuery().getWorkflow() != null) {
                        totalSteps = workflow.getRequest().getQuery().getWorkflow().size();
                    }

                    ExecutionProgressUpdate update = ExecutionProgressUpdate.builder()
                            .executionId(execution.getExecutionId())
                            .agentId(execution.getAgentId())
                            .agentName(execution.getAgentName())
                            .taskId(execution.getTaskId())
                            .taskName(execution.getTaskName())
                            .status("COMPLETED")
                            .currentStep(totalSteps)
                            .totalSteps(totalSteps)
                            .executionDurationMs(execution.getExecutionDurationMs())
                            .lastUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
                            .build();

                    return executionMonitoringService.sendExecutionCompleted(update);
                })
                .onErrorResume(e -> {
                    log.error("Failed to send completion update: {}", e.getMessage());
                    return Mono.empty();
                });
    }

    protected Mono<Void> sendFailedUpdate(AgentExecution execution, String errorMessage, String workflowExecutionId) {
        if (executionMonitoringService == null)
            return Mono.empty();

        Mono<Integer> totalStepsMono = Mono.just(0);
        if (workflowExecutionId != null) {
            totalStepsMono = workflowExecutionService.getExecution(workflowExecutionId)
                    .map(workflow -> {
                        if (workflow.getRequest() != null && workflow.getRequest().getQuery() != null
                                && workflow.getRequest().getQuery().getWorkflow() != null) {
                            return workflow.getRequest().getQuery().getWorkflow().size();
                        }
                        return 0;
                    })
                    .onErrorReturn(0);
        }

        return totalStepsMono.flatMap(totalSteps -> {
            ExecutionProgressUpdate update = ExecutionProgressUpdate.builder()
                    .executionId(execution.getExecutionId())
                    .agentId(execution.getAgentId())
                    .agentName(execution.getAgentName())
                    .taskId(execution.getTaskId())
                    .taskName(execution.getTaskName())
                    .status("FAILED")
                    .currentStep(0) // Reset or keep? Usually stuck at last step if failed
                    .totalSteps(totalSteps)
                    .executionDurationMs(execution.getExecutionDurationMs())
                    .lastUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
                    .build();

            return executionMonitoringService.sendExecutionFailed(update, errorMessage);
        });
    }

}
