package org.lite.gateway.executor;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.service.TriggerWorkflowByIdService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

/**
 * Task executor for WORKFLOW_TRIGGER tasks
 * Triggers workflows by ID (reference-based workflows)
 */
@Service
@Slf4j
public class WorkflowTriggerAgentTaskExecutor extends AgentTaskExecutor {

    private final TriggerWorkflowByIdService triggerWorkflowByIdService;

    public WorkflowTriggerAgentTaskExecutor(TriggerWorkflowByIdService triggerWorkflowByIdService, 
                                          AgentExecutionRepository agentExecutionRepository) {
        super(agentExecutionRepository);
        this.triggerWorkflowByIdService = triggerWorkflowByIdService;
    }

    @Override
    public Mono<Void> executeTask(AgentExecution execution, AgentTask task, Agent agent, ServerWebExchange exchange) {
        log.info("Executing WORKFLOW_TRIGGER task: {} (execution: {})", task.getName(), execution.getExecutionId());
        
        try {
            // Extract workflow ID from task configuration
            String workflowId = extractWorkflowId(task);
            if (workflowId == null) {
                log.warn("No workflow ID found in task configuration for task: {}", task.getName());
                return updateExecutionStatus(execution, ExecutionStatus.FAILED, "No workflow ID configured", null);
            }

            // Prepare workflow parameters
            Map<String, Object> parameters = prepareWorkflowParameters(execution, task, agent);
            
            // Get timeout for task type
            Duration timeout = getTimeoutForTaskType(task);
            log.info("Triggering workflow {} with timeout of {} minutes (adjusted for type {}) and max retries of {}", 
                    workflowId, timeout.toMinutes(), task.getTaskType(), task.getMaxRetries());
            
            return triggerWorkflowByIdService.triggerWorkflow(workflowId, parameters, execution.getTeamId(), 
                    agent.getId(), task.getId(), execution.getExecutionId(), exchange)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(task.getMaxRetries(), Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofMinutes(1))
                            .doBeforeRetry(retrySignal -> {
                                log.warn("Retrying workflow execution for task {} (attempt {}/{}): {}", 
                                    task.getName(), retrySignal.totalRetries() + 1, task.getMaxRetries(), 
                                    retrySignal.failure().getMessage());
                                execution.addRetryAttempt();
                            }))
                    .flatMap(workflowExecutionId -> {
                        log.info("Workflow triggered successfully: {} for execution: {}", workflowExecutionId, execution.getExecutionId());
                        return updateExecutionStatus(execution, ExecutionStatus.RUNNING, "Workflow started", workflowExecutionId);
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
                    
        } catch (Exception e) {
            log.error("Error executing workflow for task: {}", task.getName(), e);
            return updateExecutionStatus(execution, ExecutionStatus.FAILED, "Workflow execution error: " + e.getMessage(), null);
        }
    }
}
