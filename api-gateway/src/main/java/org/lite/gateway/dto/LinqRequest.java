package org.lite.gateway.dto;

import lombok.Data;
import org.lite.gateway.validation.annotations.Required;
import org.lite.gateway.validation.annotations.ValidStep;
import org.lite.gateway.validation.annotations.ValidLlmConfig;
import org.lite.gateway.validation.annotations.ValidAction;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.Duration;
import java.time.LocalDateTime;

@Data
public class LinqRequest {
    @Required(message = "Link configuration is required")
    private Link link;
    
    @Required(message = "Query configuration is required")
    private Query query;
    
    private String executedBy; // Username of the user who initiated the workflow execution

    @Data
    public static class Link {
        @Required(message = "Link target is required")
        private String target; // e.g., "quotes-service", "openai", "workflow"
        
        @Required(message = "Link action is required")
        @ValidAction
        private String action; // e.g., "fetch", "generate", "execute"
    }

    @Data
    public static class Query {
        private String intent; // e.g., "random", "generate", "get_historical_saying", "uscis_marriage_based_qna"
        private String workflowId;  // Add this field to link to predefined workflows
        @JsonInclude(JsonInclude.Include.ALWAYS)
        private Map<String, Object> params = new HashMap<>();
        private Object payload; // Request body for POST/PUT/PATCH
        private LlmConfig llmConfig; // For AI LLM Configuration
        
        @ValidStep
        private List<WorkflowStep> workflow; // For chained steps (workflow execution)
        
        // Chat conversation support
        private ChatConversation chat; // For AI Assistant chat conversations

        @Data
        public static class LlmConfig {
            @Required(message = "Model is required when llmConfig is present")
            private String model; // e.g., "gpt-4o", "gemini-1.5-pro"
            private Map<String, Object> settings; // e.g., {"temperature": 0.7, "max_tokens": 1000}
        }

        @Data
        public static class WorkflowStep {
            private int step; // Step number (e.g., 1, 2)
            
            @Required(message = "Step target is required")
            private String target; // e.g., "quotes-service", "openai"
            
            @Required(message = "Step action is required")
            @ValidAction
            private String action; // e.g., "fetch", "generate"
            
            @Required(message = "Step intent is required")
            private String intent; // e.g., "random", "generate"
            
            private Map<String, Object> params; // e.g., {"prompt": "{{step1.result.name}}"}
            private Object payload; // Request body, e.g., [{"role": "user", "content": "..."}]
            
            @ValidLlmConfig
            private LlmConfig llmConfig; // For AI tools

            private Boolean async; // Whether this step should be executed asynchronously

            private CacheConfig cacheConfig; // Cache configuration for this step

            private String description; // Optional description explaining what this step does

            @Data
            public static class CacheConfig {
                private boolean enabled = false; // Whether caching is enabled for this step
                private String ttl; // Time-to-live in seconds
                private String key; // Custom cache key (optional)

                @JsonIgnore
                public Duration getTtlAsDuration() {
                    if (ttl == null) {
                        return Duration.ofMinutes(5); // Default 5 minutes
                    }
                    try {
                        return Duration.ofSeconds(Long.parseLong(ttl));
                    } catch (NumberFormatException e) {
                        return Duration.ofMinutes(5); // Default to 5 minutes if parsing fails
                    }
                }
            }

            //Do not delete this, it's being used internally by the json
            public void setLlmConfig(LlmConfig llmConfig) {
                if (llmConfig != null && llmConfig.getSettings() != null) {
                    Map<String, Object> settings = new HashMap<>(llmConfig.getSettings());
                    if (settings.containsKey("max.tokens")) {
                        Object value = settings.remove("max.tokens");
                        settings.put("max_tokens", value);
                    }
                    llmConfig.setSettings(settings);
                }
                this.llmConfig = llmConfig;
            }
        }
        
        @Data
        public static class ChatConversation {
            private String conversationId; // Existing conversation ID (for multi-turn)
            private String assistantId; // AI Assistant ID
            private String message; // User's message/query
            private List<ChatMessage> history; // Conversation history (for context)
            private Map<String, Object> context; // Additional context data
            
            @Data
            public static class ChatMessage {
                private String role; // "user" or "assistant"
                private String content; // Message content
                private LocalDateTime timestamp; // Message timestamp
                private Map<String, Object> metadata; // Optional metadata (task executions, etc.)
            }
        }
    }
}