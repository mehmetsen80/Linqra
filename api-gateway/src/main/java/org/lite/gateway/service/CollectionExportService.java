package org.lite.gateway.service;

import org.lite.gateway.entity.CollectionExportJob;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Service for exporting collections and their documents
 */
public interface CollectionExportService {
    
    /**
     * Queue a collection export job
     * @param collectionIds List of collection IDs to export
     * @param teamId Team ID
     * @param exportedBy Username of user who initiated export
     * @return Mono with the created export job
     */
    Mono<CollectionExportJob> queueExport(List<String> collectionIds, String teamId, String exportedBy);
    
    /**
     * Get export job status by job ID
     */
    Mono<CollectionExportJob> getExportJobStatus(String jobId, String teamId);
    
    /**
     * Get all export jobs for a team
     */
    Flux<CollectionExportJob> getExportJobsForTeam(String teamId);
    
    /**
     * Cancel a running export job
     */
    Mono<Boolean> cancelExportJob(String jobId, String teamId);
    
    /**
     * Process the next export job in the queue (called by scheduler)
     */
    void processQueue();
    
    /**
     * Update export job progress and publish via WebSocket
     */
    Mono<Void> updateExportProgress(String jobId, Integer processedDocuments, Integer totalDocuments,
                                   Integer processedFiles, Integer totalFiles);
}

