package org.lite.gateway.dto;

import lombok.Data;

@Data
public class MilvusVerifyRequest {
    private String textField;
    private String text;
    private String teamId;
    private String modelCategory;      // e.g., "openai-chat", "openai-embed", "gemini-chat", "gemini-embed", "cohere-chat", "cohere-embed", etc.
    private String modelName;   // e.g., "text-embedding-3-small", "text-embedding-ada-002", "gemini-embedding-001", etc.
} 