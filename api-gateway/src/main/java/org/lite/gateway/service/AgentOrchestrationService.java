package org.lite.gateway.service;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.AgentStatus;
import org.lite.gateway.enums.AgentTaskStatus;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface AgentOrchestrationService {
    
    // ==================== AGENT MANAGEMENT ====================
    
    /**
     * Create a new agent
     */
    Mono<Agent> createAgent(Agent agent, String teamId, String createdBy);
    
    /**
     * Update an existing agent
     */
    Mono<Agent> updateAgent(String agentId, Agent agentUpdates, String teamId, String updatedBy);
    
    /**
     * Delete an agent (soft delete by setting enabled to false)
     */
    Mono<Boolean> deleteAgent(String agentId, String teamId);
    
    /**
     * Enable/disable an agent
     */
    Mono<Agent> setAgentEnabled(String agentId, String teamId, boolean enabled);
    
    /**
     * Get agent by ID with team validation
     */
    Mono<Agent> getAgentById(String agentId, String teamId);
    
    /**
     * Get all agents for a team
     */
    Flux<Agent> getAgentsByTeam(String teamId);
    
    /**
     * Get agents by status for a team
     */
    Flux<Agent> getAgentsByTeamAndStatus(String teamId, AgentStatus status);
    
    // ==================== TASK MANAGEMENT ====================
    
    /**
     * Create a new task for an agent
     */
    Mono<AgentTask> createTask(String agentId, AgentTask task, String teamId, String createdBy);
    
    /**
     * Update an existing task
     */
    Mono<AgentTask> updateTask(String taskId, AgentTask taskUpdates, String teamId, String updatedBy);
    
    /**
     * Delete a task
     */
    Mono<Boolean> deleteTask(String taskId, String teamId);
    
    /**
     * Enable/disable a task
     */
    Mono<AgentTask> setTaskEnabled(String taskId, String teamId, boolean enabled);
    
    /**
     * Get task by ID with team validation
     */
    Mono<AgentTask> getTaskById(String taskId, String teamId);
    
    /**
     * Get all tasks for an agent
     */
    Flux<AgentTask> getTasksByAgent(String agentId, String teamId);
    
    /**
     * Get tasks by status for an agent
     */
    Flux<AgentTask> getTasksByAgentAndStatus(String agentId, String teamId, AgentTaskStatus status);
    
    // ==================== EXECUTION MANAGEMENT ====================
    
    /**
     * Start execution of an agent task
     */
    Mono<AgentExecution> startTaskExecution(String agentId, String taskId, String teamId, String executedBy);
    
    /**
     * Execute a task manually (bypass scheduling)
     */
    Mono<AgentExecution> executeTaskManually(String agentId, String taskId, String teamId, String executedBy);
    
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