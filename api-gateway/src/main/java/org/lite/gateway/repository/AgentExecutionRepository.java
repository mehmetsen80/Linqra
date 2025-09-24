package org.lite.gateway.repository;

import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.ExecutionType;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AgentExecutionRepository extends ReactiveMongoRepository<AgentExecution, String> {
    
    // Basic CRUD operations are inherited from ReactiveMongoRepository
    
    // Find executions by agent
    Flux<AgentExecution> findByAgentId(String agentId);
    
    // Find executions by task
    Flux<AgentExecution> findByTaskId(String taskId);
    
    // Find executions by agent and task
    Flux<AgentExecution> findByAgentIdAndTaskId(String agentId, String taskId);
    
    // Find executions by team (via agent)
    @Query("{'agentId': {$in: ?0}}")
    Flux<AgentExecution> findByAgentIds(List<String> agentIds);
    
    // Find executions by status
    Flux<AgentExecution> findByStatus(String status);
    
    // Find executions by result
    Flux<AgentExecution> findByResult(String result);
    
    // Find executions by agent and status
    Flux<AgentExecution> findByAgentIdAndStatus(String agentId, String status);
    
    // Find executions by agent and result
    Flux<AgentExecution> findByAgentIdAndResult(String agentId, String result);
    
    // Find executions by task and status
    Flux<AgentExecution> findByTaskIdAndStatus(String taskId, String status);
    
    // Find executions by task and result
    Flux<AgentExecution> findByTaskIdAndResult(String taskId, String result);
    
    // Find executions by execution type
    Flux<AgentExecution> findByExecutionType(String executionType);
    
    // Find executions by execution type
    Flux<AgentExecution> findByExecutionType(ExecutionType executionType);
    
    // Find executions by agent and execution type
    Flux<AgentExecution> findByAgentIdAndExecutionType(String agentId, ExecutionType executionType);
    
    // Find executions by executed by
    Flux<AgentExecution> findByExecutedBy(String executedBy);
    
    // Find executions by agent and executed by
    Flux<AgentExecution> findByAgentIdAndExecutedBy(String agentId, String executedBy);
    
    // Find executions by execution environment
    Flux<AgentExecution> findByExecutionEnvironment(String executionEnvironment);
    
    // Find executions by agent and execution environment
    Flux<AgentExecution> findByAgentIdAndExecutionEnvironment(String agentId, String executionEnvironment);
    
    // Find executions by version
    Flux<AgentExecution> findByVersion(String version);
    
    // Find executions by agent and version
    Flux<AgentExecution> findByAgentIdAndVersion(String agentId, String version);
    
    // Find executions by workflow ID
    Flux<AgentExecution> findByWorkflowId(String workflowId);
    
    // Find executions by agent and workflow ID
    Flux<AgentExecution> findByAgentIdAndWorkflowId(String agentId, String workflowId);
    
    // Find executions by workflow execution ID
    Flux<AgentExecution> findByWorkflowExecutionId(String workflowExecutionId);
    
    // Find executions by agent and workflow execution ID
    Flux<AgentExecution> findByAgentIdAndWorkflowExecutionId(String agentId, String workflowExecutionId);
    
    // Find executions by workflow status
    Flux<AgentExecution> findByWorkflowStatus(String workflowStatus);
    
    // Find executions by agent and workflow status
    Flux<AgentExecution> findByAgentIdAndWorkflowStatus(String agentId, String workflowStatus);
    
    // Find executions by error code
    Flux<AgentExecution> findByErrorCode(String errorCode);
    
    // Find executions by agent and error code
    Flux<AgentExecution> findByAgentIdAndErrorCode(String agentId, String errorCode);
    
    // Find executions with errors
    Flux<AgentExecution> findByErrorMessageIsNotNull();
    
    // Find executions by agent with errors
    Flux<AgentExecution> findByAgentIdAndErrorMessageIsNotNull(String agentId);
    
    // Find executions by task with errors
    Flux<AgentExecution> findByTaskIdAndErrorMessageIsNotNull(String taskId);
    
    // Find executions by retry count
    Flux<AgentExecution> findByRetryCount(int retryCount);
    
    // Find executions by agent and retry count
    Flux<AgentExecution> findByAgentIdAndRetryCount(String agentId, int retryCount);
    
    // Find executions by task and retry count
    Flux<AgentExecution> findByTaskIdAndRetryCount(String taskId, int retryCount);
    
    // Find executions by max retries
    Flux<AgentExecution> findByMaxRetries(int maxRetries);
    
    // Find executions by agent and max retries
    Flux<AgentExecution> findByAgentIdAndMaxRetries(String agentId, int maxRetries);
    
    // Find executions by task and max retries
    Flux<AgentExecution> findByTaskIdAndMaxRetries(String taskId, int maxRetries);
    
    // Find executions by tags
    @Query("{'tags': ?0}")
    Flux<AgentExecution> findByTag(String tag);
    
    // Find executions by agent and tags
    @Query("{'agentId': ?0, 'tags': ?1}")
    Flux<AgentExecution> findByAgentIdAndTag(String agentId, String tag);
    
    // Find executions by task and tags
    @Query("{'taskId': ?0, 'tags': ?1}")
    Flux<AgentExecution> findByTaskIdAndTag(String taskId, String tag);
    
    // Find executions by scheduled time range
    Flux<AgentExecution> findByScheduledAtBetween(LocalDateTime from, LocalDateTime to);
    
    // Find executions by agent and scheduled time range
    Flux<AgentExecution> findByAgentIdAndScheduledAtBetween(String agentId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by task and scheduled time range
    Flux<AgentExecution> findByTaskIdAndScheduledAtBetween(String taskId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by started time range
    Flux<AgentExecution> findByStartedAtBetween(LocalDateTime from, LocalDateTime to);
    
    // Find executions by agent and started time range
    Flux<AgentExecution> findByAgentIdAndStartedAtBetween(String agentId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by task and started time range
    Flux<AgentExecution> findByTaskIdAndStartedAtBetween(String taskId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by completed time range
    Flux<AgentExecution> findByCompletedAtBetween(LocalDateTime from, LocalDateTime to);
    
    // Find executions by agent and completed time range
    Flux<AgentExecution> findByAgentIdAndCompletedAtBetween(String agentId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by task and completed time range
    Flux<AgentExecution> findByTaskIdAndCompletedAtBetween(String taskId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by created time range
    Flux<AgentExecution> findByCreatedAtBetween(LocalDateTime from, LocalDateTime to);
    
    // Find executions by agent and created time range
    Flux<AgentExecution> findByAgentIdAndCreatedAtBetween(String agentId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by task and created time range
    Flux<AgentExecution> findByTaskIdAndCreatedAtBetween(String taskId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by updated time range
    Flux<AgentExecution> findByUpdatedAtBetween(LocalDateTime from, LocalDateTime to);
    
    // Find executions by agent and updated time range
    Flux<AgentExecution> findByAgentIdAndUpdatedAtBetween(String agentId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by task and updated time range
    Flux<AgentExecution> findByTaskIdAndUpdatedAtBetween(String taskId, LocalDateTime from, LocalDateTime to);
    
    // Find executions by execution duration range
    Flux<AgentExecution> findByExecutionDurationMsBetween(Long minDuration, Long maxDuration);
    
    // Find executions by agent and execution duration range
    Flux<AgentExecution> findByAgentIdAndExecutionDurationMsBetween(String agentId, Long minDuration, Long maxDuration);
    
    // Find executions by task and execution duration range
    Flux<AgentExecution> findByTaskIdAndExecutionDurationMsBetween(String taskId, Long minDuration, Long maxDuration);
    
    // Find executions by memory usage range
    Flux<AgentExecution> findByMemoryUsageBytesBetween(Long minMemory, Long maxMemory);
    
    // Find executions by agent and memory usage range
    Flux<AgentExecution> findByAgentIdAndMemoryUsageBytesBetween(String agentId, Long minMemory, Long maxMemory);
    
    // Find executions by task and memory usage range
    Flux<AgentExecution> findByTaskIdAndMemoryUsageBytesBetween(String taskId, Long minMemory, Long maxMemory);
    
    // Find executions by CPU usage range
    Flux<AgentExecution> findByCpuUsagePercentBetween(Double minCpu, Double maxCpu);
    
    // Find executions by agent and CPU usage range
    Flux<AgentExecution> findByAgentIdAndCpuUsagePercentBetween(String agentId, Double minCpu, Double maxCpu);
    
    // Find executions by task and CPU usage range
    Flux<AgentExecution> findByTaskIdAndCpuUsagePercentBetween(String taskId, Double minCpu, Double maxCpu);
    
    // Count executions by agent
    Mono<Long> countByAgentId(String agentId);
    
    // Count executions by task
    Mono<Long> countByTaskId(String taskId);
    
    // Count executions by status
    Mono<Long> countByStatus(String status);
    
    // Count executions by result
    Mono<Long> countByResult(String result);
    
    // Count executions by agent and status
    Mono<Long> countByAgentIdAndStatus(String agentId, String status);
    
    // Count executions by agent and result
    Mono<Long> countByAgentIdAndResult(String agentId, String result);
    
    // Count executions by task and status
    Mono<Long> countByTaskIdAndStatus(String taskId, String status);
    
    // Count executions by task and result
    Mono<Long> countByTaskIdAndResult(String taskId, String result);
    
    // Count executions by execution type
    Mono<Long> countByExecutionType(String executionType);
    
    // Count executions by execution type
    Mono<Long> countByExecutionType(ExecutionType executionType);
    
    // Count executions by agent and execution type
    Mono<Long> countByAgentIdAndExecutionType(String agentId, ExecutionType executionType);
    
    // Count executions by executed by
    Mono<Long> countByExecutedBy(String executedBy);
    
    // Count executions by agent and executed by
    Mono<Long> countByAgentIdAndExecutedBy(String agentId, String executedBy);
    
    // Count executions by execution environment
    Mono<Long> countByExecutionEnvironment(String executionEnvironment);
    
    // Count executions by agent and execution environment
    Mono<Long> countByAgentIdAndExecutionEnvironment(String agentId, String executionEnvironment);
    
    // Count executions by version
    Mono<Long> countByVersion(String version);
    
    // Count executions by agent and version
    Mono<Long> countByAgentIdAndVersion(String agentId, String version);
    
    // Count executions by workflow ID
    Mono<Long> countByWorkflowId(String workflowId);
    
    // Count executions by agent and workflow ID
    Mono<Long> countByAgentIdAndWorkflowId(String agentId, String workflowId);
    
    // Count executions by workflow execution ID
    Mono<Long> countByWorkflowExecutionId(String workflowExecutionId);
    
    // Count executions by agent and workflow execution ID
    Mono<Long> countByAgentIdAndWorkflowExecutionId(String agentId, String workflowExecutionId);
    
    // Count executions by workflow status
    Mono<Long> countByWorkflowStatus(String workflowStatus);
    
    // Count executions by agent and workflow status
    Mono<Long> countByAgentIdAndWorkflowStatus(String agentId, String workflowStatus);
    
    // Count executions by error code
    Mono<Long> countByErrorCode(String errorCode);
    
    // Count executions by agent and error code
    Mono<Long> countByAgentIdAndErrorCode(String agentId, String errorCode);
    
    // Count executions with errors
    Mono<Long> countByErrorMessageIsNotNull();
    
    // Count executions by agent with errors
    Mono<Long> countByAgentIdAndErrorMessageIsNotNull(String agentId);
    
    // Count executions by task with errors
    Mono<Long> countByTaskIdAndErrorMessageIsNotNull(String taskId);
    
    // Count executions by retry count
    Mono<Long> countByRetryCount(int retryCount);
    
    // Count executions by agent and retry count
    Mono<Long> countByAgentIdAndRetryCount(String agentId, int retryCount);
    
    // Count executions by task and retry count
    Mono<Long> countByTaskIdAndRetryCount(String taskId, int retryCount);
    
    // Count executions by max retries
    Mono<Long> countByMaxRetries(int maxRetries);
    
    // Count executions by agent and max retries
    Mono<Long> countByAgentIdAndMaxRetries(String agentId, int maxRetries);
    
    // Count executions by task and max retries
    Mono<Long> countByTaskIdAndMaxRetries(String taskId, int maxRetries);
    
    // Check if execution exists by execution ID
    Mono<Boolean> existsByExecutionId(String executionId);
    
    // Check if execution exists by agent and execution ID
    Mono<Boolean> existsByAgentIdAndExecutionId(String agentId, String executionId);
    
    // Check if execution exists by task and execution ID
    Mono<Boolean> existsByTaskIdAndExecutionId(String taskId, String executionId);
    
    // Find executions by priority order (for analysis)
    Flux<AgentExecution> findByAgentIdOrderByCreatedAtDesc(String agentId);
    
    // Find executions by task priority order (for analysis)
    Flux<AgentExecution> findByTaskIdOrderByCreatedAtDesc(String taskId);
    
    // Find executions by execution time order (for performance analysis)
    Flux<AgentExecution> findByAgentIdOrderByExecutionDurationMsDesc(String agentId);
    
    // Find executions by task execution time order (for performance analysis)
    Flux<AgentExecution> findByTaskIdOrderByExecutionDurationMsDesc(String taskId);
    
    // Find executions by memory usage order (for resource analysis)
    Flux<AgentExecution> findByAgentIdOrderByMemoryUsageBytesDesc(String agentId);
    
    // Find executions by task memory usage order (for resource analysis)
    Flux<AgentExecution> findByTaskIdOrderByMemoryUsageBytesDesc(String taskId);
    
    // Find executions by CPU usage order (for resource analysis)
    Flux<AgentExecution> findByAgentIdOrderByCpuUsagePercentDesc(String agentId);
    
    // Find executions by task CPU usage order (for resource analysis)
    Flux<AgentExecution> findByTaskIdOrderByCpuUsagePercentDesc(String taskId);
} 