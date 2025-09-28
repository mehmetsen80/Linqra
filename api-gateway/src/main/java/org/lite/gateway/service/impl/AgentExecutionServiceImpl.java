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
import org.springframework.stereotype.Service;


import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
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
            AgentExecution execution = AgentExecution.builder()
                    .executionId(UUID.randomUUID().toString())
                    .agentId(agentId)
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
            
            return agentExecutionRepository.save(execution)
            .then(executeWorkflow(execution, task, agent))
            .thenReturn(execution)  // Return the execution object immediately
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
}
