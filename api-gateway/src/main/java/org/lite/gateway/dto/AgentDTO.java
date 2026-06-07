package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.AgentCapability;
import org.lite.gateway.enums.AgentIntent;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentDTO {
    private String id;
    private String name;
    private String description;
    private String teamId;
    private Set<AgentIntent> supportedIntents;
    private Set<AgentCapability> capabilities;
    private Map<String, String> appEndpoints;
    private boolean enabled;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private List<AgentTaskDTO> tasks;

    public static AgentDTO fromEntity(Agent agent, List<AgentTask> tasks) {
        if (agent == null) {
            return null;
        }
        List<AgentTaskDTO> taskDTOs = tasks != null ? tasks.stream()
                .map(AgentTaskDTO::fromEntity)
                .collect(java.util.stream.Collectors.toList()) : null;
                
        return AgentDTO.builder()
                .id(agent.getId())
                .name(agent.getName())
                .description(agent.getDescription())
                .teamId(agent.getTeamId())
                .supportedIntents(agent.getSupportedIntents())
                .capabilities(agent.getCapabilities())
                .appEndpoints(agent.getAppEndpoints())
                .enabled(agent.isEnabled())
                .createdAt(agent.getCreatedAt())
                .updatedAt(agent.getUpdatedAt())
                .createdBy(agent.getCreatedBy())
                .updatedBy(agent.getUpdatedBy())
                .tasks(taskDTOs)
                .build();
    }
}
