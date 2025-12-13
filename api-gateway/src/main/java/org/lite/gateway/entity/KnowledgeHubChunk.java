package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;

/**
 * Represents a single chunk of text extracted from a document
 */
@Document(collection = "knowledge_hub_chunks")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(def = "{'documentId': 1, 'chunkIndex': 1}", unique = true, name = "document_chunk_idx")
@CompoundIndex(def = "{'documentId': 1, 'teamId': 1}", name = "document_team_idx")
public class KnowledgeHubChunk {
    
    @Id
    private String id;
    
    @Indexed
    private String chunkId; // Unique identifier for the chunk
    
    @Indexed
    private String documentId; // Reference to the parent document
    
    @Indexed
    private String teamId; // Team that owns this chunk
    
    private Integer chunkIndex; // Sequential index of the chunk in the document
    
    private String text; // The actual chunk text (encrypted if encryptionKeyVersion is set)
    
    /**
     * Encryption key version used to encrypt this chunk.
     * Format: "v1", "v2", etc.
     * Null or "v1" = default/legacy key or unencrypted
     * "v2" = new key after rotation
     */
    private String encryptionKeyVersion; // e.g., "v1", "v2"
    
    private Integer tokenCount; // Number of tokens in this chunk
    
    private Integer startPosition; // Starting position in the original text
    
    private Integer endPosition; // Ending position in the original text
    
    private List<Integer> pageNumbers; // Page numbers where this chunk appears
    
    private Boolean containsTable; // Whether this chunk contains tabular data
    
    private String language; // Detected language code (e.g., "en")
    
    private Double qualityScore; // Quality score between 0 and 1
    
    private Boolean metadataOnly; // Indicates if this chunk contains only metadata (no document text)
    
    private Long createdAt; // Timestamp when chunk was created
    
    // Optional metadata that might be useful for filtering
    private List<String> detectedEntities; // Named entities detected in this chunk
    
    private String chunkStrategy; // Strategy used for chunking (e.g., "token", "sentence", "paragraph")
}

