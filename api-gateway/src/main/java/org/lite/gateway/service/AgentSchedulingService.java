package org.lite.gateway.service;

import org.lite.gateway.entity.AgentTask;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

/**
 * Task-level scheduling service.
 * Each task can have its own scheduling configuration and state.
 */
public interface AgentSchedulingService {
    
    /**
     * Schedule a task for execution with cron expression
     */
    Mono<AgentTask> scheduleTask(String taskId, String cronExpression, String teamId);
    
    /**
     * Unschedule a task (remove cron scheduling)
     */
    Mono<AgentTask> unscheduleTask(String taskId, String teamId);
    
    /**
     * Get all tasks ready to run (for scheduler)
     */
    Flux<AgentTask> getTasksReadyToRun();
    
    /**
     * Get tasks ready to run for a specific agent
     */
    Flux<AgentTask> getTasksReadyToRunByAgent(String agentId);
    
    /**
     * Get tasks ready to run for a specific team
     */
    Flux<AgentTask> getTasksReadyToRunByTeam(String teamId);
    
    /**
     * Update next run time for a task
     */
    Mono<AgentTask> updateTaskNextRunTime(String taskId, LocalDateTime nextRun);
    
    /**
     * Update last run time for a task (called after execution)
     */
    Mono<AgentTask> updateTaskLastRunTime(String taskId, LocalDateTime lastRun);
    
} 