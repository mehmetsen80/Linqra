package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExecutionProgressUpdate {
    
    // Helper methods for builder pattern
    public ExecutionProgressUpdate withStatus(String status) {
        this.status = status;
        return this;
    }
    
    public ExecutionProgressUpdate withErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
        return this;
    }
    
    public ExecutionProgressUpdate withMemoryUsage(MemoryUsage memoryUsage) {
        this.memoryUsage = memoryUsage;
        return this;
    }
    
    // Execution identification
    private String executionId;
    private String agentId;
    private String agentName;
    private String taskId;
    private String taskName;
    private String teamId;
    
    // Progress information
    private String status; // STARTED, RUNNING, COMPLETED, FAILED, CANCELLED
    private int currentStep;
    private int totalSteps;
    private String currentStepName;
    private String currentStepTarget;
    private String currentStepAction;
    
    // Timing information
    private LocalDateTime startedAt;
    private LocalDateTime lastUpdatedAt;
    private Long executionDurationMs;
    private Long stepDurationMs;
    
    // Memory usage
    private MemoryUsage memoryUsage;
    
    // Error information
    private String errorMessage;
    private String errorDetails;
    
    // Step results
    private Map<String, Object> stepResults;
    
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemoryUsage {
        private long heapUsed;
        private long heapMax;
        private long nonHeapUsed;
        private double heapUsagePercent;
        private double nonHeapUsagePercent;
        
        public static MemoryUsage fromCurrentMemory() {
            var memoryBean = java.lang.management.ManagementFactory.getMemoryMXBean();
            var heapUsage = memoryBean.getHeapMemoryUsage();
            var nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
            
            long heapUsed = heapUsage.getUsed();
            long heapMax = heapUsage.getMax();
            long nonHeapUsed = nonHeapUsage.getUsed();
            
            return MemoryUsage.builder()
                    .heapUsed(heapUsed)
                    .heapMax(heapMax)
                    .nonHeapUsed(nonHeapUsed)
                    .heapUsagePercent(heapMax > 0 ? (double) heapUsed / heapMax * 100 : 0)
                    .nonHeapUsagePercent(nonHeapUsed > 0 ? (double) nonHeapUsed / heapMax * 100 : 0)
                    .build();
        }
    }
}
