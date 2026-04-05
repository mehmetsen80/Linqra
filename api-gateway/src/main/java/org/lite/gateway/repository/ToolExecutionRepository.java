package org.lite.gateway.repository;

import org.lite.gateway.entity.ToolExecution;
import org.lite.gateway.model.ExecutionStatus;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface ToolExecutionRepository extends ReactiveMongoRepository<ToolExecution, String> {

    // Unique lookup
    Mono<ToolExecution> findByExecutionId(String executionId);

    // Per-tool history
    Flux<ToolExecution> findByToolId(String toolId);
    Flux<ToolExecution> findByToolId(String toolId, Sort sort);
    Flux<ToolExecution> findByToolIdOrderByExecutedAtDesc(String toolId);

    // Team-level analytics
    Flux<ToolExecution> findByTeamId(String teamId);
    Flux<ToolExecution> findByTeamIdOrderByExecutedAtDesc(String teamId);

    // Per-user history
    Flux<ToolExecution> findByExecutedBy(String executedBy);
    Flux<ToolExecution> findByExecutedByOrderByExecutedAtDesc(String executedBy);

    // Status filtering
    Flux<ToolExecution> findByStatus(ExecutionStatus status);
    Flux<ToolExecution> findByToolIdAndStatus(String toolId, ExecutionStatus status);
    Flux<ToolExecution> findByTeamIdAndStatus(String teamId, ExecutionStatus status);

    // Time range analytics
    Flux<ToolExecution> findByToolIdAndExecutedAtBetween(String toolId, LocalDateTime from, LocalDateTime to);
    Flux<ToolExecution> findByTeamIdAndExecutedAtBetween(String teamId, LocalDateTime from, LocalDateTime to);

    // Counts
    Mono<Long> countByToolId(String toolId);
    Mono<Long> countByTeamId(String teamId);
    Mono<Long> countByToolIdAndStatus(String toolId, ExecutionStatus status);
    Mono<Long> countByTeamIdAndStatus(String teamId, ExecutionStatus status);
}
