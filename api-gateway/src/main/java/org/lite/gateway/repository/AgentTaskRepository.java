package org.lite.gateway.repository;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.AgentTaskStatus;
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
    
    // Find tasks by agent and status
    Flux<AgentTask> findByAgentIdAndStatus(String agentId, AgentTaskStatus status);
    
    // Find tasks by agent and type
    Flux<AgentTask> findByAgentIdAndTaskType(String agentId, AgentTaskType taskType);
    
    // Find tasks by team (via agent)
    @Query("{'agentId': {$in: ?0}}")
    Flux<AgentTask> findByAgentIds(List<String> agentIds);
    
    // Find tasks by status
    Flux<AgentTask> findByStatus(AgentTaskStatus status);
    
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
    
    // Find tasks by capability
    @Query("{'capabilities': ?0}")
    Flux<AgentTask> findByCapability(String capability);
    
    // Find tasks by agent and capability
    @Query("{'agentId': ?0, 'capabilities': ?1}")
    Flux<AgentTask> findByAgentIdAndCapability(String agentId, String capability);
    
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
    
    // Find tasks by dependencies
    @Query("{'dependencies': ?0}")
    Flux<AgentTask> findByDependency(String dependencyTaskId);
    
    // Find tasks that depend on a specific task
    @Query("{'dependencies': ?0}")
    Flux<AgentTask> findTasksDependingOn(String taskId);
    
    // Find tasks by agent that depend on a specific task
    @Query("{'agentId': ?0, 'dependencies': ?1}")
    Flux<AgentTask> findByAgentIdAndDependency(String agentId, String dependencyTaskId);
    
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
    
    // Find tasks that haven't been executed recently
    Flux<AgentTask> findByLastExecutedBefore(LocalDateTime date);
    
    // Find tasks by agent that haven't been executed recently
    Flux<AgentTask> findByAgentIdAndLastExecutedBefore(String agentId, LocalDateTime date);
    
    // Find tasks with errors
    Flux<AgentTask> findByStatusAndLastErrorIsNotNull(AgentTaskStatus status);
    
    // Find tasks by agent with errors
    Flux<AgentTask> findByAgentIdAndStatusAndLastErrorIsNotNull(String agentId, AgentTaskStatus status);
    
    // Find tasks by next execution time
    Flux<AgentTask> findByNextExecutionBefore(LocalDateTime date);
    
    // Find tasks by agent and next execution time
    Flux<AgentTask> findByAgentIdAndNextExecutionBefore(String agentId, LocalDateTime date);
    
    // Count tasks by agent
    Mono<Long> countByAgentId(String agentId);
    
    // Count enabled tasks by agent
    Mono<Long> countByAgentIdAndEnabledTrue(String agentId);
    
    // Count tasks by status
    Mono<Long> countByStatus(AgentTaskStatus status);
    
    // Count tasks by agent and status
    Mono<Long> countByAgentIdAndStatus(String agentId, AgentTaskStatus status);
    
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
    
    // Find tasks ready to execute (for scheduler)
    @Query("{'enabled': true, 'status': {$in: ['PENDING', 'READY']}, 'cronExpression': {$exists: true, $ne: null}, 'autoExecute': true}")
    Flux<AgentTask> findTasksReadyToExecute();
    
    // Find tasks by agent ready to execute
    @Query("{'agentId': ?0, 'enabled': true, 'status': {$in: ['PENDING', 'READY']}, 'cronExpression': {$exists: true, $ne: null}, 'autoExecute': true}")
    Flux<AgentTask> findTasksReadyToExecuteByAgent(String agentId);
    
    // Find tasks by priority order
    Flux<AgentTask> findByAgentIdOrderByPriorityAsc(String agentId);
    
    // Find tasks by creation date order
    Flux<AgentTask> findByAgentIdOrderByCreatedAtDesc(String agentId);
    
    // Find tasks by last execution order
    Flux<AgentTask> findByAgentIdOrderByLastExecutedDesc(String agentId);
} 