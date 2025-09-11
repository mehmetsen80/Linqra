package org.lite.gateway.repository;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.AgentTaskType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentTaskRepository extends ReactiveMongoRepository<AgentTask, String> {
    
    // Basic CRUD operations are inherited from ReactiveMongoRepository
    
    // Find tasks by agent
    Flux<AgentTask> findByAgentId(String agentId);
    
    // NOTE: Task status is now managed by AgentExecution, not AgentTask
    // Use AgentExecutionRepository to find tasks by execution status
    
    // Find tasks by agent and type
    Flux<AgentTask> findByAgentIdAndTaskType(String agentId, AgentTaskType taskType);
    
    // Find tasks by team (via agent)
    @Query("{'agentId': {$in: ?0}}")
    Flux<AgentTask> findByAgentIds(List<String> agentIds);
    
    // NOTE: Task status removed - use AgentExecutionRepository for execution status
    
    // Find enabled tasks
    Flux<AgentTask> findByEnabledTrue();
    
    // Find enabled tasks by agent
    Flux<AgentTask> findByAgentIdAndEnabledTrue(String agentId);
    
    // Find tasks by priority
    Flux<AgentTask> findByPriority(int priority);
    
    // Find tasks by agent and priority
    Flux<AgentTask> findByAgentIdAndPriority(String agentId, int priority);
    
    // Find tasks by priority range
    Flux<AgentTask> findByPriorityBetween(int minPriority, int maxPriority);
    
    // Find tasks by agent and priority range
    Flux<AgentTask> findByAgentIdAndPriorityBetween(String agentId, int minPriority, int maxPriority);
    
    // Find tasks by type
    Flux<AgentTask> findByTaskType(AgentTaskType taskType);
    
    // Find scheduled tasks (have cron expression and auto-execute enabled)
    Flux<AgentTask> findByCronExpressionIsNotNullAndAutoExecuteTrue();
    
    // Find scheduled tasks by agent
    Flux<AgentTask> findByAgentIdAndCronExpressionIsNotNullAndAutoExecuteTrue(String agentId);
    
    // Find tasks by implementation type
    Flux<AgentTask> findByImplementationType(String implementationType);
    
    // Find tasks by agent and implementation type
    Flux<AgentTask> findByAgentIdAndImplementationType(String agentId, String implementationType);
    
    // NOTE: Capabilities removed from AgentTask - capabilities are managed at Agent level
    // Use AgentRepository to find agents by capability, then get their tasks
    
    // Find tasks by name (case-insensitive)
    @Query("{'name': {$regex: ?0, $options: 'i'}}")
    Flux<AgentTask> findByNameContainingIgnoreCase(String name);
    
    // Find tasks by agent and name (case-insensitive)
    @Query("{'agentId': ?0, 'name': {$regex: ?1, $options: 'i'}}")
    Flux<AgentTask> findByAgentIdAndNameContainingIgnoreCase(String agentId, String name);
    
    // Find tasks by description (case-insensitive)
    @Query("{'description': {$regex: ?0, $options: 'i'}}")
    Flux<AgentTask> findByDescriptionContainingIgnoreCase(String description);
    
    // Find tasks by agent and description (case-insensitive)
    @Query("{'agentId': ?0, 'description': {$regex: ?1, $options: 'i'}}")
    Flux<AgentTask> findByAgentIdAndDescriptionContainingIgnoreCase(String agentId, String description);
    
    // Dependencies-related methods removed - handled by orchestration layer
    
    // Find tasks by created by
    Flux<AgentTask> findByCreatedBy(String createdBy);
    
    // Find tasks by agent and created by
    Flux<AgentTask> findByAgentIdAndCreatedBy(String agentId, String createdBy);
    
    // Find tasks created after specific date
    Flux<AgentTask> findByCreatedAtAfter(LocalDateTime date);
    
    // Find tasks by agent created after specific date
    Flux<AgentTask> findByAgentIdAndCreatedAtAfter(String agentId, LocalDateTime date);
    
    // Find tasks updated after specific date
    Flux<AgentTask> findByUpdatedAtAfter(LocalDateTime date);
    
    // Find tasks by agent updated after specific date
    Flux<AgentTask> findByAgentIdAndUpdatedAtAfter(String agentId, LocalDateTime date);
    
    // NOTE: lastExecuted field removed - use AgentExecutionRepository for execution history
    
    // NOTE: status and lastError fields removed - use AgentExecutionRepository for execution errors
    
    // NOTE: nextExecution field removed - calculate from cron expressions if needed
    
    // Count tasks by agent
    Mono<Long> countByAgentId(String agentId);
    
    // Count enabled tasks by agent
    Mono<Long> countByAgentIdAndEnabledTrue(String agentId);
    
    // NOTE: status field removed - use AgentExecutionRepository for execution status counts
    
    // Count tasks by type
    Mono<Long> countByTaskType(AgentTaskType taskType);
    
    // Count tasks by agent and type
    Mono<Long> countByAgentIdAndTaskType(String agentId, AgentTaskType taskType);
    
    // Check if task exists by name and agent
    Mono<Boolean> existsByNameAndAgentId(String name, String agentId);
    
    // Check if task exists by implementation target and agent
    Mono<Boolean> existsByImplementationTargetAndAgentId(String implementationTarget, String agentId);
    
    // Find tasks with specific configuration
    @Query("{'taskConfig.?0': ?1}")
    Flux<AgentTask> findByTaskConfig(String key, Object value);
    
    // Find tasks by agent with specific configuration
    @Query("{'agentId': ?0, 'taskConfig.?1': ?2}")
    Flux<AgentTask> findByAgentIdAndTaskConfig(String agentId, String key, Object value);
    
    // Find tasks ready to execute (for scheduler) - simplified without status field
    @Query("{'enabled': true, 'cronExpression': {$exists: true, $ne: null}, 'autoExecute': true}")
    Flux<AgentTask> findTasksReadyToExecute();
    
    // Find tasks by agent ready to execute - simplified without status field
    @Query("{'agentId': ?0, 'enabled': true, 'cronExpression': {$exists: true, $ne: null}, 'autoExecute': true}")
    Flux<AgentTask> findTasksReadyToExecuteByAgent(String agentId);
    
    // Find tasks by priority order
    Flux<AgentTask> findByAgentIdOrderByPriorityAsc(String agentId);
    
    // Find tasks by creation date order
    Flux<AgentTask> findByAgentIdOrderByCreatedAtDesc(String agentId);
    
    // Scheduling queries (moved from AgentRepository)
    @Query("{'nextRun': {$lte: ?0}, 'enabled': true, 'autoExecute': true}")
    Flux<AgentTask> findTasksReadyToRun(LocalDateTime now);
    
    // Find tasks by agent that need to run soon
    @Query("{'agentId': ?0, 'nextRun': {$lte: ?1}, 'enabled': true, 'autoExecute': true}")
    Flux<AgentTask> findTasksReadyToRunByAgent(String agentId, LocalDateTime now);
    
    // Find tasks by team that need to run soon (requires join with Agent)
    // Note: This will need to be implemented in the service layer with a join
    
    // Find tasks that haven't run recently (for health checks)
    @Query("{'lastRun': {$lt: ?0}}")
    Flux<AgentTask> findTasksByLastRunBefore(LocalDateTime date);
    
    // NOTE: lastExecuted field removed - use AgentExecutionRepository for execution ordering
} 