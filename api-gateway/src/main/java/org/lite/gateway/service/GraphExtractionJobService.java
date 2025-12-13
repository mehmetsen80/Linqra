package org.lite.gateway.service;

import org.lite.gateway.entity.GraphExtractionJob;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for managing graph extraction jobs
 */
public interface GraphExtractionJobService {
    
    /**
     * Queue a graph extraction job
     * @param documentId Document to extract from
     * @param teamId Team ID
     * @param extractionType "entities", "relationships", or "all"
     * @param force Force re-extraction even if already extracted
     * @return Mono with the created job
     */
    Mono<GraphExtractionJob> queueExtraction(String documentId, String teamId, String extractionType, boolean force);
    
    /**
     * Get job status by job ID
     */
    Mono<GraphExtractionJob> getJobStatus(String jobId);
    
    /**
     * Get all jobs for a document
     */
    Flux<GraphExtractionJob> getJobsForDocument(String documentId, String teamId);
    
    /**
     * Cancel a running job
     */
    Mono<Boolean> cancelJob(String jobId, String teamId);
    
    /**
     * Process the next job in the queue (called by scheduler)
     */
    void processQueue();
    
    /**
     * Update job progress and publish via WebSocket
     * @param jobId Job ID
     * @param processedBatches Number of processed batches
     * @param totalBatches Total number of batches
     * @param totalEntities Total entities extracted so far
     * @param totalRelationships Total relationships extracted so far
     * @param totalCostUsd Total cost so far
     */
    Mono<Void> updateJobProgress(String jobId, Integer processedBatches, Integer totalBatches, 
                                  Integer totalEntities, Integer totalRelationships, Double totalCostUsd);
}

