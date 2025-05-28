package org.lite.gateway.entity;

import lombok.Builder;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.lite.gateway.dto.LinqRequest;

@Data
@Builder
@Document("linq_workflow_versions")
public class LinqWorkflowVersion {
    @Id
    private String id;
    private String workflowId;      // References the original workflow
    private String team;            // Team ID for access control
    private Integer version;        // Version number
    private LinqRequest request;    // The request at this version
    private Long createdAt;         // Creation timestamp
    private String createdBy;       // User who created this version
    private String changeDescription; // Description of changes in this version
} 