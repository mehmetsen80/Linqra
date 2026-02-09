package org.lite.gateway.service;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentExecution;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;

public interface AgentMonitoringService {

    // ==================== HEALTH MONITORING ====================

    /**
     * Get health information for a specific agent
     */
    Mono<Map<String, Object>> getAgentHealth(String agentId);

    /**
     * Get health summary for all agents in a team
     */
    Mono<Map<String, Object>> getTeamAgentsHealth(String teamId);

    /**
     * Get agents that have errors (based on recent execution failures)
     */
    Flux<Agent> getAgentsWithErrors(String teamId);

    // ==================== PERFORMANCE MONITORING ====================

    /**
     * Get performance metrics for a specific agent
     */
    Mono<Map<String, Object>> getAgentPerformance(String agentId, LocalDateTime from, LocalDateTime to);

    /**
     * Get execution statistics for a team
     */
    Mono<Map<String, Object>> getTeamExecutionStats(String teamId, LocalDateTime from, LocalDateTime to);

    /**
     * Get execution statistics for a team with optional agent filtering
     */
    Mono<Map<String, Object>> getTeamExecutionStats(String teamId, LocalDateTime from, LocalDateTime to,
            String agentId);

    // ==================== RESOURCE MONITORING ====================

    /**
     * Get agent capabilities summary for a team
     */
    Mono<Map<String, Object>> getAgentCapabilitiesSummary(String teamId);

    /**
     * Get resource usage statistics for a team
     */
    Mono<Map<String, Object>> getTeamResourceUsage(String teamId);

    // ==================== EXECUTION MONITORING ====================

    /**
     * Get failed executions for a team
     */
    Flux<AgentExecution> getFailedExecutions(String teamId, int limit);

    /**
     * Get workflow execution status
     */
    Mono<Map<String, Object>> getWorkflowExecutionStatus(String workflowExecutionId);

    /**
     * Get task-level statistics for all tasks belonging to a specific agent
     */
    Mono<Map<String, Object>> getTaskStatisticsByAgent(String agentId, String teamId);
}