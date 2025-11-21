package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for graph extraction job progress updates via WebSocket
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GraphExtractionProgressUpdate {
    
    private String jobId;
    private String documentId;
    private String teamId;
    private String extractionType; // "entities", "relationships", "all"
    private String status; // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    
    // Progress tracking
    private Integer totalBatches;
    private Integer processedBatches;
    private Integer totalEntities;
    private Integer totalRelationships;
    
    // Cost tracking
    private Double totalCostUsd;
    
    // Error information
    private String errorMessage;
    
    // Timestamps
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime timestamp;
    
    public GraphExtractionProgressUpdate withStatus(String newStatus) {
        return GraphExtractionProgressUpdate.builder()
                .jobId(this.jobId)
                .documentId(this.documentId)
                .teamId(this.teamId)
                .extractionType(this.extractionType)
                .status(newStatus)
                .totalBatches(this.totalBatches)
                .processedBatches(this.processedBatches)
                .totalEntities(this.totalEntities)
                .totalRelationships(this.totalRelationships)
                .totalCostUsd(this.totalCostUsd)
                .errorMessage(this.errorMessage)
                .queuedAt(this.queuedAt)
                .startedAt(this.startedAt)
                .completedAt(this.completedAt)
                .timestamp(LocalDateTime.now())
                .build();
    }
}
