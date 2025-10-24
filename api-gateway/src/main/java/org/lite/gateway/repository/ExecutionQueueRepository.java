package org.lite.gateway.repository;

import org.lite.gateway.entity.ExecutionQueue;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ExecutionQueueRepository extends ReactiveMongoRepository<ExecutionQueue, String> {
    
    // Get all queued executions for a team, ordered by priority and queued time
    @Query("{ 'teamId': ?0, 'status': { $in: ['QUEUED', 'STARTING'] } }")
    Flux<ExecutionQueue> findByTeamIdAndStatusInOrderByPriorityDescQueuedAtAsc(String teamId);
    
    // Get a specific execution by executionId
    Mono<ExecutionQueue> findByExecutionId(String executionId);
    
    // Get queue position for a team
    @Query("{ 'teamId': ?0, 'status': 'QUEUED' }")
    Flux<ExecutionQueue> findByTeamIdAndStatusQueuedOrderByPriorityDescQueuedAtAsc(String teamId);
    
    // Delete by executionId
    Mono<Void> deleteByExecutionId(String executionId);
}
