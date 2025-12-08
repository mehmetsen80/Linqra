package org.lite.gateway.service;

import org.lite.gateway.dto.AuditLogQueryRequest;
import org.lite.gateway.dto.AuditLogPageResponse;
import org.lite.gateway.entity.AuditLog;
import org.lite.gateway.enums.AuditEventType;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service for audit logging - tracks all critical user actions and system
 * events
 */
public interface AuditService {

        /**
         * Get a hot stream of audit logs for real-time monitoring
         */
        Flux<AuditLog> getAuditStream();

        /**
         * Log an audit event with automatic context extraction from ServerWebExchange
         * 
         * @param exchange     ServerWebExchange to extract user/team/IP info
         * @param eventType    Type of audit event
         * @param action       Action type: READ, CREATE, UPDATE, DELETE, EXPORT
         * @param resourceType Resource type: DOCUMENT, CHUNK, METADATA, etc.
         * @param resourceId   Resource ID (e.g., document ID)
         * @param result       Result: SUCCESS, FAILED, DENIED
         * @param metadata     Additional metadata about the event
         * @return Mono that completes when the audit log is saved
         */
        Mono<Void> logEvent(
                        ServerWebExchange exchange,
                        AuditEventType eventType,
                        String action,
                        String resourceType,
                        String resourceId,
                        String result,
                        AuditLog.AuditMetadata metadata);

        /**
         * Log an audit event with explicit context
         * 
         * @param userId       User ID who performed the action
         * @param username     Username (display name)
         * @param teamId       Team ID
         * @param ipAddress    Client IP address
         * @param userAgent    User agent string
         * @param eventType    Type of audit event
         * @param action       Action type
         * @param resourceType Resource type
         * @param resourceId   Resource ID
         * @param result       Result: SUCCESS, FAILED, DENIED
         * @param metadata     Additional metadata
         * @return Mono that completes when the audit log is saved
         */
        Mono<Void> logEvent(
                        String userId,
                        String username,
                        String teamId,
                        String ipAddress,
                        String userAgent,
                        AuditEventType eventType,
                        String action,
                        String resourceType,
                        String resourceId,
                        String result,
                        AuditLog.AuditMetadata metadata);

        /**
         * Log an audit event with additional context fields
         * 
         * @param userId          User ID
         * @param username        Username
         * @param teamId          Team ID
         * @param ipAddress       IP address
         * @param userAgent       User agent
         * @param eventType       Event type
         * @param action          Action
         * @param resourceType    Resource type
         * @param resourceId      Resource ID
         * @param documentId      Document ID (if applicable)
         * @param collectionId    Collection ID (if applicable)
         * @param result          Result
         * @param metadata        Metadata
         * @param complianceFlags Compliance flags
         * @return Mono that completes when the audit log is saved
         */
        Mono<Void> logEvent(
                        String userId,
                        String username,
                        String teamId,
                        String ipAddress,
                        String userAgent,
                        AuditEventType eventType,
                        String action,
                        String resourceType,
                        String resourceId,
                        String documentId,
                        String collectionId,
                        String result,
                        AuditLog.AuditMetadata metadata,
                        AuditLog.ComplianceFlags complianceFlags);

        /**
         * Query audit logs with filters and pagination
         * 
         * @param request Query request with filters
         * @return Paginated audit log response
         */
        Mono<AuditLogPageResponse> queryAuditLogs(AuditLogQueryRequest request);

        /**
         * Get audit logs for a specific document (complete lifecycle)
         * 
         * @param documentId Document ID
         * @param teamId     Team ID (for authorization)
         * @return Flux of audit logs for the document
         */
        reactor.core.publisher.Flux<AuditLog> getDocumentAuditTrail(String documentId, String teamId);

        /**
         * Get audit logs for a specific user
         * 
         * @param userId    User ID
         * @param teamId    Team ID (for authorization)
         * @param startTime Start time
         * @param endTime   End time
         * @return Flux of audit logs for the user
         */
        reactor.core.publisher.Flux<AuditLog> getUserAuditLogs(
                        String userId,
                        String teamId,
                        java.time.LocalDateTime startTime,
                        java.time.LocalDateTime endTime);
}
