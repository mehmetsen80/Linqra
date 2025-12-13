package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Entity for tracking collection export jobs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "collection_export_jobs")
@CompoundIndexes({
    @CompoundIndex(name = "team_status_created_idx", def = "{'teamId': 1, 'status': 1, 'createdAt': -1}"),
    @CompoundIndex(name = "exported_by_created_idx", def = "{'exportedBy': 1, 'createdAt': -1}")
})
public class CollectionExportJob {
    
    @Id
    private String id;
    
    @Indexed(unique = true)
    private String jobId; // Unique job identifier (UUID)
    
    @Indexed
    private String teamId;
    
    /**
     * Username of the user who initiated the export
     */
    @Indexed
    private String exportedBy;
    
    /**
     * List of collection IDs being exported
     */
    private List<String> collectionIds;
    
    private String status; // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    // Progress tracking
    private Integer totalDocuments;
    private Integer processedDocuments;
    private Integer totalFiles;
    private Integer processedFiles;
    
    // Export results - one ZIP file per collection
    private List<CollectionExportResult> exportResults;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CollectionExportResult {
        private String collectionId;
        private String collectionName;
        private String s3Key; // S3 key for the exported ZIP file
        private String downloadUrl; // Presigned URL for download (temporary)
        private Long fileSizeBytes; // Size of the exported ZIP file
        private Integer documentCount; // Number of documents in this collection's export
        private LocalDateTime expiresAt; // When the download URL expires
    }
    
    // Error information
    private String errorMessage;
    private String errorStackTrace;
    
    // Metadata
    private String format; // "zip" (for now)
    private Boolean includeVectors; // Always false - vectors are too large
}

