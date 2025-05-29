package org.lite.gateway.model;

import lombok.Data;
import java.util.Map;

@Data
public class LinqWorkflowStats {
    // Overall execution stats
    private int totalExecutions;
    private int successfulExecutions;
    private int failedExecutions;
    private double averageExecutionTime;
    
    // Step-level stats
    private Map<Integer, StepStats> stepStats;
    private Map<String, TargetStats> targetStats;
    
    // Model usage stats
    private Map<String, ModelStats> modelStats;
    
    // Time-based stats
    private Map<String, Integer> hourlyExecutions;
    private Map<String, Integer> dailyExecutions;
    
    @Data
    public static class StepStats {
        private int totalExecutions;
        private int successfulExecutions;
        private int failedExecutions;
        private double averageDurationMs;
        private String mostCommonTarget;
        private String mostCommonIntent;
    }
    
    @Data
    public static class TargetStats {
        private int totalExecutions;
        private double averageDurationMs;
        private Map<String, Integer> intentCounts;
    }
    
    @Data
    public static class ModelStats {
        private int totalExecutions;
        private double averageDurationMs;
        private long totalPromptTokens;
        private long totalCompletionTokens;
        private long totalTokens;
    }
}
