package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "knowledge_hub_documents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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
    private String status; // PENDING_UPLOAD, UPLOADED, PARSING, PROCESSED, EMBEDDING, AI_READY, FAILED
    
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
}

