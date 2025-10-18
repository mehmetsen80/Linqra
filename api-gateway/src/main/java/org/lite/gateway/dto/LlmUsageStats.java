package org.lite.gateway.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

@Data
public class LlmUsageStats {
    private String teamId;
    private Period period;
    private TotalUsage totalUsage;
    private Map<String, ModelUsage> modelBreakdown = new HashMap<>();
    private Map<String, ProviderUsage> providerBreakdown = new HashMap<>();
    
    @Data
    public static class Period {
        private String from;
        private String to;
    }
    
    @Data
    public static class TotalUsage {
        private long totalRequests;
        private long totalPromptTokens;
        private long totalCompletionTokens;
        private long totalTokens;
        private double totalCostUsd;
    }
    
    @Data
    public static class ModelUsage {
        private String modelName;
        private String provider; // "openai", "gemini", etc.
        private long requests;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private double costUsd;
        private double averageLatencyMs;
    }
    
    @Data
    public static class ProviderUsage {
        private String provider;
        private long requests;
        private long totalTokens;
        private double costUsd;
    }
}

