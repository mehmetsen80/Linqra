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
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Task executor for WORKFLOW_EMBEDDED tasks
 * Executes workflows with steps defined directly in the task
 */
@Service
@Slf4j
public class WorkflowEmbeddedAgentTaskExecutor extends AgentTaskExecutor {

    public WorkflowEmbeddedAgentTaskExecutor(LinqWorkflowService linqWorkflowService,
                                            LinqWorkflowExecutionService workflowExecutionService,
                                            AgentRepository agentRepository,
                                            AgentTaskRepository agentTaskRepository,
                                            AgentExecutionRepository agentExecutionRepository,
                                            ObjectMapper objectMapper) {
        super(linqWorkflowService, workflowExecutionService, agentRepository,
                agentTaskRepository, agentExecutionRepository, objectMapper);
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
            return triggerWorkflow(task, parameters, execution.getTeamId(),
                    agent.getId(), task.getId(), execution, exchange)
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
                        return updateExecutionStatus(execution, ExecutionStatus.RUNNING, "Embedded workflow started", workflowExecutionId)
                                .then(monitorWorkflowCompletion(workflowExecutionId, execution));
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

    /**
     * Trigger an embedded workflow using steps defined directly in the task
     */
    public Mono<String> triggerWorkflow(AgentTask task, Map<String, Object> parameters, String teamId,
                                        String agentId, String agentTaskId, AgentExecution execution, ServerWebExchange exchange) {
        log.info("Triggering embedded workflow for team {} with agent context: agent={}, task={}", teamId, agentId, agentTaskId);

        try {
            // The linq_config already contains a complete LinqRequest structure
            // We just need to convert it to a LinqRequest object and merge parameters
            Map<String, Object> linqConfig = task.getLinqConfig();
            if (linqConfig == null) {
                return Mono.error(new RuntimeException("Invalid embedded workflow: missing linq_config"));
            }

            // Convert linq_config Map to LinqRequest object (same as stored workflows)
            LinqRequest request = convertMapToLinqRequest(linqConfig);

            // Ensure it's a workflow request
            if (!request.getLink().getTarget().equals("workflow")) {
                return Mono.error(new RuntimeException("Invalid embedded workflow request: target must be 'workflow'"));
            }

            // Merge additional parameters if provided
            if (!parameters.isEmpty() && request.getQuery() != null) {
                Map<String, Object> existingParams = request.getQuery().getParams();
                if (existingParams == null) {
                    request.getQuery().setParams(new HashMap<>(parameters));
                } else {
                    Map<String, Object> mergedParams = new HashMap<>(existingParams);
                    mergedParams.putAll(parameters);
                    request.getQuery().setParams(mergedParams);
                }
            }

            // Fix the max.tokens issue in the workflow steps (same as controller)
            assert request.getQuery() != null;
            List<LinqRequest.Query.WorkflowStep> workflowSteps = request.getQuery().getWorkflow();
            if (workflowSteps != null) {
                workflowSteps.forEach(step -> {
                    if (step.getToolConfig() != null && step.getToolConfig().getSettings() != null) {
                        Map<String, Object> settings = step.getToolConfig().getSettings();
                        if (settings.containsKey("max.tokens")) {
                            Object value = settings.remove("max.tokens");
                            settings.put("max_tokens", value);
                        }
                    }
                });
            }

            // Set executedBy from the actual user who initiated the agent task
            request.setExecutedBy(execution.getExecutedBy());

            // Execute the embedded workflow
            return workflowExecutionService.executeWorkflow(request)
                    .flatMap(response -> {
                        // Get agent and task names for context
                        Mono<String> agentNameMono = agentRepository.findById(agentId)
                                .map(Agent::getName);
                        Mono<String> taskNameMono = agentTaskRepository.findById(agentTaskId)
                                .map(AgentTask::getName);

                        return Mono.zip(agentNameMono, taskNameMono)
                                .map(tuple -> {
                                    String agentName = tuple.getT1();
                                    String taskName = tuple.getT2();

                                    // Prepare agent context for tracking

                                    return Map.<String, Object>of(
                                            "agentId", agentId,
                                            "agentName", agentName,
                                            "agentTaskId", agentTaskId,
                                            "agentTaskName", taskName,
                                            "executionSource", "agent_embedded",
                                            "agentExecutionId", execution.getExecutionId()
                                    );
                                })
                                .flatMap(agentContext ->
                                        workflowExecutionService.trackExecutionWithAgentContext(request, response, agentContext)
                                                .map(LinqWorkflowExecution::getId)
                                );
                    });

        } catch (Exception e) {
            log.error("Error executing embedded workflow for task {}: {}", task.getName(), e.getMessage());
            return Mono.error(new RuntimeException("Failed to execute embedded workflow: " + e.getMessage()));
        }
    }
}
