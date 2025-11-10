package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Transient;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "linq_llm_models")
@CompoundIndex(name = "modelcategory_modelname_team_idx", def = "{'modelCategory': 1, 'modelName': 1, 'teamId': 1}", unique = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinqLlmModel {
    @Id
    private String id;
    private String provider;          // e.g., "openai", "gemini", "cohere", "claude"
    private String modelCategory;     // e.g., "openai-chat", "openai-embed", "gemini-chat", "gemini-embed"
    private String modelName;         // e.g., "gpt-4o", "text-embedding-004", "gemini-embedding-001"
    private String endpoint;          // e.g., "https://api.openai.com/v1/chat/completions"
    private String method;            // e.g., "POST"
    private Map<String, String> headers; // e.g., {"Content-Type": "application/json"}
    private String authType;          // e.g., "bearer", "api_key_query", "none"
    private String apiKey;            // The API key for the service
    private List<String> supportedIntents; // e.g., ["generate", "summarize"]
    private String teamId;              // e.g., "67d0aeb17172416c411d419e" (team ID)

    @Transient
    private Integer embeddingDimension; // Derived from LlmModel for embedding category models

    @Transient
    private Double inputPricePer1M; // Derived pricing metadata (USD per 1M input tokens)

    @Transient
    private Double outputPricePer1M; // Derived pricing metadata (USD per 1M output tokens)

    @Transient
    private Integer contextWindowTokens; // Derived maximum context window tokens (if available)
}

