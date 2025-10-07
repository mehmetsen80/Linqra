package org.lite.gateway.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.lite.gateway.dto.LinqRequest;

@Data
@Builder
@Document("linq_workflow_versions")
@CompoundIndexes({
    // Primary: version history (sorted DESC) - three-field compound for efficiency
    @CompoundIndex(name = "workflow_team_version_idx", def = "{'workflowId': 1, 'teamId': 1, 'version': -1}")
})
public class LinqWorkflowVersion {
    @Id
    private String id;
    private String workflowId;      // References the original workflow
    private String teamId;            // Team ID for access control
    private Integer version;        // Version number
    private LinqRequest request;    // The request at this version
    private Long createdAt;         // Creation timestamp
    private String createdBy;       // User who created this version
    private String changeDescription; // Description of changes in this version
} 