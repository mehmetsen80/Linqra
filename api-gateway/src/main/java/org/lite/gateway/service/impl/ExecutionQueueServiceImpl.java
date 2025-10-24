package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ExecutionQueue;
import org.lite.gateway.repository.ExecutionQueueRepository;
import org.lite.gateway.service.ExecutionQueueService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExecutionQueueServiceImpl implements ExecutionQueueService {
    
    private final ExecutionQueueRepository executionQueueRepository;
    
    @Override
    public Mono<ExecutionQueue> addToQueue(String executionId, String agentId, String agentName, 
                                          String taskId, String taskName, String teamId, String userId) {
        log.info("📋 Adding execution to queue: {}", executionId);
        
        return executionQueueRepository.findByTeamIdAndStatusQueuedOrderByPriorityDescQueuedAtAsc(teamId)
            .collectList()
            .flatMap(queuedExecutions -> {
                int queuePosition = queuedExecutions.size() + 1;
                
                ExecutionQueue queueItem = ExecutionQueue.builder()
                    .id(UUID.randomUUID().toString())
                    .executionId(executionId)
                    .agentId(agentId)
                    .agentName(agentName)
                    .taskId(taskId)
                    .taskName(taskName)
                    .teamId(teamId)
                    .userId(userId)
                    .status("QUEUED")
                    .queuedAt(LocalDateTime.now(java.time.ZoneOffset.UTC))
                    .priority(1) // Default priority
                    .queuePosition(String.valueOf(queuePosition))
                    .description("Pending execution")
                    .build();
                
                return executionQueueRepository.save(queueItem)
                    .doOnSuccess(saved -> log.info("📋 Added execution to queue: {} at position {}", executionId, queuePosition))
                    .doOnError(error -> log.error("📋 Failed to add execution to queue: {}", executionId, error));
            });
    }
    
    @Override
    public Flux<ExecutionQueue> getQueueForTeam(String teamId) {
        log.info("📋 Getting queue for team: {}", teamId);
        return executionQueueRepository.findByTeamIdAndStatusInOrderByPriorityDescQueuedAtAsc(teamId)
            .doOnNext(queueItem -> log.debug("📋 Found queued execution: {}", queueItem.getExecutionId()));
    }
    
    @Override
    public Mono<ExecutionQueue> getNextExecution(String teamId) {
        log.info("📋 Getting next execution for team: {}", teamId);
        return executionQueueRepository.findByTeamIdAndStatusQueuedOrderByPriorityDescQueuedAtAsc(teamId)
            .next()
            .doOnNext(next -> log.info("📋 Next execution in queue: {}", next.getExecutionId()));
    }
    
    @Override
    public Mono<ExecutionQueue> markAsStarting(String executionId) {
        log.info("📋 Marking execution as starting: {}", executionId);
        return executionQueueRepository.findByExecutionId(executionId)
            .flatMap(queueItem -> {
                queueItem.setStatus("STARTING");
                queueItem.setStartedAt(LocalDateTime.now(java.time.ZoneOffset.UTC));
                return executionQueueRepository.save(queueItem);
            })
            .doOnSuccess(updated -> log.info("📋 Marked execution as starting: {}", executionId))
            .doOnError(error -> log.error("📋 Failed to mark execution as starting: {}", executionId, error));
    }
    
    @Override
    public Mono<Void> markAsStartedAndRemove(String executionId) {
        log.info("📋 Marking execution as started and removing from queue: {}", executionId);
        return executionQueueRepository.deleteByExecutionId(executionId)
            .doOnSuccess(deleted -> log.info("📋 Removed execution from queue: {}", executionId))
            .doOnError(error -> log.error("📋 Failed to remove execution from queue: {}", executionId, error));
    }
    
    @Override
    public Mono<Void> removeFromQueue(String executionId) {
        log.info("📋 Removing execution from queue: {}", executionId);
        return executionQueueRepository.deleteByExecutionId(executionId)
            .doOnSuccess(deleted -> log.info("📋 Removed execution from queue: {}", executionId))
            .doOnError(error -> log.error("📋 Failed to remove execution from queue: {}", executionId, error));
    }
    
    @Override
    public Mono<Void> updateQueuePositions(String teamId) {
        log.info("📋 Updating queue positions for team: {}", teamId);
        return executionQueueRepository.findByTeamIdAndStatusQueuedOrderByPriorityDescQueuedAtAsc(teamId)
            .collectList()
            .flatMapMany(queuedExecutions -> {
                return Flux.fromIterable(queuedExecutions)
                    .index()
                    .flatMap(indexed -> {
                        ExecutionQueue queueItem = indexed.getT2();
                        queueItem.setQueuePosition(String.valueOf(indexed.getT1() + 1));
                        return executionQueueRepository.save(queueItem);
                    });
            })
            .then()
            .doOnSuccess(updated -> log.info("📋 Updated queue positions for team: {}", teamId))
            .doOnError(error -> log.error("📋 Failed to update queue positions for team: {}", teamId, error));
    }
}
