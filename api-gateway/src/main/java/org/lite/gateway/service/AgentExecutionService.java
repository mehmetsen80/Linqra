package org.lite.gateway.service;

import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.entity.AgentTask;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface AgentExecutionService {
    
    // ==================== EXECUTION MANAGEMENT ====================
    
    /**
     * Start execution of an agent task
     */
    Mono<AgentExecution> startTaskExecution(String agentId, String taskId, String teamId, String executedBy);
    
    /**
     * Cancel a running execution
     */
    Mono<Boolean> cancelExecution(String executionId, String teamId, String cancelledBy);
    
    /**
     * Get execution history for an agent
     */
    Flux<AgentExecution> getExecutionHistory(String agentId, int limit);
    
    /**
     * Get execution history for a task
     */
    Flux<AgentExecution> getTaskExecutionHistory(String taskId, int limit);
    
    /**
     * Get executions by status for a team
     */
    Flux<AgentExecution> getExecutionsByTeamAndStatus(String teamId, String status, int limit);
    
    Mono<AgentExecution> getExecutionById(String executionId);
    
    /**
     * Get recent executions for a team
     */
    Flux<AgentExecution> getRecentExecutions(String teamId, int limit);
    
    /**
     * Execute an ad-hoc task without storing it in database or creating execution records
     */
    Mono<Object> executeAdhocTask(AgentTask agentTask, String teamId, String executedBy);
    
} 