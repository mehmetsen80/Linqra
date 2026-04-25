package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ExecutionProgressUpdate;
import org.lite.gateway.service.ExecutionMonitoringService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class ExecutionMonitoringServiceImpl implements ExecutionMonitoringService {
    
    @Qualifier("executionMessageChannel")
    private final MessageChannel executionMessageChannel;
    
    // Constructor to log the injected message channel
    public ExecutionMonitoringServiceImpl(@Qualifier("executionMessageChannel") MessageChannel executionMessageChannel) {
        this.executionMessageChannel = executionMessageChannel;
        log.info("📊 ExecutionMonitoringServiceImpl initialized with message channel: {}", executionMessageChannel);
    }
    
    @Override
    public Mono<Void> sendExecutionStarted(ExecutionProgressUpdate update) {
        return sendUpdate(update.withStatus("STARTED"));
    }
    
    @Override
    public Mono<Void> sendStepProgress(ExecutionProgressUpdate update) {
        log.info("📊 sendStepProgress method called with executionId: {} and executionDurationMs: {}", update.getExecutionId(), update.getExecutionDurationMs());
        ExecutionProgressUpdate updatedWithStatus = update.withStatus("RUNNING");
        log.info("📊 After withStatus('RUNNING') - executionDurationMs: {}", updatedWithStatus.getExecutionDurationMs());
        return sendUpdate(updatedWithStatus);
    }
    
    @Override
    public Mono<Void> sendExecutionCompleted(ExecutionProgressUpdate update) {
        return sendUpdate(update.withStatus("COMPLETED"));
    }
    
    @Override
    public Mono<Void> sendExecutionFailed(ExecutionProgressUpdate update, String errorMessage) {
        return sendUpdate(update.withStatus("FAILED").withErrorMessage(errorMessage));
    }
    
    @Override
    public Mono<Void> sendExecutionCancelled(ExecutionProgressUpdate update, String reason) {
        return sendUpdate(update.withStatus("CANCELLED").withErrorMessage(reason));
    }
    
    @Override
    public Mono<Void> sendMemoryUpdate(ExecutionProgressUpdate update) {
        return sendUpdate(update.withMemoryUsage(ExecutionProgressUpdate.MemoryUsage.fromCurrentMemory()));
    }

    @Override
    public Mono<Void> sendResultChunk(ExecutionProgressUpdate update, String chunk) {
        update.setChunk(chunk);
        return sendUpdate(update.withStatus("STREAMING"));
    }

    @Override
    public Mono<Void> sendResultChunkWithAccumulated(ExecutionProgressUpdate update, String chunk, String accumulated) {
        update.setChunk(chunk);
        update.setAccumulated(accumulated);
        return sendUpdate(update.withStatus("STREAMING"));
    }
    
    private Mono<Void> sendUpdate(ExecutionProgressUpdate update) {
        log.info("📊 sendUpdate method called with executionId: {}", update.getExecutionId());
        return Mono.fromRunnable(() -> {
            try {
                log.info("📊 ExecutionMonitoringService - Before sending update: executionDurationMs={}, lastUpdatedAt={}", 
                    update.getExecutionDurationMs(), update.getLastUpdatedAt());
                
                update.setLastUpdatedAt(java.time.LocalDateTime.now(java.time.ZoneOffset.UTC));
                update.setMemoryUsage(ExecutionProgressUpdate.MemoryUsage.fromCurrentMemory());
                
                log.info("📊 ExecutionMonitoringService - After setting fields: executionDurationMs={}, lastUpdatedAt={}", 
                    update.getExecutionDurationMs(), update.getLastUpdatedAt());
                
                log.info("📊 Attempting to send message through executionMessageChannel...");
                log.info("📊 executionMessageChannel is null: {}", executionMessageChannel == null);
                if (executionMessageChannel == null) {
                    log.error("📊 executionMessageChannel is null! Cannot send message.");
                    return;
                }
                boolean sent = executionMessageChannel.send(
                    MessageBuilder.withPayload(update).build()
                );
                
                log.info("📊 Message send result: {}", sent);
                
                if (sent) {
                    log.info("📊 Sent execution update for executionId: {} with status: {} and durationMs: {}", 
                        update.getExecutionId(), update.getStatus(), update.getExecutionDurationMs());
                } else {
                    log.warn("Failed to send execution update for executionId: {}", update.getExecutionId());
                }
            } catch (Exception e) {
                log.error("Error sending execution update for executionId: {}", update.getExecutionId(), e);
            }
        });
    }
}
