package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.S3Properties;
import org.lite.gateway.dto.UploadInitiateRequest;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.event.DocumentProcessingEvent;
import org.lite.gateway.repository.DocumentRepository;
import org.lite.gateway.repository.TeamRepository;
import org.lite.gateway.service.DocumentService;
import org.lite.gateway.service.S3Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentServiceImpl implements DocumentService {
    
    private final DocumentRepository documentRepository;
    private final TeamRepository teamRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final S3Service s3Service;
    private final S3Properties s3Properties;
    
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
                                DocumentProcessingEvent event = DocumentProcessingEvent.builder()
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
                                        // File doesn't exist, safe to delete the record
                                        return documentRepository.deleteById(document.getId())
                                                .doOnSuccess(success -> log.info("Successfully deleted document: {}", documentId));
                                    }
                                });
                    } else {
                        log.info("No S3 key for document: {}, safe to delete", documentId);
                        // No S3 key, safe to delete the record
                        return documentRepository.deleteById(document.getId())
                                .doOnSuccess(success -> log.info("Successfully deleted document: {}", documentId));
                    }
                });
    }
}

