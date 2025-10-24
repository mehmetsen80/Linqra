package org.lite.gateway.service.impl;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.ExecutionTrigger;

import org.lite.gateway.executor.WorkflowEmbeddedAgentTaskExecutor;
import org.lite.gateway.executor.WorkflowTriggerAgentTaskExecutor;
import org.lite.gateway.executor.WorkflowAdHocAgentTaskExecutor;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.service.AgentExecutionService;
import org.lite.gateway.service.ExecutionMonitoringService;
import org.lite.gateway.service.ExecutionQueueService;
import org.lite.gateway.dto.ExecutionProgressUpdate;
import org.springframework.stereotype.Service;


import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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
    private final WorkflowTriggerAgentTaskExecutor workflowTriggerExecutor;
    private final WorkflowEmbeddedAgentTaskExecutor workflowEmbeddedExecutor;
    private final WorkflowAdHocAgentTaskExecutor workflowAdhocExecutor;
    private final ExecutionMonitoringService executionMonitoringService;
    private final ExecutionQueueService executionQueueService;
    
    // ==================== EXECUTION MANAGEMENT ====================
    
    @Override
    public Mono<AgentExecution> startTaskExecution(String agentId, String taskId, String teamId, String executedBy) {
        log.info("Starting execution of task {} for agent {} in team {}", taskId, agentId, teamId);
        
        return Mono.zip(
                agentRepository.findById(agentId),
                agentTaskRepository.findById(taskId)
        ).flatMap(tuple -> {
            Agent agent = tuple.getT1();
            AgentTask task = tuple.getT2();
            
            // Validate task can be executed
            log.info("Task {} state: enabled={}, cronExpression={}, autoExecute={}, executionTrigger={}", 
                    taskId, task.isEnabled(), task.getCronExpression(), task.isAutoExecute(), task.getExecutionTrigger());
            
            if (!task.isReadyToExecute()) {
                log.error("Task {} is not ready to execute. enabled={}, cronExpression={}, autoExecute={}, executionTrigger={}", 
                        taskId, task.isEnabled(), task.getCronExpression(), task.isAutoExecute(), task.getExecutionTrigger());
                return Mono.error(new RuntimeException("Task is not ready to execute"));
            }
            
            // Validate execution trigger configuration
            if (!task.isExecutionTriggerValid()) {
                log.error("Task {} has invalid execution trigger configuration: {}", taskId, task.getExecutionTrigger());
                return Mono.error(new RuntimeException("Invalid execution trigger configuration"));
            }
            
            // Validate that manual execution is allowed for this trigger type
            if (task.getExecutionTrigger() == ExecutionTrigger.CRON && !task.isAutoExecute()) {
                log.error("Task {} is configured for CRON trigger but autoExecute is false", taskId);
                return Mono.error(new RuntimeException("CRON tasks must have autoExecute enabled"));
            }
            
            if (!agent.canExecute()) {
                return Mono.error(new RuntimeException("Agent is not ready to execute"));
            }
            
            // Create execution record
            String executionId = UUID.randomUUID().toString();
            AgentExecution execution = AgentExecution.builder()
                    .executionId(executionId)
                    .agentId(agentId)
                    .agentName(agent.getName())  // Set the agent name
                    .taskId(taskId)
                    .taskName(task.getName())
                    .teamId(teamId)
                    .executionType(ExecutionType.MANUAL)
                    .scheduledAt(LocalDateTime.now())
                    .startedAt(LocalDateTime.now())
                    .executedBy(executedBy)
                    .executionEnvironment("production")
                    .maxRetries(task.getMaxRetries())
                    .build();
            
            // Add to queue first, then execute after a delay
            return agentExecutionRepository.save(execution)
            .then(executionQueueService.addToQueue(executionId, agentId, agent.getName(), taskId, task.getName(), teamId, executedBy))
            .then(Mono.delay(java.time.Duration.ofSeconds(2))) // Wait 2 seconds to show queue
            .then(executionQueueService.markAsStarting(executionId))
            .then(Mono.delay(java.time.Duration.ofSeconds(1))) // Wait 1 more second for starting animation
            .then(executionQueueService.markAsStartedAndRemove(executionId))
            .then(sendExecutionStartedUpdate(execution, agent, task))
            .then(executeWorkflow(execution, task, agent))
            .thenReturn(execution)  // Return the execution object immediately
            .onErrorResume(error -> {
                // ERROR CASE - Only update execution status
                log.error("Task execution failed: {}", error.getMessage());
                execution.markAsFailed(error.getMessage(), "Workflow execution failed");
                
                return agentExecutionRepository.save(execution)
                .then(sendExecutionFailedUpdate(execution, agent, task, error.getMessage()))
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
    
    @Override
    public Mono<AgentExecution> getExecutionById(String executionId) {
        return agentExecutionRepository.findById(executionId)
                .doOnSuccess(execution -> log.debug("Found execution: {} for executionId: {}", 
                    execution != null ? execution.getExecutionId() : "null", executionId))
                .doOnError(error -> log.error("Error fetching execution by id {}: {}", executionId, error.getMessage()))
                .switchIfEmpty(Mono.error(new RuntimeException("Execution not found with id: " + executionId)));
    }
    
    @Override
    public Flux<AgentExecution> getRecentExecutions(String teamId, int limit) {
        return agentExecutionRepository.findByTeamIdOrderByCreatedAtDesc(teamId)
                .take(limit)
                .doOnNext(execution -> log.debug("Found recent execution: {} for team: {}", 
                    execution.getExecutionId(), teamId))
                .doOnError(error -> log.error("Error fetching recent executions for team {}: {}", teamId, error.getMessage()));
    }
    
    // ==================== WORKFLOW INTEGRATION ====================
    
    /**
     * Execute the task based on its type using dedicated executors
     */
    private Mono<Void> executeWorkflow(AgentExecution execution, AgentTask task, Agent agent) {
        log.info("Executing task: {} (type: {}, execution: {})", task.getName(), task.getTaskType(), execution.getExecutionId());
        
        return switch (task.getTaskType()) {
            case WORKFLOW_EMBEDDED -> workflowEmbeddedExecutor.executeTask(execution, task, agent);
            case WORKFLOW_TRIGGER -> workflowTriggerExecutor.executeTask(execution, task, agent);
            default -> Mono.error(new IllegalArgumentException("Unsupported task type: " + task.getTaskType()));
        };
    }
    
    @Override
    public Mono<Object> executeAdhocTask(AgentTask agentTask, String teamId, String executedBy) {
        log.info("Executing ad-hoc task: {} for team: {}", agentTask.getName(), teamId);
        if (agentTask.getTaskType() == null) {
            return Mono.error(new IllegalArgumentException("taskType is required for ad-hoc execution"));
        }
        if (agentTask.getTaskType() != org.lite.gateway.enums.AgentTaskType.WORKFLOW_EMBEDDED_ADHOC) {
            return Mono.error(new IllegalArgumentException("Only WORKFLOW_EMBEDDED_ADHOC tasks are supported for ad-hoc execution"));
        }
        return workflowAdhocExecutor.executeAdhocTask(agentTask, teamId, executedBy, null);
    }
    
    // ==================== EXECUTION MONITORING ====================
    
    private Mono<Void> sendExecutionStartedUpdate(AgentExecution execution, Agent agent, AgentTask task) {
        // Calculate duration from start time to now (both in UTC)
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        long durationMs = java.time.Duration.between(execution.getStartedAt(), now).toMillis();
        
        log.info("📊 STARTED message - startedAt: {}, now: {}, durationMs: {}", 
            execution.getStartedAt(), now, durationMs);
        
        ExecutionProgressUpdate update = ExecutionProgressUpdate.builder()
                .executionId(execution.getExecutionId())
                .agentId(agent.getId())
                .agentName(agent.getName())
                .taskId(task.getId())
                .taskName(task.getName())
                .teamId(execution.getTeamId())
                .status("STARTED")
                .currentStep(0)
                .totalSteps(getTotalSteps(task))
                .startedAt(execution.getStartedAt())
                .lastUpdatedAt(now)
                .executionDurationMs(durationMs)
                .build();
        
        return executionMonitoringService.sendExecutionStarted(update);
    }
    
    private Mono<Void> sendExecutionFailedUpdate(AgentExecution execution, Agent agent, AgentTask task, String errorMessage) {
        // Calculate duration from start time to now (both in UTC)
        java.time.LocalDateTime now = java.time.LocalDateTime.now(java.time.ZoneOffset.UTC);
        long durationMs = java.time.Duration.between(execution.getStartedAt(), now).toMillis();
        
        ExecutionProgressUpdate update = ExecutionProgressUpdate.builder()
                .executionId(execution.getExecutionId())
                .agentId(agent.getId())
                .agentName(agent.getName())
                .taskId(task.getId())
                .taskName(task.getName())
                .teamId(execution.getTeamId())
                .status("FAILED")
                .currentStep(0)
                .totalSteps(getTotalSteps(task))
                .startedAt(execution.getStartedAt())
                .lastUpdatedAt(now)
                .executionDurationMs(durationMs)
                .errorMessage(errorMessage)
                .build();
        
        return executionMonitoringService.sendExecutionFailed(update, errorMessage);
    }
    
    private int getTotalSteps(AgentTask task) {
        try {
            // Extract workflow steps from the task's linq_config
            Map<String, Object> linqConfig = task.getLinqConfig();
            if (linqConfig != null && linqConfig.containsKey("query")) {
                Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
                if (query.containsKey("workflow")) {
                    List<?> workflow = (List<?>) query.get("workflow");
                    return workflow.size();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to extract step count from task {}, using default: {}", task.getId(), e.getMessage());
        }
        return 1; // Default fallback
    }
}
