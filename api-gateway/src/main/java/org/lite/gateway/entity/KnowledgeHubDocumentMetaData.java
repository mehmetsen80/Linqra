package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Extracted metadata from processed documents
 */
@Document(collection = "knowledge_hub_document_metadata")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    // Ensure uniqueness per document/team/collection combination
    @CompoundIndex(name = "document_team_collection_unique_idx",
            def = "{'documentId': 1, 'teamId': 1, 'collectionId': 1}", unique = true),
    // Support queries by team and collection
    @CompoundIndex(name = "team_collection_idx", def = "{'teamId': 1, 'collectionId': 1}")
})
public class KnowledgeHubDocumentMetaData {
    
    @Id
    private String id;
    
    @Indexed
    private String documentId; // Reference to KnowledgeHubDocument
    
    @Indexed
    private String teamId; // Team that owns this document
    
    @Indexed
    private String collectionId; // Collection this document belongs to
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    private LocalDateTime extractedAt;
    
    // Extracted metadata fields
    private String title;
    private String author;
    private String subject;
    private String keywords;
    private String language;
    private String creator;
    private String producer;
    private LocalDateTime creationDate;
    private LocalDateTime modificationDate;
    
    // Document statistics
    private Integer pageCount;
    private Integer wordCount;
    private Integer characterCount;
    
    // Content type specific metadata
    private String documentType; // e.g., "PDF", "DOCX", "TXT"
    private String mimeType;
    
    // Additional custom metadata (flexible key-value pairs)
    private Map<String, Object> customMetadata;
    
    // Extraction metadata
    private String extractionModel; // Model or method used for extraction
    private String extractionVersion; // Version of extraction logic
    
    /**
     * Encryption key version used to encrypt sensitive metadata fields.
     * Format: "v1", "v2", etc.
     * Null or "v1" = default/legacy key or unencrypted
     * "v2" = new key after rotation
     */
    private String encryptionKeyVersion; // e.g., "v1", "v2"
    
    // Status
    private String status; // EXTRACTING, EXTRACTED, FAILED
    private String errorMessage;
}

