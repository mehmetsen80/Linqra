package org.lite.gateway.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeCollectionSearchRequest {

    @NotBlank(message = "Search query must not be empty")
    private String query;

    @Min(value = 1, message = "topK must be at least 1")
    private Integer topK;

    private Map<String, Object> metadataFilters;
}

