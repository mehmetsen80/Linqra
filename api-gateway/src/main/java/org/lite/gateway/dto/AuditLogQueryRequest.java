package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.enums.AuditEventType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * DTO for querying audit logs
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogQueryRequest {
    
    /**
     * Team ID (required for authorization)
     */
    private String teamId;
    
    /**
     * Start time for query range
     */
    private LocalDateTime startTime;
    
    /**
     * End time for query range
     */
    private LocalDateTime endTime;
    
    /**
     * User ID to filter by
     */
    private String userId;
    
    /**
     * Event types to filter by
     */
    private List<AuditEventType> eventTypes;
    
    /**
     * Result filter: SUCCESS, FAILED, DENIED
     */
    private String result;
    
    /**
     * Document ID to filter by
     */
    private String documentId;
    
    /**
     * Collection ID to filter by
     */
    private String collectionId;
    
    /**
     * Resource type to filter by
     */
    private String resourceType;
    
    /**
     * Page number (0-indexed)
     */
    @Builder.Default
    private Integer page = 0;
    
    /**
     * Page size
     */
    @Builder.Default
    private Integer size = 50;
}

