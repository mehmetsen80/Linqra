package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.enums.KnowledgeCategory;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "knowledge_hub_collection")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    // Unique constraint: name within team
    @CompoundIndex(name = "team_name_unique_idx", def = "{'teamId': 1, 'name': 1}", unique = true)
})
public class KnowledgeHubCollection {
    
    @Id
    private String id;
    
    private String name;
    
    private String description;
    
    @Indexed
    private String teamId;
    
    private String createdBy;
    
    private String updatedBy;
    
    private List<KnowledgeCategory> categories;
    
    /**
     * Assigned Milvus collection name that stores embeddings for this Knowledge Hub collection
     */
    @Indexed
    private String milvusCollectionName;
    
    /**
     * Embedding provider / tool category (e.g. openai-embed, gemini-embed, cohere-embed)
     */
    private String embeddingModel;
    
    /**
     * Concrete embedding model name (e.g. text-embedding-3-small, jina-embeddings-v2-base-en)
     */
    private String embeddingModelName;
    
    /**
     * Expected embedding vector dimension for the assigned model
     */
    private Integer embeddingDimension;
    
    /**
     * Whether late chunking pipeline should be used when generating embeddings
     */
    @Builder.Default
    private boolean lateChunkingEnabled = true;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}

