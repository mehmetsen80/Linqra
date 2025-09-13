package org.lite.gateway.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.lite.gateway.enums.AgentTaskType;
import org.lite.gateway.enums.ExecutionTrigger;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import com.fasterxml.jackson.annotation.JsonProperty;

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
    
    // Task Configuration
    private int priority;                   // 1 (highest) to 10 (lowest)
    private boolean enabled;                // true/false - can be disabled without deletion
    private int maxRetries;                 // Maximum retry attempts for failed executions
    private int timeoutMinutes;             // Task timeout duration in minutes
    
    // Input/Output Specifications
    @Field("input_sources")
    @JsonProperty("input_sources")
    private List<String> inputSources;      // ["mongodb://linqra/whatsapp_messages", "linq://komunas-app/status"]
    
    @Field("output_targets")
    @JsonProperty("output_targets")
    private List<String> outputTargets;     // ["milvus://analysis_results", "mongodb://linqra/processed_data"]
    
    @Field("prerequisites")
    private Map<String, Object> prerequisites; // Conditions that must be met (e.g., {"time": "09:00", "data_available": true})
    
    // Execution Control
    private String cronExpression;          // "0 0 9 * * *" (daily at 9 AM) - if null, manual execution only
    private boolean autoExecute;            // true/false - can run automatically based on cron
    private ExecutionTrigger executionTrigger; // How this task should be triggered
    
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;               // User who created the task
    private String updatedBy;               // User who last updated the task
    
    // Pre-persist and pre-update methods
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (!enabled) enabled = true; // Only set to true if not already set
        if (priority == 0) priority = 5; // Only set default if not already set
        if (maxRetries == 0) maxRetries = 3;
        if (timeoutMinutes == 0) timeoutMinutes = 30;
    }
    
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper methods
        public boolean isReadyToExecute() {
        return enabled;
    }
    
    public boolean isScheduled() {
        return autoExecute && cronExpression != null && !cronExpression.trim().isEmpty();
    }
    
    // Task Type helper methods
    public boolean isDataProcessingTask() {
        return AgentTaskType.DATA_PROCESSING.equals(taskType);
    }
    
    public boolean isApiCallTask() {
        return AgentTaskType.API_CALL.equals(taskType);
    }
    
    public boolean isWorkflowTriggerTask() {
        return AgentTaskType.WORKFLOW_TRIGGER.equals(taskType);
    }
    
    public boolean isWorkflowEmbeddedTask() {
        return AgentTaskType.WORKFLOW_EMBEDDED.equals(taskType);
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
        
        switch (executionTrigger) {
            case CRON:
                // CRON trigger requires cronExpression and autoExecute should be true
                return cronExpression != null && !cronExpression.trim().isEmpty() && autoExecute;
            case MANUAL:
                // MANUAL trigger should not have cron and autoExecute should be false
                return (cronExpression == null || cronExpression.trim().isEmpty()) && !autoExecute;
            case AGENT_SCHEDULED:
                // AGENT_SCHEDULED allows autoExecute but doesn't require cron
                return autoExecute;
            case EVENT_DRIVEN:
            case WORKFLOW:
                // These can have various configurations
                return true;
            default:
                return false;
        }
    }
    
    /**
     * Validates that the task configuration is consistent with the taskType
     */
    public boolean isTaskTypeConfigurationValid() {
        if (taskType == null) {
            return false;
        }
        
        switch (taskType) {
            case WORKFLOW_TRIGGER:
                // WORKFLOW_TRIGGER tasks must have linq_config with workflowId
                return linqConfig != null && 
                       linqConfig.containsKey("query") &&
                       ((Map<?, ?>) linqConfig.get("query")).containsKey("workflowId");
                       
            case WORKFLOW_EMBEDDED:
                // WORKFLOW_EMBEDDED tasks must have linq_config with embedded workflow steps
                return linqConfig != null && 
                       linqConfig.containsKey("query") &&
                       ((Map<?, ?>) linqConfig.get("query")).containsKey("workflow");
                       
            case API_CALL:
                // API_CALL tasks must have either linq_config or api_config
                return linqConfig != null || apiConfig != null;
                
            case CUSTOM_SCRIPT:
                // CUSTOM_SCRIPT tasks must have script content and language
                return scriptContent != null && !scriptContent.trim().isEmpty() &&
                       scriptLanguage != null && !scriptLanguage.trim().isEmpty();
                       
            case LLM_ANALYSIS:
                // LLM_ANALYSIS tasks should have linq_config with AI model configuration
                return linqConfig != null;
                
            case VECTOR_OPERATIONS:
                // VECTOR_OPERATIONS tasks should have linq_config for Milvus operations
                return linqConfig != null;
                
            case DATA_PROCESSING:
            case NOTIFICATION:
            case DATA_SYNC:
            case MONITORING:
            case REPORTING:
                // These types are flexible and can use various configurations
                return linqConfig != null || apiConfig != null || scriptContent != null;
                
            default:
                return false;
        }
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
    
    public List<String> getInputSources() {
        return inputSources;
    }
    
    public void setInputSources(List<String> inputSources) {
        this.inputSources = inputSources;
    }
    
    public List<String> getOutputTargets() {
        return outputTargets;
    }
    
    public void setOutputTargets(List<String> outputTargets) {
        this.outputTargets = outputTargets;
    }
    
    public Map<String, Object> getLinqConfig() {
        return linqConfig;
    }
    
    public void setLinqConfig(Map<String, Object> linqConfig) {
        this.linqConfig = linqConfig;
    }
} 
