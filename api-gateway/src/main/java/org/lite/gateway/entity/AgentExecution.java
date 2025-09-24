package org.lite.gateway.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.lite.gateway.enums.ExecutionType;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.enums.ExecutionResult;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "agent_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentExecution {
    @Id
    private String id;
    
    // Execution Identification
    private String executionId;             // Unique execution identifier (UUID)
    private String agentId;                 // Reference to the Agent
    private String agentName;               // Denormalized agent name for easy querying
    private String taskId;                  // Reference to the AgentTask
    private String taskName;                // Denormalized task name for easy querying
    
    // Execution Context
    private String teamId;                  // Team that owns this execution
    private ExecutionType executionType;    // SCHEDULED, MANUAL, EVENT_DRIVEN, WORKFLOW, AGENT_SCHEDULED
    
    // Execution Timeline
    private LocalDateTime scheduledAt;      // When execution was scheduled
    private LocalDateTime startedAt;        // When execution actually started
    private LocalDateTime completedAt;      // When execution completed (success or failure)
    private Long executionDurationMs;       // Total execution time in milliseconds
    
    // Execution Status & Results
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.RUNNING;         // RUNNING, COMPLETED, FAILED, CANCELLED, TIMEOUT
    @Builder.Default
    private ExecutionResult result = ExecutionResult.UNKNOWN;         // SUCCESS, PARTIAL_SUCCESS, FAILURE, SKIPPED, UNKNOWN
    private String errorMessage;            // Error message if execution failed
    private String errorCode;               // Error code for programmatic handling
    private String errorStack;              // Full error stack trace for debugging
    
    // Input/Output Data
    @Field("input_data")
    private Map<String, Object> inputData;  // Input data provided to the task
    
    @Field("output_data")
    private Map<String, Object> outputData; // Output data produced by the task
    
    @Field("intermediate_results")
    private List<Map<String, Object>> intermediateResults; // Results from each step
    
    // Performance Metrics
    private Long memoryUsageBytes;          // Memory usage during execution
    private Double cpuUsagePercent;         // CPU usage during execution
    private Long networkBytesIn;            // Network bytes received
    private Long networkBytesOut;           // Network bytes sent
    
    // Retry Information
    @Builder.Default
    private int retryCount = 0;             // Number of retry attempts
    private int maxRetries;                 // Maximum retries allowed
    private List<LocalDateTime> retryAttempts; // Timestamps of retry attempts
    
    // Dependencies & Prerequisites
    @Field("dependency_results")
    private Map<String, Object> dependencyResults; // Results from dependent tasks
    
    @Field("prerequisite_checks")
    private Map<String, Boolean> prerequisiteChecks; // Whether prerequisites were met
    
    // External Service Calls
    @Field("api_calls")
    private List<Map<String, Object>> apiCalls; // External API calls made during execution
    
    @Field("llm_calls")
    private List<Map<String, Object>> llmCalls; // LLM API calls made during execution
    
    // Resource Usage
    @Field("mongodb_operations")
    private List<Map<String, Object>> mongodbOperations; // MongoDB operations performed
    
    @Field("milvus_operations")
    private List<Map<String, Object>> milvusOperations; // Milvus operations performed
    
    // Workflow Integration
    private String workflowId;              // If this execution triggered a workflow
    private String workflowExecutionId;     // Workflow execution ID
    private String workflowStatus;          // Status of the triggered workflow
    
    // Notifications & Alerts
    @Field("notifications_sent")
    private List<Map<String, Object>> notificationsSent; // Notifications sent during execution
    
    @Field("alerts_triggered")
    private List<Map<String, Object>> alertsTriggered; // Alerts triggered during execution
    
    // Audit & Compliance
    private String executedBy;              // User or system that triggered execution
    private String executionEnvironment;    // "dev", "staging", "production"
    private String version;                 // Agent version at time of execution
    private String commitHash;              // Git commit hash if applicable
    
    // Metadata
    @Field("execution_metadata")
    private Map<String, Object> executionMetadata; // Additional execution-specific metadata
    
    @Field("tags")
    private List<String> tags;              // Tags for categorization and filtering
    
    // Audit Fields
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    
    // Timestamps and defaults are automatically handled by annotations
    
    // Helper methods
    public boolean isCompleted() {
        return status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED || 
               status == ExecutionStatus.CANCELLED || status == ExecutionStatus.TIMEOUT;
    }
    
    public boolean isSuccessful() {
        return result == ExecutionResult.SUCCESS;
    }
    
    public boolean canRetry() {
        return retryCount < maxRetries && status == ExecutionStatus.FAILED;
    }
    
    public void markAsCompleted() {
        this.status = ExecutionStatus.COMPLETED;
        this.result = ExecutionResult.SUCCESS;
        this.completedAt = LocalDateTime.now();
        this.errorMessage = "";  // Clear error message on successful completion
        if (this.startedAt != null) {
            this.executionDurationMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }
    
    public void markAsFailed(String error, String errorCode) {
        this.status = ExecutionStatus.FAILED;
        this.result = ExecutionResult.FAILURE;
        this.errorMessage = error;
        this.errorCode = errorCode;
        this.completedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.executionDurationMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }
    
    public void markAsTimeout() {
        this.status = ExecutionStatus.TIMEOUT;
        this.result = ExecutionResult.FAILURE;
        this.errorMessage = "Execution timed out";
        this.errorCode = "TIMEOUT";
        this.completedAt = LocalDateTime.now();
        if (this.startedAt != null) {
            this.executionDurationMs = java.time.Duration.between(this.startedAt, this.completedAt).toMillis();
        }
    }
    
    public void addRetryAttempt() {
        this.retryCount++;
        if (this.retryAttempts == null) {
            this.retryAttempts = new java.util.ArrayList<>();
        }
        this.retryAttempts.add(LocalDateTime.now());
    }
    
    public void addApiCall(String endpoint, String method, int statusCode, Long responseTime) {
        if (this.apiCalls == null) {
            this.apiCalls = new java.util.ArrayList<>();
        }
        this.apiCalls.add(Map.of(
            "endpoint", endpoint,
            "method", method,
            "statusCode", statusCode,
            "responseTime", responseTime,
            "timestamp", LocalDateTime.now()
        ));
    }
    
    public void addLlmCall(String provider, String model, int tokensUsed, Long responseTime) {
        if (this.llmCalls == null) {
            this.llmCalls = new java.util.ArrayList<>();
        }
        this.llmCalls.add(Map.of(
            "provider", provider,
            "model", model,
            "tokensUsed", tokensUsed,
            "responseTime", responseTime,
            "timestamp", LocalDateTime.now()
        ));
    }
    
    public double getSuccessRate() {
        if (retryCount == 0) return 100.0;
        return (double) (maxRetries - retryCount) / maxRetries * 100;
    }
} 