package org.lite.gateway.service.impl;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;

import org.lite.gateway.enums.AgentStatus;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.service.AgentOrchestrationService;

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
public class AgentOrchestrationServiceImpl implements AgentOrchestrationService {
    
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
    public Mono<AgentExecution> executeTaskManually(String agentId, String taskId, String teamId, String executedBy, ServerWebExchange exchange) {
        return startTaskExecution(agentId, taskId, teamId, executedBy, exchange);
    }
    
    @Override
    public Mono<Boolean> cancelExecution(String executionId, String teamId, String cancelledBy) {
        log.info("Cancelling execution {} for team {} by {}", executionId, teamId, cancelledBy);
        
        return getExecutionById(executionId, teamId)
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
    public Mono<AgentExecution> getExecutionById(String executionId, String teamId) {
        return agentExecutionRepository.findById(executionId)
                .filter(execution -> teamId.equals(execution.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Execution not found or access denied")));
    }
    
    @Override
    public Flux<AgentExecution> getExecutionHistory(String agentId, String teamId, int limit) {
        return agentRepository.findById(agentId)
                .thenMany(agentExecutionRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                        .take(limit));
    }
    
    @Override
    public Flux<AgentExecution> getTaskExecutionHistory(String taskId, String teamId, int limit) {
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
    
    // ==================== SCHEDULING ====================
    
    @Override
    public Mono<Agent> scheduleAgent(String agentId, String cronExpression, String teamId) {
        log.info("Scheduling agent {} with cron: {} for team {}", agentId, cronExpression, teamId);
        
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    agent.setCronExpression(cronExpression);
                    agent.setAutoSchedule(true);
                    agent.setStatus(AgentStatus.SCHEDULED);
                    agent.setUpdatedBy("system");
                    agent.onUpdate();
                    
                    return agentRepository.save(agent);
                })
                .doOnSuccess(scheduledAgent -> log.info("Agent {} scheduled successfully", agentId))
                .doOnError(error -> log.error("Failed to schedule agent {}: {}", agentId, error.getMessage()));
    }
    
    @Override
    public Mono<Agent> unscheduleAgent(String agentId, String teamId) {
        log.info("Unschedule agent {} for team {}", agentId, teamId);
        
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    agent.setCronExpression(null);
                    agent.setAutoSchedule(false);
                    agent.setStatus(AgentStatus.IDLE);
                    agent.setUpdatedBy("system");
                    agent.onUpdate();
                    
                    return agentRepository.save(agent);
                })
                .doOnSuccess(unscheduledAgent -> log.info("Agent {} unscheduled successfully", agentId))
                .doOnError(error -> log.error("Failed to unschedule agent {}: {}", agentId, error.getMessage()));
    }
    
    @Override
    public Flux<Agent> getAgentsReadyToRun() {
        return agentRepository.findAgentsReadyToRun(LocalDateTime.now());
    }
    
    @Override
    public Flux<Agent> getAgentsReadyToRunByTeam(String teamId) {
        return agentRepository.findByTeamId(teamId)
                .filter(Agent::canExecute)
                .filter(Agent::isScheduled);
    }
    
    @Override
    public Mono<Agent> updateNextRunTime(String agentId, LocalDateTime nextRun) {
        return agentRepository.findById(agentId)
                .flatMap(agent -> {
                    agent.setNextRun(nextRun);
                    agent.setUpdatedBy("system");
                    agent.onUpdate();
                    
                    return agentRepository.save(agent);
                });
    }
    
    // ==================== MONITORING & HEALTH ====================
    
    @Override
    public Mono<Map<String, Object>> getAgentHealth(String agentId, String teamId) {
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    Map<String, Object> health = Map.of(
                            "agentId", agentId,
                            "status", agent.getStatus(),
                            "enabled", agent.isEnabled(),
                            "lastRun", agent.getLastRun(),
                            "nextRun", agent.getNextRun(),
                            "lastError", agent.getLastError(),
                            "canExecute", agent.canExecute(),
                            "isScheduled", agent.isScheduled()
                    );
                    
                    return Mono.just(health);
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getTeamAgentsHealth(String teamId) {
        return agentRepository.findByTeamId(teamId)
                .collectList()
                .map(agents -> {
                    long totalAgents = agents.size();
                    long enabledAgents = agents.stream().filter(Agent::isEnabled).count();
                    long runningAgents = agents.stream().filter(a -> AgentStatus.RUNNING.equals(a.getStatus())).count();
                    long errorAgents = agents.stream().filter(a -> AgentStatus.ERROR.equals(a.getStatus())).count();
                    
                    return Map.of(
                            "teamId", teamId,
                            "totalAgents", totalAgents,
                            "enabledAgents", enabledAgents,
                            "runningAgents", runningAgents,
                            "errorAgents", errorAgents,
                            "healthPercentage", totalAgents > 0 ? (enabledAgents * 100.0 / totalAgents) : 0.0
                    );
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getAgentPerformance(String agentId, String teamId, LocalDateTime from, LocalDateTime to) {
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .thenMany(agentExecutionRepository.findByAgentIdAndStartedAtBetween(agentId, from, to))
                .collectList()
                .map(executions -> {
                    long totalExecutions = executions.size();
                    long successfulExecutions = executions.stream()
                            .filter(e -> "SUCCESS".equals(e.getResult()))
                            .count();
                    long failedExecutions = executions.stream()
                            .filter(e -> "FAILURE".equals(e.getResult()))
                            .count();
                    
                    double avgExecutionTime = executions.stream()
                            .filter(e -> e.getExecutionDurationMs() != null)
                            .mapToLong(AgentExecution::getExecutionDurationMs)
                            .average()
                            .orElse(0.0);
                    
                    return Map.of(
                            "agentId", agentId,
                            "totalExecutions", totalExecutions,
                            "successfulExecutions", successfulExecutions,
                            "failedExecutions", failedExecutions,
                            "successRate", totalExecutions > 0 ? (successfulExecutions * 100.0 / totalExecutions) : 0.0,
                            "averageExecutionTimeMs", avgExecutionTime
                    );
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getTaskPerformance(String taskId, String teamId, LocalDateTime from, LocalDateTime to) {
        return agentTaskRepository.findById(taskId)
                .thenMany(agentExecutionRepository.findByTaskIdAndStartedAtBetween(taskId, from, to))
                .collectList()
                .map(executions -> {
                    long totalExecutions = executions.size();
                    long successfulExecutions = executions.stream()
                            .filter(e -> "SUCCESS".equals(e.getResult()))
                            .count();
                    long failedExecutions = executions.stream()
                            .filter(e -> "FAILURE".equals(e.getResult()))
                            .count();
                    
                    double avgExecutionTime = executions.stream()
                            .filter(e -> e.getExecutionDurationMs() != null)
                            .mapToLong(AgentExecution::getExecutionDurationMs)
                            .average()
                            .orElse(0.0);
                    
                    return Map.of(
                            "taskId", taskId,
                            "totalExecutions", totalExecutions,
                            "successfulExecutions", successfulExecutions,
                            "failedExecutions", failedExecutions,
                            "successRate", totalExecutions > 0 ? (successfulExecutions * 100.0 / totalExecutions) : 0.0,
                            "averageExecutionTimeMs", avgExecutionTime
                    );
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getTeamExecutionStats(String teamId, LocalDateTime from, LocalDateTime to) {
        // First get all agents for the team, then get their executions
        return agentRepository.findByTeamId(teamId)
                .map(Agent::getId)
                .collectList()
                .flatMap(agentIds -> {
                    if (agentIds.isEmpty()) {
                        return Mono.just(Map.of(
                                "teamId", teamId,
                                "totalExecutions", 0L,
                                "successfulExecutions", 0L,
                                "failedExecutions", 0L,
                                "successRate", 0.0,
                                "period", Map.of("from", from, "to", to)
                        ));
                    }
                    
                    return Flux.fromIterable(agentIds)
                            .flatMap(agentId -> agentExecutionRepository.findByAgentIdAndStartedAtBetween(agentId, from, to))
                            .collectList()
                            .map(executions -> {
                                long totalExecutions = executions.size();
                                long successfulExecutions = executions.stream()
                                        .filter(e -> ExecutionResult.SUCCESS.name().equals(e.getResult()))
                                        .count();
                                long failedExecutions = executions.stream()
                                        .filter(e -> ExecutionResult.FAILURE.name().equals(e.getResult()))
                                        .count();
                                
                                return Map.of(
                                        "teamId", teamId,
                                        "totalExecutions", totalExecutions,
                                        "successfulExecutions", successfulExecutions,
                                        "failedExecutions", failedExecutions,
                                        "successRate", totalExecutions > 0 ? (successfulExecutions * 100.0 / totalExecutions) : 0.0,
                                        "period", Map.of("from", from, "to", to)
                                );
                            });
                });
    }
    
    // ==================== ERROR HANDLING & RECOVERY ====================
    
    @Override
    public Mono<AgentExecution> retryExecution(String executionId, String teamId, String retriedBy) {
        log.info("Retrying execution {} for team {}", executionId, teamId);
        
        return getExecutionById(executionId, teamId)
                .flatMap(execution -> {
                    if (!execution.canRetry()) {
                        return Mono.error(new RuntimeException("Execution cannot be retried"));
                    }
                    
                    execution.addRetryAttempt();
                    execution.setStatus(ExecutionStatus.RUNNING);
                    execution.setResult(ExecutionResult.UNKNOWN);
                    execution.setStartedAt(LocalDateTime.now());
                    execution.setExecutedBy(retriedBy);
                    execution.onUpdate();
                    
                    return agentExecutionRepository.save(execution);
                })
                .doOnSuccess(retriedExecution -> log.info("Execution {} retried successfully", executionId))
                .doOnError(error -> log.error("Failed to retry execution {}: {}", executionId, error.getMessage()));
    }
    
    @Override
    public Mono<Agent> resetAgentError(String agentId, String teamId, String resetBy) {
        log.info("Resetting error state for agent {} in team {}", agentId, teamId);
        
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    // No agent state management - just update audit fields
                    agent.setUpdatedBy(resetBy);
                    agent.onUpdate();
                    
                    return agentRepository.save(agent);
                })
                .doOnSuccess(resetAgent -> log.info("Agent {} error state reset successfully", agentId))
                .doOnError(error -> log.error("Failed to reset agent {} error state: {}", agentId, error.getMessage()));
    }
    
    @Override
    public Flux<Agent> getAgentsWithErrors(String teamId) {
        return agentRepository.findByTeamId(teamId)
                .filter(agent -> AgentStatus.ERROR.equals(agent.getStatus()));
    }
    
    @Override
    public Flux<AgentExecution> getFailedExecutions(String teamId, int limit) {
        return agentExecutionRepository.findByStatus("FAILED")
                .filter(execution -> teamId.equals(execution.getTeamId()))
                .take(limit);
    }
    
    // ==================== WORKFLOW INTEGRATION ====================
    
    @Override
    public Mono<String> triggerWorkflow(String workflowId, Map<String, Object> parameters, String teamId) {
        log.info("Triggering workflow {} for team {} with parameters: {}", workflowId, teamId, parameters);
        
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
                    
                    // Execute the workflow
                    return workflowExecutionService.executeWorkflow(request)
                            .flatMap(response -> workflowExecutionService.trackExecution(request, response)
                                    .map(execution -> execution.getId()));
                })
                .doOnSuccess(executionId -> log.info("Workflow {} triggered successfully with execution ID: {}", workflowId, executionId))
                .doOnError(error -> log.error("Failed to trigger workflow {}: {}", workflowId, error.getMessage()));
    }
    
    /**
     * Trigger workflow with agent context for tracking
     */
    public Mono<String> triggerWorkflowWithAgentContext(String workflowId, Map<String, Object> parameters, String teamId, 
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
            return triggerWorkflowWithAgentContext(workflowId, parameters, execution.getTeamId(), 
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
    
    @Override
    public Mono<Map<String, Object>> getWorkflowExecutionStatus(String workflowExecutionId, String teamId) {
        log.info("Getting workflow execution status {} for team {}", workflowExecutionId, teamId);
        
        // TODO: Implement workflow status checking
        // This will integrate with your existing workflow system
        
        return Mono.just(Map.of(
                "workflowExecutionId", workflowExecutionId,
                "status", "RUNNING",
                "progress", 50.0
        ));
    }
    
    // ==================== TEAM & PERMISSIONS ====================
    
    @Override
    public Mono<Boolean> validateTeamAccess(String agentId, String teamId) {
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .thenReturn(true)
                .onErrorReturn(false);
    }
    
    @Override
    public Flux<Agent> getAccessibleAgents(String teamId) {
        return agentRepository.findByTeamId(teamId);
    }
    
    @Override
    public Mono<Agent> transferAgentOwnership(String agentId, String fromTeamId, String toTeamId, String transferredBy) {
        log.info("Transferring agent {} from team {} to team {}", agentId, fromTeamId, toTeamId);
        
        return agentRepository.findById(agentId)
                .filter(agent -> fromTeamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    agent.setTeamId(toTeamId);
                    agent.setUpdatedBy(transferredBy);
                    agent.onUpdate();
                    
                    return agentRepository.save(agent);
                })
                .doOnSuccess(transferredAgent -> log.info("Agent {} ownership transferred successfully", agentId))
                .doOnError(error -> log.error("Failed to transfer agent {} ownership: {}", agentId, error.getMessage()));
    }
    
    // ==================== BULK OPERATIONS ====================
    
    @Override
    public Mono<List<Agent>> bulkSetAgentsEnabled(List<String> agentIds, String teamId, boolean enabled, String updatedBy) {
        log.info("Bulk setting {} agents enabled={} for team {}", agentIds.size(), enabled, teamId);
        
        return Flux.fromIterable(agentIds)
                .flatMap(agentId -> agentRepository.findById(agentId)
                        .filter(agent -> teamId.equals(agent.getTeamId()))
                        .flatMap(agent -> {
                            agent.setEnabled(enabled);
                            agent.setStatus(enabled ? AgentStatus.IDLE : AgentStatus.DISABLED);
                            agent.setUpdatedBy(updatedBy);
                            agent.onUpdate();
                            return agentRepository.save(agent);
                        }))
                .collectList()
                .doOnSuccess(agents -> log.info("Bulk operation completed for {} agents", agents.size()))
                .doOnError(error -> log.error("Bulk operation failed: {}", error.getMessage()));
    }
    
    @Override
    public Mono<List<Boolean>> bulkDeleteAgents(List<String> agentIds, String teamId) {
        log.info("Bulk deleting {} agents for team {}", agentIds.size(), teamId);
        
        return Flux.fromIterable(agentIds)
                .flatMap(agentId -> agentRepository.findById(agentId)
                        .filter(agent -> teamId.equals(agent.getTeamId()))
                        .flatMap(agent -> agentRepository.delete(agent).thenReturn(true)))
                .collectList()
                .doOnSuccess(results -> log.info("Bulk delete completed for {} agents", results.size()))
                .doOnError(error -> log.error("Bulk delete failed: {}", error.getMessage()));
    }
    
    @Override
    public Mono<List<Agent>> bulkScheduleAgents(List<String> agentIds, String cronExpression, String teamId) {
        log.info("Bulk scheduling {} agents with cron: {} for team {}", agentIds.size(), cronExpression, teamId);
        
        return Flux.fromIterable(agentIds)
                .flatMap(agentId -> scheduleAgent(agentId, cronExpression, teamId))
                .collectList()
                .doOnSuccess(agents -> log.info("Bulk scheduling completed for {} agents", agents.size()))
                .doOnError(error -> log.error("Bulk scheduling failed: {}", error.getMessage()));
    }
    
    // ==================== UTILITY METHODS ====================
    
    @Override
    public Mono<Map<String, Object>> validateAgentConfiguration(Agent agent) {
        log.info("Validating agent configuration for: {}", agent.getName());
        
        Map<String, Object> validation = Map.of(
                "valid", true,
                "errors", List.of(),
                "warnings", List.of()
        );
        
        // TODO: Implement validation logic
        // Check required fields, validate cron expressions, etc.
        
        return Mono.just(validation);
    }
    
    @Override
    public Mono<Map<String, Object>> validateTaskConfiguration(AgentTask task) {
        log.info("Validating task configuration for: {}", task.getName());
        
        Map<String, Object> validation = Map.of(
                "valid", true,
                "errors", List.of(),
                "warnings", List.of()
        );
        
        // TODO: Implement validation logic
        // Check required fields, validate dependencies, etc.
        
        return Mono.just(validation);
    }
    
    @Override
    public Mono<Map<String, Object>> getAgentCapabilitiesSummary(String teamId) {
        return agentRepository.findByTeamId(teamId)
                .collectList()
                .map(agents -> {
                    Map<String, Long> capabilityCounts = agents.stream()
                            .flatMap(agent -> agent.getCapabilities().stream())
                            .collect(java.util.stream.Collectors.groupingBy(
                                    capability -> capability,
                                    java.util.stream.Collectors.counting()
                            ));
                    
                    return Map.of(
                            "teamId", teamId,
                            "totalAgents", agents.size(),
                            "capabilityCounts", capabilityCounts
                    );
                });
    }
    
    @Override
    public Mono<Map<String, Object>> getTeamResourceUsage(String teamId) {
        return agentRepository.findByTeamId(teamId)
                .collectList()
                .map(agents -> {
                    long totalAgents = agents.size();
                    long runningAgents = agents.stream()
                            .filter(a -> AgentStatus.RUNNING.equals(a.getStatus()))
                            .count();
                    long scheduledAgents = agents.stream()
                            .filter(Agent::isScheduled)
                            .count();
                    
                    return Map.of(
                            "teamId", teamId,
                            "totalAgents", totalAgents,
                            "runningAgents", runningAgents,
                            "scheduledAgents", scheduledAgents,
                            "utilizationPercentage", totalAgents > 0 ? (runningAgents * 100.0 / totalAgents) : 0.0
                    );
                });
    }
} 
