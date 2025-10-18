package org.lite.gateway.dto;

import lombok.Data;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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
        private String modelName;              // Normalized name (e.g., "gpt-4o")
        private Set<String> versions;          // Actual versions used (e.g., ["gpt-4o-2024-08-06"])
        private String provider;               // "openai", "gemini", etc.
        private long requests;
        private long promptTokens;
        private long completionTokens;
        private long totalTokens;
        private double costUsd;
        private double averageLatencyMs;
        
        public ModelUsage() {
            this.versions = new HashSet<>();
        }
    }
    
    @Data
    public static class ProviderUsage {
        private String provider;
        private long requests;
        private long totalTokens;
        private double costUsd;
    }
}

