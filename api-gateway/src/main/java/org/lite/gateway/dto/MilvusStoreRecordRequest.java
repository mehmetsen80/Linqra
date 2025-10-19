package org.lite.gateway.dto;

import lombok.Data;
import java.util.Map;

@Data
public class MilvusStoreRecordRequest {
    private Map<String, Object> record;
    private String target;      // e.g., "openai-embed", "gemini-embed", "cohere-embed"
    private String modelType;   // e.g., "text-embedding-3-small", "gemini-embedding-001"
    private String textField;
    private String teamId;
} 