package org.lite.gateway.executor;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.service.TriggerEmbeddedWorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.Map;

/**
 * Task executor for WORKFLOW_EMBEDDED tasks
 * Executes workflows with steps defined directly in the task
 */
@Service
@Slf4j
public class WorkflowEmbeddedAgentTaskExecutor extends AgentTaskExecutor {

    private final TriggerEmbeddedWorkflowService triggerEmbeddedWorkflowService;

    public WorkflowEmbeddedAgentTaskExecutor(TriggerEmbeddedWorkflowService triggerEmbeddedWorkflowService, 
                                           AgentExecutionRepository agentExecutionRepository) {
        super(agentExecutionRepository);
        this.triggerEmbeddedWorkflowService = triggerEmbeddedWorkflowService;
    }

    @Override
    public Mono<Void> executeTask(AgentExecution execution, AgentTask task, Agent agent, ServerWebExchange exchange) {
        log.info("Executing WORKFLOW_EMBEDDED task: {} (execution: {})", task.getName(), execution.getExecutionId());
        
        try {
            // For WORKFLOW_EMBEDDED, we don't need to extract workflowId since steps are embedded
            // We can directly prepare parameters and trigger the workflow execution
            Map<String, Object> parameters = prepareWorkflowParameters(execution, task, agent);
            
            // Get timeout for task type
            Duration timeout = getTimeoutForTaskType(task);
            log.info("Triggering embedded workflow with timeout of {} minutes (adjusted for type {}) and max retries of {}", 
                    timeout.toMinutes(), task.getTaskType(), task.getMaxRetries());
            
            // For embedded workflows, we create a LinqRequest directly from the task's embedded steps
            return triggerEmbeddedWorkflowService.triggerWorkflow(task, parameters, execution.getTeamId(),
                    agent.getId(), task.getId(), execution.getExecutionId(), exchange)
                    .timeout(timeout)
                    .retryWhen(Retry.backoff(task.getMaxRetries(), Duration.ofSeconds(2))
                            .maxBackoff(Duration.ofMinutes(1))
                            .doBeforeRetry(retrySignal -> {
                                log.warn("Retrying embedded workflow execution for task {} (attempt {}/{}): {}", 
                                    task.getName(), retrySignal.totalRetries() + 1, task.getMaxRetries(), 
                                    retrySignal.failure().getMessage());
                                execution.addRetryAttempt();
                            }))
                    .flatMap(workflowExecutionId -> {
                        log.info("Embedded workflow triggered successfully: {} for execution: {}", workflowExecutionId, execution.getExecutionId());
                        return updateExecutionStatus(execution, ExecutionStatus.RUNNING, "Embedded workflow started", workflowExecutionId);
                    })
                    .onErrorResume(error -> {
                        if (error instanceof java.util.concurrent.TimeoutException) {
                            log.error("Embedded workflow execution timed out after {} minutes for task: {}", timeout.toMinutes(), task.getName());
                            execution.markAsTimeout();
                            return agentExecutionRepository.save(execution).then(Mono.error(error));
                        } else {
                            log.error("Failed to trigger embedded workflow for task: {} after {} retries", task.getName(), task.getMaxRetries(), error);
                            return updateExecutionStatus(execution, ExecutionStatus.FAILED, 
                                "Embedded workflow trigger failed after " + task.getMaxRetries() + " retries: " + error.getMessage(), null);
                        }
                    });
                    
        } catch (Exception e) {
            log.error("Error executing embedded workflow for task: {}", task.getName(), e);
            return updateExecutionStatus(execution, ExecutionStatus.FAILED, "Embedded workflow execution error: " + e.getMessage(), null);
        }
    }
}
