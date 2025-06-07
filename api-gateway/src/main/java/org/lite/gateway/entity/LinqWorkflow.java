package org.lite.gateway.entity;

import org.lite.gateway.dto.LinqRequest;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Document(collection = "linq_workflows")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinqWorkflow {
    @Id
    private String id;
    private String name;                    // e.g., "Historical Saying Generator"
    private String description;             // e.g., "Generates inspirational sayings from historical figures"
    private String team;                    // Team ID that owns this workflow
    private LinqRequest request;            // The complete request template
    private boolean isPublic;               // Whether other teams can use this workflow
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer version = 1;            // Current version number
    private boolean hasAsyncSteps;          // Whether the workflow contains async steps
    private List<String> asyncStepIds;      // List of step IDs that should be executed asynchronously

    @Override
    public String toString() {
        return "LinqWorkflow{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", team='" + team + '\'' +
                ", request=" + request +
                ", isPublic=" + isPublic +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", createdBy='" + createdBy + '\'' +
                ", updatedBy='" + updatedBy + '\'' +
                ", version=" + version +
                ", hasAsyncSteps=" + hasAsyncSteps +
                ", asyncStepIds=" + asyncStepIds +
                '}';
    }
}
