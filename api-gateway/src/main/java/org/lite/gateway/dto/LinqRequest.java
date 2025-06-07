package org.lite.gateway.dto;

import lombok.Data;
import org.lite.gateway.validation.annotations.Required;
import org.lite.gateway.validation.annotations.ValidStep;
import org.lite.gateway.validation.annotations.ValidToolConfig;
import org.lite.gateway.validation.annotations.ValidAction;
import com.fasterxml.jackson.annotation.JsonIgnore;

import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.time.Duration;

@Data
public class LinqRequest {
    @Required(message = "Link configuration is required")
    private Link link;
    
    @Required(message = "Query configuration is required")
    private Query query;

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
        private String intent; // e.g., "random", "generate", "get_historical_saying"
        private String workflowId;  // Add this field to link to predefined workflows
        private Map<String, Object> params; // e.g., {"prompt": "Hello"}
        private Object payload; // Request body for POST/PUT/PATCH
        private ToolConfig toolConfig; // For AI tools
        
        @ValidStep
        private List<WorkflowStep> workflow; // For chained steps

        @Data
        public static class ToolConfig {
            @Required(message = "Model is required when toolConfig is present")
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
            
            @ValidToolConfig
            private ToolConfig toolConfig; // For AI tools

            private Boolean async; // Whether this step should be executed asynchronously

            private CacheConfig cacheConfig; // Cache configuration for this step

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
            public void setToolConfig(ToolConfig toolConfig) {
                if (toolConfig != null && toolConfig.getSettings() != null) {
                    Map<String, Object> settings = new HashMap<>(toolConfig.getSettings());
                    if (settings.containsKey("max.tokens")) {
                        Object value = settings.remove("max.tokens");
                        settings.put("max_tokens", value);
                    }
                    toolConfig.setSettings(settings);
                }
                this.toolConfig = toolConfig;
            }
        }
    }
}