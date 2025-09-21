package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.ExecutionResult;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.AgentExecutionService;
import org.lite.gateway.service.AgentTaskService;
import org.lite.gateway.service.CronDescriptionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentTaskServiceImpl implements AgentTaskService {

    private final AgentRepository agentRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final AgentExecutionService agentExecutionService;
    private final CronDescriptionService cronDescriptionService;

    @Override
    public Mono<AgentTask> createTask(AgentTask task) {
        log.info("Creating task '{}' for agent {} (type: {})", task.getName(), task.getAgentId(), task.getTaskType());
        
        // Validate task type configuration
        if (!task.isTaskTypeConfigurationValid()) {
            return Mono.error(new IllegalArgumentException(
                String.format("Invalid configuration for task type %s. Task: %s", 
                    task.getTaskType(), task.getName())));
        }
        
        // Validate execution trigger configuration
        if (!task.isExecutionTriggerValid()) {
            return Mono.error(new IllegalArgumentException(
                String.format("Invalid execution trigger configuration for task: %s", task.getName())));
        }
        
        return agentRepository.findById(task.getAgentId())
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found")))
                .flatMap(agent -> {
                    task.setUpdatedBy(task.getCreatedBy());
                    
                    // Generate cron description if cron expression is provided
                    if (task.getCronExpression() != null && !task.getCronExpression().trim().isEmpty()) {
                        return Mono.just(cronDescriptionService.getCronDescription(task.getCronExpression()))
                                .flatMap(description -> {
                                    task.setCronDescription(description);
                                    log.info("Generated cron description for task '{}': {}", task.getName(), description);
                                    return agentTaskRepository.save(task);
                                });
                    } else {
                        return agentTaskRepository.save(task);
                    }
                })
                .doOnSuccess(savedTask -> log.info("Task '{}' created successfully with ID: {} (type: {})", 
                    savedTask.getName(), savedTask.getId(), savedTask.getTaskType()))
                .doOnError(error -> log.error("Failed to create task '{}': {}", task.getName(), error.getMessage()));
    }

    @Override
    public Mono<Boolean> deleteTask(String taskId) {
        log.info("Deleting task {}", taskId);
        return getTaskById(taskId)
                .flatMap(task -> agentTaskRepository.deleteById(taskId)
                        .thenReturn(true))
                .doOnSuccess(deleted -> log.info("Task {} deleted successfully", taskId))
                .doOnError(error -> log.error("Failed to delete task {}: {}", taskId, error.getMessage()));
    }

    @Override
    public Mono<AgentTask> setTaskEnabled(String taskId, boolean enabled, String updatedBy) {
        log.info("Setting task {} enabled={} by user {}", taskId, enabled, updatedBy);
        return getTaskById(taskId)
                .flatMap(task -> {
                    task.setEnabled(enabled);
                    task.setUpdatedBy(updatedBy);
                    return agentTaskRepository.save(task);
                })
                .doOnSuccess(updatedTask -> log.info("Task {} enabled={} successfully by user {}", taskId, enabled, updatedBy))
                .doOnError(error -> log.error("Failed to set task {} enabled={} by user {}: {}", taskId, enabled, updatedBy, error.getMessage()));
    }

    @Override
    public Mono<AgentTask> getTaskById(String taskId) {
        return agentTaskRepository.findById(taskId)
                .switchIfEmpty(Mono.error(new RuntimeException("Task not found")));
    }

    @Override
    public Flux<AgentTask> getTasksByAgent(String agentId, String teamId) {
        return agentRepository.findById(agentId)
                .thenMany(agentTaskRepository.findByAgentId(agentId));
    }

    // NOTE: getTasksByAgentAndStatus method removed - task status now managed by AgentExecution
    // Use AgentExecutionRepository to find tasks by execution status
    
    @Override
    public Mono<Map<String, Object>> getTaskStatistics(String taskId, String teamId) {
        log.info("Getting statistics for task {} in team {}", taskId, teamId);
        
        return getTaskById(taskId)
                .flatMap(task -> {
                    // Get all executions for this task to calculate statistics
                    return agentExecutionService.getTaskExecutionHistory(taskId, Integer.MAX_VALUE)
                            .collectList()
                            .map(executions -> {
                                // Calculate execution statistics
                                long totalExecutions = executions.size();
                                long successfulExecutions = executions.stream()
                                        .filter(e -> e.getResult() != null && e.getResult() == ExecutionResult.SUCCESS)
                                        .count();
                                long failedExecutions = executions.stream()
                                        .filter(e -> e.getResult() != null && e.getResult() == ExecutionResult.FAILURE)
                                        .count();
                                
                                double successRate = totalExecutions > 0 ? 
                                        (double) successfulExecutions / totalExecutions * 100 : 0.0;
                                
                                // Calculate average execution time (only for completed executions)
                                double averageExecutionTime = executions.stream()
                                        .filter(e -> e.getExecutionDurationMs() != null && e.getExecutionDurationMs() > 0)
                                        .mapToLong(e -> e.getExecutionDurationMs())
                                        .average()
                                        .orElse(0.0);
                                
                                // Find last execution time
                                LocalDateTime lastExecuted = executions.stream()
                                        .filter(e -> e.getCompletedAt() != null)
                                        .map(e -> e.getCompletedAt())
                                        .max(LocalDateTime::compareTo)
                                        .orElse(null);
                                
                                Map<String, Object> stats = Map.of(
                                        "taskId", taskId,
                                        "taskName", task.getName(),
                                        "enabled", task.isEnabled(),
                                        "taskType", task.getTaskType().toString(),
                                        "totalExecutions", totalExecutions,
                                        "successfulExecutions", successfulExecutions,
                                        "failedExecutions", failedExecutions,
                                        "successRate", Math.round(successRate * 100.0) / 100.0, // Round to 2 decimal places
                                        "averageExecutionTime", Math.round(averageExecutionTime * 100.0) / 100.0, // Round to 2 decimal places
                                        "lastExecuted", lastExecuted
                                );
                                return stats;
                            });
                });
    }
}
