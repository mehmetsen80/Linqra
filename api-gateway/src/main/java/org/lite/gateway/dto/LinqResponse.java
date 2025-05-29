package org.lite.gateway.dto;

import lombok.Data;

import java.util.List;
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
    }

    @Data
    public static class WorkflowResult {
        private List<WorkflowStep> steps;
        private String finalResult;
    }

    @Data
    public static class WorkflowStep {
        private int step; // Step number
        private String target; // e.g., "quotes-service"
        private Object result; // Step output, e.g., {"name": "Socrates"}
    }



    @Data
    public static class WorkflowStepMetadata {
        private int step;
        private String status;
        private long durationMs;
        private String target;
    }
}