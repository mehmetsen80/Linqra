package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Document(collection = "tool_definitions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    @Id
    private String id;

    @Indexed(unique = true)
    @NotBlank(message = "Tool ID is required")
    @Pattern(regexp = "^[a-z]+(\\.[a-z]+)*$", message = "Tool ID must be lowercase alpha characters separated by dots (e.g. uscis.status.forms)")
    private String toolId; // e.g., "uscis.form.monitor"

    private String name;
    private String description;
    private String category; // e.g., "legal", "security", "utility"
    private String teamId; // Optional: for team-private tools

    private String type; // HTTP | INTERNAL_SERVICE | LLM_CHAIN | MCP — execution discriminator
    private String inputSchema; // JSON Schema for parameters
    private String outputSchema; // JSON Schema for results
    private PricingConfig pricing;

    @Field("linq_config")
    @JsonProperty("linq_config")
    private Map<String, Object> linqConfig;

    private List<ToolExample> examples; // Postman-style documentation examples
    private String instructions; // API Usage and Form Guidance

    @Builder.Default
    private String visibility = "PUBLIC"; // PUBLIC | PRIVATE

    @CreatedDate
    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();
    @LastModifiedDate
    @Builder.Default
    private LocalDateTime lastModifiedDate = LocalDateTime.now();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ToolExample {
        private String name;
        private String request; // JSON string
        private String response; // JSON string
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PricingConfig {
        private String type; // FREE, PER_EXECUTION
        private Double cost;
    }
}
