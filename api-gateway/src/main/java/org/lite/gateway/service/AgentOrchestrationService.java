package org.lite.gateway.service;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.AgentTaskStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.web.server.ServerWebExchange;

public interface AgentOrchestrationService {
    
    // ==================== AGENT MANAGEMENT ====================
    

    // ==================== TASK MANAGEMENT ====================
    // Task CRUD operations have been moved to AgentTaskService
    
    // ==================== EXECUTION MANAGEMENT ====================
    
    /**
     * Start execution of an agent task
     */
    Mono<AgentExecution> startTaskExecution(String agentId, String taskId, String teamId, String executedBy, ServerWebExchange exchange);
    
    /**
     * Execute a task manually (bypass scheduling)
     */
    Mono<AgentExecution> executeTaskManually(String agentId, String taskId, String teamId, String executedBy, ServerWebExchange exchange);
    
    /**
     * Cancel a running execution
     */
    Mono<Boolean> cancelExecution(String executionId, String teamId, String cancelledBy);
    
    /**
     * Get execution by ID with team validation
     */
    Mono<AgentExecution> getExecutionById(String executionId, String teamId);
    
    /**
     * Get execution history for an agent
     */
    Flux<AgentExecution> getExecutionHistory(String agentId, String teamId, int limit);
    
    /**
     * Get execution history for a task
     */
    Flux<AgentExecution> getTaskExecutionHistory(String taskId, String teamId, int limit);
    
    /**
     * Get executions by status for a team
     */
    Flux<AgentExecution> getExecutionsByTeamAndStatus(String teamId, String status, int limit);
    
    // ==================== SCHEDULING ====================
    
    /**
     * Schedule an agent for execution
     */
    Mono<Agent> scheduleAgent(String agentId, String cronExpression, String teamId);
    
    /**
     * Unschedule an agent
     */
    Mono<Agent> unscheduleAgent(String agentId, String teamId);
    
    /**
     * Get agents ready to run (for scheduler)
     */
    Flux<Agent> getAgentsReadyToRun();
    
    /**
     * Get agents ready to run for a specific team
     */
    Flux<Agent> getAgentsReadyToRunByTeam(String teamId);
    
    /**
     * Update next run time for an agent
     */
    Mono<Agent> updateNextRunTime(String agentId, LocalDateTime nextRun);
    
    // ==================== MONITORING & HEALTH ====================
    
    /**
     * Get agent health status
     */
    Mono<Map<String, Object>> getAgentHealth(String agentId, String teamId);
    
    /**
     * Get team agents health summary
     */
    Mono<Map<String, Object>> getTeamAgentsHealth(String teamId);
    
    /**
     * Get agent performance metrics
     */
    Mono<Map<String, Object>> getAgentPerformance(String agentId, String teamId, LocalDateTime from, LocalDateTime to);
    
    /**
     * Get task performance metrics
     */
    Mono<Map<String, Object>> getTaskPerformance(String taskId, String teamId, LocalDateTime from, LocalDateTime to);
    
    /**
     * Get execution statistics for a team
     */
    Mono<Map<String, Object>> getTeamExecutionStats(String teamId, LocalDateTime from, LocalDateTime to);
    
    // ==================== ERROR HANDLING & RECOVERY ====================
    
    /**
     * Retry a failed execution
     */
    Mono<AgentExecution> retryExecution(String executionId, String teamId, String retriedBy);
    
    /**
     * Reset agent error state
     */
    Mono<Agent> resetAgentError(String agentId, String teamId, String resetBy);
    
    /**
     * Get agents with errors for a team
     */
    Flux<Agent> getAgentsWithErrors(String teamId);
    
    /**
     * Get failed executions for a team
     */
    Flux<AgentExecution> getFailedExecutions(String teamId, int limit);
    
    // ==================== WORKFLOW INTEGRATION ====================
    
    /**
     * Trigger a workflow from agent execution
     */
    Mono<String> triggerWorkflow(String workflowId, Map<String, Object> parameters, String teamId);
    
    /**
     * Get workflow execution status
     */
    Mono<Map<String, Object>> getWorkflowExecutionStatus(String workflowExecutionId, String teamId);
    
    // ==================== TEAM & PERMISSIONS ====================
    
    /**
     * Validate team has access to agent
     */
    Mono<Boolean> validateTeamAccess(String agentId, String teamId);
    
    /**
     * Get agents accessible by team
     */
    Flux<Agent> getAccessibleAgents(String teamId);
    
    /**
     * Transfer agent ownership to another team
     */
    Mono<Agent> transferAgentOwnership(String agentId, String fromTeamId, String toTeamId, String transferredBy);
    
    // ==================== BULK OPERATIONS ====================
    
    /**
     * Bulk enable/disable agents
     */
    Mono<List<Agent>> bulkSetAgentsEnabled(List<String> agentIds, String teamId, boolean enabled, String updatedBy);
    
    /**
     * Bulk delete agents
     */
    Mono<List<Boolean>> bulkDeleteAgents(List<String> agentIds, String teamId);
    
    /**
     * Bulk schedule agents
     */
    Mono<List<Agent>> bulkScheduleAgents(List<String> agentIds, String cronExpression, String teamId);
    
    // ==================== UTILITY METHODS ====================
    
    /**
     * Validate agent configuration
     */
    Mono<Map<String, Object>> validateAgentConfiguration(Agent agent);
    
    /**
     * Validate task configuration
     */
    Mono<Map<String, Object>> validateTaskConfiguration(AgentTask task);
    
    /**
     * Get agent capabilities summary
     */
    Mono<Map<String, Object>> getAgentCapabilitiesSummary(String teamId);
    
    /**
     * Get team resource usage
     */
    Mono<Map<String, Object>> getTeamResourceUsage(String teamId);
} 