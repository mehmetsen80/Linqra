package org.lite.gateway.repository;

import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.AgentTaskType;
import org.lite.gateway.enums.ExecutionTrigger;
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
    
    // Find tasks by agent and type
    Flux<AgentTask> findByAgentIdAndTaskType(String agentId, AgentTaskType taskType);
    
    // Find tasks by team (via agent)
    @Query("{'agentId': {$in: ?0}}")
    Flux<AgentTask> findByAgentIds(List<String> agentIds);
    
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
    
    // Find scheduled tasks (have cron expression and auto-execution trigger)
    @Query("{'cronExpression': {$exists: true, $ne: null, $ne: ''}, 'executionTrigger': 'CRON'}")
    Flux<AgentTask> findScheduledTasksWithCron();
    
    // Find scheduled tasks by agent
    @Query("{'agentId': ?0, 'cronExpression': {$exists: true, $ne: null, $ne: ''}, 'executionTrigger': 'CRON'}")
    Flux<AgentTask> findScheduledTasksByAgent(String agentId);
    
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
    Mono<Boolean> existsByNameAndAgentId(String name, String agentId);    // implementationTarget queries removed - field no longer exists
    
    // Find tasks ready to execute (for scheduler) - simplified without status field
    @Query("{'enabled': true, 'cronExpression': {$exists: true, $ne: null, $ne: ''}, 'executionTrigger': 'CRON'}")
    Flux<AgentTask> findTasksReadyToExecute();
    
    // Find tasks by agent ready to execute - simplified without status field
    @Query("{'agentId': ?0, 'enabled': true, 'cronExpression': {$exists: true, $ne: null, $ne: ''}, 'executionTrigger': 'CRON'}")
    Flux<AgentTask> findTasksReadyToExecuteByAgent(String agentId);
    
    // Find tasks by priority order
    Flux<AgentTask> findByAgentIdOrderByPriorityAsc(String agentId);
    
    // Find tasks by creation date order
    Flux<AgentTask> findByAgentIdOrderByCreatedAtDesc(String agentId);
    
    // Scheduling queries (moved from AgentRepository)
    @Query("{'nextRun': {$lte: ?0}, 'enabled': true, 'executionTrigger': 'CRON'}")
    Flux<AgentTask> findTasksReadyToRun(LocalDateTime now);
    
    // Find tasks by agent that need to run soon
    @Query("{'agentId': ?0, 'nextRun': {$lte: ?1}, 'enabled': true, 'executionTrigger': 'CRON'}")
    Flux<AgentTask> findTasksReadyToRunByAgent(String agentId, LocalDateTime now);
    
    // Find tasks that haven't run recently (for health checks)
    @Query("{'lastRun': {$lt: ?0}}")
    Flux<AgentTask> findTasksByLastRunBefore(LocalDateTime date);
    
    // ==================== EXECUTION TRIGGER QUERIES ====================
    
    // Find tasks by execution trigger type
    Flux<AgentTask> findByExecutionTrigger(ExecutionTrigger executionTrigger);
    
    // Find tasks by agent and execution trigger type
    Flux<AgentTask> findByAgentIdAndExecutionTrigger(String agentId, ExecutionTrigger executionTrigger);
    
    // Find enabled tasks by execution trigger type
    @Query("{'enabled': true, 'executionTrigger': ?0}")
    Flux<AgentTask> findEnabledTasksByExecutionTrigger(ExecutionTrigger executionTrigger);
    
    // Find manual tasks (for UI display)
    @Query("{'executionTrigger': 'MANUAL'}")
    Flux<AgentTask> findManualTasks();
    
    // Find scheduled tasks (CRON)
    @Query("{'executionTrigger': 'CRON', 'enabled': true}")
    Flux<AgentTask> findScheduledTasks();

    // Find CRON tasks to schedule at startup
    @Query("{'enabled': true, 'cronExpression': {$exists: true, $ne: null, $ne: ''}, 'executionTrigger': 'CRON', 'scheduleOnStartup': true}")
    Flux<AgentTask> findCronTasksToScheduleOnStartup();
} 