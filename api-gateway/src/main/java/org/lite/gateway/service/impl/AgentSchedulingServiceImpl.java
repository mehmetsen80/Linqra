package org.lite.gateway.service.impl;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.service.AgentSchedulingService;

import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class AgentSchedulingServiceImpl implements AgentSchedulingService {
    
    private final AgentTaskRepository agentTaskRepository;
    private final AgentRepository agentRepository;
    
    @Override
    public Mono<AgentTask> scheduleTask(String taskId, String cronExpression, String teamId) {
        log.info("Scheduling task {} with cron: {} for team {}", taskId, cronExpression, teamId);
        
        return agentTaskRepository.findById(taskId)
                .flatMap(task -> agentRepository.findById(task.getAgentId())
                        .filter(agent -> teamId.equals(agent.getTeamId()))
                        .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                        .thenReturn(task))
                .flatMap(task -> {
                    task.setCronExpression(cronExpression);
                    task.setAutoExecute(true);
                    task.setUpdatedBy("system");
                    task.onUpdate();
                    
                    return agentTaskRepository.save(task);
                })
                .doOnSuccess(scheduledTask -> log.info("Task {} scheduled successfully", taskId))
                .doOnError(error -> log.error("Failed to schedule task {}: {}", taskId, error.getMessage()));
    }
    
    @Override
    public Mono<AgentTask> unscheduleTask(String taskId, String teamId) {
        log.info("Unschedule task {} for team {}", taskId, teamId);
        
        return agentTaskRepository.findById(taskId)
                .flatMap(task -> agentRepository.findById(task.getAgentId())
                        .filter(agent -> teamId.equals(agent.getTeamId()))
                        .switchIfEmpty(Mono.error(new RuntimeException("Agent not found or access denied")))
                        .thenReturn(task))
                .flatMap(task -> {
                    task.setCronExpression(null);
                    task.setAutoExecute(false);
                    task.setUpdatedBy("system");
                    task.onUpdate();
                    
                    return agentTaskRepository.save(task);
                })
                .doOnSuccess(unscheduledTask -> log.info("Task {} unscheduled successfully", taskId))
                .doOnError(error -> log.error("Failed to unschedule task {}: {}", taskId, error.getMessage()));
    }
    
    @Override
    public Flux<AgentTask> getTasksReadyToRun() {
        return agentTaskRepository.findTasksReadyToRun(LocalDateTime.now());
    }
    
    @Override
    public Flux<AgentTask> getTasksReadyToRunByAgent(String agentId) {
        return agentTaskRepository.findTasksReadyToRunByAgent(agentId, LocalDateTime.now());
    }
    
    @Override
    public Flux<AgentTask> getTasksReadyToRunByTeam(String teamId) {
        // This requires joining with agents, so we'll implement it in the service layer
        return agentRepository.findByTeamId(teamId)
                .flatMap(agent -> agentTaskRepository.findTasksReadyToRunByAgent(agent.getId(), LocalDateTime.now()));
    }
    
    @Override
    public Mono<AgentTask> updateTaskNextRunTime(String taskId, LocalDateTime nextRun) {
        return agentTaskRepository.findById(taskId)
                .flatMap(task -> {
                    task.setNextRun(nextRun);
                    task.setUpdatedBy("system");
                    task.onUpdate();
                    
                    return agentTaskRepository.save(task);
                });
    }
    
    @Override
    public Mono<AgentTask> updateTaskLastRunTime(String taskId, LocalDateTime lastRun) {
        return agentTaskRepository.findById(taskId)
                .flatMap(task -> {
                    task.setLastRun(lastRun);
                    task.setUpdatedBy("system");
                    task.onUpdate();
                    
                    return agentTaskRepository.save(task);
                });
    }
    
} 