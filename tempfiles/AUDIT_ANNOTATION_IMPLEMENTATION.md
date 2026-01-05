# Audit Annotation-Based Logging Implementation

## Overview

We've implemented two approaches for annotation-based audit logging:

1. **AOP Aspect Approach** (Recommended for most cases)
   - Fully automatic via `@AuditLog` annotation
   - Works with reactive and non-reactive methods
   - Zero boilerplate code

2. **Manual Helper Approach** (Alternative)
   - Still uses annotation for metadata
   - More explicit control
   - Better for complex cases

## Approach 1: AOP Aspect (Automatic)

### Setup

The AOP aspect is automatically enabled. Just add the annotation!

### Usage Examples

#### Simple Controller Method

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
    return documentService.getDocument(documentId)
            .map(ResponseEntity::ok);
    // Audit event is logged automatically!
}
```

#### Delete Operation

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
    return documentService.deleteDocument(documentId)
            .thenReturn(ResponseEntity.noContent().build());
}
```

#### Export Operation

```java
@PostMapping("/collections/export")
@AuditLog(
    eventType = AuditEventType.EXPORT_INITIATED,
    action = "EXPORT",
    resourceType = "EXPORT",
    reason = "Collection export initiated by user"
)
public Mono<ResponseEntity<?>> exportCollections(
        @RequestBody ExportRequest request,
        ServerWebExchange exchange) {
    return exportService.export(request);
}
```

#### Class-Level Annotation

```java
@AuditLog(
    defaultEventType = AuditEventType.DOCUMENT_ACCESSED,
    defaultAction = "READ",
    defaultResourceType = "DOCUMENT"
)
@RestController
@RequestMapping("/api/documents")
public class DocumentController {
    
    // All methods inherit defaults
    
    @GetMapping("/{documentId}")
    // Uses: DOCUMENT_ACCESSED, READ, DOCUMENT
    public Mono<ResponseEntity<Document>> getDocument(@PathVariable String documentId) { }
    
    @DeleteMapping("/{documentId}")
    @AuditLog(eventType = AuditEventType.DOCUMENT_DELETED, action = "DELETE")
    // Overrides: DOCUMENT_DELETED, DELETE (keeps DOCUMENT from class)
    public Mono<ResponseEntity<Void>> deleteDocument(@PathVariable String documentId) { }
}
```

## Approach 2: Manual Helper (Explicit Control)

For cases where AOP doesn't work or you need more control:

```java
@Autowired
private AuditLogHelper auditLogHelper;

@GetMapping("/documents/{documentId}")
@AuditLog(eventType = AuditEventType.DOCUMENT_ACCESSED, action = "READ")
public Mono<ResponseEntity<Document>> getDocument(
        @PathVariable String documentId,
        ServerWebExchange exchange) {
    
    return auditLogHelper.logMethod(this, "getDocument", exchange, documentId)
            .flatMap(context -> {
                // Your business logic
                return documentService.getDocument(documentId)
                        .map(ResponseEntity::ok)
                        .doOnSuccess(result -> context.logSuccess(exchange))
                        .doOnError(error -> context.logFailure(exchange, error.getMessage()));
            });
}
```

## Features

### Automatic Context Extraction

The aspect automatically extracts:
- **User Info**: From `ServerWebExchange` (via `UserContextService`)
- **Team ID**: From security context (via `TeamContextService`)
- **IP Address**: From request
- **User Agent**: From request headers
- **Resource IDs**: From method parameters

### Parameter Extraction

Automatically recognizes these parameter names:
- `documentId`, `document_id`
- `userId`, `user_id`
- `collectionId`, `collection_id`
- `resourceId`, `resource_id`
- `id` (fallback)

Or specify explicitly:
```java
@AuditLog(
    resourceIdParam = "docId",  // Extract from parameter named "docId"
    documentIdParam = "docId",
    collectionIdParam = "colId"
)
```

### Reactive Support

Works with:
- `Mono<T>` - Logs on success/error
- `Flux<T>` - Logs on subscription
- Non-reactive methods - Logs synchronously

### Success/Failure Handling

```java
@AuditLog(
    logOnSuccessOnly = true  // Only log if method succeeds
)
```

## Integration Points

### Where to Add Audit Logging

1. **Document Operations**
   - Upload, view, delete, hard delete
   - Metadata extraction
   
2. **Chunk Operations**
   - Read, decrypt, create, delete
   
3. **Export Operations**
   - Initiate, complete, download
   
4. **Authentication**
   - Login, logout, failed attempts
   
5. **Admin Actions**
   - User creation, role changes
   - Configuration changes

## Examples by Service

### ❌ VaultEncryptionService (Why NOT to Audit Low-Level Services)

**Important:** We removed audit logging from `VaultEncryptionService.decrypt()` because:

1. **No User Context**: Low-level decryption happens as an internal implementation detail and doesn't have access to `ServerWebExchange` or user context
2. **Too Noisy**: Vault decryption happens frequently (on every secret read) and would create audit log spam
3. **Implementation Detail**: Vault decryption is not a user action - it's how secrets are retrieved internally

**When to Audit:**
- ✅ Audit **user actions** at the controller/service layer where you have `ServerWebExchange`
- ✅ Audit **business operations** (document access, exports, etc.)
- ❌ Don't audit **internal implementation details** (low-level crypto operations, file I/O, etc.)

If you need to audit vault/secret access, audit at the business logic level where you have user context (e.g., when a specific secret is accessed in response to a user action).

### KnowledgeHubDocumentController

```java
@GetMapping("/documents/{documentId}")
@AuditLog(
    eventType = AuditEventType.DOCUMENT_ACCESSED,
    action = "READ",
    resourceType = "DOCUMENT"
)
public Mono<ResponseEntity<?>> getDocument(
        @PathVariable String documentId,
        ServerWebExchange exchange) {
    // Implementation
}

@DeleteMapping("/documents/{documentId}/hard")
@AuditLog(
    eventType = AuditEventType.DOCUMENT_HARD_DELETED,
    action = "DELETE",
    resourceType = "DOCUMENT"
)
public Mono<ResponseEntity<Void>> hardDeleteDocument(
        @PathVariable String documentId,
        ServerWebExchange exchange) {
    // Implementation
}
```

### CollectionExportController

```java
@PostMapping
@AuditLog(
    eventType = AuditEventType.EXPORT_INITIATED,
    action = "EXPORT",
    resourceType = "EXPORT",
    reason = "Collection export job queued"
)
public Mono<ResponseEntity<?>> queueExport(
        @RequestBody Map<String, Object> request,
        ServerWebExchange exchange) {
    // Implementation
}
```

## Best Practices

1. **Always Include ServerWebExchange**: Required for automatic context extraction
2. **Use Meaningful Reasons**: Add context with `reason` parameter
3. **Specify Resource IDs**: Use `resourceIdParam` when parameter names don't match conventions
4. **Class-Level for Consistency**: Use class-level annotations for controllers with similar operations
5. **Override When Needed**: Override class-level defaults with method-level annotations

## Limitations & Considerations

1. **Reactive Methods**: AOP works with reactive methods, but there may be edge cases
2. **Parameter Names**: Method parameter names must be available (not obfuscated) for auto-extraction
3. **ServerWebExchange Required**: Automatic context extraction requires `ServerWebExchange` parameter
4. **AOP Proxies**: Spring AOP uses proxies, which may affect some advanced use cases

## Testing

To test audit logging:

1. Call an annotated endpoint
2. Check MongoDB `audit_logs` collection
3. Verify event type, action, resource IDs are correct
4. Verify user/team context is extracted correctly

## Troubleshooting

**Annotation not working?**
- Ensure `@AuditLog` annotation is on method or class
- Verify `ServerWebExchange` parameter exists
- Check logs for aspect errors

**Resource ID not extracted?**
- Specify `resourceIdParam` explicitly
- Ensure parameter name matches annotation value
- Check if parameter is actually present in method signature

**Context not extracted?**
- Ensure `ServerWebExchange` is a method parameter
- Verify user is authenticated
- Check security context configuration

