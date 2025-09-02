package org.lite.gateway.service.impl;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.AgentStatus;
import org.lite.gateway.enums.AgentTaskStatus;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.service.AgentOrchestrationService;
import org.lite.gateway.service.CronDescriptionService;
import org.springframework.stereotype.Service;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentOrchestrationServiceImpl implements AgentOrchestrationService {
    
    private final AgentRepository agentRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final CronDescriptionService cronDescriptionService;
    
    // ==================== AGENT MANAGEMENT ====================
    
    @Override
    public Mono<Agent> createAgent(Agent agent, String teamId, String createdBy) {
        log.info("Creating agent '{}' for team {}", agent.getName(), teamId);
        
        agent.setTeamId(teamId);
        agent.setCreatedBy(createdBy);
        agent.setUpdatedBy(createdBy);
        agent.onCreate();
        
        // Auto-generate cron description if cron expression is provided
        if (agent.getCronExpression() != null && !agent.getCronExpression().trim().isEmpty()) {
            return Mono.just(cronDescriptionService.getCronDescription(agent.getCronExpression()))
                    .flatMap(description -> {
                        agent.setCronDescription(description);
                        log.info("Generated cron description: {}", description);
                        return agentRepository.save(agent);
                    })
                    .doOnSuccess(savedAgent -> log.info("Agent '{}' created successfully with ID: {}", 
                            savedAgent.getName(), savedAgent.getId()))
                    .doOnError(error -> log.error("Failed to create agent '{}': {}", agent.getName(), error.getMessage()));
        }
        
        return agentRepository.save(agent)
                .doOnSuccess(savedAgent -> log.info("Agent '{}' created successfully with ID: {}", 
                        savedAgent.getName(), savedAgent.getId()))
                .doOnError(error -> log.error("Failed to create agent '{}': {}", agent.getName(), error.getMessage()));
    }
    
    @Override
    public Mono<Agent> updateAgent(String agentId, Agent agentUpdates) {
        log.info("Updating agent {}", agentId);
        
        return agentRepository.findById(agentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found")))
                .flatMap(existingAgent -> {
                    // Update only non-null fields
                    if (agentUpdates.getName() != null) existingAgent.setName(agentUpdates.getName());
                    if (agentUpdates.getDescription() != null) existingAgent.setDescription(agentUpdates.getDescription());
                    if (agentUpdates.getStatus() != null) existingAgent.setStatus(agentUpdates.getStatus());
                    if (agentUpdates.getRouteIdentifier() != null) existingAgent.setRouteIdentifier(agentUpdates.getRouteIdentifier());
                    if (agentUpdates.getPrimaryLinqToolId() != null) existingAgent.setPrimaryLinqToolId(agentUpdates.getPrimaryLinqToolId());
                    if (agentUpdates.getSupportedIntents() != null) existingAgent.setSupportedIntents(agentUpdates.getSupportedIntents());
                    if (agentUpdates.getCapabilities() != null) existingAgent.setCapabilities(agentUpdates.getCapabilities());
                    if (agentUpdates.getCronExpression() != null) existingAgent.setCronExpression(agentUpdates.getCronExpression());
                    // Note: boolean fields are primitive, so we can't check for null
                    // We'll only update if the field is explicitly set in the updates
                    if (agentUpdates.getMaxRetries() > 0) existingAgent.setMaxRetries(agentUpdates.getMaxRetries());
                    if (agentUpdates.getTimeoutMinutes() != null) existingAgent.setTimeoutMinutes(agentUpdates.getTimeoutMinutes());
                    
                    existingAgent.setUpdatedBy(agentUpdates.getUpdatedBy());
                    existingAgent.onUpdate();
                    
                    return agentRepository.save(existingAgent);
                })
                .doOnSuccess(updatedAgent -> log.info("Agent {} updated successfully", agentId))
                .doOnError(error -> log.error("Failed to update agent {}: {}", agentId, error.getMessage()));
    }
    
    @Override
    public Mono<Boolean> deleteAgent(String agentId, String teamId) {
        log.info("Deleting agent {} for team {}", agentId, teamId);
        
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    agent.setEnabled(false);
                    agent.setStatus(AgentStatus.DISABLED);
                    agent.setUpdatedBy("system");
                    agent.onUpdate();
                    
                    return agentRepository.save(agent)
                            .thenReturn(true);
                })
                .doOnSuccess(deleted -> log.info("Agent {} deleted successfully", agentId))
                .doOnError(error -> log.error("Failed to delete agent {}: {}", agentId, error.getMessage()));
    }
    
    @Override
    public Mono<Agent> setAgentEnabled(String agentId, String teamId, boolean enabled) {
        log.info("Setting agent {} enabled={} for team {}", agentId, enabled, teamId);
        
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                .flatMap(agent -> {
                    agent.setEnabled(enabled);
                    if (enabled) {
                        agent.setStatus(AgentStatus.IDLE);
                    } else {
                        agent.setStatus(AgentStatus.DISABLED);
                    }
                    agent.setUpdatedBy("system");
                    agent.onUpdate();
                    
                    return agentRepository.save(agent);
                })
                .doOnSuccess(updatedAgent -> log.info("Agent {} enabled={} successfully", agentId, enabled))
                .doOnError(error -> log.error("Failed to set agent {} enabled={}: {}", agentId, enabled, error.getMessage()));
    }
    
    @Override
    public Mono<Agent> getAgentById(String agentId, String teamId) {
        return agentRepository.findById(agentId)
                .filter(agent -> teamId.equals(agent.getTeamId()))
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")));
    }
    
    @Override
    public Flux<Agent> getAgentsByTeam(String teamId) {
        return agentRepository.findByTeamId(teamId);
    }
    
    @Override
    public Flux<Agent> getAgentsByTeamAndStatus(String teamId, AgentStatus status) {
        return agentRepository.findByTeamIdAndStatus(teamId, status);
    }
    
    // ==================== TASK MANAGEMENT ====================
    
    @Override
    public Mono<AgentTask> createTask(AgentTask task) {
        log.info("Creating task '{}' for agent {}", task.getName(), task.getAgentId());
        
        return agentRepository.findById(task.getAgentId())
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found")))
                .flatMap(agent -> {
                    task.setAgentName(agent.getName());
                    task.setUpdatedBy(task.getCreatedBy());
                    task.onCreate();
                    
                    return agentTaskRepository.save(task)
                            .flatMap(savedTask -> {
                                // Add task to agent
                                agent.addTask(savedTask.getId());
                                return agentRepository.save(agent)
                                        .thenReturn(savedTask);
                            });
                })
                .doOnSuccess(savedTask -> log.info("Task '{}' created successfully with ID: {}", 
                        savedTask.getName(), savedTask.getId()))
                .doOnError(error -> log.error("Failed to create task '{}': {}", task.getName(), error.getMessage()));
    }
    
    @Override
    public Mono<AgentTask> updateTask(String taskId, AgentTask taskUpdates, String teamId, String updatedBy) {
        log.info("Updating task {} for team {}", taskId, teamId);
        
        return getTaskById(taskId, teamId)
                .flatMap(existingTask -> {
                    // Update only non-null fields
                    if (taskUpdates.getName() != null) existingTask.setName(taskUpdates.getName());
                    if (taskUpdates.getDescription() != null) existingTask.setDescription(taskUpdates.getDescription());
                    if (taskUpdates.getTaskType() != null) existingTask.setTaskType(taskUpdates.getTaskType());
                    if (taskUpdates.getPriority() != 0) existingTask.setPriority(taskUpdates.getPriority());
                    // Note: boolean fields are primitive, so we can't check for null
                    if (taskUpdates.getMaxRetries() > 0) existingTask.setMaxRetries(taskUpdates.getMaxRetries());
                    if (taskUpdates.getTimeoutMinutes() > 0) existingTask.setTimeoutMinutes(taskUpdates.getTimeoutMinutes());
                    if (taskUpdates.getTaskConfig() != null) existingTask.setTaskConfig(taskUpdates.getTaskConfig());
                    if (taskUpdates.getCronExpression() != null) existingTask.setCronExpression(taskUpdates.getCronExpression());
                    
                    existingTask.setUpdatedBy(updatedBy);
                    existingTask.onUpdate();
                    
                    return agentTaskRepository.save(existingTask);
                })
                .doOnSuccess(updatedTask -> log.info("Task {} updated successfully", taskId))
                .doOnError(error -> log.error("Failed to update task {}: {}", taskId, error.getMessage()));
    }
    
    @Override
    public Mono<Boolean> deleteTask(String taskId, String teamId) {
        log.info("Deleting task {} for team {}", taskId, teamId);
        
        return getTaskById(taskId, teamId)
                .flatMap(task -> {
                    // Remove task from agent
                    return agentRepository.findById(task.getAgentId())
                            .flatMap(agent -> {
                                agent.removeTask(taskId);
                                return agentRepository.save(agent);
                            })
                            .then(agentTaskRepository.deleteById(taskId))
                            .thenReturn(true);
                })
                .doOnSuccess(deleted -> log.info("Task {} deleted successfully", taskId))
                .doOnError(error -> log.error("Failed to delete task {}: {}", taskId, error.getMessage()));
    }
    
    @Override
    public Mono<AgentTask> setTaskEnabled(String taskId, String teamId, boolean enabled) {
        log.info("Setting task {} enabled={} for team {}", taskId, enabled, teamId);
        
        return getTaskById(taskId, teamId)
                .flatMap(task -> {
                    task.setEnabled(enabled);
                    task.setUpdatedBy("system");
                    task.onUpdate();
                    
                    return agentTaskRepository.save(task);
                })
                .doOnSuccess(updatedTask -> log.info("Task {} enabled={} successfully", taskId, enabled))
                .doOnError(error -> log.error("Failed to set task {} enabled={}: {}", taskId, enabled, error.getMessage()));
    }
    
    @Override
    public Mono<AgentTask> getTaskById(String taskId, String teamId) {
        return agentTaskRepository.findById(taskId)
                .flatMap(task -> getAgentById(task.getAgentId(), teamId)
                        .thenReturn(task))
                .switchIfEmpty(Mono.error(new RuntimeException("Task not found or access denied")));
    }
    
    @Override
    public Flux<AgentTask> getTasksByAgent(String agentId, String teamId) {
        return getAgentById(agentId, teamId)
                .thenMany(agentTaskRepository.findByAgentId(agentId));
    }
    
    @Override
    public Flux<AgentTask> getTasksByAgentAndStatus(String agentId, String teamId, AgentTaskStatus status) {
        return getAgentById(agentId, teamId)
                .thenMany(agentTaskRepository.findByAgentIdAndStatus(agentId, status));
    }
    
    // ==================== EXECUTION MANAGEMENT ====================
    
    @Override
    public Mono<AgentExecution> startTaskExecution(String agentId, String taskId, String teamId, String executedBy) {
        log.info("Starting execution of task {} for agent {} in team {}", taskId, agentId, teamId);
        
        return Mono.zip(
                getAgentById(agentId, teamId),
                getTaskById(taskId, teamId)
        ).flatMap(tuple -> {
            Agent agent = tuple.getT1();
            AgentTask task = tuple.getT2();
            
            // Validate task can be executed
            if (!task.isReadyToExecute()) {
                return Mono.error(new RuntimeException("Task is not ready to execute"));
            }
            
            if (!agent.canExecute()) {
                return Mono.error(new RuntimeException("Agent is not ready to execute"));
            }
            
            // Create execution record
            AgentExecution execution = AgentExecution.builder()
                    .executionId(UUID.randomUUID().toString())
                    .agentId(agentId)
                    .agentName(agent.getName())
                    .taskId(taskId)
                    .taskName(task.getName())
                    .teamId(teamId)
                    .routeIdentifier(agent.getRouteIdentifier())
                    .executionType("manual")
                    .triggerSource("user")
                    .scheduledAt(LocalDateTime.now())
                    .startedAt(LocalDateTime.now())
                    .status("RUNNING")
                    .result("UNKNOWN")
                    .executedBy(executedBy)
                    .executionEnvironment("production")
                    .maxRetries(task.getMaxRetries())
                    .build();
            
            execution.onCreate();
            
            // Update agent and task status
            agent.markAsRunning("Executing task: " + task.getName());
            task.markAsExecuting();
            
            return Mono.zip(
                    agentRepository.save(agent),
                    agentTaskRepository.save(task),
                    agentExecutionRepository.save(execution)
            ).thenReturn(execution);
        })
        .doOnSuccess(execution -> log.info("Task execution started: {}", execution.getExecutionId()))
        .doOnError(error -> log.error("Failed to start task execution: {}", error.getMessage()));
    }
    
    @Override
    public Mono<AgentExecution> executeTaskManually(String agentId, String taskId, String teamId, String executedBy) {
        return startTaskExecution(agentId, taskId, teamId, executedBy);
    }
    
    @Override
    public Mono<Boolean> cancelExecution(String executionId, String teamId, String cancelledBy) {
        log.info("Cancelling execution {} for team {} by {}", executionId, teamId, cancelledBy);
        
        return getExecutionById(executionId, teamId)
                .flatMap(execution -> {
                    execution.setStatus("CANCELLED");
                    execution.setResult("FAILURE");
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
        return getAgentById(agentId, teamId)
                .thenMany(agentExecutionRepository.findByAgentIdOrderByCreatedAtDesc(agentId)
                        .take(limit));
    }
    
    @Override
    public Flux<AgentExecution> getTaskExecutionHistory(String taskId, String teamId, int limit) {
        return getTaskById(taskId, teamId)
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
        return getAgentsByTeam(teamId)
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
        return getTaskById(taskId, teamId)
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
        return getAgentsByTeam(teamId)
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
                                        .filter(e -> "SUCCESS".equals(e.getResult()))
                                        .count();
                                long failedExecutions = executions.stream()
                                        .filter(e -> "FAILURE".equals(e.getResult()))
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
                    execution.setStatus("RUNNING");
                    execution.setResult("UNKNOWN");
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
                    agent.markAsIdle();
                    agent.setUpdatedBy(resetBy);
                    agent.onUpdate();
                    
                    return agentRepository.save(agent);
                })
                .doOnSuccess(resetAgent -> log.info("Agent {} error state reset successfully", agentId))
                .doOnError(error -> log.error("Failed to reset agent {} error state: {}", agentId, error.getMessage()));
    }
    
    @Override
    public Flux<Agent> getAgentsWithErrors(String teamId) {
        return getAgentsByTeam(teamId)
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
        
        // TODO: Implement workflow triggering logic
        // This will integrate with your existing workflow system
        
        return Mono.just("workflow_execution_id_" + System.currentTimeMillis());
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
        return getAgentsByTeam(teamId);
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
                .flatMap(agentId -> setAgentEnabled(agentId, teamId, enabled))
                .collectList()
                .doOnSuccess(agents -> log.info("Bulk operation completed for {} agents", agents.size()))
                .doOnError(error -> log.error("Bulk operation failed: {}", error.getMessage()));
    }
    
    @Override
    public Mono<List<Boolean>> bulkDeleteAgents(List<String> agentIds, String teamId) {
        log.info("Bulk deleting {} agents for team {}", agentIds.size(), teamId);
        
        return Flux.fromIterable(agentIds)
                .flatMap(agentId -> deleteAgent(agentId, teamId))
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
        return getAgentsByTeam(teamId)
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
        return getAgentsByTeam(teamId)
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