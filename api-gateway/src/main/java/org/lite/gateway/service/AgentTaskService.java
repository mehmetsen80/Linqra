package org.lite.gateway.service;

import org.lite.gateway.entity.AgentTask;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AgentTaskService {
    Mono<AgentTask> createTask(AgentTask task);
    Mono<AgentTask> updateTask(String taskId, AgentTask taskUpdates, String teamId, String updatedBy);
    Mono<Boolean> deleteTask(String taskId, String teamId);
    Mono<AgentTask> setTaskEnabled(String taskId, String teamId, boolean enabled);
    Mono<AgentTask> getTaskById(String taskId, String teamId);
    Mono<AgentTask> getTaskByIdInternal(String taskId);
    Flux<AgentTask> getTasksByAgent(String agentId, String teamId);
    // NOTE: getTasksByAgentAndStatus removed - task status now managed by AgentExecution
}
