package org.lite.gateway.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class LinqResponse {
    private Object result; // Single request result or WorkflowResult for workflows
    private Metadata metadata;

    @Data
    public static class Metadata {
        private String source; // e.g., "quotes-service", "openai", "workflow"
        private String status; // e.g., "success", "error"
        private String team;   // e.g., "67d0aeb17172416c411d419e"
        private boolean cacheHit; // e.g., false
        private List<WorkflowStepMetadata> workflowMetadata; // Step-specific metadata
    }

    @Data
    public static class WorkflowResult {
        private List<StepResult> steps; // Results from each step
        private Object finalResult; // Final answer, e.g., OpenAI's saying
    }

    @Data
    public static class StepResult {
        private int step; // Step number
        private String target; // e.g., "quotes-service"
        private Object result; // Step output, e.g., {"name": "Socrates"}
    }

    @Data
    public static class WorkflowStepMetadata {
        private int step; // Step number
        private String status; // e.g., "success", "error"
        private Long durationMs; // Execution time
    }
}