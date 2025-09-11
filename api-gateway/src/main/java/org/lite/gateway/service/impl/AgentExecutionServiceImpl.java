package org.lite.gateway.service.impl;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;

import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.service.AgentExecutionService;

import org.lite.gateway.service.LinqWorkflowService;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.dto.LinqRequest;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.web.server.ServerWebExchange;
import org.lite.gateway.enums.ExecutionType;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.enums.ExecutionResult;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentExecutionServiceImpl implements AgentExecutionService {
    
    private final AgentRepository agentRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final LinqWorkflowService linqWorkflowService;
    private final LinqWorkflowExecutionService workflowExecutionService;
    
    // ==================== EXECUTION MANAGEMENT ====================
    
    @Override
    public Mono<AgentExecution> startTaskExecution(String agentId, String taskId, String teamId, String executedBy, ServerWebExchange exchange) {
        log.info("Starting execution of task {} for agent {} in team {}", taskId, agentId, teamId);
        
        return Mono.zip(
                agentRepository.findById(agentId),
                agentTaskRepository.findById(taskId)
        ).flatMap(tuple -> {
            Agent agent = tuple.getT1();
            AgentTask task = tuple.getT2();
            
            // Validate task can be executed
            log.info("Task {} state: enabled={}, cronExpression={}, autoExecute={}", 
                    taskId, task.isEnabled(), task.getCronExpression(), task.isAutoExecute());
            
            if (!task.isReadyToExecute()) {
                log.error("Task {} is not ready to execute. enabled={}, cronExpression={}, autoExecute={}", 
                        taskId, task.isEnabled(), task.getCronExpression(), task.isAutoExecute());
                return Mono.error(new RuntimeException("Task is not ready to execute"));
            }
            
            if (!agent.canExecute()) {
                return Mono.error(new RuntimeException("Agent is not ready to execute"));
            }
            
            // Create execution record
            AgentExecution execution = AgentExecution.builder()
                    .executionId(UUID.randomUUID().toString())
                    .agentId(agentId)
                    .taskId(taskId)
                    .taskName(task.getName())
                    .teamId(teamId)
                    .routeIdentifier(agent.getRouteIdentifier())
                    .executionType(ExecutionType.MANUAL)
                    .triggerSource("user")
                    .scheduledAt(LocalDateTime.now())
                    .startedAt(LocalDateTime.now())
                    .executedBy(executedBy)
                    .executionEnvironment("production")
                    .maxRetries(task.getMaxRetries())
                    .build();
            
            execution.onCreate();
            
            // No agent state management - agents can execute multiple tasks simultaneously
            
            return agentExecutionRepository.save(execution)
            .then(executeWorkflow(execution, task, agent, exchange))
            .flatMap(workflowExecutionId -> {
                // SUCCESS CASE - Only update execution status
                execution.markAsCompleted();
                
                return agentExecutionRepository.save(execution)
                .thenReturn(execution);
            })
            .onErrorResume(error -> {
                // ERROR CASE - Only update execution status
                log.error("Task execution failed: {}", error.getMessage());
                execution.markAsFailed(error.getMessage(), "Workflow execution failed");
                
                return agentExecutionRepository.save(execution)
                .then(Mono.error(error));
            });
        })
        .doOnSuccess(execution -> log.info("Task execution started: {}", execution.getExecutionId()))
        .doOnError(error -> log.error("Failed to start task execution: {}", error.getMessage()));
    }

    @Override
    public Mono<Boolean> cancelExecution(String executionId, String teamId, String cancelledBy) {
        log.info("Cancelling execution {} for team {} by {}", executionId, teamId, cancelledBy);
        
        return agentExecutionRepository.findById(executionId)
                .filter(execution -> teamId.equals(execution.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Execution not found or access denied")))
                .flatMap(execution -> {
                    execution.setStatus(ExecutionStatus.CANCELLED);
                    execution.setResult(ExecutionResult.FAILURE);
                    execution.setErrorMessage("Execution cancelled by: " + cancelledBy);
                    execution.setCompletedAt(LocalDateTime.now());
                    execution.onUpdate();
                    
                    return agentExecutionRepository.save(execution)
                            .thenReturn(true);
                })
                .doOnSuccess(cancelled -> log.info("Execution {} cancelled successfully by {}", executionId, cancelledBy))
                .doOnError(error -> log.error("Failed to cancel execution {}: {}", executionId, error.getMessage()));
    }
    
    @Override
    public Flux<AgentExecution> getExecutionHistory(String agentId, int limit) {
        return agentRepository.findById(agentId)
                .thenMany(agentExecutionRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                        .take(limit));
    }
    
    @Override
    public Flux<AgentExecution> getTaskExecutionHistory(String taskId, int limit) {
        return agentTaskRepository.findById(taskId)
                .thenMany(agentExecutionRepository.findByTaskIdOrderByCreatedAtDesc(taskId)
                        .take(limit));
    }
    
    @Override
    public Flux<AgentExecution> getExecutionsByTeamAndStatus(String teamId, String status, int limit) {
        return agentExecutionRepository.findByStatus(status)
                .filter(execution -> teamId.equals(execution.getTeamId()))
                .take(limit);
    }

    // ==================== WORKFLOW INTEGRATION ====================
    
    /**
     * Trigger workflow with agent context for tracking
     */
    public Mono<String> triggerWorkflow(String workflowId, Map<String, Object> parameters, String teamId, 
                                       String agentId, String agentTaskId, String agentExecutionId, ServerWebExchange exchange) {
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
                            if (step.getToolConfig() != null && step.getToolConfig().getSettings() != null) {
                                Map<String, Object> settings = step.getToolConfig().getSettings();
                                if (settings.containsKey("max.tokens")) {
                                    Object value = settings.remove("max.tokens");
                                    settings.put("max_tokens", value);
                                }
                            }
                        });
                    }
                    
                    // Set the executedBy field from parameters
                    if (parameters.containsKey("executedBy")) {
                        request.setExecutedBy((String) parameters.get("executedBy"));
                    }
                    
                    // Execute the workflow with security context
                    return Mono.just(exchange)
                            .doOnNext(ex -> log.info("ServerWebExchange available: {}", ex != null))
                            .flatMap(ex -> 
                                ReactiveSecurityContextHolder.getContext()
                                    .doOnNext(ctx -> log.info("Security context available: {}", ctx != null))
                                    .doOnNext(ctx -> {
                                        if (ctx != null) {
                                            log.info("Security context authentication: {}", ctx.getAuthentication());
                                            if (ctx.getAuthentication() != null) {
                                                log.info("Authentication principal: {}", ctx.getAuthentication().getPrincipal());
                                                log.info("Authentication credentials: {}", ctx.getAuthentication().getCredentials());
                                            }
                                        }
                                    })
                                    .flatMap(securityContext -> 
                                        workflowExecutionService.executeWorkflow(request)
                                            .doOnNext(response -> log.info("Workflow execution response received"))
                                            .flatMap(response -> {
                                                // Check if the workflow execution actually succeeded
                                                if (response.getMetadata() != null && 
                                                    response.getMetadata().getStatus() != null && 
                                                    !"success".equals(response.getMetadata().getStatus())) {
                                                    log.error("Workflow execution failed with status: {}", response.getMetadata().getStatus());
                                                    return Mono.<String>error(new RuntimeException("Workflow execution failed with status: " + response.getMetadata().getStatus()));
                                                }
                                                
                                                // Get agent name by looking up the agent
                                                return agentRepository.findById(agentId)
                                                        .map(agent -> {
                                                            // Prepare agent context for tracking
                                                            Map<String, Object> agentContext = Map.of(
                                                                "agentId", agentId,
                                                                "agentName", agent.getName(),
                                                                "agentTaskId", agentTaskId,
                                                                "executionSource", "agent",
                                                                "agentExecutionId", agentExecutionId
                                                            );
                                                            
                                                            return agentContext;
                                                        })
                                                        .flatMap(agentContext -> 
                                                            workflowExecutionService.trackExecutionWithAgentContext(request, response, agentContext)
                                                                    .map(workflowExecution -> workflowExecution.getId())
                                                        );
                                            })
                                    )
                            );
                })
                .doOnSuccess(executionId -> log.info("Workflow {} triggered successfully with execution ID: {} for agent: {}", workflowId, executionId, agentId))
                .doOnError(error -> log.error("Failed to trigger workflow {} for agent {}: {}", workflowId, agentId, error.getMessage()));
    }
    
    /**
     * Execute the workflow associated with a task
     */
    private Mono<Void> executeWorkflow(AgentExecution execution, AgentTask task, Agent agent, ServerWebExchange exchange) {
        log.info("Executing workflow for task: {} (execution: {})", task.getName(), execution.getExecutionId());
        
        try {
            // Extract workflow ID from task configuration
            String workflowId = extractWorkflowId(task);
            if (workflowId == null) {
                log.warn("No workflow ID found in task configuration for task: {}", task.getName());
                return updateExecutionStatus(execution, ExecutionStatus.FAILED, "No workflow ID configured", null);
            }
            
            // Prepare workflow parameters
            Map<String, Object> parameters = prepareWorkflowParameters(execution, task, agent);
            
            // Trigger the workflow with agent context
            return triggerWorkflow(workflowId, parameters, execution.getTeamId(), 
                    agent.getId(), task.getId(), execution.getExecutionId(), exchange)
                    .flatMap(workflowExecutionId -> {
                        log.info("Workflow triggered successfully: {} for execution: {}", workflowExecutionId, execution.getExecutionId());
                        return updateExecutionStatus(execution, ExecutionStatus.RUNNING, "Workflow started", workflowExecutionId);
                    })
                    .onErrorResume(error -> {
                        log.error("Failed to trigger workflow for task: {}", task.getName(), error);
                        return updateExecutionStatus(execution, ExecutionStatus.FAILED, "Workflow trigger failed: " + error.getMessage(), null);
                    });
                    
        } catch (Exception e) {
            log.error("Error executing workflow for task: {}", task.getName(), e);
            return updateExecutionStatus(execution, ExecutionStatus.FAILED, "Workflow execution error: " + e.getMessage(), null);
        }
    }
    
    /**
     * Extract workflow ID from task configuration
     */
    private String extractWorkflowId(AgentTask task) {
        try {
            // Check task_config first
            if (task.getTaskConfig() != null && task.getTaskConfig().containsKey("workflowId")) {
                return (String) task.getTaskConfig().get("workflowId");
            }
            
            // Check linq_config as fallback
            if (task.getLinqConfig() != null) {
                Map<String, Object> query = (Map<String, Object>) task.getLinqConfig().get("query");
                if (query != null && query.containsKey("workflowId")) {
                    return (String) query.get("workflowId");
                }
            }
            
            return null;
        } catch (Exception e) {
            log.warn("Error extracting workflow ID from task: {}", task.getName(), e);
            return null;
        }
    }
    
    /**
     * Prepare workflow parameters
     */
    private Map<String, Object> prepareWorkflowParameters(AgentExecution execution, AgentTask task, Agent agent) {
        Map<String, Object> parameters = new HashMap<>();
        
        // Basic execution info
        parameters.put("executionId", execution.getExecutionId());
        parameters.put("taskId", task.getId());
        parameters.put("agentId", agent.getId());
        parameters.put("teamId", execution.getTeamId());
        parameters.put("executedBy", execution.getExecutedBy());
        
        // Task-specific parameters
        if (task.getTaskConfig() != null) {
            parameters.putAll(task.getTaskConfig());
        }
        
        // Note: execution parameters would be added here if AgentExecution had them
        // For now, we use the basic parameters above
        
        return parameters;
    }
    
    /**
     * Update execution status
     */
    private Mono<Void> updateExecutionStatus(AgentExecution execution, ExecutionStatus status, String message, String workflowExecutionId) {
        execution.setStatus(status);
        execution.setResult(status == ExecutionStatus.RUNNING ? ExecutionResult.UNKNOWN : 
                           status == ExecutionStatus.COMPLETED ? ExecutionResult.SUCCESS : 
                           status == ExecutionStatus.FAILED ? ExecutionResult.FAILURE : 
                           ExecutionResult.UNKNOWN);
        if (workflowExecutionId != null) {
            execution.setWorkflowExecutionId(workflowExecutionId);
        }
        if (message != null) {
            execution.setErrorMessage(message);
        }
        execution.onUpdate();
        
        return agentExecutionRepository.save(execution)
                .then();
    }
    
} 