package org.lite.gateway.service;

import org.lite.gateway.entity.ExecutionQueue;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExecutionQueueService {
    
    /**
     * Add an execution to the queue
     */
    Mono<ExecutionQueue> addToQueue(String executionId, String agentId, String agentName, 
                                   String taskId, String taskName, String teamId, String userId);
    
    /**
     * Get all queued executions for a team
     */
    Flux<ExecutionQueue> getQueueForTeam(String teamId);
    
    /**
     * Get the next execution to start from the queue
     */
    Mono<ExecutionQueue> getNextExecution(String teamId);
    
    /**
     * Mark an execution as starting
     */
    Mono<ExecutionQueue> markAsStarting(String executionId);
    
    /**
     * Mark an execution as started and remove from queue
     */
    Mono<Void> markAsStartedAndRemove(String executionId);
    
    /**
     * Remove an execution from the queue
     */
    Mono<Void> removeFromQueue(String executionId);
    
    /**
     * Update queue positions for a team
     */
    Mono<Void> updateQueuePositions(String teamId);
}
