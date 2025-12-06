package org.lite.gateway.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.annotation.AuditLog;
import org.lite.gateway.entity.AuditLog.AuditMetadata;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.service.AuditService;
import org.lite.gateway.enums.AuditResultType;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * Helper utility for annotation-based audit logging
 * This provides a simpler, reactive-compatible approach without requiring
 * AspectJ
 * 
 * Usage:
 * 
 * <pre>
 * {@code @AuditLog(eventType = AuditEventType.DOCUMENT_ACCESSED, action = "READ", resourceType = "DOCUMENT")}
 * public Mono<ResponseEntity<?>> getDocument(@PathVariable String documentId, ServerWebExchange exchange) {
 *     return auditLogHelper.logMethod(this, "getDocument", exchange, documentId)
 *             .flatMap(context -> {
 *                 // Your method logic here
 *                 return service.getDocument(documentId);
 *             });
 * }
 * </pre>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuditLogHelper {

    private final AuditService auditService;
    private final UserContextService userContextService;
    private final TeamContextService teamContextService;

    /**
     * Log audit event from method annotation and return audit context
     * This method extracts annotation metadata and logs the audit event
     * 
     * @param target     Object instance (usually 'this')
     * @param methodName Method name (for annotation lookup)
     * @param exchange   ServerWebExchange for context extraction
     * @param resourceId Resource ID (optional, can be null)
     * @return Mono with audit context (can be ignored if not needed)
     */
    public Mono<AuditContext> logMethod(Object target, String methodName, ServerWebExchange exchange,
            String resourceId) {
        return logMethod(target, methodName, exchange, resourceId, null, null);
    }

    /**
     * Log audit event with additional parameters
     */
    public Mono<AuditContext> logMethod(Object target, String methodName, ServerWebExchange exchange,
            String resourceId, String documentId, String collectionId) {
        try {
            // Find method by name
            Method method = findMethod(target.getClass(), methodName);
            if (method == null) {
                log.warn("Method {} not found in class {}", methodName, target.getClass().getName());
                return Mono.empty();
            }

            // Get annotation
            AuditLog annotation = AnnotationUtils.findAnnotation(method, AuditLog.class);
            if (annotation == null) {
                // Try class-level annotation
                annotation = AnnotationUtils.findAnnotation(target.getClass(), AuditLog.class);
            }

            if (annotation == null) {
                log.debug("No @AuditLog annotation found for method {}", methodName);
                return Mono.empty();
            }

            // Extract audit log parameters
            AuditEventType eventType = annotation.eventType();
            String action = annotation.action().name();
            String resourceType = annotation.resourceType().name();
            String reason = annotation.reason();

            LocalDateTime startTime = LocalDateTime.now();

            // Create audit context for return value
            AuditContext context = AuditContext.builder()
                    .auditService(auditService)
                    .startTime(startTime)
                    .eventType(eventType)
                    .action(action)
                    .resourceType(resourceType)
                    .resourceId(resourceId)
                    .documentId(documentId)
                    .collectionId(collectionId)
                    .reason(reason)
                    .annotation(annotation)
                    .build();

            // Log the event
            AuditMetadata metadata = AuditMetadata.builder()
                    .reason(reason)
                    .build();

            return auditService.logEvent(exchange, eventType, action, resourceType, resourceId, "SUCCESS", metadata)
                    .thenReturn(context)
                    .onErrorResume(error -> {
                        log.error("Failed to log audit event: {}", error.getMessage(), error);
                        return Mono.just(context); // Return context even if logging fails
                    });

        } catch (Exception e) {
            log.error("Error in audit log helper: {}", e.getMessage(), e);
            return Mono.empty();
        }
    }

    /**
     * Log a detailed audit event from service layer (without ServerWebExchange)
     * This is a convenient method for service methods that need to log detailed
     * audit events
     * with rich metadata context.
     * 
     * Usage example:
     * 
     * <pre>
     * Map&lt;String, Object&gt; context = new HashMap&lt;&gt;();
     * context.put("teamName", team.getName());
     * context.put("membersDeleted", 5);
     * 
     * return auditLogHelper.logDetailedEvent(
     *     AuditEventType.TEAM_DELETED,
     *     AuditActionType.DELETE,
     *     AuditResourceType.TEAM,
     *     teamId,
     *     "Team deletion with details",
     *     context,
     *     null,  // documentId (optional)
     *     null,  // collectionId (optional)
     *     AuditResultType.SUCCESS // result
     * ).then(/* continue with business logic *\/);
     * </pre>
     * 
     * @param eventType    Type of audit event
     * @param action       Action type
     * @param resourceType Resource type
     * @param resourceId   Resource ID (e.g., teamId, documentId, collectionId)
     * @param reason       Reason/description of the action
     * @param contextMap   Additional context data (can be null)
     * @param documentId   Document ID (optional, for document-related events)
     * @param collectionId Collection ID (optional, for collection-related events)
     * @param result       Result status (defaults to SUCCESS if null)
     * @return Mono that completes when audit log is saved
     */
    public Mono<Void> logDetailedEvent(
            AuditEventType eventType,
            AuditActionType action,
            AuditResourceType resourceType,
            String resourceId,
            String reason,
            Map<String, Object> contextMap,
            String documentId,
            String collectionId,
            AuditResultType result) {

        String effectiveResult = result != null ? result.name() : "SUCCESS";

        return Mono.zip(
                userContextService.getCurrentUsername().defaultIfEmpty(UserContextService.SYSTEM_USER),
                teamContextService.getTeamFromContext().onErrorResume(e -> Mono.just((String) null)))
                .flatMap(tuple -> {
                    String usernameFromContext = tuple.getT1();
                    String teamIdFromContext = tuple.getT2();

                    // Use username from contextMap as fallback if available (e.g., executedBy)
                    String username = usernameFromContext;
                    if (contextMap != null) {
                        Object executedByObj = contextMap.get("executedBy");
                        if (executedByObj != null && !executedByObj.toString().isEmpty()) {
                            username = executedByObj.toString();
                        }
                    }

                    // Use teamId from contextMap as fallback if security context doesn't have it
                    String teamId = teamIdFromContext;
                    if ((teamId == null || teamId.isEmpty()) && contextMap != null) {
                        Object teamIdObj = contextMap.get("teamId");
                        if (teamIdObj != null) {
                            teamId = teamIdObj.toString();
                        }
                    }

                    // Build metadata with context
                    AuditMetadata metadata = AuditMetadata.builder()
                            .reason(reason)
                            .context(contextMap != null ? contextMap : new HashMap<>())
                            .build();

                    // Log the event
                    return auditService.logEvent(
                            username, // userId
                            username, // username (from security context or contextMap fallback)
                            teamId, // teamId (from security context or contextMap fallback)
                            null, // ipAddress (not available in service layer)
                            null, // userAgent (not available in service layer)
                            eventType,
                            action.name(),
                            resourceType.name(),
                            resourceId, // resourceId
                            documentId, // documentId (optional)
                            collectionId, // collectionId (optional)
                            effectiveResult,
                            metadata,
                            null // complianceFlags
                    );
                })
                .doOnError(error -> log.error("Failed to log detailed audit event: {}", error.getMessage(), error))
                .onErrorResume(error -> Mono.empty()) // Don't fail the operation if audit logging fails
                .then();
    }

    /**
     * Overloaded method without result (defaults to SUCCESS)
     */
    public Mono<Void> logDetailedEvent(
            AuditEventType eventType,
            AuditActionType action,
            AuditResourceType resourceType,
            String resourceId,
            String reason,
            Map<String, Object> contextMap,
            String documentId,
            String collectionId) {
        return logDetailedEvent(eventType, action, resourceType, resourceId, reason, contextMap, documentId,
                collectionId, AuditResultType.SUCCESS);
    }

    /**
     * Overloaded method without documentId and collectionId
     */
    public Mono<Void> logDetailedEvent(
            AuditEventType eventType,
            AuditActionType action,
            AuditResourceType resourceType,
            String resourceId,
            String reason,
            Map<String, Object> contextMap) {
        return logDetailedEvent(eventType, action, resourceType, resourceId, reason, contextMap, null, null,
                AuditResultType.SUCCESS);
    }

    /**
     * Overloaded method without context map (simpler for basic cases)
     */
    public Mono<Void> logDetailedEvent(
            AuditEventType eventType,
            AuditActionType action,
            AuditResourceType resourceType,
            String resourceId,
            String reason) {
        return logDetailedEvent(eventType, action, resourceType, resourceId, reason, null, null, null,
                AuditResultType.SUCCESS);
    }

    /**
     * Find method by name in class
     */
    private Method findMethod(Class<?> clazz, String methodName) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.getName().equals(methodName)) {
                return method;
            }
        }
        // Check parent class
        Class<?> superClass = clazz.getSuperclass();
        if (superClass != null && superClass != Object.class) {
            return findMethod(superClass, methodName);
        }
        return null;
    }

    /**
     * Context object returned from logMethod for additional operations
     */
    @lombok.Data
    @lombok.Builder
    public static class AuditContext {
        private AuditService auditService; // Reference to audit service
        private LocalDateTime startTime;
        private AuditEventType eventType;
        private String action;
        private String resourceType;
        private String resourceId;
        private String documentId;
        private String collectionId;
        private String reason;
        private AuditLog annotation;

        /**
         * Log success result
         */
        public Mono<Void> logSuccess(ServerWebExchange exchange) {
            if (auditService == null) {
                log.warn("AuditService not available in AuditContext");
                return Mono.empty();
            }

            long durationMs = Duration.between(startTime, LocalDateTime.now()).toMillis();

            AuditMetadata metadata = AuditMetadata.builder()
                    .reason(reason)
                    .durationMs(durationMs)
                    .build();

            return auditService.logEvent(exchange, eventType, action, resourceType, resourceId, "SUCCESS", metadata)
                    .onErrorResume(error -> {
                        log.error("Failed to log audit success: {}", error.getMessage(), error);
                        return Mono.empty();
                    });
        }

        /**
         * Log failure result
         */
        public Mono<Void> logFailure(ServerWebExchange exchange, String errorMessage) {
            if (auditService == null) {
                log.warn("AuditService not available in AuditContext");
                return Mono.empty();
            }

            if (annotation != null && annotation.logOnSuccessOnly()) {
                return Mono.empty(); // Don't log failures if configured
            }

            long durationMs = Duration.between(startTime, LocalDateTime.now()).toMillis();

            AuditMetadata metadata = AuditMetadata.builder()
                    .reason(reason)
                    .durationMs(durationMs)
                    .errorMessage(errorMessage)
                    .build();

            return auditService.logEvent(exchange, eventType, action, resourceType, resourceId, "FAILED", metadata)
                    .onErrorResume(error -> {
                        log.error("Failed to log audit failure: {}", error.getMessage(), error);
                        return Mono.empty();
                    });
        }
    }
}
