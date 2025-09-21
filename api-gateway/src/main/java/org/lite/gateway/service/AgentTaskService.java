package org.lite.gateway.service;

import org.lite.gateway.entity.AgentTask;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Map;

/**
 * Service interface for managing agent tasks.
 * 
 * This service provides CRUD operations and management functionality for agent tasks,
 * including task creation, updates, deletion, and statistics calculation.
 * Task execution status is managed separately by AgentExecution entities.
 */
public interface AgentTaskService {
    
    /**
     * Create a new agent task.
     * 
     * @param task The task entity to create (must include agentId and basic task information)
     * @return Mono containing the created task with generated ID and timestamps
     * @throws RuntimeException if the associated agent is not found
     */
    Mono<AgentTask> createTask(AgentTask task);
    
    /**
     * Delete an agent task and remove it from the associated agent.
     * 
     * This operation will also remove the task reference from the agent's task list
     * and permanently delete the task from the database.
     * 
     * @param taskId The unique identifier of the task to delete
     * @return Mono containing true if deletion was successful, false otherwise
     * @throws RuntimeException if the task is not found
     */
    Mono<Boolean> deleteTask(String taskId);
    
    /**
     * Enable or disable an agent task.
     * 
     * Disabled tasks will not be executed automatically or manually until re-enabled.
     * The task's updatedAt timestamp will be automatically set.
     * 
     * @param taskId The unique identifier of the task to enable/disable
     * @param enabled true to enable the task, false to disable it
     * @param updatedBy The username of the user performing the update
     * @return Mono containing the updated task with new enabled status
     * @throws RuntimeException if the task is not found
     */
    Mono<AgentTask> setTaskEnabled(String taskId, boolean enabled, String updatedBy);
    
    /**
     * Retrieve an agent task by its unique identifier.
     * 
     * @param taskId The unique identifier of the task to retrieve
     * @return Mono containing the task if found
     * @throws RuntimeException if the task is not found
     */
    Mono<AgentTask> getTaskById(String taskId);
    
    /**
     * Retrieve all tasks associated with a specific agent.
     * 
     * This method first verifies that the agent exists, then returns all tasks
     * that belong to that agent.
     * 
     * @param agentId The unique identifier of the agent
     * @param teamId The team ID for access validation (currently for logging only)
     * @return Flux containing all tasks for the specified agent
     * @throws RuntimeException if the agent is not found
     */
    Flux<AgentTask> getTasksByAgent(String agentId, String teamId);
    
    // NOTE: getTasksByAgentAndStatus removed - task status now managed by AgentExecution
    
    /**
     * Get comprehensive statistics for a specific task including execution metrics.
     * 
     * This method calculates real-time statistics based on all execution records
     * for the specified task, including success rates, execution times, and counts.
     * 
     * Statistics returned:
     * - taskId: The task identifier
     * - taskName: The task name
     * - enabled: Whether the task is currently enabled
     * - taskType: The type of task (SCHEDULED, MANUAL, etc.)
     * - totalExecutions: Total number of task executions
     * - successfulExecutions: Number of successful executions
     * - failedExecutions: Number of failed executions  
     * - successRate: Success percentage (0-100, rounded to 2 decimal places)
     * - averageExecutionTime: Average execution time in milliseconds (rounded to 2 decimal places)
     * - lastExecuted: Timestamp of the most recent completed execution (null if none)
     * 
     * @param taskId The unique identifier of the task
     * @param teamId The team ID for access validation
     * @return Mono containing a map with comprehensive task statistics
     * @throws RuntimeException if the task is not found or access is denied
     */
    Mono<Map<String, Object>> getTaskStatistics(String taskId, String teamId);
}
