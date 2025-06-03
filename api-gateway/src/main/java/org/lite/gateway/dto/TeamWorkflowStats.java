package org.lite.gateway.dto;

import lombok.Data;
import java.util.Map;

@Data
public class TeamWorkflowStats {
    private long totalExecutions;
    private long successfulExecutions;
    private long failedExecutions;
    private double averageExecutionTime;
    
    // Stats by workflow
    private Map<String, WorkflowStats> workflowStats;
    
    // Stats by step
    private Map<String, StepStats> stepStats;
    
    // Stats by target
    private Map<String, TargetStats> targetStats;
    
    // Stats by model
    private Map<String, ModelStats> modelStats;
    
    // Time-based stats
    private Map<String, Integer> hourlyExecutions;
    private Map<String, Integer> dailyExecutions;
    
    @Data
    public static class WorkflowStats {
        private String workflowId;
        private String workflowName;
        private long totalExecutions;
        private long successfulExecutions;
        private long failedExecutions;
        private double averageExecutionTime;
        private Map<String, StepStats> stepStats;
        
        public void incrementExecutions(boolean success) {
            totalExecutions++;
            if (success) {
                successfulExecutions++;
            } else {
                failedExecutions++;
            }
        }
    }
    
    @Data
    public static class StepStats {
        private int stepNumber;
        private long totalExecutions;
        private long successfulExecutions;
        private long failedExecutions;
        private double averageDurationMs;
        private String mostCommonTarget;
        private String mostCommonIntent;
        
        public void incrementExecutions(boolean success) {
            totalExecutions++;
            if (success) {
                successfulExecutions++;
            } else {
                failedExecutions++;
            }
        }
    }
    
    @Data
    public static class TargetStats {
        private String target;
        private long totalExecutions;
        private long successfulExecutions;
        private long failedExecutions;
        private double averageDurationMs;
        private Map<String, Integer> intentCounts;
        
        public void incrementExecutions(boolean success) {
            totalExecutions++;
            if (success) {
                successfulExecutions++;
            } else {
                failedExecutions++;
            }
        }
    }
    
    @Data
    public static class ModelStats {
        private String model;
        private long totalExecutions;
        private long successfulExecutions;
        private long failedExecutions;
        private double averageDurationMs;
        private long totalPromptTokens;
        private long totalCompletionTokens;
        private long totalTokens;
        
        public void incrementExecutions(boolean success) {
            totalExecutions++;
            if (success) {
                successfulExecutions++;
            } else {
                failedExecutions++;
            }
        }
    }
} 