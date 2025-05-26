package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Document(collection = "linq_tools")
@CompoundIndex(name = "target_team_idx", def = "{'target': 1, 'team': 1}", unique = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinqTool {
    @Id
    private String id;
    private String target;            // e.g., "openai"
    private String endpoint;          // e.g., "https://api.openai.com/v1/chat/completions"
    private String method;            // e.g., "POST"
    private Map<String, String> headers; // e.g., {"Content-Type": "application/json"}
    private String authType;          // e.g., "bearer", "api_key_query", "none"
    private String apiKey;            // The API key for the service
    private List<String> supportedIntents; // e.g., ["generate", "summarize"]
    private String team;              // e.g., "67d0aeb17172416c411d419e" (team ID)
}
