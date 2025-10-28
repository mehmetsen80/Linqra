package org.lite.gateway.dto;

import lombok.Data;
import java.util.*;

@Data
public class LlmUsageStats {
    private String teamId;
    private Period period;
    private TotalUsage totalUsage;
    private Map<String, ModelUsage> modelBreakdown = new HashMap<>();
    private Map<String, ProviderUsage> providerBreakdown = new HashMap<>();
    private List<DailyUsage> dailyBreakdown = new ArrayList<>();
    
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
        private String modelCategory;          // Model category (e.g., "openai-chat", "gemini-chat", etc.)
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
    
    @Data
    public static class DailyUsage {
        private String date;                // yyyy-MM-dd format
        private long requests;
        private long totalTokens;
        private double costUsd;
    }
}

