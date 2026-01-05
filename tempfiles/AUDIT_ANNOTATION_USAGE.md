# Audit Logging with @AuditLog Annotation

## Overview

The `@AuditLog` annotation provides declarative audit logging for controller methods and service methods. Simply add the annotation to automatically log audit events.

## Quick Start

### 1. Basic Usage (Method-Level)

```java
@GetMapping("/documents/{documentId}")
@AuditLog(
    eventType = AuditEventType.DOCUMENT_ACCESSED,
    action = "READ",
    resourceType = "DOCUMENT",
    resourceIdParam = "documentId"
)
public Mono<ResponseEntity<Document>> getDocument(
        @PathVariable String documentId,
        ServerWebExchange exchange) {
    // Method implementation
    // Audit event is logged automatically on success/failure
}
```

### 2. Automatic Resource ID Extraction

The aspect automatically tries to extract resource IDs from common parameter names:

```java
@DeleteMapping("/documents/{documentId}")
@AuditLog(
    eventType = AuditEventType.DOCUMENT_DELETED,
    action = "DELETE",
    resourceType = "DOCUMENT"
    // resourceIdParam not needed - automatically extracts "documentId"
)
public Mono<ResponseEntity<Void>> deleteDocument(
        @PathVariable String documentId,
        ServerWebExchange exchange) {
    // Automatically extracts documentId as resourceId
}
```

### 3. Class-Level Annotation (All Methods)

```java
@AuditLog(
    defaultEventType = AuditEventType.DOCUMENT_ACCESSED,
    defaultAction = "READ",
    defaultResourceType = "DOCUMENT"
)
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    
    // All methods inherit the default audit log settings
    // Can override with method-level annotations
    
    @GetMapping("/{documentId}")
    // Uses class-level defaults
    public Mono<ResponseEntity<Document>> getDocument(@PathVariable String documentId) { }
    
    @DeleteMapping("/{documentId}")
    @AuditLog(eventType = AuditEventType.DOCUMENT_DELETED, action = "DELETE")
    // Overrides action and eventType, keeps resourceType from class
    public Mono<ResponseEntity<Void>> deleteDocument(@PathVariable String documentId) { }
}
```

### 4. Multiple Resource IDs

```java
@GetMapping("/collections/{collectionId}/documents/{documentId}")
@AuditLog(
    eventType = AuditEventType.DOCUMENT_ACCESSED,
    action = "READ",
    resourceType = "DOCUMENT",
    resourceIdParam = "documentId",
    documentIdParam = "documentId",
    collectionIdParam = "collectionId"
)
public Mono<ResponseEntity<Document>> getDocument(
        @PathVariable String collectionId,
        @PathVariable String documentId,
        ServerWebExchange exchange) {
    // Logs with documentId, collectionId, and documentId again
}
```

### 5. Custom Reason/Context

```java
@AuditLog(
    eventType = AuditEventType.EXPORT_INITIATED,
    action = "EXPORT",
    resourceType = "EXPORT",
    reason = "User initiated collection export"
)
public Mono<ResponseEntity<?>> exportCollection(
        @RequestBody ExportRequest request,
        ServerWebExchange exchange) {
    // Reason is included in audit log metadata
}
```

### 6. Log Only on Success

```java
@AuditLog(
    eventType = AuditEventType.CHUNK_DECRYPTED,
    action = "READ",
    resourceType = "CHUNK",
    logOnSuccessOnly = true  // Only log if method succeeds
)
public Mono<ResponseEntity<String>> decryptChunk(
        @PathVariable String chunkId,
        ServerWebExchange exchange) {
    // Only logs on success, ignores failures
}
```

## Supported Parameter Names (Auto-Extraction)

The aspect automatically recognizes these common parameter names:
- `documentId`, `document_id`
- `userId`, `user_id`
- `collectionId`, `collection_id`
- `resourceId`, `resource_id`
- `id` (as fallback)

## How It Works

1. **AOP Aspect**: `AuditLogAspect` intercepts methods with `@AuditLog` annotation
2. **Context Extraction**: Automatically extracts:
   - User info from `ServerWebExchange`
   - Team ID from security context
   - IP address and user agent
   - Resource IDs from method parameters
3. **Reactive Support**: Handles both reactive (`Mono`/`Flux`) and non-reactive methods
4. **Automatic Logging**: Logs on success or failure (configurable)

## Configuration

AOP is enabled via `AuditAspectConfig`. Spring Boot includes AOP support by default.

## Limitations

1. **Reactive Methods**: Works best with methods that return `Mono`. For `Flux`, logs on subscription.
2. **Parameter Extraction**: Parameter names must match or be specified explicitly via annotation attributes.
3. **ServerWebExchange Required**: For automatic user/team context extraction, methods should include `ServerWebExchange` parameter.

## Alternative: Manual Logging

If annotation-based logging doesn't work for your use case, you can still use the service directly:

```java
@Autowired
private AuditService auditService;

public Mono<ResponseEntity<?>> someMethod(ServerWebExchange exchange) {
    return serviceCall()
        .doOnSuccess(result -> {
            auditService.logEvent(exchange, 
                AuditEventType.DOCUMENT_ACCESSED,
                "READ", "DOCUMENT", documentId, "SUCCESS", null)
                .subscribe();
        });
}
```

## Examples by Use Case

### Document Upload
```java
@PostMapping("/documents")
@AuditLog(
    eventType = AuditEventType.DOCUMENT_UPLOADED,
    action = "CREATE",
    resourceType = "DOCUMENT"
)
public Mono<ResponseEntity<Document>> uploadDocument(
        @RequestPart("file") FilePart file,
        ServerWebExchange exchange) {
    // Automatically logs document upload
}
```

### Metadata Extraction
```java
@PostMapping("/documents/{documentId}/metadata")
@AuditLog(
    eventType = AuditEventType.METADATA_EXTRACTED,
    action = "CREATE",
    resourceType = "METADATA",
    resourceIdParam = "documentId",
    documentIdParam = "documentId"
)
public Mono<ResponseEntity<Metadata>> extractMetadata(
        @PathVariable String documentId,
        ServerWebExchange exchange) {
    // Logs metadata extraction
}
```

### Export Initiation
```java
@PostMapping("/collections/export")
@AuditLog(
    eventType = AuditEventType.EXPORT_INITIATED,
    action = "EXPORT",
    resourceType = "EXPORT",
    reason = "Collection export initiated"
)
public Mono<ResponseEntity<?>> exportCollections(
        @RequestBody ExportRequest request,
        ServerWebExchange exchange) {
    // Logs export initiation
}
```

### Hard Delete
```java
@DeleteMapping("/documents/{documentId}/hard")
@AuditLog(
    eventType = AuditEventType.DOCUMENT_HARD_DELETED,
    action = "DELETE",
    resourceType = "DOCUMENT",
    resourceIdParam = "documentId"
)
public Mono<ResponseEntity<Void>> hardDeleteDocument(
        @PathVariable String documentId,
        ServerWebExchange exchange) {
    // Logs hard delete operation
}
```

## Best Practices

1. **Use Method-Level Annotations**: More explicit and easier to understand
2. **Specify Resource IDs**: Use `resourceIdParam` when parameter names don't match conventions
3. **Include ServerWebExchange**: Required for automatic user/team context extraction
4. **Add Meaningful Reasons**: Use `reason` parameter for additional context
5. **Test Both Success and Failure**: Ensure audit logs capture both scenarios


