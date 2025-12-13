package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.enums.IncidentSeverity;
import org.lite.gateway.enums.IncidentStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "security_incidents")
public class SecurityIncident {

    @Id
    private String id;

    private String referenceId; // Human readable ID e.g., INC-2024-001

    private IncidentSeverity severity;
    private IncidentStatus status;

    private String ruleId; // The ID of the detection rule that triggered this
    private String ruleName;
    private String description;

    private String affectedUserId;
    private String affectedUsername;
    private String affectedTeamId;

    private LocalDateTime detectedAt;
    private LocalDateTime updatedAt;
    private LocalDateTime closedAt;
    private String resolvedByUserId;
    private String resolutionNotes;

    private List<String> evidenceAuditLogIds; // IDs of audit logs that triggered the incident
    private Map<String, Object> context; // Snapshot of counters or relevant data

    @Builder.Default
    private boolean accountLocked = false;

    @Builder.Default
    private boolean notified = false;
}
