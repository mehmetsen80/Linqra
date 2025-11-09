package org.lite.gateway.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

@Data
public class AssignMilvusCollectionRequest {

    @NotBlank(message = "Milvus collection name is required")
    private String milvusCollectionName;

    @NotBlank(message = "Embedding model category is required")
    private String embeddingModel;

    @NotBlank(message = "Embedding model name is required")
    private String embeddingModelName;

    @NotNull(message = "Embedding dimension is required")
    @Positive(message = "Embedding dimension must be greater than zero")
    private Integer embeddingDimension;

    private boolean lateChunkingEnabled;
}

