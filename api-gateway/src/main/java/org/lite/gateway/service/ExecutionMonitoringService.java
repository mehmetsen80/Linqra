package org.lite.gateway.service;

import org.lite.gateway.dto.ExecutionProgressUpdate;
import reactor.core.publisher.Mono;

/**
 * Service for monitoring execution progress and sending real-time updates
 */
public interface ExecutionMonitoringService {
    
    /**
     * Send execution started update
     */
    Mono<Void> sendExecutionStarted(ExecutionProgressUpdate update);
    
    /**
     * Send step progress update
     */
    Mono<Void> sendStepProgress(ExecutionProgressUpdate update);
    
    /**
     * Send execution completed update
     */
    Mono<Void> sendExecutionCompleted(ExecutionProgressUpdate update);
    
    /**
     * Send execution failed update
     */
    Mono<Void> sendExecutionFailed(ExecutionProgressUpdate update, String errorMessage);
    
    /**
     * Send execution cancelled update
     */
    Mono<Void> sendExecutionCancelled(ExecutionProgressUpdate update, String reason);
    
    /**
     * Send memory usage update
     */
    Mono<Void> sendMemoryUpdate(ExecutionProgressUpdate update);
}
