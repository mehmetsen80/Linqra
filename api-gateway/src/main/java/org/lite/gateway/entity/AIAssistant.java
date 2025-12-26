package org.lite.gateway.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "ai_assistants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@CompoundIndexes({
        @CompoundIndex(name = "team_name_unique_idx", def = "{'teamId': 1, 'name': 1}", unique = true),
        @CompoundIndex(name = "team_created_idx", def = "{'teamId': 1, 'createdAt': -1}"),
        @CompoundIndex(name = "public_api_key_idx", def = "{'accessControl.publicApiKey': 1}", unique = true, sparse = true)
})
public class AIAssistant {
    @Id
    private String id;

    // Basic Information
    // Basic Information
    private String name; // e.g., "USCIS Immigration Assistant"
    private String description; // e.g., "Helps with USCIS forms and immigration questions"
    private String teamId; // Team ID that owns this assistant
    @Indexed
    private String status; // ACTIVE, INACTIVE, DRAFT

    public enum Category {
        CHAT,
        REVIEW_DOC
    }

    @Builder.Default
    private Category category = Category.CHAT;

    // Model Configuration
    private ModelConfig defaultModel; // Default LLM model for the assistant

    // System Prompt / Personality
    private String systemPrompt; // System prompt for the assistant

    // Selected Agent Tasks
    private List<SelectedTask> selectedTasks; // Agent Tasks this assistant can execute

    // Context Management
    private ContextManagement contextManagement;

    // Access Control
    private AccessControl accessControl;

    // Widget Configuration (for public assistants)
    private WidgetConfig widgetConfig;

    // Guardrails
    private Guardrails guardrails;

    // Audit Fields
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ModelConfig {
        private String provider; // e.g., "openai", "anthropic", "google"
        private String modelName; // e.g., "gpt-4o", "claude-sonnet-4-5"
        private String modelCategory; // e.g., "chat", "embed", "openai-chat", "openai-embed"
        private Map<String, Object> settings; // e.g., {"temperature": 0.7, "max_tokens": 2000}
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SelectedTask {
        private String taskId; // Agent Task ID
        private String taskName; // Denormalized task name
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class ContextManagement {
        private String strategy; // sliding_window, summarization, relevance, hybrid
        private Integer maxRecentMessages; // Last N messages to include
        private Integer maxTotalTokens; // Max tokens for history
        private Boolean summarizationEnabled;
        private Boolean relevanceRetrievalEnabled;
        private Integer summaryMaxTokens;
        private Integer relevanceMaxMessages;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class AccessControl {
        private String type; // PRIVATE, PUBLIC
        private String publicApiKey; // Generated when type is PUBLIC
        private List<String> allowedDomains; // Optional domain whitelist
        private Boolean allowAnonymousAccess;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WidgetConfig {
        private Boolean enabled; // Only for PUBLIC assistants
        private Theme theme;
        private String position; // bottom-right, bottom-left, top-right, top-left
        private Size size;
        private String headerText;
        private String welcomeMessage;
        private String embedScriptUrl; // Generated widget script URL

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class Theme {
            private String primaryColor;
            private String secondaryColor;
            private String backgroundColor;
            private String textColor;
        }

        @Data
        @NoArgsConstructor
        @AllArgsConstructor
        @Builder
        public static class Size {
            private String width;
            private String height;
        }
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class Guardrails {
        private Boolean piiDetectionEnabled;
        private Boolean redactionEnabled;
        private Boolean auditLoggingEnabled;
    }
}
