package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * DTO for collection export job progress updates via WebSocket
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CollectionExportProgressUpdate {
    
    private String jobId;
    private String teamId;
    private String exportedBy;
    private String status; // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    
    // Progress tracking
    private Integer totalDocuments;
    private Integer processedDocuments;
    private Integer totalFiles;
    private Integer processedFiles;
    
    // Export results - one ZIP file per collection
    private java.util.List<ExportResult> exportResults;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ExportResult {
        private String collectionId;
        private String collectionName;
        private String downloadUrl;
        private Long fileSizeBytes;
        private Integer documentCount;
        private LocalDateTime expiresAt;
    }
    
    // Error information
    private String errorMessage;
    
    // Timestamps
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime timestamp;
}

