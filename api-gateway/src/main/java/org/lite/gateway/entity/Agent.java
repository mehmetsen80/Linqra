package org.lite.gateway.entity;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.lite.gateway.enums.AgentStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Document(collection = "agents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Agent {
    @Id
    private String id;
    
    // Basic Information
    private String name;                    // "WhatsApp Analysis Agent"
    private String description;             // "Analyzes WhatsApp messages and categorizes them"
    
    private AgentStatus status;             // IDLE, RUNNING, WAITING, COMPLETED, ERROR
    
    // Team & AI App Association
    private String teamId;                  // Team that owns this agent (inherits team permissions)
    private String routeIdentifier;         // "komunas-app" - which AI app this agent serves
    
    // Resource Configuration (MongoDB connections, API keys, etc.)
    @Field("resource_configs")
    private Map<String, String> resourceConfigs; // Database connections, external service configs
    
    // LLM Configuration (using existing LinqTool setup)
    private String primaryLinqToolId;       // Reference to existing LinqTool for LLM integration
    private List<String> supportedIntents;  // ["mongodb_read", "llm_analysis", "milvus_write"]
    
    // Agent Capabilities
    @Field("capabilities")
    private Set<String> capabilities;       // ["mongodb_read", "llm_analysis", "milvus_write"]
    
    // Scheduling (Quartz - Database backed)
    private String cronExpression;          // "0 */1 * * * *" (every hour)
    private String cronDescription;         // "Every hour at minute 0" (human-readable description)
    private boolean autoSchedule;           // true/false
    private String quartzJobKey;            // Quartz job identifier for persistence
    
    // Current State
    private String currentTask;             // What the agent is doing
    private LocalDateTime lastRun;          // Last execution time
    private LocalDateTime nextRun;          // Next scheduled run
    private String lastError;               // Last error message if any
    
    // AI App Endpoints (provided by developers, accessed via /r/route-identifier/)
    @Field("app_endpoints")
    private Map<String, String> appEndpoints; // "analyze_messages": "/api/analyze", "save_results": "/api/save"
    
    // Agent Configuration
    private String agentType;               // "data_analysis", "workflow_orchestrator", "monitoring"
    private boolean enabled;                // true/false - can be disabled without deletion
    private int maxRetries;                 // Maximum retry attempts for failed tasks
    
    private Integer timeoutMinutes;         // Task timeout duration in minutes
    
    // Audit Fields
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;               // User who created the agent
    private String updatedBy;               // User who last updated the agent
    
    // Relationships
    @Field("tasks")
    private List<String> taskIds;           // List of AgentTask IDs this agent can perform
    
    // private List<AgentExecution> executions; // Execution history (will be created later)
    
    // Pre-persist and pre-update methods
    public void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        status = AgentStatus.IDLE;
        enabled = true;
        maxRetries = 3;
        timeoutMinutes = 30;
    }
    
    public void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper methods
    public Duration getTimeoutAsDuration() {
        return Duration.ofMinutes(timeoutMinutes != null ? timeoutMinutes : 30);
    }
    
    public void setTimeoutFromDuration(Duration duration) {
        this.timeoutMinutes = (int) duration.toMinutes();
    }
    
    public boolean isScheduled() {
        return autoSchedule && cronExpression != null && !cronExpression.trim().isEmpty();
    }
    
    public boolean canExecute() {
        return enabled && status != AgentStatus.ERROR;
    }
    
    public void markAsRunning(String task) {
        this.status = AgentStatus.RUNNING;
        this.currentTask = task;
        this.lastRun = LocalDateTime.now();
    }
    
    public void markAsCompleted() {
        this.status = AgentStatus.COMPLETED;
        this.currentTask = null;
        this.lastError = null;
    }
    
    public void markAsError(String error) {
        this.status = AgentStatus.ERROR;
        this.lastError = error;
        this.currentTask = null;
    }
    
    public void markAsIdle() {
        this.status = AgentStatus.IDLE;
        this.currentTask = null;
        this.lastError = null;
    }
    
    public boolean hasTasks() {
        return taskIds != null && !taskIds.isEmpty();
    }
    
    public void addTask(String taskId) {
        if (taskIds == null) {
            taskIds = new java.util.ArrayList<>();
        }
        if (!taskIds.contains(taskId)) {
            taskIds.add(taskId);
        }
    }
    
    public void removeTask(String taskId) {
        if (taskIds != null) {
            taskIds.remove(taskId);
        }
    }
} 