package org.lite.gateway.service;

import java.util.List;
import java.util.Map;

import org.lite.gateway.dto.DocumentInitiationResult;
import org.lite.gateway.dto.ResourceDeltaContentResponse;
import org.lite.gateway.dto.UploadInitiateRequest;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.entity.KnowledgeHubDocumentVersion;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface KnowledgeHubDocumentService {

        /**
         * Initiate document upload: generate presigned URL and create document record
         */
        Mono<DocumentInitiationResult> initiateDocumentUpload(UploadInitiateRequest request, String teamId);

        /**
         * Create document record when upload is initiated
         */
        Mono<KnowledgeHubDocument> createDocument(UploadInitiateRequest request, String s3Key, String documentId,
                        String teamId);

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
         * This will delete everything: chunks, S3 files (raw and processed), and
         * document record
         */
        Mono<Void> hardDeleteDocument(String documentId, String teamId);

        /**
         * Delete derived artifacts from the document processing pipeline.
         *
         * @param documentId the document identifier
         * @param teamId     the requesting team
         * @param scope      which artifacts to delete (embedding only,
         *                   metadata+embedding, processed+metadata+embedding)
         */
        Mono<Void> deleteDocumentArtifacts(String documentId, String teamId, DeletionScope scope);

        /**
         * Get the full text content of a document by reconstructing it from chunks.
         */
        Mono<String> getDocumentText(String documentId, String teamId);

        /**
         * Update the content of a document (e.g. after editing).
         * This will overwrite the file in storage and update metadata.
         *
         * @param documentId The document ID
         * @param teamId     The requesting team ID for access control
         * @param content    The new content bytes
         * @return Mono<Void>
         */
        Mono<Void> updateDocumentContent(String documentId, String teamId, byte[] content);

        /**
         * Generate a presigned download URL for the document.
         * 
         * @param documentId The document ID
         * @return Map containing "downloadUrl" key
         */
        Mono<Map<String, String>> generateDownloadUrl(String documentId);

        enum DeletionScope {
                EMBEDDING,
                METADATA,
                PROCESSED
        }

        /**
         * Get all version history for a document.
         */
        Flux<KnowledgeHubDocumentVersion> getDocumentVersions(
                        String documentId, String teamId);

        /**
         * pointers to the *target* version's content.
         */
        Mono<KnowledgeHubDocument> restoreVersion(String documentId, Integer versionNumber, String teamId);

        /**
         * Analyze changes between two document versions using LLM.
         * Fetch content of two document versions.
         *
         * @param oldDocId         The old document ID
         * @param newDocId         The new document ID
         * @param teamId           The team ID for context
         * @param resourceId       The unique identifier of the resource (e.g., I-485)
         * @param resourceCategory The category of the resource (e.g., uscis-sentinel)
         * @return Mono of ResourceDeltaContentResponse
         */
        Mono<ResourceDeltaContentResponse> fetchDeltaContent(String oldDocId, String newDocId, String teamId,
                        String resourceId, String resourceCategory, List<String> categories);
}
