package org.lite.gateway.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

@Data
public class LinqRequest {
    private Link link;
    private Query query;

    @Data
    public static class Link {
        private String target; // e.g., "quotes-service", "openai", "workflow"
        private String action; // e.g., "fetch", "generate", "execute"
    }

    @Data
    public static class Query {
        private String intent; // e.g., "random", "generate", "get_historical_saying"
        private String workflowId;  // Add this field to link to predefined workflows
        private Map<String, Object> params; // e.g., {"prompt": "Hello"}
        private Object payload; // Request body for POST/PUT/PATCH
        private ToolConfig toolConfig; // For AI tools
        private List<WorkflowStep> workflow; // For chained steps

        @Data
        public static class ToolConfig {
            private String model; // e.g., "gpt-4o", "gemini-1.5-pro"
            private Map<String, Object> settings; // e.g., {"temperature": 0.7, "max_tokens": 1000}
        }

        @Data
        public static class WorkflowStep {
            private int step; // Step number (e.g., 1, 2)
            private String target; // e.g., "quotes-service", "openai"
            private String action; // e.g., "fetch", "generate"
            private String intent; // e.g., "random", "generate"
            private Map<String, Object> params; // e.g., {"prompt": "{{step1.result.name}}"}
            private Object payload; // Request body, e.g., [{"role": "user", "content": "..."}]
            private ToolConfig toolConfig; // For AI tools

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