package org.lite.gateway.dto;

import lombok.Data;

import java.util.List;
import java.time.LocalDateTime;
import java.util.Map;

@Data
public class LinqResponse {
    private Object result;  // Can be WorkflowResult, List, Map, or any other type
    private Metadata metadata;

    @Data
    public static class Metadata {
        private String source; // e.g., "quotes-service", "openai", "workflow"
        private String status; // e.g., "success", "error"
        private String team;   // e.g., "67d0aeb17172416c411d419e"
        private boolean cacheHit; // e.g., false
        private List<WorkflowStepMetadata> workflowMetadata;  // Optional, only for workflow responses
        private List<AsyncStepStatus> asyncSteps;  // Status of async steps
    }

    @Data
    public static class AsyncStepStatus {
        private String stepId;
        private String status;  // "pending", "processing", "completed", "failed"
        private String message;
        private String error;
        private Object result;
        private LocalDateTime queuedAt;
        private LocalDateTime completedAt;
        private LocalDateTime cancelledAt;
    }

    @Data
    public static class WorkflowResult {
        private List<WorkflowStep> steps;
        private String finalResult;
        private List<String> pendingAsyncSteps;  // IDs of steps that will be executed asynchronously
    }

    @Data
    public static class WorkflowStep {
        private int step;
        private String stepId;  // Unique identifier for the step
        private String target; // e.g., "quotes-service"
        private Object result; // Step output, e.g., {"name": "Socrates"}
        private boolean isAsync;  // Whether this step is executed asynchronously
        private Map<String, Object> params; // Step parameters
        private String action; // Step action
        private String intent; // Step intent
    }

    @Data
    public static class WorkflowStepMetadata {
        private int step;
        private String stepId;  // Unique identifier for the step
        private String status;
        private long durationMs;
        private String target;
        private LocalDateTime executedAt;  // When the step was executed
        private TokenUsage tokenUsage;     // Token usage for AI models
        private boolean isAsync;  // Whether this step was executed asynchronously
        private String model;     // The model used for this step (e.g., "gpt-4o-2024-08-06", "gemini-2.0-flash")
        
        @Data
        public static class TokenUsage {
            private long promptTokens;
            private long completionTokens;
            private long totalTokens;
        }
    }
}