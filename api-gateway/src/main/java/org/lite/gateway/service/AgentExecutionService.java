package org.lite.gateway.service;

import org.lite.gateway.entity.AgentExecution;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.web.server.ServerWebExchange;

public interface AgentExecutionService {
    
    // ==================== EXECUTION MANAGEMENT ====================
    
    /**
     * Start execution of an agent task
     */
    Mono<AgentExecution> startTaskExecution(String agentId, String taskId, String teamId, String executedBy, ServerWebExchange exchange);
    
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
    
} 