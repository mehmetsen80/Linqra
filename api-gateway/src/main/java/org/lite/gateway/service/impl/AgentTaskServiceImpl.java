package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.ExecutionResult;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.AgentExecutionService;
import org.lite.gateway.service.AgentTaskService;
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
                    task.onCreate();
                    return agentTaskRepository.save(task);
                })
                .doOnSuccess(savedTask -> log.info("Task '{}' created successfully with ID: {} (type: {})", 
                    savedTask.getName(), savedTask.getId(), savedTask.getTaskType()))
                .doOnError(error -> log.error("Failed to create task '{}': {}", task.getName(), error.getMessage()));
    }

    @Override
    public Mono<AgentTask> updateTask(String taskId, AgentTask taskUpdates, String updatedBy) {
        log.info("Updating task {}", taskId);
        return getTaskById(taskId)
                .flatMap(existingTask -> {
                    if (taskUpdates.getName() != null) existingTask.setName(taskUpdates.getName());
                    if (taskUpdates.getDescription() != null) existingTask.setDescription(taskUpdates.getDescription());
                    if (taskUpdates.getTaskType() != null) existingTask.setTaskType(taskUpdates.getTaskType());
                    if (taskUpdates.getPriority() != 0) existingTask.setPriority(taskUpdates.getPriority());
                    if (taskUpdates.getMaxRetries() > 0) existingTask.setMaxRetries(taskUpdates.getMaxRetries());
                    if (taskUpdates.getTimeoutMinutes() > 0) existingTask.setTimeoutMinutes(taskUpdates.getTimeoutMinutes());
                    // taskConfig field removed - use specific fields or linq_config instead
                    if (taskUpdates.getCronExpression() != null) existingTask.setCronExpression(taskUpdates.getCronExpression());
                    if (taskUpdates.getLinqConfig() != null) existingTask.setLinqConfig(taskUpdates.getLinqConfig());
                    if (taskUpdates.getApiConfig() != null) existingTask.setApiConfig(taskUpdates.getApiConfig());
                    if (taskUpdates.getScriptContent() != null) existingTask.setScriptContent(taskUpdates.getScriptContent());
                    if (taskUpdates.getScriptLanguage() != null) existingTask.setScriptLanguage(taskUpdates.getScriptLanguage());
                    if (taskUpdates.getExecutionTrigger() != null) existingTask.setExecutionTrigger(taskUpdates.getExecutionTrigger());
                    
                    existingTask.setUpdatedBy(updatedBy);
                    existingTask.onUpdate();
                    
                    // Validate updated configuration
                    if (!existingTask.isTaskTypeConfigurationValid()) {
                        return Mono.error(new IllegalArgumentException(
                            String.format("Invalid configuration for task type %s after update. Task: %s", 
                                existingTask.getTaskType(), existingTask.getName())));
                    }
                    
                    if (!existingTask.isExecutionTriggerValid()) {
                        return Mono.error(new IllegalArgumentException(
                            String.format("Invalid execution trigger configuration after update. Task: %s", 
                                existingTask.getName())));
                    }
                    
                    return agentTaskRepository.save(existingTask);
                })
                .doOnSuccess(updatedTask -> log.info("Task {} updated successfully (type: {})", taskId, updatedTask.getTaskType()))
                .doOnError(error -> log.error("Failed to update task {}: {}", taskId, error.getMessage()));
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
    public Mono<AgentTask> setTaskEnabled(String taskId, boolean enabled) {
        log.info("Setting task {} enabled={}", taskId, enabled);
        return getTaskById(taskId)
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
