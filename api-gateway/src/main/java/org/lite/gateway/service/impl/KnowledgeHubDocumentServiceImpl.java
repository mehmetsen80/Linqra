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
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.lite.gateway.service.S3Service;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.lite.gateway.repository.KnowledgeHubCollectionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
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
    private final KnowledgeHubChunkRepository chunkRepository;
    private final KnowledgeHubDocumentMetaDataRepository metadataRepository;
    private final KnowledgeHubCollectionRepository collectionRepository;
    private final LinqMilvusStoreService milvusStoreService;
    
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
                            request.getFileName()
                    );
                    
                    // Generate presigned URL
                    return s3Service.generatePresignedUploadUrl(s3Key, request.getContentType())
                            .flatMap(presignedUrl -> {
                                // Create document record
                                return createDocument(request, s3Key, documentId, teamId)
                                        .map(document -> new DocumentInitiationResult(document, presignedUrl));
                            });
                });
    }
    
    @Override
    public Mono<KnowledgeHubDocument> createDocument(UploadInitiateRequest request, String s3Key, String documentId, String teamId) {
        KnowledgeHubDocument document = KnowledgeHubDocument.builder()
                .documentId(documentId)
                .fileName(request.getFileName())
                .collectionId(request.getCollectionId())
                .fileSize(request.getFileSize())
                .contentType(request.getContentType())
                .s3Key(s3Key)
                .status("PENDING_UPLOAD")
                .teamId(teamId)
                .createdAt(LocalDateTime.now())
                .chunkSize(request.getChunkSize() != null ? request.getChunkSize() : 400)
                .overlapTokens(request.getOverlapTokens() != null ? request.getOverlapTokens() : 50)
                .chunkStrategy(request.getChunkStrategy() != null ? request.getChunkStrategy() : "sentence")
                .build();
        
        return documentRepository.save(document)
                .doOnSuccess(doc -> log.info("Created document record: {} for team: {}", documentId, teamId));
    }
    
    @Override
    public Mono<KnowledgeHubDocument> completeUpload(String documentId, String s3Key) {
        return documentRepository.findByDocumentId(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                .flatMap(document -> {
                    log.info("Completing upload for document: {}", documentId);
                    
                    document.setStatus("UPLOADED");
                    document.setUploadedAt(LocalDateTime.now());
                    document.setS3Key(s3Key);
                    
                    return documentRepository.save(document)
                            .doOnSuccess(doc -> {
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
                            });
                });
    }
    
    @Override
    public Mono<KnowledgeHubDocument> getDocumentById(String documentId, String teamId) {
        return documentRepository.findByDocumentId(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                .filter(document -> document.getTeamId().equals(teamId))
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found or access denied: " + documentId)));
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
                    document.setStatus(status);
                    document.setErrorMessage(errorMessage);
                    if ("READY".equals(status)) {
                        document.setProcessedAt(LocalDateTime.now());
                    }
                    return documentRepository.save(document);
                });
    }
    
    @Override
    public Mono<Void> deleteDocument(String documentId, String teamId) {
        return getDocumentById(documentId, teamId)
                .flatMap(document -> {
                    log.info("Checking if S3 file exists for document: {}", documentId);
                    
                    // Check if S3 file exists
                    if (document.getS3Key() != null && !document.getS3Key().isEmpty()) {
                        return s3Service.fileExists(document.getS3Key())
                                .flatMap(exists -> {
                                    if (exists) {
                                        log.info("File exists in S3 for document: {}", documentId);
                                        return Mono.error(new RuntimeException(
                                                "Cannot delete document with an active S3 file. Please delete the file from the document page first."));
                                    } else {
                                        log.info("File does not exist in S3 for document: {}", documentId);
                                        // File doesn't exist, safe to delete chunks and then the record
                                        return deleteDocumentAndChunks(document, documentId);
                                    }
                                });
                    } else {
                        log.info("No S3 key for document: {}, safe to delete", documentId);
                        // No S3 key, safe to delete chunks and then the record
                        return deleteDocumentAndChunks(document, documentId);
                    }
                });
    }
    
    /**
     * Delete all chunks associated with the document and then delete the document record
     */
    private Mono<Void> deleteDocumentAndChunks(KnowledgeHubDocument document, String documentId) {
        // First, delete all chunks associated with this document
        return chunkRepository.deleteAllByDocumentId(documentId)
                .doOnSuccess(v -> log.info("Deleted all chunks for document: {}", documentId))
                .doOnError(error -> log.warn("Error deleting chunks for document {}: {}", documentId, error.getMessage()))
                .onErrorResume(error -> {
                    // Log warning but continue with document deletion even if chunk deletion fails
                    log.warn("Failed to delete chunks for document {}, continuing with document deletion: {}", 
                            documentId, error.getMessage());
                    return Mono.empty();
                })
                .then(documentRepository.deleteById(document.getId()))
                .doOnSuccess(success -> log.info("Successfully deleted document: {}", documentId));
    }
    
    @Override
    public Mono<Void> hardDeleteDocument(String documentId, String teamId) {
        log.info("Hard deleting document (including S3 files): {}", documentId);
        
        return getDocumentById(documentId, teamId)
                .flatMap(document -> {
                    // Delete all chunks first
                    Mono<Void> deleteChunks = chunkRepository.deleteAllByDocumentId(documentId)
                            .doOnSuccess(v -> log.info("Deleted all chunks for document: {}", documentId))
                            .doOnError(error -> log.warn("Error deleting chunks for document {}: {}", 
                                    documentId, error.getMessage()))
                            .onErrorResume(error -> {
                                // Log warning but continue even if chunk deletion fails
                                log.warn("Failed to delete chunks for document {}, continuing: {}", 
                                        documentId, error.getMessage());
                                return Mono.empty();
                            });
                    
                    // Delete metadata record
                    Mono<Void> deleteMetadata = metadataRepository.deleteByDocumentId(documentId)
                            .doOnSuccess(v -> log.info("Deleted metadata for document: {}", documentId))
                            .doOnError(error -> log.warn("Error deleting metadata for document {}: {}", 
                                    documentId, error.getMessage()))
                            .onErrorResume(error -> {
                                // Log warning but continue even if metadata deletion fails
                                log.warn("Failed to delete metadata for document {}, continuing: {}", 
                                        documentId, error.getMessage());
                                return Mono.empty();
                            });
                    
                    // Delete raw S3 file if it exists
                    Mono<Void> deleteRawFile = Mono.empty();
                    if (document.getS3Key() != null && !document.getS3Key().isEmpty()) {
                        deleteRawFile = s3Service.deleteFile(document.getS3Key())
                                .doOnSuccess(v -> log.info("Deleted raw S3 file for document: {} - {}", 
                                        documentId, document.getS3Key()))
                                .doOnError(error -> log.warn("Error deleting raw S3 file for document {}: {}", 
                                        documentId, error.getMessage()))
                                .onErrorResume(error -> {
                                    // Log warning but continue even if S3 deletion fails
                                    log.warn("Failed to delete raw S3 file for document {}, continuing: {}", 
                                            documentId, error.getMessage());
                                    return Mono.empty();
                                });
                    }
                    
                    // Delete processed S3 file if it exists
                    Mono<Void> deleteProcessedFile = Mono.empty();
                    if (document.getProcessedS3Key() != null && !document.getProcessedS3Key().isEmpty()) {
                        deleteProcessedFile = s3Service.deleteFile(document.getProcessedS3Key())
                                .doOnSuccess(v -> log.info("Deleted processed S3 file for document: {} - {}", 
                                        documentId, document.getProcessedS3Key()))
                                .doOnError(error -> log.warn("Error deleting processed S3 file for document {}: {}", 
                                        documentId, error.getMessage()))
                                .onErrorResume(error -> {
                                    // Log warning but continue even if S3 deletion fails
                                    log.warn("Failed to delete processed S3 file for document {}, continuing: {}", 
                                            documentId, error.getMessage());
                                    return Mono.empty();
                                });
                    }

                    Mono<Void> deleteMilvusEmbeddings = Mono.empty();
                    if (document.getCollectionId() != null && !document.getCollectionId().isEmpty()) {
                        deleteMilvusEmbeddings = collectionRepository.findById(document.getCollectionId())
                                .flatMap(collection -> {
                                    String milvusCollectionName = collection.getMilvusCollectionName();
                                    if (milvusCollectionName == null || milvusCollectionName.isBlank()) {
                                        log.info("Collection {} has no assigned RAG collection; skipping embedding deletion for document {}", collection.getId(), documentId);
                                        return Mono.empty();
                                    }
                                    return milvusStoreService.deleteDocumentEmbeddings(milvusCollectionName, documentId, document.getTeamId())
                                            .doOnSuccess(deleted -> log.info("Deleted {} embeddings from RAG collection {} for document {}", deleted, milvusCollectionName, documentId))
                                            .doOnError(error -> log.warn("Error deleting embeddings for document {} from RAG collection {}: {}", documentId, milvusCollectionName, error.getMessage()))
                                            .onErrorResume(error -> Mono.empty())
                                            .then();
                                })
                                .switchIfEmpty(Mono.fromRunnable(() -> log.warn("KnowledgeHub collection {} not found while hard deleting document {}", document.getCollectionId(), documentId)));
                    }

                    // Execute all deletions in parallel, then delete the document record
                    return Mono.when(deleteChunks, deleteMetadata, deleteRawFile, deleteProcessedFile, deleteMilvusEmbeddings)
                            .then(documentRepository.deleteById(document.getId()))
                            .doOnSuccess(success -> log.info("Successfully hard deleted document: {}", documentId));
                });
    }
}

