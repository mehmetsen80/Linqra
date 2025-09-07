package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.AgentTaskService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentTaskServiceImpl implements AgentTaskService {

    private final AgentRepository agentRepository;
    private final AgentTaskRepository agentTaskRepository;

    @Override
    public Mono<AgentTask> createTask(AgentTask task) {
        log.info("Creating task '{}' for agent {}", task.getName(), task.getAgentId());
        return agentRepository.findById(task.getAgentId())
                .switchIfEmpty(Mono.error(new RuntimeException("Agent not found")))
                .flatMap(agent -> {
                    task.setUpdatedBy(task.getCreatedBy());
                    task.onCreate();
                    return agentTaskRepository.save(task)
                            .flatMap(savedTask -> {
                                agent.addTask(savedTask.getId());
                                return agentRepository.save(agent)
                                        .thenReturn(savedTask);
                            });
                })
                .doOnSuccess(savedTask -> log.info("Task '{}' created successfully with ID: {}", savedTask.getName(), savedTask.getId()))
                .doOnError(error -> log.error("Failed to create task '{}': {}", task.getName(), error.getMessage()));
    }

    @Override
    public Mono<AgentTask> updateTask(String taskId, AgentTask taskUpdates, String teamId, String updatedBy) {
        log.info("Updating task {} for team {}", taskId, teamId);
        return getTaskById(taskId, teamId)
                .flatMap(existingTask -> {
                    if (taskUpdates.getName() != null) existingTask.setName(taskUpdates.getName());
                    if (taskUpdates.getDescription() != null) existingTask.setDescription(taskUpdates.getDescription());
                    if (taskUpdates.getTaskType() != null) existingTask.setTaskType(taskUpdates.getTaskType());
                    if (taskUpdates.getPriority() != 0) existingTask.setPriority(taskUpdates.getPriority());
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
                .flatMap(task -> agentRepository.findById(task.getAgentId())
                        .flatMap(agent -> {
                            agent.removeTask(taskId);
                            return agentRepository.save(agent);
                        })
                        .then(agentTaskRepository.deleteById(taskId))
                        .thenReturn(true))
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
                .flatMap(task -> agentRepository.findById(task.getAgentId()).thenReturn(task))
                .switchIfEmpty(Mono.error(new RuntimeException("Task not found or access denied")));
    }

    @Override
    public Mono<AgentTask> getTaskByIdInternal(String taskId) {
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
}
