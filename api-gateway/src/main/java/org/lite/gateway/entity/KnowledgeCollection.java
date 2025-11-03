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

@Document(collection = "knowledge_collection")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    // Unique constraint: name within team
    @CompoundIndex(name = "team_name_unique_idx", def = "{'teamId': 1, 'name': 1}", unique = true)
})
public class KnowledgeCollection {
    
    @Id
    private String id;
    
    private String name;
    
    private String description;
    
    @Indexed
    private String teamId;
    
    private String createdBy;
    
    private String updatedBy;
    
    private List<KnowledgeCategory> categories;
    
    @CreatedDate
    private LocalDateTime createdAt;
    
    @LastModifiedDate
    private LocalDateTime updatedAt;
}

