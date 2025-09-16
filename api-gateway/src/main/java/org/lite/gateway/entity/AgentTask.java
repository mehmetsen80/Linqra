package org.lite.gateway.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.lite.gateway.enums.AgentTaskType;
import org.lite.gateway.enums.ExecutionTrigger;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
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
    @Builder.Default
    private AgentTaskType taskType = AgentTaskType.WORKFLOW_EMBEDDED; // WORKFLOW_EMBEDDED, WORKFLOW_TRIGGER
    
    // Agent Association
    private String agentId;                 // Reference to the Agent that owns this task
    
    // Task Configuration
    @Builder.Default
    private int priority = 5;               // 1 (highest) to 10 (lowest)
    @Builder.Default
    private boolean enabled = true;         // true/false - can be disabled without deletion
    @Builder.Default
    private int maxRetries = 3;             // Maximum retry attempts for failed executions
    @Builder.Default
    private int timeoutMinutes = 30;        // Task timeout duration in minutes
    
    // Execution Control
    @Builder.Default
    private String cronExpression = "";     // "0 0 9 * * *" (daily at 9 AM) - if empty, manual execution only
    @Builder.Default
    private String cronDescription = "";    // "Daily at 9 AM" (human-readable description)
    @Builder.Default
    private ExecutionTrigger executionTrigger = ExecutionTrigger.MANUAL; // How this task should be triggered
    
    // Scheduling State (moved from Agent entity)
    private LocalDateTime nextRun;          // Next scheduled execution time for this task
    private LocalDateTime lastRun;          // Last execution time for this task
    
    // Linq Protocol specific fields
    @Field("linq_config")
    @JsonProperty("linq_config")
    private Map<String, Object> linqConfig; // Linq protocol configuration matching LinqRequest structure
    
    // Direct API specific fields
    @Field("api_config")
    @JsonProperty("api_config")
    private Map<String, Object> apiConfig;  // API call configuration (method, headers, body template)
    
    // Custom Script specific fields
    private String scriptContent;           // Custom script content if needed
    private String scriptLanguage;          // "javascript", "python", "groovy"
    
    // Audit Fields
    @CreatedDate
    private LocalDateTime createdAt;
    @LastModifiedDate
    private LocalDateTime updatedAt;
    private String createdBy;               // User who created the task
    private String updatedBy;               // User who last updated the task
    
    // Helper methods
        public boolean isReadyToExecute() {
        return enabled;
    }
    
    public boolean isScheduled() {
        return isAutoExecute() && cronExpression != null && !cronExpression.trim().isEmpty();
    }
    
    /**
     * Derives autoExecute behavior from executionTrigger
     */
    public boolean isAutoExecute() {
        if (executionTrigger == null) return false;
        return switch (executionTrigger) {
            case CRON, AGENT_SCHEDULED -> true;
            case MANUAL -> false;
            case EVENT_DRIVEN, WORKFLOW -> true; // Usually automatic
        };
    }
    
    // Task Type helper methods
    public boolean isWorkflowTriggerTask() {
        return AgentTaskType.WORKFLOW_TRIGGER.equals(taskType);
    }
    
    public boolean isWorkflowEmbeddedTask() {
        return AgentTaskType.WORKFLOW_EMBEDDED.equals(taskType);
    }
    
    /*
    // Future task type helper methods (commented out for now)
    public boolean isDataProcessingTask() {
        return AgentTaskType.DATA_PROCESSING.equals(taskType);
    }
    
    public boolean isApiCallTask() {
        return AgentTaskType.API_CALL.equals(taskType);
    }
    
    public boolean isLlmAnalysisTask() {
        return AgentTaskType.LLM_ANALYSIS.equals(taskType);
    }
    
    public boolean isVectorOperationsTask() {
        return AgentTaskType.VECTOR_OPERATIONS.equals(taskType);
    }
    
    public boolean isNotificationTask() {
        return AgentTaskType.NOTIFICATION.equals(taskType);
    }
    
    public boolean isDataSyncTask() {
        return AgentTaskType.DATA_SYNC.equals(taskType);
    }
    
    public boolean isMonitoringTask() {
        return AgentTaskType.MONITORING.equals(taskType);
    }
    
    public boolean isReportingTask() {
        return AgentTaskType.REPORTING.equals(taskType);
    }
    
    public boolean isCustomScriptTask() {
        return AgentTaskType.CUSTOM_SCRIPT.equals(taskType);
    }
    */
    
    // Execution Trigger validation methods
    public boolean isManualTrigger() {
        return ExecutionTrigger.MANUAL.equals(executionTrigger);
    }
    
    public boolean isCronTrigger() {
        return ExecutionTrigger.CRON.equals(executionTrigger);
    }
    
    public boolean isEventDrivenTrigger() {
        return ExecutionTrigger.EVENT_DRIVEN.equals(executionTrigger);
    }
    
    public boolean isWorkflowTrigger() {
        return ExecutionTrigger.WORKFLOW.equals(executionTrigger);
    }
    
    public boolean isAgentScheduledTrigger() {
        return ExecutionTrigger.AGENT_SCHEDULED.equals(executionTrigger);
    }
    
    /**
     * Validates that the executionTrigger is consistent with other task configuration
     */
    public boolean isExecutionTriggerValid() {
        if (executionTrigger == null) {
            return false;
        }

        return switch (executionTrigger) {
            case CRON ->
                // CRON trigger requires cronExpression and should be auto-executable
                    cronExpression != null && !cronExpression.trim().isEmpty();
            case MANUAL ->
                // MANUAL trigger should not have cron and should not be auto-executable
                    (cronExpression == null || cronExpression.trim().isEmpty());
            case AGENT_SCHEDULED ->
                // AGENT_SCHEDULED is always auto-executable
                    true;
            case EVENT_DRIVEN, WORKFLOW ->
                // These can have various configurations and are auto-executable
                    true;
        };
    }
    
    /**
     * Validates that the task configuration is consistent with the taskType
     */
    public boolean isTaskTypeConfigurationValid() {
        if (taskType == null) {
            return false;
        }

        return switch (taskType) {
            case WORKFLOW_TRIGGER ->
                // WORKFLOW_TRIGGER tasks must have linq_config with workflowId
                    linqConfig != null &&
                            linqConfig.containsKey("query") &&
                            ((Map<?, ?>) linqConfig.get("query")).containsKey("workflowId");
            case WORKFLOW_EMBEDDED ->
                // WORKFLOW_EMBEDDED tasks must have linq_config with embedded workflow steps
                    linqConfig != null &&
                            linqConfig.containsKey("query") &&
                            ((Map<?, ?>) linqConfig.get("query")).containsKey("workflow");
            /*
            // Future task type validations (commented out for now)
            case API_CALL ->
                // API_CALL tasks must have either linq_config or api_config
                    linqConfig != null || apiConfig != null;
            case CUSTOM_SCRIPT ->
                // CUSTOM_SCRIPT tasks must have script content and language
                    scriptContent != null && !scriptContent.trim().isEmpty() &&
                            scriptLanguage != null && !scriptLanguage.trim().isEmpty();
            case LLM_ANALYSIS ->
                // LLM_ANALYSIS tasks should have linq_config with AI model configuration
                    linqConfig != null;
            case VECTOR_OPERATIONS ->
                // VECTOR_OPERATIONS tasks should have linq_config for Milvus operations
                    linqConfig != null;
            case DATA_PROCESSING, NOTIFICATION, DATA_SYNC, MONITORING, REPORTING ->
                // These types are flexible and can use various configurations
                    linqConfig != null || apiConfig != null || scriptContent != null;
            */
        };
    }
    
    // Linq Protocol helper methods - aligned with LinqRequest structure
    public String getLinqTarget() {
        if (linqConfig != null) {
            // Extract from link.target
            Map<String, Object> link = (Map<String, Object>) linqConfig.get("link");
            if (link != null) {
                return (String) link.get("target");
            }
        }
        return null;
    }
    
    public String getLinqAction() {
        if (linqConfig != null) {
            // Extract from link.action
            Map<String, Object> link = (Map<String, Object>) linqConfig.get("link");
            if (link != null) {
                return (String) link.get("action");
            }
        }
        return null;
    }
    
    public String getLinqIntent() {
        if (linqConfig != null) {
            // Extract from query.intent
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            if (query != null) {
                return (String) query.get("intent");
            }
        }
        return null;
    }
    
    public Map<String, Object> getLinqParams() {
        if (linqConfig != null) {
            // Extract from query.params
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            if (query != null) {
                return (Map<String, Object>) query.get("params");
            }
        }
        return null;
    }
    
    public Object getLinqPayload() {
        if (linqConfig != null) {
            // Extract from query.payload
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            if (query != null) {
                return query.get("payload");
            }
        }
        return null;
    }
    
    public Map<String, Object> getLinqToolConfig() {
        if (linqConfig != null) {
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
        if (linqConfig == null) {
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

    // Explicit getters and setters for complex fields
    
    public Map<String, Object> getLinqConfig() {
        return linqConfig;
    }
    
    public void setLinqConfig(Map<String, Object> linqConfig) {
        this.linqConfig = linqConfig;
    }
} 
