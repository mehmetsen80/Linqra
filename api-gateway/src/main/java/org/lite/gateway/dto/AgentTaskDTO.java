package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.AgentTaskType;
import org.lite.gateway.enums.ExecutionTrigger;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentTaskDTO {
    private String id;
    private String name;
    private String description;
    private AgentTaskType taskType;
    private String agentId;
    private int priority;
    private boolean enabled;
    private int maxRetries;
    private int timeoutMinutes;
    private String cronExpression;
    private String cronDescription;
    private ExecutionTrigger executionTrigger;
    private boolean scheduleOnStartup;
    private LocalDateTime nextRun;
    private LocalDateTime lastRun;
    private Map<String, Object> linqConfig;
    private Map<String, Object> apiConfig;
    private String scriptContent;
    private String scriptLanguage;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer version;

    public static AgentTaskDTO fromEntity(AgentTask task) {
        if (task == null) {
            return null;
        }
        return AgentTaskDTO.builder()
                .id(task.getId())
                .name(task.getName())
                .description(task.getDescription())
                .taskType(task.getTaskType())
                .agentId(task.getAgentId())
                .priority(task.getPriority())
                .enabled(task.isEnabled())
                .maxRetries(task.getMaxRetries())
                .timeoutMinutes(task.getTimeoutMinutes())
                .cronExpression(task.getCronExpression())
                .cronDescription(task.getCronDescription())
                .executionTrigger(task.getExecutionTrigger())
                .scheduleOnStartup(task.isScheduleOnStartup())
                .nextRun(task.getNextRun())
                .lastRun(task.getLastRun())
                .linqConfig(task.getLinqConfig())
                .apiConfig(task.getApiConfig())
                .scriptContent(task.getScriptContent())
                .scriptLanguage(task.getScriptLanguage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .createdBy(task.getCreatedBy())
                .updatedBy(task.getUpdatedBy())
                .version(task.getVersion())
                .build();
    }
}
