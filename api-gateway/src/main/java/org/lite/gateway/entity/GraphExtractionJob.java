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
import java.util.Map;

/**
 * Entity for tracking graph extraction jobs (entities and relationships)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "graph_extraction_jobs")
@CompoundIndexes({
    @CompoundIndex(name = "document_team_status_idx", def = "{'documentId': 1, 'teamId': 1, 'status': 1}"),
    @CompoundIndex(name = "team_status_created_idx", def = "{'teamId': 1, 'status': 1, 'createdAt': -1}")
})
public class GraphExtractionJob {
    
    @Id
    private String id;
    
    @Indexed
    private String jobId; // Unique job identifier (UUID)
    
    @Indexed
    private String documentId;
    
    @Indexed
    private String teamId;
    
    private String extractionType; // "entities", "relationships", "all"
    
    private String status; // QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    private LocalDateTime queuedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    
    // Progress tracking
    private Integer totalBatches;
    private Integer processedBatches;
    private Integer totalEntities;
    private Integer totalRelationships;
    
    // Cost tracking
    private Long totalPromptTokens;
    private Long totalCompletionTokens;
    private Long totalTokens;
    private Double totalCostUsd;
    
    // Error information
    private String errorMessage;
    private String errorStackTrace;
    
    // Additional metadata
    private Map<String, Object> metadata;
}

