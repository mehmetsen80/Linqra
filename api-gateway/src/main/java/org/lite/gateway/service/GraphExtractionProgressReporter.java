package org.lite.gateway.service;

/**
 * Interface for reporting graph extraction progress
 * 
 * <p><b>Future Use:</b> This interface is reserved for future enhancement when we want to implement
 * real-time batch-level progress reporting during entity and relationship extraction. Currently,
 * progress is only reported when the job status changes (QUEUED → RUNNING → COMPLETED/FAILED).
 * 
 * <p>When implemented, the extraction services ({@code KnowledgeHubGraphEntityExtractionService}
 * and {@code KnowledgeHubGraphRelationshipExtractionService}) would accept an instance of this
 * interface and call {@link #reportProgress(Integer, Integer, Integer, Integer, Double)} after
 * processing each batch, providing real-time updates on:
 * <ul>
 *   <li>Batch processing progress (processedBatches / totalBatches)</li>
 *   <li>Cumulative entity and relationship counts</li>
 *   <li>Accumulated cost</li>
 * </ul>
 * 
 * <p>The {@code GraphExtractionJobServiceImpl} would implement this interface and publish
 * progress updates via WebSocket using {@code GraphExtractionJobService.updateJobProgress()}.
 */
public interface GraphExtractionProgressReporter {
    
    /**
     * Report extraction progress
     * @param processedBatches Number of processed batches
     * @param totalBatches Total number of batches
     * @param totalEntities Total entities extracted so far
     * @param totalRelationships Total relationships extracted so far (null if not applicable)
     * @param totalCostUsd Total cost so far
     */
    void reportProgress(Integer processedBatches, Integer totalBatches, 
                       Integer totalEntities, Integer totalRelationships, Double totalCostUsd);
}
