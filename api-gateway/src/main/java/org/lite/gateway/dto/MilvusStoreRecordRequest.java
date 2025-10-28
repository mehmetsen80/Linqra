package org.lite.gateway.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class MilvusStoreRecordRequest {
    private Map<String, Object> record;
    private String modelCategory;      // e.g., "openai-chat", "gemini-chat", "cohere-chat", "openai-embed", "gemini-embed", "cohere-embed"
    private String modelName;   // e.g., "text-embedding-3-small", "gemini-embedding-001"
    private String textField;
    private String teamId;
    private List<Float> embedding;  // Optional: Pre-computed embedding from previous workflow step
} 