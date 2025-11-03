package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.enums.KnowledgeCategory;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCollectionResponse {
    private String id;
    private String name;
    private String description;
    private String teamId;
    private String createdBy;
    private String updatedBy;
    private List<KnowledgeCategory> categories;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long documentCount;
}

