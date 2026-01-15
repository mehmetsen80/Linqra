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
import org.lite.gateway.service.ExecutionMonitoringService;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.List;
import java.util.Map;

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
            ObjectMapper objectMapper,
            ExecutionMonitoringService executionMonitoringService) {
        super(linqWorkflowService, workflowExecutionService, agentRepository,
                agentTaskRepository, agentExecutionRepository, objectMapper, executionMonitoringService);
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

            // Ensure workflow exists before proceeding (bypass context for scheduler)
            return linqWorkflowService.getWorkflowByIdAndTeam(workflowId, execution.getTeamId())
                    .flatMap(existing -> {
                        // Prepare workflow parameters
                        Map<String, Object> parameters = prepareWorkflowParameters(execution, task, agent);

                        // Get timeout for task type
                        Duration timeout = getTimeoutForTaskType(task);
                        log.info(
                                "Triggering workflow {} with timeout of {} minutes (adjusted for type {}) and max retries of {}",
                                workflowId, timeout.toMinutes(), task.getTaskType(), task.getMaxRetries());

                        return triggerWorkflow(workflowId, parameters, execution.getTeamId(),
                                agent.getId(), task.getId(), execution)
                                .timeout(timeout)
                                .retryWhen(Retry.backoff(task.getMaxRetries(), Duration.ofSeconds(2))
                                        .maxBackoff(Duration.ofMinutes(1))
                                        .doBeforeRetry(retrySignal -> {
                                            log.warn("Retrying workflow execution for task {} (attempt {}/{}): {}",
                                                    task.getName(), retrySignal.totalRetries() + 1,
                                                    task.getMaxRetries(),
                                                    retrySignal.failure().getMessage());
                                            execution.addRetryAttempt();
                                        }))
                                .flatMap(workflowExecution -> {
                                    log.info("Workflow triggered and saved, checking status: {} for execution: {}",
                                            workflowExecution.getId(), execution.getExecutionId());

                                    // No need for delay or retry - we have the actual saved execution object
                                    // If workflow already failed, mark AgentExecution as failed immediately
                                    if (org.lite.gateway.model.ExecutionStatus.FAILED
                                            .equals(workflowExecution.getStatus())) {
                                        log.error(
                                                "Workflow {} already in FAILED status, marking AgentExecution as FAILED",
                                                workflowExecution.getId());
                                        return updateExecutionStatus(execution, ExecutionStatus.FAILED,
                                                "Workflow execution failed", workflowExecution.getId());
                                    }

                                    // Otherwise, set to RUNNING and monitor
                                    log.info(
                                            "Workflow {} status is {}, setting AgentExecution to RUNNING and starting monitoring",
                                            workflowExecution.getId(), workflowExecution.getStatus());
                                    return updateExecutionStatus(execution, ExecutionStatus.RUNNING, "Workflow started",
                                            workflowExecution.getId())
                                            .then(monitorWorkflowCompletion(workflowExecution.getId(), execution));
                                })
                                .doOnError(error -> {
                                    String msg = error.getMessage() != null ? error.getMessage()
                                            : "Workflow execution error";
                                    execution.markAsFailed(msg, "WORKFLOW_EXECUTION_FAILED");
                                })
                                .onErrorResume(error -> {
                                    if (error instanceof java.util.concurrent.TimeoutException) {
                                        log.error("Workflow execution timed out after {} minutes for task: {}",
                                                timeout.toMinutes(), task.getName());
                                        execution.markAsTimeout();
                                        return agentExecutionRepository.save(execution).then(Mono.error(error));
                                    } else {
                                        log.error("Failed to trigger workflow for task: {} after {} retries",
                                                task.getName(), task.getMaxRetries(), error);
                                        return updateExecutionStatus(execution, ExecutionStatus.FAILED,
                                                "Workflow trigger failed after " + task.getMaxRetries() + " retries: "
                                                        + error.getMessage(),
                                                null);
                                    }
                                });
                    });

        } catch (Exception e) {
            log.error("Error executing workflow for task: {}", task.getName(), e);
            return updateExecutionStatus(execution, ExecutionStatus.FAILED,
                    "Workflow execution error: " + e.getMessage(), null);
        }
    }

    /**
     * Trigger a workflow by its ID with agent context
     */
    public Mono<LinqWorkflowExecution> triggerWorkflow(String workflowId, Map<String, Object> parameters, String teamId,
            String agentId, String agentTaskId, AgentExecution execution) {
        log.info("Triggering workflow {} for team {} with agent context: agent={}, task={}", workflowId, teamId,
                agentId, agentTaskId);

        // Use service method that bypasses team context (scheduler has no HTTP context)
        return linqWorkflowService.getWorkflowByIdAndTeam(workflowId, teamId)
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

                    // Ensure params map exists
                    if (request.getQuery() == null) {
                        request.setQuery(new LinqRequest.Query());
                    }
                    if (request.getQuery().getParams() == null) {
                        request.getQuery().setParams(new java.util.HashMap<>());
                    }

                    // Merge additional parameters if provided (copy-on-write to avoid side-effects)
                    Map<String, Object> existingParams = request.getQuery().getParams();
                    java.util.Map<String, Object> mergedParams = new java.util.HashMap<>();
                    if (existingParams != null) {
                        mergedParams.putAll(existingParams);
                    }
                    if (!parameters.isEmpty()) {
                        mergedParams.putAll(parameters);
                    }
                    // Add teamId if not already present
                    mergedParams.putIfAbsent("teamId", teamId);
                    request.getQuery().setParams(mergedParams);

                    // Set executedBy from the actual user who initiated the agent task
                    request.setExecutedBy(execution.getExecutedBy());

                    Mono<String> agentNameMono = agentRepository.findById(agentId).map(agent -> agent.getName());
                    Mono<String> taskNameMono = agentTaskRepository.findById(agentTaskId).map(task -> task.getName());

                    return Mono.zip(agentNameMono, taskNameMono)
                            .flatMap(tuple -> {
                                String agentName = tuple.getT1();
                                String taskName = tuple.getT2();

                                // Update execution with agent details for monitoring
                                execution.setAgentName(agentName);
                                execution.setTaskName(taskName);
                                agentExecutionRepository.save(execution).subscribe();

                                Map<String, Object> agentContext = Map.of(
                                        "agentId", agentId,
                                        "agentName", agentName,
                                        "agentTaskId", agentTaskId,
                                        "agentTaskName", taskName,
                                        "executionSource", "agent",
                                        "agentExecutionId", execution.getExecutionId());

                                return workflowExecutionService
                                        .initializeExecutionWithAgentContext(request, agentContext)
                                        .then(workflowExecutionService.executeWorkflow(request)
                                                .flatMap(response -> workflowExecutionService
                                                        .trackExecutionWithAgentContext(request, response,
                                                                agentContext)));
                            });
                })
                .doOnSuccess(workflowExecution -> log.info(
                        "✅ Workflow {} triggered and tracked with execution ID: {} for agent: {}", workflowId,
                        workflowExecution.getId(), agentId))
                .doOnError(error -> log.error("❌ Failed to trigger workflow {} for agent {}: {}", workflowId, agentId,
                        error.getMessage()));
    }

}
