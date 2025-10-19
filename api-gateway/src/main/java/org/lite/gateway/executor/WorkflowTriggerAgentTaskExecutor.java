package org.lite.gateway.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.service.LinqWorkflowService;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.lite.gateway.exception.ResourceNotFoundException;
import org.lite.gateway.dto.ErrorCode;

/**
 * Task executor for WORKFLOW_TRIGGER tasks
 * Triggers workflows by ID (reference-based workflows)
 */
@Service
@Slf4j
public class WorkflowTriggerAgentTaskExecutor extends AgentTaskExecutor {

    public WorkflowTriggerAgentTaskExecutor(LinqWorkflowService linqWorkflowService,
                                            LinqWorkflowExecutionService workflowExecutionService,
                                            AgentRepository agentRepository,
                                            AgentTaskRepository agentTaskRepository,
                                            AgentExecutionRepository agentExecutionRepository,
                                            ObjectMapper objectMapper) {
        super(linqWorkflowService, workflowExecutionService, agentRepository,
                agentTaskRepository, agentExecutionRepository, objectMapper);
    }

    @Override
    public Mono<Void> executeTask(AgentExecution execution, AgentTask task, Agent agent) {
        log.info("Executing WORKFLOW_TRIGGER task: {} (execution: {})", task.getName(), execution.getExecutionId());
        
        try {
            // Extract workflow ID from task configuration
            String workflowId = extractWorkflowId(task);
            if (workflowId == null || workflowId.isBlank()) {
                log.warn("No workflow ID found in task configuration for task: {}", task.getName());
                return updateExecutionStatus(execution, ExecutionStatus.FAILED, "No workflow ID configured", null);
            }

            // Ensure workflow exists before proceeding
            return linqWorkflowService.getWorkflow(workflowId)
                    .switchIfEmpty(Mono.defer(() -> Mono.error(new ResourceNotFoundException("Workflow not found: " + workflowId, ErrorCode.WORKFLOW_NOT_FOUND))))
                    .flatMap(existing -> {
                        // Prepare workflow parameters
                        Map<String, Object> parameters = prepareWorkflowParameters(execution, task, agent);
                        
                        // Get timeout for task type
                        Duration timeout = getTimeoutForTaskType(task);
                        log.info("Triggering workflow {} with timeout of {} minutes (adjusted for type {}) and max retries of {}", 
                                workflowId, timeout.toMinutes(), task.getTaskType(), task.getMaxRetries());
                        
                        return triggerWorkflow(workflowId, parameters, execution.getTeamId(),
                                agent.getId(), task.getId(), execution)
                                .timeout(timeout)
                                .retryWhen(Retry.backoff(task.getMaxRetries(), Duration.ofSeconds(2))
                                        .maxBackoff(Duration.ofMinutes(1))
                                        .doBeforeRetry(retrySignal -> {
                                            log.warn("Retrying workflow execution for task {} (attempt {}/{}): {}", 
                                                task.getName(), retrySignal.totalRetries() + 1, task.getMaxRetries(), 
                                                retrySignal.failure().getMessage());
                                            execution.addRetryAttempt();
                                        }))
                                .flatMap(workflowExecution -> {
                                    log.info("Workflow triggered and saved, checking status: {} for execution: {}", 
                                            workflowExecution.getId(), execution.getExecutionId());
                                    
                                    // No need for delay or retry - we have the actual saved execution object
                                    // If workflow already failed, mark AgentExecution as failed immediately
                                    if (org.lite.gateway.model.ExecutionStatus.FAILED.equals(workflowExecution.getStatus())) {
                                        log.error("Workflow {} already in FAILED status, marking AgentExecution as FAILED", 
                                                workflowExecution.getId());
                                        return updateExecutionStatus(execution, ExecutionStatus.FAILED, 
                                                "Workflow execution failed", workflowExecution.getId());
                                    }
                                    
                                    // Otherwise, set to RUNNING and monitor
                                    log.info("Workflow {} status is {}, setting AgentExecution to RUNNING and starting monitoring", 
                                            workflowExecution.getId(), workflowExecution.getStatus());
                                    return updateExecutionStatus(execution, ExecutionStatus.RUNNING, "Workflow started", 
                                            workflowExecution.getId())
                                            .then(monitorWorkflowCompletion(workflowExecution.getId(), execution));
                                })
                                .doOnError(error -> {
                                    String msg = error.getMessage() != null ? error.getMessage() : "Workflow execution error";
                                    execution.markAsFailed(msg, "WORKFLOW_EXECUTION_FAILED");
                                })
                                .onErrorResume(error -> {
                                    if (error instanceof java.util.concurrent.TimeoutException) {
                                        log.error("Workflow execution timed out after {} minutes for task: {}", timeout.toMinutes(), task.getName());
                                        execution.markAsTimeout();
                                        return agentExecutionRepository.save(execution).then(Mono.error(error));
                                    } else {
                                        log.error("Failed to trigger workflow for task: {} after {} retries", task.getName(), task.getMaxRetries(), error);
                                        return updateExecutionStatus(execution, ExecutionStatus.FAILED, 
                                            "Workflow trigger failed after " + task.getMaxRetries() + " retries: " + error.getMessage(), null);
                                    }
                                });
                    });
                    
        } catch (Exception e) {
            log.error("Error executing workflow for task: {}", task.getName(), e);
            return updateExecutionStatus(execution, ExecutionStatus.FAILED, "Workflow execution error: " + e.getMessage(), null);
        }
    }

    /**
     * Trigger a workflow by its ID with agent context
     */
    public Mono<LinqWorkflowExecution> triggerWorkflow(String workflowId, Map<String, Object> parameters, String teamId,
                                        String agentId, String agentTaskId, AgentExecution execution) {
        log.info("Triggering workflow {} for team {} with agent context: agent={}, task={}", workflowId, teamId, agentId, agentTaskId);

        return linqWorkflowService.getWorkflow(workflowId)
                .flatMap(workflow -> {
                    // Use the workflow's request directly
                    LinqRequest request = workflow.getRequest();

                    // Ensure it's a workflow request
                    if (!request.getLink().getTarget().equals("workflow")) {
                        return Mono.error(new RuntimeException("Invalid workflow request: target must be 'workflow'"));
                    }

                    // Set the workflowId in the request
                    if (request.getQuery() == null) {
                        request.setQuery(new LinqRequest.Query());
                    }
                    request.getQuery().setWorkflowId(workflowId);

                    // Store the workflow steps for each individual request
                    List<LinqRequest.Query.WorkflowStep> workflowSteps = request.getQuery().getWorkflow();

                    // Fix the max.tokens issue in the workflow steps
                    if (workflowSteps != null) {
                        workflowSteps.forEach(step -> {
                            if (step.getLlmConfig() != null && step.getLlmConfig().getSettings() != null) {
                                Map<String, Object> settings = step.getLlmConfig().getSettings();
                                if (settings.containsKey("max.tokens")) {
                                    Object value = settings.remove("max.tokens");
                                    settings.put("max_tokens", value);
                                }
                            }
                        });
                    }

                    // Merge additional parameters if provided (copy-on-write to avoid side-effects)
                    if (!parameters.isEmpty()) {
                        if (request.getQuery() == null) {
                            request.setQuery(new LinqRequest.Query());
                        }
                        Map<String, Object> existingParams = request.getQuery().getParams();
                        java.util.Map<String, Object> mergedParams = new java.util.HashMap<>();
                        if (existingParams != null) {
                            mergedParams.putAll(existingParams);
                        }
                        mergedParams.putAll(parameters);
                        request.getQuery().setParams(mergedParams);
                    }

                    // Set executedBy from the actual user who initiated the agent task  
                    request.setExecutedBy(execution.getExecutedBy());

                    // Execute the workflow
                    return workflowExecutionService.executeWorkflow(request)
                            .flatMap(response ->
                                    // Get agent and task names by looking up both
                                    Mono.zip(
                                                    agentRepository.findById(agentId).map(agent -> agent.getName()),
                                                    agentTaskRepository.findById(agentTaskId).map(task -> task.getName())
                                            )
                                            .map(tuple -> {
                                                String agentName = tuple.getT1();
                                                String taskName = tuple.getT2();

                                                // Prepare agent context for tracking

                                                return Map.<String, Object>of(
                                                        "agentId", agentId,
                                                        "agentName", agentName,
                                                        "agentTaskId", agentTaskId,
                                                        "agentTaskName", taskName,
                                                        "executionSource", "agent",
                                                        "agentExecutionId", execution.getExecutionId()
                                                );
                                            })
                                            .flatMap(agentContext ->
                                                    workflowExecutionService.trackExecutionWithAgentContext(request, response, agentContext)
                                            )
                            );
                })
                .doOnSuccess(workflowExecution -> log.info("✅ Workflow {} triggered and tracked with execution ID: {} for agent: {}", workflowId, workflowExecution.getId(), agentId))
                .doOnError(error -> log.error("❌ Failed to trigger workflow {} for agent {}: {}", workflowId, agentId, error.getMessage()));
    }

}
