package org.lite.gateway.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.annotation.AuditLog;
import org.lite.gateway.config.S3Properties;
import org.lite.gateway.dto.*;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.lite.gateway.service.S3Service;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubDocumentUploadController {
    
    private final S3Service s3Service;
    private final S3Properties s3Properties;
    private final KnowledgeHubDocumentService documentService;
    private final TeamContextService teamContextService;
    private final KnowledgeHubDocumentRepository documentRepository;
    private final UserContextService userContextService;
    private final UserService userService;
    private final TeamService teamService;
    
    /**
     * Initiate upload: Get presigned URL for client-side upload
     */
    @PostMapping("/upload/initiate")
    public Mono<ResponseEntity<UploadResponse>> initiateUpload(
            @Valid @RequestBody UploadInitiateRequest request,
            ServerWebExchange exchange) {
        
        log.info("Initiating upload for file: {}", request.getFileName());
        
        // Validate file size
        if (request.getFileSize() > s3Properties.getMaxFileSize()) {
            return Mono.just(ResponseEntity.badRequest()
                .body(UploadResponse.error("File size exceeds maximum allowed: " + s3Properties.getMaxFileSize())));
        }
        
        // Use service to initiate upload (generates presigned URL and creates document)
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> documentService.initiateDocumentUpload(request, teamId)
                        .map(result -> {
                            var presignedUrl = result.presignedUrl();
                            var document = result.document();
                            
                            return ResponseEntity.ok(UploadResponse.builder()
                                    .documentId(document.getDocumentId())
                                    .uploadUrl(presignedUrl.getUploadUrl())
                                    .s3Key(document.getS3Key())
                                    .expiresInSeconds(presignedUrl.getExpiresInSeconds())
                                    .requiredHeaders(presignedUrl.getRequiredHeaders())
                                    .instructions("Upload the file using PUT request to the uploadUrl")
                                    .build());
                        }))
                .onErrorResume(error -> {
                    log.error("Error initiating upload", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(UploadResponse.error("Failed to initiate upload: " + error.getMessage())));
                });
    }
    
    /**
     * Bulk initiate upload: Get presigned URLs for multiple files
     */
    @PostMapping("/upload/initiate/bulk")
    public Mono<ResponseEntity<BulkUploadResponse>> bulkInitiateUpload(
            @Valid @RequestBody BulkUploadInitiateRequest request,
            ServerWebExchange exchange) {
        
        log.info("Initiating bulk upload for {} files", request.getFiles().size());
        
        // Validate total size if needed
        long totalSize = request.getFiles().stream()
                .mapToLong(UploadInitiateRequest::getFileSize)
                .sum();
        
        if (totalSize > s3Properties.getMaxFileSize() * request.getFiles().size()) {
            return Mono.just(ResponseEntity.badRequest()
                    .body(BulkUploadResponse.error("Total file size exceeds maximum allowed")));
        }
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> {
                    List<Mono<UploadResponse>> uploadResponses = new ArrayList<>();
                    
                    for (UploadInitiateRequest fileRequest : request.getFiles()) {
                        // Validate individual file size
                        if (fileRequest.getFileSize() > s3Properties.getMaxFileSize()) {
                            uploadResponses.add(Mono.just(UploadResponse.builder()
                                    .error("File size exceeds maximum: " + fileRequest.getFileName())
                                    .status("ERROR")
                                    .build()));
                            continue;
                        }
                        
                        uploadResponses.add(documentService.initiateDocumentUpload(fileRequest, teamId)
                                .map(result -> {
                                    var presignedUrl = result.presignedUrl();
                                    var document = result.document();
                                    return UploadResponse.builder()
                                            .documentId(document.getDocumentId())
                                            .uploadUrl(presignedUrl.getUploadUrl())
                                            .s3Key(document.getS3Key())
                                            .expiresInSeconds(presignedUrl.getExpiresInSeconds())
                                            .requiredHeaders(presignedUrl.getRequiredHeaders())
                                            .instructions("Upload the file using PUT request to the uploadUrl")
                                            .status("READY")
                                            .build();
                                })
                                .onErrorReturn(UploadResponse.builder()
                                        .error("Failed to initiate upload: " + fileRequest.getFileName())
                                        .status("ERROR")
                                        .build()));
                    }
                    
                    return Flux.fromIterable(uploadResponses)
                            .flatMap(mono -> mono)
                            .collectList()
                            .map(uploads -> {
                                int successCount = (int) uploads.stream()
                                        .filter(u -> "READY".equals(u.getStatus()))
                                        .count();
                                int failureCount = uploads.size() - successCount;
                                
                                return ResponseEntity.ok(BulkUploadResponse.builder()
                                        .uploads(uploads)
                                        .successCount(successCount)
                                        .failureCount(failureCount)
                                        .message(String.format("Initiated %d/%d files successfully", successCount, uploads.size()))
                                        .build());
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error initiating bulk upload", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(BulkUploadResponse.error("Failed to initiate bulk upload: " + error.getMessage())));
                });
    }
    
    /**
     * Client confirms upload completion and triggers processing
     */
    @PostMapping("/upload/{documentId}/complete")
    @AuditLog(
        eventType = AuditEventType.DOCUMENT_UPLOADED,
        action = AuditActionType.CREATE,
        resourceType = AuditResourceType.DOCUMENT,
        resourceIdParam = "documentId",
        documentIdParam = "documentId",
        reason = "Document upload completed and processing queued"
    )
    public Mono<ResponseEntity<UploadResponse>> completeUpload(
            @PathVariable String documentId,
            @Valid @RequestBody UploadCompleteRequest request,
            ServerWebExchange exchange) {
        
        log.info("Completing upload for document: {}", documentId);
        
        // Update document status and trigger processing
        // Note: We skip fileExists check because presigned URLs are already authenticated
        // and the check can fail in environments with SSL certificate issues
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> documentService.completeUpload(documentId, request.getS3Key())
                        .map(document -> ResponseEntity.ok(UploadResponse.builder()
                                .documentId(document.getDocumentId())
                                .status("UPLOADED")
                                .message("File uploaded successfully. Processing has been queued.")
                                .build()))
                        .onErrorResume(error -> {
                            log.error("Error completing upload for document: {}", documentId, error);
                            // Mark document as FAILED with error message
                            return documentService.updateStatus(documentId, "FAILED", error.getMessage())
                                    .then(Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                            .body(UploadResponse.error("Failed to complete upload: " + error.getMessage()))));
                        }));
    }
    
    /**
     * Get document status
     */
    @GetMapping("/{documentId}/status")
    public Mono<ResponseEntity<KnowledgeHubDocument>> getDocumentStatus(@PathVariable String documentId, ServerWebExchange exchange) {
        log.info("Getting status for document: {}", documentId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> documentService.getDocumentStatus(documentId, teamId)
                        .map(ResponseEntity::ok))
                .onErrorResume(error -> {
                    log.error("Error getting document status", error);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
    
    /**
     * Get all documents for current team
     */
    @GetMapping
    public Mono<ResponseEntity<?>> getAllDocuments(
            @RequestParam(required = false) String collectionId,
            @RequestParam(required = false) String status,
            ServerWebExchange exchange) {
        
        log.info("Getting documents - collectionId: {}, status: {}", collectionId, status);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> {
                    Flux<KnowledgeHubDocument> documents;
                    
                    if (collectionId != null && status != null) {
                        // Filter by both collection and status
                        documents = documentRepository.findByTeamIdAndCollectionId(teamId, collectionId)
                                .filter(doc -> status.equals(doc.getStatus()));
                    } else if (collectionId != null) {
                        // Filter by collection only
                        documents = documentRepository.findByTeamIdAndCollectionId(teamId, collectionId);
                    } else if (status != null) {
                        // Filter by status only
                        documents = documentRepository.findByTeamIdAndStatus(teamId, status);
                    } else {
                        // Get all documents for team
                        documents = documentRepository.findByTeamId(teamId);
                    }
                    
                    return documents.collectList()
                            .<ResponseEntity<?>>map(ResponseEntity::ok);
                })
                .onErrorResume(error -> {
                    log.error("Error getting documents", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body("Failed to get documents: " + error.getMessage()));
                });
    }
    
    /**
     * Generate presigned URL for downloading
     */
    @GetMapping("/{documentId}/download")
    public Mono<ResponseEntity<Object>> generateDownloadUrl(@PathVariable String documentId, ServerWebExchange exchange) {
        log.info("Generating download URL for document: {}", documentId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> documentService.getDocumentById(documentId, teamId)
                        .flatMap(document -> s3Service.generatePresignedDownloadUrl(document.getS3Key())
                                .map(url -> {
                                    java.util.Map<String, String> response = java.util.Map.of(
                                            "downloadUrl", url,
                                            "fileName", document.getFileName()
                                    );
                                    return ResponseEntity.<Object>ok(response);
                                })))
                .onErrorResume(error -> {
                    log.error("Error generating download URL", error);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
    
    /**
     * Hard delete document (deletes everything including S3 files)
     * Note: This must come before the regular delete endpoint to ensure proper route matching
     */
    @DeleteMapping("/{documentId}/hard")
    @AuditLog(
        eventType = AuditEventType.DOCUMENT_HARD_DELETED,
        action = AuditActionType.DELETE,
        resourceType = AuditResourceType.DOCUMENT,
        resourceIdParam = "documentId",
        documentIdParam = "documentId",
        reason = "Hard delete removes all data including S3 files, chunks, metadata, and graph entities"
    )
    public Mono<ResponseEntity<Object>> hardDeleteDocument(@PathVariable String documentId, ServerWebExchange exchange) {
        log.info("Hard deleting document (including S3 files): {}", documentId);
        
        return Mono.zip(
                teamContextService.getTeamFromContext(exchange),
                userContextService.getCurrentUsername(exchange)
        )
        .flatMap(tuple -> {
            String teamId = tuple.getT1();
            String username = tuple.getT2();
            
            return userService.findByUsername(username)
                    .flatMap(user ->
                            teamService.hasRole(teamId, user.getId(), "ADMIN")
                                    .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                                    .switchIfEmpty(Mono.error(new AccessDeniedException(
                                            "Admin access required for team " + teamId)))
                                    .then(documentService.hardDeleteDocument(documentId, teamId))
                    )
                    .then(Mono.just(ResponseEntity.ok().build()));
        })
        .onErrorResume(error -> {
            log.error("Error hard deleting document: {}", documentId, error);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to hard delete document: " + error.getMessage()));
        });
    }
    
    /**
     * Delete document (soft delete - only if S3 file doesn't exist)
     */
    @DeleteMapping("/{documentId}")
    public Mono<ResponseEntity<Object>> deleteDocument(@PathVariable String documentId, ServerWebExchange exchange) {
        log.info("Deleting document: {}", documentId);
        
        return Mono.zip(
                teamContextService.getTeamFromContext(exchange),
                userContextService.getCurrentUsername(exchange)
        )
        .flatMap(tuple -> {
            String teamId = tuple.getT1();
            String username = tuple.getT2();
            
            return userService.findByUsername(username)
                    .flatMap(user ->
                            teamService.hasRole(teamId, user.getId(), "ADMIN")
                                    .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                                    .switchIfEmpty(Mono.error(new AccessDeniedException(
                                            "Admin access required for team " + teamId)))
                                    .then(documentService.deleteDocument(documentId, teamId))
                    )
                    .then(Mono.just(ResponseEntity.ok().build()));
        })
        .onErrorResume(error -> {
            log.error("Error deleting document: {}", documentId, error);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete document: " + error.getMessage()));
        });
    }

    /**
     * Delete document artifacts from a specific pipeline stage (processed data, metadata, embeddings)
     */
    @DeleteMapping("/{documentId}/artifacts/{scope}")
    public Mono<ResponseEntity<Object>> deleteDocumentArtifacts(@PathVariable String documentId,
                                                                @PathVariable String scope,
                                                                ServerWebExchange exchange) {
        log.info("Deleting document artifacts for document {} with scope {}", documentId, scope);

        KnowledgeHubDocumentService.DeletionScope deletionScope;
        try {
            deletionScope = KnowledgeHubDocumentService.DeletionScope.valueOf(scope.toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Mono.just(ResponseEntity.badRequest()
                    .body("Invalid deletion scope: " + scope));
        }

        return Mono.zip(
                teamContextService.getTeamFromContext(exchange),
                userContextService.getCurrentUsername(exchange)
        )
        .flatMap(tuple -> {
            String teamId = tuple.getT1();
            String username = tuple.getT2();

            return userService.findByUsername(username)
                    .flatMap(user ->
                            teamService.hasRole(teamId, user.getId(), "ADMIN")
                                    .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                                    .switchIfEmpty(Mono.error(new AccessDeniedException(
                                            "Admin access required for team " + teamId)))
                                    .then(documentService.deleteDocumentArtifacts(documentId, teamId, deletionScope))
                    )
                    .then(Mono.just(ResponseEntity.ok().build()));
        })
        .onErrorResume(error -> {
            log.error("Error deleting artifacts for document {} with scope {}: {}", documentId, scope, error.getMessage(), error);
            return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Failed to delete document artifacts: " + error.getMessage()));
        });
    }
}

