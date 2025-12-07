package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.S3Properties;
import org.lite.gateway.dto.UploadInitiateRequest;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.event.KnowledgeHubDocumentProcessingEvent;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.repository.KnowledgeHubChunkRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentMetaDataRepository;
import org.lite.gateway.repository.TeamRepository;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.lite.gateway.service.S3Service;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.lite.gateway.util.AuditLogHelper;
import org.lite.gateway.repository.KnowledgeHubCollectionRepository;
import org.lite.gateway.enums.AuditResultType;
import org.lite.gateway.enums.DocumentStatus;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubDocumentServiceImpl implements KnowledgeHubDocumentService {

        private final KnowledgeHubDocumentRepository documentRepository;
        private final TeamRepository teamRepository;
        private final ApplicationEventPublisher eventPublisher;
        private final S3Service s3Service;
        private final S3Properties s3Properties;
        private final ChunkEncryptionService chunkEncryptionService;
        private final KnowledgeHubChunkRepository chunkRepository;
        private final KnowledgeHubDocumentMetaDataRepository metadataRepository;
        private final KnowledgeHubCollectionRepository collectionRepository;
        private final LinqMilvusStoreService milvusStoreService;
        private final org.lite.gateway.service.Neo4jGraphService neo4jGraphService;
        private final org.lite.gateway.repository.GraphExtractionJobRepository graphExtractionJobRepository;
        private final AuditLogHelper auditLogHelper;

        @Override
        public Mono<DocumentInitiationResult> initiateDocumentUpload(UploadInitiateRequest request, String teamId) {
                // Validate team exists
                return teamRepository.existsById(teamId)
                                .flatMap(exists -> {
                                        if (!exists) {
                                                return Mono.error(new RuntimeException("Team not found: " + teamId));
                                        }

                                        // Generate unique document ID
                                        String documentId = UUID.randomUUID().toString();

                                        // Build S3 key
                                        String s3Key = s3Properties.buildRawKey(
                                                        teamId,
                                                        request.getCollectionId(),
                                                        documentId,
                                                        request.getFileName());

                                        // Generate presigned URL
                                        return s3Service.generatePresignedUploadUrl(s3Key, request.getContentType())
                                                        .flatMap(presignedUrl -> {
                                                                // Create document record
                                                                return createDocument(request, s3Key, documentId,
                                                                                teamId)
                                                                                .map(document -> new DocumentInitiationResult(
                                                                                                document,
                                                                                                presignedUrl));
                                                        });
                                });
        }

        @Override
        public Mono<KnowledgeHubDocument> createDocument(UploadInitiateRequest request, String s3Key, String documentId,
                        String teamId) {
                KnowledgeHubDocument document = KnowledgeHubDocument.builder()
                                .documentId(documentId)
                                .fileName(request.getFileName())
                                .collectionId(request.getCollectionId())
                                .fileSize(request.getFileSize())
                                .contentType(request.getContentType())
                                .s3Key(s3Key)
                                .status(DocumentStatus.PENDING_UPLOAD)
                                .teamId(teamId)
                                .createdAt(LocalDateTime.now())
                                .chunkSize(request.getChunkSize() != null ? request.getChunkSize() : 400)
                                .overlapTokens(request.getOverlapTokens() != null ? request.getOverlapTokens() : 50)
                                .chunkStrategy(request.getChunkStrategy() != null ? request.getChunkStrategy()
                                                : "sentence")
                                .build();

                return documentRepository.save(document)
                                .doOnSuccess(doc -> log.info("Created document record: {} for team: {}", documentId,
                                                teamId));
        }

        @Override
        public Mono<KnowledgeHubDocument> completeUpload(String documentId, String s3Key) {
                LocalDateTime startTime = LocalDateTime.now();

                return documentRepository.findByDocumentId(documentId)
                                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                                .flatMap(document -> {
                                        log.info("Completing upload for document: {}", documentId);

                                        // Capture document details for audit
                                        String fileName = document.getFileName();
                                        String collectionId = document.getCollectionId();
                                        String contentType = document.getContentType();
                                        Long fileSize = document.getFileSize();
                                        String teamId = document.getTeamId();

                                        // Check if file is already encrypted
                                        boolean alreadyEncrypted = document.getEncrypted() != null
                                                        && document.getEncrypted()
                                                        && document.getEncryptionKeyVersion() != null
                                                        && !document.getEncryptionKeyVersion().isEmpty();

                                        if (alreadyEncrypted) {
                                                log.info("Document {} is already encrypted with key version: {}",
                                                                documentId,
                                                                document.getEncryptionKeyVersion());

                                                // File is already encrypted, just update status
                                                document.setStatus(DocumentStatus.UPLOADED);
                                                document.setUploadedAt(LocalDateTime.now());
                                                document.setS3Key(s3Key);

                                                return documentRepository.save(document)
                                                                .flatMap(doc -> {
                                                                        // Log upload completion for already-encrypted
                                                                        // file
                                                                        long durationMs = java.time.Duration.between(
                                                                                        startTime, LocalDateTime.now())
                                                                                        .toMillis();

                                                                        Map<String, Object> auditContext = new HashMap<>();
                                                                        auditContext.put("fileName", fileName);
                                                                        auditContext.put("teamId", teamId);
                                                                        auditContext.put("collectionId", collectionId);
                                                                        auditContext.put("contentType", contentType);
                                                                        auditContext.put("fileSize", fileSize);
                                                                        auditContext.put("s3Key", s3Key);
                                                                        auditContext.put("encrypted", true);
                                                                        auditContext.put("encryptionKeyVersion",
                                                                                        document.getEncryptionKeyVersion());
                                                                        auditContext.put("alreadyEncrypted", true);
                                                                        auditContext.put("durationMs", durationMs);
                                                                        auditContext.put("uploadTimestamp",
                                                                                        LocalDateTime.now().toString());

                                                                        String auditReason = String.format(
                                                                                        "Upload completed for document '%s' (already encrypted with key version %s)",
                                                                                        fileName,
                                                                                        document.getEncryptionKeyVersion());

                                                                        return auditLogHelper.logDetailedEvent(
                                                                                        AuditEventType.DOCUMENT_UPLOADED,
                                                                                        AuditActionType.CREATE,
                                                                                        AuditResourceType.DOCUMENT,
                                                                                        documentId,
                                                                                        auditReason,
                                                                                        auditContext,
                                                                                        documentId,
                                                                                        collectionId).thenReturn(doc);
                                                                })
                                                                .doOnSuccess(doc -> publishProcessingEvent(doc,
                                                                                documentId));
                                        }

                                        // File is not encrypted yet - encrypt it
                                        log.info("Encrypting file for document: {}", documentId);

                                        return s3Service.downloadFileContent(s3Key)
                                                        .flatMap(plainBytes -> {
                                                                // Encrypt file bytes
                                                                return chunkEncryptionService
                                                                                .getCurrentKeyVersion(
                                                                                                document.getTeamId())
                                                                                .flatMap(encryptionKeyVersion -> chunkEncryptionService
                                                                                                .encryptFile(
                                                                                                                plainBytes,
                                                                                                                document.getTeamId(),
                                                                                                                encryptionKeyVersion)
                                                                                                .flatMap(encryptedBytes -> {
                                                                                                        log.info(
                                                                                                                        "Encrypted file for document {}: {} bytes -> {} bytes (key version: {})",
                                                                                                                        documentId,
                                                                                                                        plainBytes.length,
                                                                                                                        encryptedBytes.length,
                                                                                                                        encryptionKeyVersion);

                                                                                                        // Re-upload
                                                                                                        // encrypted
                                                                                                        // file (replace
                                                                                                        // original)
                                                                                                        return s3Service.uploadFileBytes(
                                                                                                                        s3Key,
                                                                                                                        encryptedBytes,
                                                                                                                        document.getContentType(),
                                                                                                                        encryptionKeyVersion // Pass
                                                                                                                                             // encryption
                                                                                                                                             // key
                                                                                                                                             // version
                                                                                                                                             // to
                                                                                                                                             // store
                                                                                                                                             // in
                                                                                                                                             // S3
                                                                                                                                             // metadata
                                                                                                        )
                                                                                                                        .then(Mono.fromCallable(
                                                                                                                                        () -> {
                                                                                                                                                // Update
                                                                                                                                                // document
                                                                                                                                                // metadata
                                                                                                                                                // with
                                                                                                                                                // encryption
                                                                                                                                                // info
                                                                                                                                                document.setStatus(
                                                                                                                                                                DocumentStatus.UPLOADED);
                                                                                                                                                document.setUploadedAt(
                                                                                                                                                                LocalDateTime.now());
                                                                                                                                                document.setS3Key(
                                                                                                                                                                s3Key);
                                                                                                                                                document.setEncrypted(
                                                                                                                                                                true);
                                                                                                                                                document.setEncryptionKeyVersion(
                                                                                                                                                                encryptionKeyVersion);
                                                                                                                                                document.setFileSize(
                                                                                                                                                                (long) encryptedBytes.length); // Update
                                                                                                                                                                                               // file
                                                                                                                                                                                               // size
                                                                                                                                                                                               // after
                                                                                                                                                                                               // encryption

                                                                                                                                                log.info(
                                                                                                                                                                "File encrypted and re-uploaded for document: {}",
                                                                                                                                                                documentId);
                                                                                                                                                return document;
                                                                                                                                        }))
                                                                                                                        .flatMap(updatedDoc -> {
                                                                                                                                // Build
                                                                                                                                // audit
                                                                                                                                // context
                                                                                                                                // for
                                                                                                                                // successful
                                                                                                                                // encryption
                                                                                                                                long durationMs = java.time.Duration
                                                                                                                                                .between(startTime,
                                                                                                                                                                LocalDateTime.now())
                                                                                                                                                .toMillis();

                                                                                                                                Map<String, Object> auditContext = new HashMap<>();
                                                                                                                                auditContext.put(
                                                                                                                                                "fileName",
                                                                                                                                                fileName);
                                                                                                                                auditContext.put(
                                                                                                                                                "teamId",
                                                                                                                                                teamId);
                                                                                                                                auditContext.put(
                                                                                                                                                "collectionId",
                                                                                                                                                collectionId);
                                                                                                                                auditContext.put(
                                                                                                                                                "contentType",
                                                                                                                                                contentType);
                                                                                                                                auditContext.put(
                                                                                                                                                "originalFileSize",
                                                                                                                                                fileSize);
                                                                                                                                auditContext.put(
                                                                                                                                                "encryptedFileSize",
                                                                                                                                                encryptedBytes.length);
                                                                                                                                auditContext.put(
                                                                                                                                                "s3Key",
                                                                                                                                                s3Key);
                                                                                                                                auditContext.put(
                                                                                                                                                "encrypted",
                                                                                                                                                true);
                                                                                                                                auditContext.put(
                                                                                                                                                "encryptionKeyVersion",
                                                                                                                                                encryptionKeyVersion);
                                                                                                                                auditContext.put(
                                                                                                                                                "sizeChangeBytes",
                                                                                                                                                encryptedBytes.length
                                                                                                                                                                - plainBytes.length);
                                                                                                                                auditContext.put(
                                                                                                                                                "durationMs",
                                                                                                                                                durationMs);
                                                                                                                                auditContext.put(
                                                                                                                                                "uploadTimestamp",
                                                                                                                                                LocalDateTime.now()
                                                                                                                                                                .toString());

                                                                                                                                String auditReason = String
                                                                                                                                                .format(
                                                                                                                                                                "Document '%s' uploaded and encrypted successfully - %d bytes -> %d bytes (key version: %s)",
                                                                                                                                                                fileName,
                                                                                                                                                                plainBytes.length,
                                                                                                                                                                encryptedBytes.length,
                                                                                                                                                                encryptionKeyVersion);

                                                                                                                                return documentRepository
                                                                                                                                                .save(updatedDoc)
                                                                                                                                                .flatMap(doc -> {
                                                                                                                                                        // Log
                                                                                                                                                        // successful
                                                                                                                                                        // encryption
                                                                                                                                                        return auditLogHelper
                                                                                                                                                                        .logDetailedEvent(
                                                                                                                                                                                        AuditEventType.DOCUMENT_UPLOADED,
                                                                                                                                                                                        AuditActionType.CREATE,
                                                                                                                                                                                        AuditResourceType.DOCUMENT,
                                                                                                                                                                                        documentId,
                                                                                                                                                                                        auditReason,
                                                                                                                                                                                        auditContext,
                                                                                                                                                                                        documentId,
                                                                                                                                                                                        collectionId)
                                                                                                                                                                        .thenReturn(doc);
                                                                                                                                                });
                                                                                                                        });
                                                                                                }));
                                                        })
                                                        .doOnSuccess(doc -> publishProcessingEvent(doc, documentId))
                                                        .onErrorResume(error -> {
                                                                log.error("Error encrypting file for document {}: {}",
                                                                                documentId, error.getMessage(),
                                                                                error);

                                                                // If encryption fails, still mark as uploaded but log
                                                                // warning
                                                                document.setStatus(DocumentStatus.UPLOADED);
                                                                document.setUploadedAt(LocalDateTime.now());
                                                                document.setS3Key(s3Key);
                                                                document.setEncrypted(false);
                                                                document.setEncryptionKeyVersion(null);

                                                                return documentRepository.save(document)
                                                                                .flatMap(doc -> {
                                                                                        // Log failed encryption attempt
                                                                                        long durationMs = java.time.Duration
                                                                                                        .between(startTime,
                                                                                                                        LocalDateTime.now())
                                                                                                        .toMillis();

                                                                                        Map<String, Object> errorContext = new HashMap<>();
                                                                                        errorContext.put("fileName",
                                                                                                        fileName);
                                                                                        errorContext.put("teamId",
                                                                                                        teamId);
                                                                                        errorContext.put("collectionId",
                                                                                                        collectionId);
                                                                                        errorContext.put("contentType",
                                                                                                        contentType);
                                                                                        errorContext.put("fileSize",
                                                                                                        fileSize);
                                                                                        errorContext.put("s3Key",
                                                                                                        s3Key);
                                                                                        errorContext.put("encrypted",
                                                                                                        false);
                                                                                        errorContext.put("error", error
                                                                                                        .getMessage());
                                                                                        errorContext.put("errorType",
                                                                                                        error.getClass().getSimpleName());
                                                                                        errorContext.put("durationMs",
                                                                                                        durationMs);
                                                                                        errorContext.put(
                                                                                                        "uploadTimestamp",
                                                                                                        LocalDateTime.now()
                                                                                                                        .toString());

                                                                                        String auditReason = String
                                                                                                        .format(
                                                                                                                        "Document '%s' uploaded but encryption failed: %s",
                                                                                                                        fileName,
                                                                                                                        error.getMessage());

                                                                                        return auditLogHelper
                                                                                                        .logDetailedEvent(
                                                                                                                        AuditEventType.DOCUMENT_UPLOADED,
                                                                                                                        AuditActionType.CREATE,
                                                                                                                        AuditResourceType.DOCUMENT,
                                                                                                                        documentId,
                                                                                                                        auditReason,
                                                                                                                        errorContext,
                                                                                                                        documentId,
                                                                                                                        collectionId,
                                                                                                                        AuditResultType.FAILED // Result:
                                                                                                                                               // FAILED
                                                                                                                                               // -
                                                                                                                                               // encryption
                                                                                                                                               // failed
                                                                                        ).thenReturn(doc);
                                                                                })
                                                                                .doOnSuccess(doc -> {
                                                                                        log.warn("Document {} uploaded but NOT encrypted due to error: {}",
                                                                                                        documentId,
                                                                                                        error.getMessage());
                                                                                        publishProcessingEvent(doc,
                                                                                                        documentId);
                                                                                });
                                                        });
                                });
        }

        private void publishProcessingEvent(KnowledgeHubDocument doc, String documentId) {
                log.info("Document upload completed: {}", documentId);

                // Publish processing event
                KnowledgeHubDocumentProcessingEvent event = KnowledgeHubDocumentProcessingEvent.builder()
                                .documentId(doc.getDocumentId())
                                .teamId(doc.getTeamId())
                                .collectionId(doc.getCollectionId())
                                .s3Bucket(s3Properties.getBucketName())
                                .s3Key(doc.getS3Key())
                                .fileName(doc.getFileName())
                                .fileSize(doc.getFileSize())
                                .contentType(doc.getContentType())
                                .chunkSize(doc.getChunkSize())
                                .overlapTokens(doc.getOverlapTokens())
                                .chunkStrategy(doc.getChunkStrategy())
                                .build();

                eventPublisher.publishEvent(event);
                log.info("Published document processing event for: {}", documentId);
        }

        @Override
        public Mono<KnowledgeHubDocument> getDocumentById(String documentId, String teamId) {
                return documentRepository.findByDocumentId(documentId)
                                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                                .filter(document -> document.getTeamId().equals(teamId))
                                .switchIfEmpty(Mono.error(new RuntimeException(
                                                "Document not found or access denied: " + documentId)));
        }

        @Override
        public Mono<KnowledgeHubDocument> getDocumentStatus(String documentId, String teamId) {
                return getDocumentById(documentId, teamId);
        }

        @Override
        public Mono<KnowledgeHubDocument> updateStatus(String documentId, String status, String errorMessage) {
                return documentRepository.findByDocumentId(documentId)
                                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                                .flatMap(document -> {
                                        // Convert string status to enum, handling null/empty strings
                                        DocumentStatus documentStatus = null;
                                        if (status != null && !status.isEmpty()) {
                                                try {
                                                        documentStatus = DocumentStatus.valueOf(status.toUpperCase());
                                                } catch (IllegalArgumentException e) {
                                                        log.warn("Invalid document status: {}, keeping existing status",
                                                                        status);
                                                        documentStatus = document.getStatus(); // Keep existing status
                                                }
                                        }
                                        document.setStatus(documentStatus);
                                        document.setErrorMessage(errorMessage);
                                        if ("READY".equals(status) || documentStatus == DocumentStatus.AI_READY) {
                                                document.setProcessedAt(LocalDateTime.now());
                                        }
                                        return documentRepository.save(document);
                                });
        }

        @Override
        public Mono<Void> deleteDocument(String documentId, String teamId) {
                return getDocumentById(documentId, teamId)
                                .flatMap(document -> {
                                        log.info("Soft deleting (trashing) document: {}", documentId);

                                        // Update status to DELETED and set deletedAt
                                        document.setStatus(DocumentStatus.DELETED);
                                        document.setDeletedAt(LocalDateTime.now());

                                        return documentRepository.save(document)
                                                        .doOnSuccess(saved -> log.info(
                                                                        "Successfully soft deleted (trashed) document: {}",
                                                                        documentId))
                                                        .then();
                                });
        }

        /**
         * Delete all chunks associated with the document and then delete the document
         * record
         */

        @Override
        public Mono<Void> hardDeleteDocument(String documentId, String teamId) {
                log.info("Hard deleting document (including S3 files): {}", documentId);

                return getDocumentById(documentId, teamId)
                                .flatMap(document -> {
                                        // Delete all chunks first
                                        Mono<Void> deleteChunks = chunkRepository.deleteAllByDocumentId(documentId)
                                                        .doOnSuccess(v -> log.info(
                                                                        "Deleted all chunks for document: {}",
                                                                        documentId))
                                                        .doOnError(error -> log.warn(
                                                                        "Error deleting chunks for document {}: {}",
                                                                        documentId, error.getMessage()))
                                                        .onErrorResume(error -> {
                                                                // Log warning but continue even if chunk deletion fails
                                                                log.warn("Failed to delete chunks for document {}, continuing: {}",
                                                                                documentId, error.getMessage());
                                                                return Mono.empty();
                                                        });

                                        // Delete metadata record
                                        Mono<Void> deleteMetadata = metadataRepository.deleteByDocumentId(documentId)
                                                        .doOnSuccess(v -> log.info("Deleted metadata for document: {}",
                                                                        documentId))
                                                        .doOnError(error -> log.warn(
                                                                        "Error deleting metadata for document {}: {}",
                                                                        documentId, error.getMessage()))
                                                        .onErrorResume(error -> {
                                                                // Log warning but continue even if metadata deletion
                                                                // fails
                                                                log.warn("Failed to delete metadata for document {}, continuing: {}",
                                                                                documentId, error.getMessage());
                                                                return Mono.empty();
                                                        });

                                        // Delete raw S3 file if it exists
                                        Mono<Void> deleteRawFile = Mono.empty();
                                        if (document.getS3Key() != null && !document.getS3Key().isEmpty()) {
                                                log.info("Attempting to delete raw S3 file for document: {} - S3 key: {}",
                                                                documentId, document.getS3Key());
                                                deleteRawFile = s3Service.deleteFile(document.getS3Key())
                                                                .doOnSuccess(v -> log.info(
                                                                                "✅ Successfully deleted raw S3 file for document: {} - {}",
                                                                                documentId, document.getS3Key()))
                                                                .doOnError(error -> log.error(
                                                                                "❌ ERROR deleting raw S3 file for document {} - S3 key: {} - Error: {}",
                                                                                documentId, document.getS3Key(),
                                                                                error.getMessage(), error))
                                                                .onErrorResume(error -> {
                                                                        // Log error but continue even if S3 deletion
                                                                        // fails (don't block other
                                                                        // deletions)
                                                                        log.error(
                                                                                        "❌ FAILED to delete raw S3 file for document {} - S3 key: {} - Error: {} - Continuing with other deletions",
                                                                                        documentId, document.getS3Key(),
                                                                                        error.getMessage());
                                                                        return Mono.empty();
                                                                });
                                        } else {
                                                log.warn("⚠️ No S3 key found for document: {} - skipping raw file deletion",
                                                                documentId);
                                        }

                                        // Delete processed S3 file if it exists
                                        Mono<Void> deleteProcessedFile = Mono.empty();
                                        if (document.getProcessedS3Key() != null
                                                        && !document.getProcessedS3Key().isEmpty()) {
                                                log.info("Attempting to delete processed S3 file for document: {} - S3 key: {}",
                                                                documentId, document.getProcessedS3Key());
                                                deleteProcessedFile = s3Service.deleteFile(document.getProcessedS3Key())
                                                                .doOnSuccess(
                                                                                v -> log.info("✅ Successfully deleted processed S3 file for document: {} - {}",
                                                                                                documentId,
                                                                                                document.getProcessedS3Key()))
                                                                .doOnError(error -> log.error(
                                                                                "❌ ERROR deleting processed S3 file for document {} - S3 key: {} - Error: {}",
                                                                                documentId,
                                                                                document.getProcessedS3Key(),
                                                                                error.getMessage(), error))
                                                                .onErrorResume(error -> {
                                                                        // Log error but continue even if S3 deletion
                                                                        // fails (don't block other
                                                                        // deletions)
                                                                        log.error(
                                                                                        "❌ FAILED to delete processed S3 file for document {} - S3 key: {} - Error: {} - Continuing with other deletions",
                                                                                        documentId,
                                                                                        document.getProcessedS3Key(),
                                                                                        error.getMessage());
                                                                        return Mono.empty();
                                                                });
                                        } else {
                                                log.info("No processed S3 key for document: {} - skipping processed file deletion",
                                                                documentId);
                                        }

                                        Mono<Void> deleteMilvusEmbeddings = Mono.empty();
                                        if (document.getCollectionId() != null
                                                        && !document.getCollectionId().isEmpty()) {
                                                deleteMilvusEmbeddings = collectionRepository
                                                                .findById(document.getCollectionId())
                                                                .flatMap(collection -> {
                                                                        String milvusCollectionName = collection
                                                                                        .getMilvusCollectionName();
                                                                        if (milvusCollectionName == null
                                                                                        || milvusCollectionName
                                                                                                        .isBlank()) {
                                                                                log.info(
                                                                                                "Collection {} has no assigned RAG collection; skipping embedding deletion for document {}",
                                                                                                collection.getId(),
                                                                                                documentId);
                                                                                return Mono.empty();
                                                                        }
                                                                        return milvusStoreService
                                                                                        .deleteDocumentEmbeddings(
                                                                                                        milvusCollectionName,
                                                                                                        documentId,
                                                                                                        document.getTeamId())
                                                                                        .doOnSuccess(deleted -> log
                                                                                                        .info(
                                                                                                                        "Deleted {} embeddings from RAG collection {} for document {}",
                                                                                                                        deleted,
                                                                                                                        milvusCollectionName,
                                                                                                                        documentId))
                                                                                        .doOnError(error -> log.warn(
                                                                                                        "Error deleting embeddings for document {} from RAG collection {}: {}",
                                                                                                        documentId,
                                                                                                        milvusCollectionName,
                                                                                                        error.getMessage()))
                                                                                        .onErrorResume(error -> Mono
                                                                                                        .empty())
                                                                                        .then();
                                                                })
                                                                .switchIfEmpty(Mono.fromRunnable(() -> log.warn(
                                                                                "KnowledgeHub collection {} not found while hard deleting document {}",
                                                                                document.getCollectionId(),
                                                                                documentId)));
                                        }

                                        // Delete GraphExtractionJob records for this document
                                        Mono<Void> deleteGraphExtractionJobs = graphExtractionJobRepository
                                                        .findByDocumentIdAndTeamId(documentId, document.getTeamId())
                                                        .flatMap(job -> graphExtractionJobRepository
                                                                        .deleteById(job.getId()))
                                                        .then()
                                                        .doOnSuccess(
                                                                        v -> log.info("Deleted GraphExtractionJob records for document: {}",
                                                                                        documentId))
                                                        .doOnError(
                                                                        error -> log.warn(
                                                                                        "Error deleting GraphExtractionJob records for document {}: {}",
                                                                                        documentId, error.getMessage()))
                                                        .onErrorResume(error -> {
                                                                log.warn("Failed to delete GraphExtractionJob records for document {}, continuing: {}",
                                                                                documentId, error.getMessage());
                                                                return Mono.empty();
                                                        });

                                        // Delete Neo4j graph data (entities and relationships) for this document
                                        // First, delete relationships with this documentId
                                        Mono<Void> deleteGraphRelationships = neo4jGraphService.executeQuery(
                                                        "MATCH ()-[r]-() WHERE r.documentId = $documentId DELETE r RETURN count(r) as deleted",
                                                        java.util.Map.of("documentId", documentId))
                                                        .collectList()
                                                        .flatMap(results -> {
                                                                long totalDeleted = results.stream()
                                                                                .mapToLong(result -> {
                                                                                        Object deleted = result
                                                                                                        .get("deleted");
                                                                                        if (deleted instanceof Number) {
                                                                                                return ((Number) deleted)
                                                                                                                .longValue();
                                                                                        }
                                                                                        return 0L;
                                                                                })
                                                                                .sum();
                                                                if (totalDeleted > 0) {
                                                                        log.info("Deleted {} relationships from Neo4j graph for document: {}",
                                                                                        totalDeleted,
                                                                                        documentId);
                                                                }
                                                                return Mono.empty();
                                                        })
                                                        .doOnError(error -> log.warn(
                                                                        "Error deleting Neo4j relationships for document {}: {}",
                                                                        documentId, error.getMessage()))
                                                        .onErrorResume(error -> {
                                                                log.warn("Failed to delete Neo4j relationships for document {}, continuing: {}",
                                                                                documentId, error.getMessage());
                                                                return Mono.empty();
                                                        })
                                                        .then();

                                        // Delete entities with this documentId (using DETACH DELETE to remove any
                                        // remaining relationships)
                                        // Note: We delete by documentId only since team access is already verified at
                                        // the service layer
                                        log.info("Deleting Neo4j entities for document: {} (team: {})", documentId,
                                                        document.getTeamId());
                                        Mono<Void> deleteGraphEntities = neo4jGraphService.executeQuery(
                                                        "MATCH (e) WHERE e.documentId = $documentId " +
                                                                        "DETACH DELETE e RETURN count(e) as deleted",
                                                        java.util.Map.of("documentId", documentId))
                                                        .collectList()
                                                        .flatMap(results -> {
                                                                long totalDeleted = results.stream()
                                                                                .mapToLong(result -> {
                                                                                        Object deleted = result
                                                                                                        .get("deleted");
                                                                                        if (deleted instanceof Number) {
                                                                                                return ((Number) deleted)
                                                                                                                .longValue();
                                                                                        }
                                                                                        return 0L;
                                                                                })
                                                                                .sum();
                                                                if (totalDeleted > 0) {
                                                                        log.info("✅ Deleted {} entities from Neo4j graph for document: {} (team: {})",
                                                                                        totalDeleted, documentId,
                                                                                        document.getTeamId());
                                                                } else {
                                                                        log.warn(
                                                                                        "⚠️ No entities found to delete for document: {} (team: {}). Entities may have already been deleted or don't exist.",
                                                                                        documentId,
                                                                                        document.getTeamId());
                                                                }
                                                                return Mono.empty();
                                                        })
                                                        .doOnError(
                                                                        error -> log.error(
                                                                                        "❌ Error deleting Neo4j entities for document {} (team: {}): {}",
                                                                                        documentId,
                                                                                        document.getTeamId(),
                                                                                        error.getMessage(), error))
                                                        .onErrorResume(error -> {
                                                                log.error(
                                                                                "❌ Failed to delete Neo4j entities for document {} (team: {}), continuing: {}",
                                                                                documentId, document.getTeamId(),
                                                                                error.getMessage());
                                                                return Mono.empty();
                                                        })
                                                        .then();

                                        // Execute all deletions in parallel, then delete the document record
                                        return Mono
                                                        .when(deleteChunks, deleteMetadata, deleteRawFile,
                                                                        deleteProcessedFile,
                                                                        deleteMilvusEmbeddings,
                                                                        deleteGraphExtractionJobs,
                                                                        deleteGraphRelationships,
                                                                        deleteGraphEntities)
                                                        .then(documentRepository.deleteById(document.getId()))
                                                        .doOnSuccess(success -> log.info(
                                                                        "Successfully hard deleted document: {}",
                                                                        documentId));
                                });

        }

        @Override
        public Mono<Void> deleteDocumentArtifacts(String documentId, String teamId, DeletionScope scope) {
                log.info("Deleting document artifacts for document {} (team {}) with scope {}", documentId, teamId,
                                scope);

                return getDocumentById(documentId, teamId)
                                .flatMap(document -> switch (scope) {
                                        case EMBEDDING -> removeEmbeddingData(document).then();
                                        case METADATA -> removeEmbeddingData(document)
                                                        .flatMap(this::removeMetadataData)
                                                        .then();
                                        case PROCESSED -> removeEmbeddingData(document)
                                                        .flatMap(this::removeMetadataData)
                                                        .flatMap(this::removeProcessedData)
                                                        .then();
                                });
        }

        private Mono<KnowledgeHubDocument> removeEmbeddingData(KnowledgeHubDocument document) {
                Mono<Long> deleteMilvusEmbeddings;
                if (StringUtils.hasText(document.getCollectionId())) {
                        deleteMilvusEmbeddings = collectionRepository.findById(document.getCollectionId())
                                        .flatMap(collection -> {
                                                String milvusCollectionName = collection.getMilvusCollectionName();
                                                if (!StringUtils.hasText(milvusCollectionName)) {
                                                        log.info(
                                                                        "Collection {} has no Milvus collection configured; skipping embedding deletion for document {}",
                                                                        collection.getId(), document.getDocumentId());
                                                        return Mono.just(0L);
                                                }
                                                return milvusStoreService
                                                                .deleteDocumentEmbeddings(milvusCollectionName,
                                                                                document.getDocumentId(),
                                                                                document.getTeamId())
                                                                .doOnSuccess(count -> log.info(
                                                                                "Deleted {} embedding vectors for document {} (collection {})",
                                                                                count,
                                                                                document.getDocumentId(),
                                                                                milvusCollectionName))
                                                                .onErrorResume(error -> {
                                                                        log.warn(
                                                                                        "Failed to delete embeddings for document {} from Milvus collection {}: {}",
                                                                                        document.getDocumentId(),
                                                                                        milvusCollectionName,
                                                                                        error.getMessage());
                                                                        return Mono.error(error);
                                                                });
                                        })
                                        .switchIfEmpty(Mono.fromCallable(() -> {
                                                log.warn("KnowledgeHub collection {} not found when deleting embeddings for document {}",
                                                                document.getCollectionId(), document.getDocumentId());
                                                return 0L;
                                        }))
                                        .onErrorResume(error -> {
                                                log.warn("Error during Milvus embedding deletion for document {}: {}",
                                                                document.getDocumentId(),
                                                                error.getMessage());
                                                return Mono.just(0L);
                                        });
                } else {
                        deleteMilvusEmbeddings = Mono.just(0L);
                }

                return deleteMilvusEmbeddings
                                .onErrorResume(error -> {
                                        log.warn("Continuing after embedding deletion failure for document {}: {}",
                                                        document.getDocumentId(), error.getMessage());
                                        return Mono.just(0L);
                                })
                                .then(Mono.defer(() -> {
                                        document.setTotalEmbeddings(null);
                                        document.setProcessingModel(null);
                                        document.setStatus(DocumentStatus.METADATA_EXTRACTION);
                                        document.setErrorMessage(null);
                                        return documentRepository.save(document)
                                                        .doOnSuccess(saved -> log.info(
                                                                        "Updated document {} after embedding deletion",
                                                                        saved.getDocumentId()));
                                }));
        }

        private Mono<KnowledgeHubDocument> removeMetadataData(KnowledgeHubDocument document) {
                return metadataRepository
                                .deleteByDocumentIdAndTeamIdAndCollectionId(document.getDocumentId(),
                                                document.getTeamId(),
                                                document.getCollectionId())
                                .doOnSuccess(v -> log.info("Deleted metadata record for document {}",
                                                document.getDocumentId()))
                                .doOnError(error -> log.warn("Failed to delete metadata for document {}: {}",
                                                document.getDocumentId(),
                                                error.getMessage()))
                                .onErrorResume(error -> Mono.empty())
                                .then(Mono.defer(() -> {
                                        document.setStatus(DocumentStatus.PROCESSED);
                                        document.setErrorMessage(null);
                                        return documentRepository.save(document)
                                                        .doOnSuccess(saved -> log.info(
                                                                        "Updated document {} after metadata deletion",
                                                                        saved.getDocumentId()));
                                }));
        }

        private Mono<KnowledgeHubDocument> removeProcessedData(KnowledgeHubDocument document) {
                Mono<Void> deleteChunks = chunkRepository.deleteAllByDocumentId(document.getDocumentId())
                                .doOnSuccess(v -> log.info("Deleted chunk records for document {}",
                                                document.getDocumentId()))
                                .doOnError(error -> log.warn("Failed to delete chunks for document {}: {}",
                                                document.getDocumentId(),
                                                error.getMessage()))
                                .onErrorResume(error -> Mono.empty());

                Mono<Void> deleteProcessedFile = Mono.empty();
                if (StringUtils.hasText(document.getProcessedS3Key())) {
                        deleteProcessedFile = s3Service.deleteFile(document.getProcessedS3Key())
                                        .doOnSuccess(v -> log.info("Deleted processed S3 file {} for document {}",
                                                        document.getProcessedS3Key(), document.getDocumentId()))
                                        .doOnError(error -> log.warn(
                                                        "Failed to delete processed S3 file for document {}: {}",
                                                        document.getDocumentId(), error.getMessage()))
                                        .onErrorResume(error -> Mono.empty());
                }

                return Mono.when(deleteChunks, deleteProcessedFile)
                                .then(Mono.defer(() -> {
                                        document.setProcessedS3Key(null);
                                        document.setProcessedAt(null);
                                        document.setTotalChunks(null);
                                        document.setTotalTokens(null);
                                        document.setTotalEmbeddings(null);
                                        document.setProcessingModel(null);
                                        document.setStatus(DocumentStatus.UPLOADED);
                                        document.setErrorMessage(null);
                                        return documentRepository.save(document)
                                                        .doOnSuccess(saved -> log.info(
                                                                        "Updated document {} after processed data deletion",
                                                                        saved.getDocumentId()));
                                }));
        }
}
