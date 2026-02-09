package org.lite.gateway.aspect;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.lite.gateway.annotation.AuditLog;
import org.lite.gateway.entity.AuditLog.AuditMetadata;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.service.AuditService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * AOP Aspect for automatic audit logging using @AuditLog annotation
 * 
 * This aspect intercepts methods annotated with @AuditLog and automatically
 * logs audit events with extracted context information.
 * 
 * Note: For reactive methods (returning Mono/Flux), the aspect handles
 * the reactive chain appropriately.
 */
@Aspect
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditLogAspect {

    private final AuditService auditService;

    /**
     * Intercept methods annotated with @AuditLog
     */
    @Around("@annotation(org.lite.gateway.annotation.AuditLog) || @within(org.lite.gateway.annotation.AuditLog)")
    public Object auditLog(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // Get annotation from method, or from class if method doesn't have it
        AuditLog methodAnnotation = AnnotationUtils.findAnnotation(method, AuditLog.class);
        AuditLog classAnnotation = AnnotationUtils.findAnnotation(joinPoint.getTarget().getClass(), AuditLog.class);

        // Determine which annotation to use and merge defaults if needed
        final AuditLog auditLogAnnotation;
        if (methodAnnotation != null) {
            auditLogAnnotation = methodAnnotation;
        } else if (classAnnotation != null) {
            auditLogAnnotation = classAnnotation;
        } else {
            // No annotation found, proceed normally
            return joinPoint.proceed();
        }

        // Extract context
        Object[] args = joinPoint.getArgs();
        final ServerWebExchange exchange = extractServerWebExchange(args);

        // Extract resource IDs from method parameters
        String extractedResourceId = extractParameterValue(args, method, auditLogAnnotation.resourceIdParam());
        final String documentId = extractParameterValue(args, method, auditLogAnnotation.documentIdParam());
        final String collectionId = extractParameterValue(args, method, auditLogAnnotation.collectionIdParam());

        // If resourceId not explicitly specified, try common parameter names
        if (extractedResourceId == null || extractedResourceId.isEmpty()) {
            extractedResourceId = extractCommonResourceId(args, method);
        }
        final String resourceId = extractedResourceId;

        // Determine event type, action, resource type (extract to final variables)
        // If method annotation exists, use it; otherwise use class annotation defaults
        final AuditEventType eventType;
        final AuditActionType actionEnum;
        final AuditResourceType resourceTypeEnum;

        if (methodAnnotation != null) {
            eventType = methodAnnotation.eventType();
            actionEnum = methodAnnotation.action();
            resourceTypeEnum = methodAnnotation.resourceType();
        } else {
            // Use class-level defaults
            eventType = classAnnotation.defaultEventType();
            actionEnum = classAnnotation.defaultAction();
            resourceTypeEnum = classAnnotation.defaultResourceType();
        }

        final String action = actionEnum.name();
        final String resourceType = resourceTypeEnum.name();
        final String reason = auditLogAnnotation.reason();
        final boolean logOnSuccessOnly = auditLogAnnotation.logOnSuccessOnly();

        final LocalDateTime startTime = LocalDateTime.now();

        try {
            // For reactive methods (returning Mono/Flux)
            Object result = joinPoint.proceed();

            if (result instanceof Mono) {
                @SuppressWarnings("unchecked")
                Mono<Object> monoResult = (Mono<Object>) result;

                return monoResult
                        .doOnSuccess(response -> {
                            LocalDateTime endTime = LocalDateTime.now();
                            long durationMs = Duration.between(startTime, endTime).toMillis();

                            logAuditEvent(exchange, eventType, action, resourceType, resourceId,
                                    documentId, collectionId, "SUCCESS", reason, durationMs, null, auditLogAnnotation);
                        })
                        .doOnError(error -> {
                            if (!logOnSuccessOnly) {
                                LocalDateTime endTime = LocalDateTime.now();
                                long durationMs = Duration.between(startTime, endTime).toMillis();

                                String errorMsg = error.getMessage() != null ? error.getMessage()
                                        : error.getClass().getSimpleName();
                                logAuditEvent(exchange, eventType, action, resourceType, resourceId,
                                        documentId, collectionId, "FAILED", reason, durationMs, errorMsg,
                                        auditLogAnnotation);
                            }
                        });
            } else if (result instanceof reactor.core.publisher.Flux) {
                // For Flux, log on subscription (not practical to log every element)
                logAuditEvent(exchange, eventType, action, resourceType, resourceId,
                        documentId, collectionId, "SUCCESS", reason, 0L, null, auditLogAnnotation);
                return result;
            } else {
                // Non-reactive method - log synchronously
                LocalDateTime endTime = LocalDateTime.now();
                long durationMs = Duration.between(startTime, endTime).toMillis();

                logAuditEvent(exchange, eventType, action, resourceType, resourceId,
                        documentId, collectionId, "SUCCESS", reason, durationMs, null, auditLogAnnotation);

                return result;
            }
        } catch (Throwable throwable) {
            // Handle synchronous exceptions
            if (!logOnSuccessOnly) {
                LocalDateTime endTime = LocalDateTime.now();
                long durationMs = Duration.between(startTime, endTime).toMillis();

                String errorMsg = throwable.getMessage() != null ? throwable.getMessage()
                        : throwable.getClass().getSimpleName();
                logAuditEvent(exchange, eventType, action, resourceType, resourceId,
                        documentId, collectionId, "FAILED", reason, durationMs, errorMsg, auditLogAnnotation);
            }
            throw throwable;
        }
    }

    /**
     * Extract ServerWebExchange from method arguments
     */
    private ServerWebExchange extractServerWebExchange(Object[] args) {
        for (Object arg : args) {
            if (arg instanceof ServerWebExchange) {
                return (ServerWebExchange) arg;
            }
        }
        return null;
    }

    /**
     * Extract parameter value by name from method arguments
     */
    private String extractParameterValue(Object[] args, Method method, String paramName) {
        if (paramName == null || paramName.isEmpty()) {
            return null;
        }

        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(paramName) ||
                    parameters[i].getName().equals("arg" + i)) { // Handle synthetic parameter names

                Object value = args[i];
                if (value != null) {
                    // Handle path variables, request params, etc.
                    if (value instanceof String) {
                        return (String) value;
                    }
                    // Could also extract from Map, request body, etc.
                }
                break;
            }
        }
        return null;
    }

    /**
     * Extract common resource IDs from method parameters (documentId, userId, etc.)
     */
    private String extractCommonResourceId(Object[] args, Method method) {
        Parameter[] parameters = method.getParameters();
        String[] commonNames = { "documentId", "userId", "collectionId", "resourceId", "id" };

        for (int i = 0; i < parameters.length; i++) {
            String paramName = parameters[i].getName().toLowerCase();
            for (String commonName : commonNames) {
                if (paramName.contains(commonName)) {
                    Object value = args[i];
                    if (value instanceof String) {
                        return (String) value;
                    }
                }
            }
        }
        return null;
    }

    /**
     * Log the audit event
     */
    private final TeamContextService teamContextService;

    // ... (rest of class) ...

    /**
     * Log the audit event
     */
    private void logAuditEvent(ServerWebExchange exchange, AuditEventType eventType, String action,
            String resourceType, String resourceId, String documentId, String collectionId,
            String result, String reason, long durationMs, String errorMessage,
            AuditLog annotation) {

        if (exchange != null) {
            // Use ServerWebExchange-based logging
            AuditMetadata metadata = AuditMetadata.builder()
                    .reason(reason)
                    .durationMs(durationMs)
                    .errorMessage(errorMessage)
                    .build();

            auditService.logEvent(exchange, eventType, action, resourceType, resourceId, result, metadata)
                    .subscribe(
                            null, // onSuccess
                            error -> log.error("Failed to log audit event: {}", error.getMessage(), error));
        } else {
            // Fallback: try to resolve context from security context
            AuditMetadata metadata = AuditMetadata.builder()
                    .reason(reason)
                    .durationMs(durationMs)
                    .errorMessage(errorMessage)
                    .build();

            // Try to resolve teamId from context
            teamContextService.getTeamFromContext()
                    .defaultIfEmpty("") // Handle empty context gracefully
                    .flatMap(teamId -> {
                        String finalTeamId = teamId.isEmpty() ? null : teamId;

                        return auditService.logEvent(
                                UserContextService.SYSTEM_USER,
                                UserContextService.SYSTEM_USER,
                                finalTeamId,
                                null, // ipAddress
                                null, // userAgent
                                eventType,
                                action,
                                resourceType,
                                resourceId,
                                documentId,
                                collectionId,
                                result,
                                metadata,
                                null // complianceFlags
                        );
                    })
                    .subscribe(
                            null,
                            error -> log.error("Failed to log audit event (fallback): {}", error.getMessage(), error));
        }
    }
}
