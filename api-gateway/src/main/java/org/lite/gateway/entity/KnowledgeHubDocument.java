package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.enums.DocumentStatus;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "knowledge_hub_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
        // Compound index for querying documents by team and collection (most common
        // query)
        @CompoundIndex(name = "team_collection_idx", def = "{'teamId': 1, 'collectionId': 1}"),
        // Compound index for querying documents by team and status
        @CompoundIndex(name = "team_status_idx", def = "{'teamId': 1, 'status': 1}"),
        // Compound index for querying documents by collection and status
        @CompoundIndex(name = "collection_status_idx", def = "{'collectionId': 1, 'status': 1}")
})
public class KnowledgeHubDocument {

    @Id
    private String id;

    @Indexed(unique = true)
    private String documentId;

    private String fileName;
    private String collectionId; // Knowledge base or collection name
    private Long fileSize;
    private String contentType;

    @Indexed
    private String teamId; // Team that owns this document

    @Indexed
    private String s3Key; // S3 key for the raw document

    @Indexed
    private DocumentStatus status;

    @CreatedDate
    private LocalDateTime createdAt;

    private LocalDateTime uploadedAt;
    private LocalDateTime processedAt;

    // Processing options
    private Integer chunkSize; // Number of tokens per chunk
    private Integer overlapTokens; // Number of overlapping tokens between chunks
    private String chunkStrategy; // e.g., "sentence", "paragraph", "token"

    // Processing results
    private Integer totalChunks;
    private String processedS3Key; // S3 key for processed chunks JSON
    private String errorMessage;

    // Metadata
    private String processingModel; // LLM model used for embeddings
    private Long totalTokens; // Total tokens in the document
    private Integer totalEmbeddings;

    // File encryption metadata
    /**
     * Encryption key version used to encrypt the raw file in S3.
     * Format: "v1", "v2", etc.
     * Null = unencrypted (legacy file)
     */
    private String encryptionKeyVersion; // e.g., "v1", "v2"

    /**
     * Whether the raw file in S3 is encrypted.
     * false = legacy unencrypted file (for backward compatibility)
     * true = encrypted file (new uploads)
     */
    @lombok.Builder.Default
    private Boolean encrypted = false; // Default false for backward compatibility

    // Deletion
    private LocalDateTime deletedAt;

    // Versioning
    @Builder.Default
    private Integer currentVersion = 1;
}
