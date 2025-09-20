package org.lite.gateway.entity;

import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import org.lite.gateway.enums.AgentTaskType;
import org.lite.gateway.enums.ExecutionTrigger;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document("agent_task_versions")
public class AgentTaskVersion {
    @Id
    private String id;
    
    // References
    private String taskId;                  // References the original AgentTask
    private String agentId;                 // Agent ID for access control
    private String teamId;                  // Team ID for access control (derived from agent)
    
    // Version Information
    private Integer version;                // Version number
    private String changeDescription;       // Description of changes in this version
    private Long createdAt;                 // Creation timestamp
    private String createdBy;               // User who created this version
    
    // Snapshot of task configuration at this version
    private String name;                    // Task name at this version
    private String description;             // Task description at this version
    private AgentTaskType taskType;         // Task type at this version
    
    // Task Configuration snapshot
    private int priority;                   // Priority at this version
    private boolean enabled;                // Enabled status at this version
    private int maxRetries;                 // Max retries at this version
    private int timeoutMinutes;             // Timeout at this version
    
    // Execution Control snapshot
    private String cronExpression;          // Cron expression at this version
    private String cronDescription;         // Cron description at this version
    private ExecutionTrigger executionTrigger; // Execution trigger at this version
    
    // Configuration snapshots
    @Field("linq_config")
    @JsonProperty("linq_config")
    private Map<String, Object> linqConfig; // Linq protocol configuration at this version
    
    @Field("api_config")
    @JsonProperty("api_config")
    private Map<String, Object> apiConfig;  // API call configuration at this version
    
    // Custom Script snapshot
    private String scriptContent;           // Script content at this version
    private String scriptLanguage;          // Script language at this version
    
    // Helper methods to compare with current task
    public boolean hasDifferentConfiguration(AgentTask currentTask) {
        if (currentTask == null) return true;
        
        return !java.util.Objects.equals(this.name, currentTask.getName()) ||
               !java.util.Objects.equals(this.description, currentTask.getDescription()) ||
               !java.util.Objects.equals(this.taskType, currentTask.getTaskType()) ||
               this.priority != currentTask.getPriority() ||
               this.enabled != currentTask.isEnabled() ||
               this.maxRetries != currentTask.getMaxRetries() ||
               this.timeoutMinutes != currentTask.getTimeoutMinutes() ||
               !java.util.Objects.equals(this.cronExpression, currentTask.getCronExpression()) ||
               !java.util.Objects.equals(this.cronDescription, currentTask.getCronDescription()) ||
               !java.util.Objects.equals(this.executionTrigger, currentTask.getExecutionTrigger()) ||
               !java.util.Objects.equals(this.linqConfig, currentTask.getLinqConfig()) ||
               !java.util.Objects.equals(this.apiConfig, currentTask.getApiConfig()) ||
               !java.util.Objects.equals(this.scriptContent, currentTask.getScriptContent()) ||
               !java.util.Objects.equals(this.scriptLanguage, currentTask.getScriptLanguage());
    }
    
    // Factory method to create version from current task
    public static AgentTaskVersion fromAgentTask(AgentTask task, String changeDescription, String createdBy) {
        return AgentTaskVersion.builder()
                .taskId(task.getId())
                .agentId(task.getAgentId())
                .version(task.getVersion())
                .changeDescription(changeDescription)
                .createdAt(System.currentTimeMillis())
                .createdBy(createdBy)
                .name(task.getName())
                .description(task.getDescription())
                .taskType(task.getTaskType())
                .priority(task.getPriority())
                .enabled(task.isEnabled())
                .maxRetries(task.getMaxRetries())
                .timeoutMinutes(task.getTimeoutMinutes())
                .cronExpression(task.getCronExpression())
                .cronDescription(task.getCronDescription())
                .executionTrigger(task.getExecutionTrigger())
                .linqConfig(task.getLinqConfig())
                .apiConfig(task.getApiConfig())
                .scriptContent(task.getScriptContent())
                .scriptLanguage(task.getScriptLanguage())
                .build();
    }
    
    // Method to restore task from this version
    public AgentTask toAgentTask() {
        return AgentTask.builder()
                .id(this.taskId)
                .agentId(this.agentId)
                .name(this.name)
                .description(this.description)
                .taskType(this.taskType)
                .priority(this.priority)
                .enabled(this.enabled)
                .maxRetries(this.maxRetries)
                .timeoutMinutes(this.timeoutMinutes)
                .cronExpression(this.cronExpression)
                .cronDescription(this.cronDescription)
                .executionTrigger(this.executionTrigger)
                .linqConfig(this.linqConfig)
                .apiConfig(this.apiConfig)
                .scriptContent(this.scriptContent)
                .scriptLanguage(this.scriptLanguage)
                .version(this.version)
                .updatedAt(LocalDateTime.now())
                .updatedBy(this.createdBy)
                .build();
    }
} 