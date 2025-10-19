package org.lite.gateway.dto;

import lombok.Data;
import java.util.Map;

@Data
public class MilvusSearchRequest {
    private String textField;
    private String text;
    private String teamId;
    private String target;      // e.g., "openai-embed", "gemini-embed", "cohere-embed", etc.
    private String modelType;   // e.g., "text-embedding-3-small", "text-embedding-ada-002", "gemini-embedding-001", etc.
    private Integer nResults;   // Number of results to return (default: 10)
    private Map<String, Object> metadataFilters; // Optional metadata filters
} 