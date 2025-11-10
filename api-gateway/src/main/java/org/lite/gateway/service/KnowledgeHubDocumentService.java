package org.lite.gateway.service;

import org.lite.gateway.dto.PresignedUploadUrl;
import org.lite.gateway.dto.UploadInitiateRequest;
import org.lite.gateway.entity.KnowledgeHubDocument;
import reactor.core.publisher.Mono;

public interface KnowledgeHubDocumentService {
    
    /**
     * Initiate document upload: generate presigned URL and create document record
     */
    Mono<DocumentInitiationResult> initiateDocumentUpload(UploadInitiateRequest request, String teamId);
    
    /**
     * Create document record when upload is initiated
     */
    Mono<KnowledgeHubDocument> createDocument(UploadInitiateRequest request, String s3Key, String documentId, String teamId);
    
    /**
     * Mark upload as complete and trigger processing
     */
    Mono<KnowledgeHubDocument> completeUpload(String documentId, String s3Key);
    
    /**
     * Get document by ID with team access control
     */
    Mono<KnowledgeHubDocument> getDocumentById(String documentId, String teamId);
    
    /**
     * Get document status
     */
    Mono<KnowledgeHubDocument> getDocumentStatus(String documentId, String teamId);
    
    /**
     * Update document status
     */
    Mono<KnowledgeHubDocument> updateStatus(String documentId, String status, String errorMessage);
    
    /**
     * Delete document by ID with team access control
     * Note: This will only delete if S3 file doesn't exist (soft delete)
     */
    Mono<Void> deleteDocument(String documentId, String teamId);
    
    /**
     * Hard delete document by ID with team access control
     * This will delete everything: chunks, S3 files (raw and processed), and document record
     */
    Mono<Void> hardDeleteDocument(String documentId, String teamId);

    /**
     * Delete derived artifacts from the document processing pipeline.
     *
     * @param documentId the document identifier
     * @param teamId     the requesting team
     * @param scope      which artifacts to delete (embedding only, metadata+embedding, processed+metadata+embedding)
     */
    Mono<Void> deleteDocumentArtifacts(String documentId, String teamId, DeletionScope scope);

    /**
     * Result of document initiation including document and presigned URL
     */
    record DocumentInitiationResult(KnowledgeHubDocument document, PresignedUploadUrl presignedUrl) {}

    enum DeletionScope {
        EMBEDDING,
        METADATA,
        PROCESSED
    }
}

