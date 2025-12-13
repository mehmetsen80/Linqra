package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.enums.AuditEventType;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * DTO for audit log response (used for API responses)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLogResponse {
    
    private String eventId;
    private LocalDateTime timestamp;
    private AuditEventType eventType;
    private String userId;
    private String username;
    private String teamId;
    private String ipAddress;
    private String userAgent;
    private String action;
    private String result;
    private String resourceType;
    private String resourceId;
    private String documentId;
    private String collectionId;
    private AuditMetadata metadata;
    private ComplianceFlags complianceFlags;
    private Boolean archived;
    private String s3Key;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuditMetadata {
        private String reason;
        private Map<String, Object> context;
        private Long durationMs;
        private Long bytesAccessed;
        private String errorMessage;
    }
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ComplianceFlags {
        private Boolean containsPII;
        private Boolean sensitiveData;
        private Boolean requiresRetention;
    }
    
    /**
     * Convert from AuditLog entity to DTO
     */
    public static AuditLogResponse fromEntity(AuditLog auditLog) {
        if (auditLog == null) {
            return null;
        }
        
        return AuditLogResponse.builder()
                .eventId(auditLog.getEventId())
                .timestamp(auditLog.getTimestamp())
                .eventType(auditLog.getEventType())
                .userId(auditLog.getUserId())
                .username(auditLog.getUsername())
                .teamId(auditLog.getTeamId())
                .ipAddress(auditLog.getIpAddress())
                .userAgent(auditLog.getUserAgent())
                .action(auditLog.getAction())
                .result(auditLog.getResult())
                .resourceType(auditLog.getResourceType())
                .resourceId(auditLog.getResourceId())
                .documentId(auditLog.getDocumentId())
                .collectionId(auditLog.getCollectionId())
                .metadata(auditLog.getMetadata() != null ? AuditMetadata.builder()
                        .reason(auditLog.getMetadata().getReason())
                        .context(auditLog.getMetadata().getContext())
                        .durationMs(auditLog.getMetadata().getDurationMs())
                        .bytesAccessed(auditLog.getMetadata().getBytesAccessed())
                        .errorMessage(auditLog.getMetadata().getErrorMessage())
                        .build() : null)
                .complianceFlags(auditLog.getComplianceFlags() != null ? ComplianceFlags.builder()
                        .containsPII(auditLog.getComplianceFlags().getContainsPII())
                        .sensitiveData(auditLog.getComplianceFlags().getSensitiveData())
                        .requiresRetention(auditLog.getComplianceFlags().getRequiresRetention())
                        .build() : null)
                .archived(auditLog.getArchivedAt() != null)
                .s3Key(auditLog.getS3Key())
                .build();
    }
}

