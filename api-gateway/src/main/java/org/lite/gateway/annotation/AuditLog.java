package org.lite.gateway.annotation;

import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation for automatic audit logging
 * 
 * Usage examples:
 * 
 * Simple usage (method-level):
 * <pre>
 * {@code @AuditLog(eventType = AuditEventType.DOCUMENT_ACCESSED, action = AuditActionType.READ, resourceType = AuditResourceType.DOCUMENT)}
 * public Mono<ResponseEntity<?>> getDocument(@PathVariable String documentId, ServerWebExchange exchange) {
 *     // Method implementation
 * }
 * </pre>
 * 
 * With resource ID extraction:
 * <pre>
 * {@code @AuditLog(
 *     eventType = AuditEventType.DOCUMENT_DELETED,
 *     action = AuditActionType.DELETE,
 *     resourceType = AuditResourceType.DOCUMENT,
 *     resourceIdParam = "documentId"  // Extracts documentId from method parameters
 * )}
 * public Mono<ResponseEntity<?>> deleteDocument(@PathVariable String documentId, ServerWebExchange exchange) {
 *     // Method implementation
 * }
 * </pre>
 * 
 * Class-level annotation (applies to all methods):
 * <pre>
 * {@code @AuditLog(defaultEventType = AuditEventType.DOCUMENT_ACCESSED, defaultAction = AuditActionType.READ, defaultResourceType = AuditResourceType.DOCUMENT)}
 * @RestController
 * public class DocumentController {
 *     // All methods will be audited with defaults
 * }
 * </pre>
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface AuditLog {
    
    /**
     * Type of audit event
     */
    AuditEventType eventType() default AuditEventType.DOCUMENT_ACCESSED;
    
    /**
     * Action type: READ, CREATE, UPDATE, DELETE, EXPORT
     */
    AuditActionType action() default AuditActionType.READ;
    
    /**
     * Resource type: DOCUMENT, CHUNK, METADATA, EXPORT, USER, etc.
     */
    AuditResourceType resourceType() default AuditResourceType.DOCUMENT;
    
    /**
     * Parameter name to extract resource ID from (e.g., "documentId", "userId")
     * If not specified, will try to extract from common parameter names or request body
     */
    String resourceIdParam() default "";
    
    /**
     * Parameter name to extract document ID from (if different from resourceIdParam)
     */
    String documentIdParam() default "";
    
    /**
     * Parameter name to extract collection ID from
     */
    String collectionIdParam() default "";
    
    /**
     * Reason/context for the audit log (static text or SpEL expression)
     * Example: "User accessed document", "#methodName", "#{documentId}"
     */
    String reason() default "";
    
    /**
     * Whether to log on success only (true) or on both success and failure (false)
     */
    boolean logOnSuccessOnly() default false;
    
    /**
     * Whether to extract metadata from method result
     */
    boolean extractResultMetadata() default false;
    
    /**
     * Default event type for class-level annotation
     */
    AuditEventType defaultEventType() default AuditEventType.DOCUMENT_ACCESSED;
    
    /**
     * Default action for class-level annotation
     */
    AuditActionType defaultAction() default AuditActionType.READ;
    
    /**
     * Default resource type for class-level annotation
     */
    AuditResourceType defaultResourceType() default AuditResourceType.DOCUMENT;
}

