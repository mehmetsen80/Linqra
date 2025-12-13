package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.enums.AuditEventType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Entity for audit logs - tracks all critical user actions and system events
 * Hot storage: Recent logs (last 90 days) in MongoDB
 * Cold storage: Archived logs (older than 90 days) in S3
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "audit_logs")
@CompoundIndexes({
    // Primary queries
    @CompoundIndex(name = "team_timestamp_idx", def = "{'teamId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "user_timestamp_idx", def = "{'userId': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "event_type_timestamp_idx", def = "{'eventType': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "document_timestamp_idx", def = "{'documentId': 1, 'timestamp': -1}"),
    
    // Compound indexes for common queries
    @CompoundIndex(name = "team_event_timestamp_idx", def = "{'teamId': 1, 'eventType': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "team_result_timestamp_idx", def = "{'teamId': 1, 'result': 1, 'timestamp': -1}"),
    @CompoundIndex(name = "team_user_timestamp_idx", def = "{'teamId': 1, 'userId': 1, 'timestamp': -1}"),
    
    // For archival queries (logs not yet archived)
    @CompoundIndex(name = "not_archived_timestamp_idx", def = "{'archivedAt': 1, 'timestamp': 1}", sparse = true)
})
public class AuditLog {
    
    @Id
    private String id;
    
    /**
     * Unique event identifier (UUID)
     */
    @Indexed(unique = true)
    private String eventId;
    
    /**
     * Timestamp when the event occurred
     */
    @Indexed
    private LocalDateTime timestamp;
    
    /**
     * Type of audit event
     */
    @Indexed
    private AuditEventType eventType;
    
    /**
     * User who performed the action
     */
    @Indexed
    private String userId;
    
    /**
     * Username (display name) for easier readability
     */
    private String username;
    
    /**
     * Team ID - for multi-tenancy
     */
    @Indexed
    private String teamId;
    
    /**
     * Client IP address
     */
    private String ipAddress;
    
    /**
     * User agent (browser/client info)
     */
    private String userAgent;
    
    /**
     * Action type: READ, CREATE, UPDATE, DELETE, EXPORT
     */
    private String action;
    
    /**
     * Result: SUCCESS, FAILED, DENIED
     */
    @Indexed
    private String result;
    
    /**
     * Resource type: DOCUMENT, CHUNK, METADATA, EXPORT, USER, etc.
     */
    private String resourceType;
    
    /**
     * Resource ID (e.g., document ID, chunk ID)
     */
    private String resourceId;
    
    /**
     * Document ID (if applicable)
     */
    @Indexed
    private String documentId;
    
    /**
     * Collection ID (if applicable)
     */
    @Indexed
    private String collectionId;
    
    /**
     * Additional metadata about the event
     */
    private AuditMetadata metadata;
    
    /**
     * Compliance flags for filtering and reporting
     */
    private ComplianceFlags complianceFlags;
    
    /**
     * Timestamp when this log was archived to S3 (null if not archived)
     */
    @Indexed(sparse = true)
    private LocalDateTime archivedAt;
    
    /**
     * S3 key where the archived log is stored (null if not archived)
     */
    private String s3Key;
    
    /**
     * Nested class for audit event metadata
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditMetadata {
        /**
         * Reason why the access/action occurred
         */
        private String reason;
        
        /**
         * Additional context (flexible object)
         */
        private Map<String, Object> context;
        
        /**
         * Duration in milliseconds (for operations that take time)
         */
        private Long durationMs;
        
        /**
         * Number of bytes accessed (for data access events)
         */
        private Long bytesAccessed;
        
        /**
         * Error message (if result is FAILED)
         */
        private String errorMessage;
    }
    
    /**
     * Nested class for compliance flags
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceFlags {
        /**
         * Whether the event involves PII (Personally Identifiable Information)
         */
        private Boolean containsPII;
        
        /**
         * Whether the event involves sensitive data
         */
        private Boolean sensitiveData;
        
        /**
         * Whether this event requires long-term retention (7+ years)
         */
        private Boolean requiresRetention;
    }
}

