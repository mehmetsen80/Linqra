package org.lite.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import org.lite.gateway.enums.KnowledgeCategory;

import java.util.List;

@Data
public class UpdateKnowledgeCollectionRequest {
    
    @NotBlank(message = "Collection name is required")
    private String name;
    
    private String description;
    
    @NotEmpty(message = "At least one category is required")
    private List<KnowledgeCategory> categories;
}

