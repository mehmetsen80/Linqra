package org.lite.gateway.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.lite.gateway.enums.AgentTaskStatus;
import org.lite.gateway.enums.AgentTaskType;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "agent_tasks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTask {
    @Id
    private String id;
    
    // Task Identification
    private String name;                    // "analyze_whatsapp_messages"
    private String description;             // "Read WhatsApp messages, categorize them, and save to Milvus"
    private AgentTaskType taskType;         // DATA_PROCESSING, API_CALL, WORKFLOW_TRIGGER, etc.
    
    // Agent Association
    private String agentId;                 // Reference to the Agent that owns this task
    private String agentName;               // Denormalized agent name for easy querying
    
    // Task Configuration
    private int priority;                   // 1 (highest) to 10 (lowest)
    private boolean enabled;                // true/false - can be disabled without deletion
    private int maxRetries;                 // Maximum retry attempts for failed executions
    private int timeoutMinutes;             // Task timeout duration in minutes
    
    // Task Parameters & Configuration
    @Field("task_config")
    private Map<String, Object> taskConfig; // Task-specific configuration (JSON-like structure)
    
    // Input/Output Specifications
    @Field("input_sources")
    private List<String> inputSources;      // ["mongodb://linqra/whatsapp_messages", "linq://komunas-app/status"]
    
    @Field("output_targets")
    private List<String> outputTargets;     // ["milvus://analysis_results", "mongodb://linqra/processed_data"]
    
    // Dependencies & Prerequisites
    @Field("dependencies")
    private List<String> dependencies;      // Task IDs that must complete before this task
    
    @Field("prerequisites")
    private Map<String, Object> prerequisites; // Conditions that must be met (e.g., {"time": "09:00", "data_available": true})
    
    // Execution Control
    private String cronExpression;          // "0 0 9 * * *" (daily at 9 AM) - if null, manual execution only
    private boolean autoExecute;            // true/false - can run automatically based on cron
    private String executionTrigger;        // "cron", "manual", "event_driven", "workflow"
    
    // Task Logic & Implementation
    private String implementationType;      // "linq_protocol", "direct_api", "custom_script", "workflow_trigger"
    private String implementationTarget;    // For Linq Protocol: "inventory-service", for Direct API: full URL
    
    // Linq Protocol specific fields (when implementationType is "linq_protocol")
    @Field("linq_config")
    private Map<String, Object> linqConfig; // Linq protocol configuration matching LinqRequest structure
    
    // Direct API specific fields (when implementationType is "direct_api")
    @Field("api_config")
    private Map<String, Object> apiConfig;  // API call configuration (method, headers, body template)
    
    // Custom Script specific fields (when implementationType is "custom_script")
    private String scriptContent;           // Custom script content if needed
    private String scriptLanguage;          // "javascript", "python", "groovy"
    
    // Task State & Status
    private AgentTaskStatus status;         // PENDING, READY, RUNNING, COMPLETED, FAILED, SKIPPED, CANCELLED, TIMEOUT
    private LocalDateTime lastExecuted;     // Last execution time
    private LocalDateTime nextExecution;    // Next scheduled execution
    private String lastError;               // Last error message if any
    
    // Execution History
    private int totalExecutions;            // Total number of executions
    private int successfulExecutions;       // Number of successful executions
    private int failedExecutions;           // Number of failed executions
    private double averageExecutionTime;    // Average execution time in milliseconds
    
    // Audit Fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;               // User who created the task
    private String updatedBy;               // User who last updated the task
    
    // Pre-persist and pre-update methods
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = AgentTaskStatus.PENDING;
        enabled = true;
        priority = 5;
        maxRetries = 3;
        timeoutMinutes = 30;
        totalExecutions = 0;
        successfulExecutions = 0;
        failedExecutions = 0;
        averageExecutionTime = 0.0;
    }
    
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper methods
    public boolean isReadyToExecute() {
        return enabled && 
               (status == AgentTaskStatus.PENDING || status == AgentTaskStatus.READY) && 
               (cronExpression == null || autoExecute);
    }
    
    public boolean hasDependencies() {
        return dependencies != null && !dependencies.isEmpty();
    }
    
    public boolean isScheduled() {
        return autoExecute && cronExpression != null && !cronExpression.trim().isEmpty();
    }
    
    public boolean isLinqProtocolTask() {
        return "linq_protocol".equals(implementationType);
    }
    
    public boolean isDirectApiTask() {
        return "direct_api".equals(implementationType);
    }
    
    public boolean isCustomScriptTask() {
        return "custom_script".equals(implementationType);
    }
    
    public boolean isWorkflowTriggerTask() {
        return "workflow_trigger".equals(implementationType);
    }
    
    public void markAsExecuting() {
        this.status = AgentTaskStatus.RUNNING;
        this.lastExecuted = LocalDateTime.now();
    }
    
    public void markAsCompleted(long executionTimeMs) {
        this.status = AgentTaskStatus.COMPLETED;
        this.totalExecutions++;
        this.successfulExecutions++;
        updateAverageExecutionTime(executionTimeMs);
    }
    
    public void markAsFailed(String error, long executionTimeMs) {
        this.status = AgentTaskStatus.FAILED;
        this.lastError = error;
        this.totalExecutions++;
        this.failedExecutions++;
        updateAverageExecutionTime(executionTimeMs);
    }
    
    public void markAsSkipped(String reason) {
        this.status = AgentTaskStatus.SKIPPED;
        this.lastError = reason;
    }
    
    public void markAsCancelled() {
        this.status = AgentTaskStatus.CANCELLED;
    }
    
    public void markAsTimeout() {
        this.status = AgentTaskStatus.TIMEOUT;
        this.lastError = "Task execution timed out";
    }
    
    private void updateAverageExecutionTime(long executionTimeMs) {
        if (totalExecutions > 0) {
            this.averageExecutionTime = ((this.averageExecutionTime * (totalExecutions - 1)) + executionTimeMs) / totalExecutions;
        }
    }
    
    public double getSuccessRate() {
        if (totalExecutions == 0) return 0.0;
        return (double) successfulExecutions / totalExecutions * 100;
    }
    
    // Linq Protocol helper methods - aligned with LinqRequest structure
    public String getLinqTarget() {
        if (isLinqProtocolTask() && linqConfig != null) {
            // Extract from link.target
            Map<String, Object> link = (Map<String, Object>) linqConfig.get("link");
            if (link != null) {
                return (String) link.get("target");
            }
        }
        return null;
    }
    
    public String getLinqAction() {
        if (isLinqProtocolTask() && linqConfig != null) {
            // Extract from link.action
            Map<String, Object> link = (Map<String, Object>) linqConfig.get("link");
            if (link != null) {
                return (String) link.get("action");
            }
        }
        return null;
    }
    
    public String getLinqIntent() {
        if (isLinqProtocolTask() && linqConfig != null) {
            // Extract from query.intent
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            if (query != null) {
                return (String) query.get("intent");
            }
        }
        return null;
    }
    
    public Map<String, Object> getLinqParams() {
        if (isLinqProtocolTask() && linqConfig != null) {
            // Extract from query.params
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            if (query != null) {
                return (Map<String, Object>) query.get("params");
            }
        }
        return null;
    }
    
    public Object getLinqPayload() {
        if (isLinqProtocolTask() && linqConfig != null) {
            // Extract from query.payload
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            if (query != null) {
                return query.get("payload");
            }
        }
        return null;
    }
    
    public Map<String, Object> getLinqToolConfig() {
        if (isLinqProtocolTask() && linqConfig != null) {
            // Extract from query.toolConfig
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            if (query != null) {
                return (Map<String, Object>) query.get("toolConfig");
            }
        }
        return null;
    }
    
    // Method to build LinqRequest from task configuration
    public Map<String, Object> buildLinqRequest() {
        if (!isLinqProtocolTask()) {
            return null;
        }
        
        Map<String, Object> linqRequest = new java.util.HashMap<>();
        
        // Build link section
        Map<String, Object> link = new java.util.HashMap<>();
        link.put("target", getLinqTarget());
        link.put("action", getLinqAction());
        linqRequest.put("link", link);
        
        // Build query section
        Map<String, Object> query = new java.util.HashMap<>();
        query.put("intent", getLinqIntent());
        query.put("params", getLinqParams() != null ? getLinqParams() : new java.util.HashMap<>());
        
        Object payload = getLinqPayload();
        if (payload != null) {
            query.put("payload", payload);
        }
        
        Map<String, Object> toolConfig = getLinqToolConfig();
        if (toolConfig != null) {
            query.put("toolConfig", toolConfig);
        }
        
        linqRequest.put("query", query);
        
        return linqRequest;
    }
} 