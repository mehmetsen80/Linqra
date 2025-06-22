package org.lite.gateway.entity;

import org.lite.gateway.dto.LinqRequest;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;
import com.fasterxml.jackson.annotation.JsonIgnore;

@Document(collection = "linq_workflows")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinqWorkflow {
    @Id
    private String id;
    private String name;                    // e.g., "Historical Saying Generator"
    private String description;             // e.g., "Generates inspirational sayings from historical figures"
    private String teamId;                  // Team ID that owns this workflow
    private LinqRequest request;            // The complete request template
    private boolean isPublic;               // Whether other teams can use this workflow
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;
    private Integer version = 1;            // Current version number

    /**
     * Checks if the workflow contains any async steps.
     * This is a computed property that is not serialized to JSON.
     * The async status is determined by the 'async' field in each step.
     * 
     * @return true if any step has async=true
     */
    public boolean hasAsyncSteps() {
        return request != null && 
               request.getQuery() != null && 
               request.getQuery().getWorkflow() != null &&
               request.getQuery().getWorkflow().stream()
                   .anyMatch(step -> Boolean.TRUE.equals(step.getAsync()));
    }

    /**
     * Gets the step numbers of all async steps
     * @return List of step numbers that are async
     */
    @JsonIgnore
    public List<Integer> getAsyncStepNumbers() {
        if (request == null || 
            request.getQuery() == null || 
            request.getQuery().getWorkflow() == null) {
            return Collections.emptyList();
        }
        
        return request.getQuery().getWorkflow().stream()
            .filter(step -> Boolean.TRUE.equals(step.getAsync()))
            .map(LinqRequest.Query.WorkflowStep::getStep)
            .collect(Collectors.toList());
    }

    @Override
    public String toString() {
        return "LinqWorkflow{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                ", description='" + description + '\'' +
                ", teamId='" + teamId + '\'' +
                ", request=" + request +
                ", isPublic=" + isPublic +
                ", createdAt=" + createdAt +
                ", updatedAt=" + updatedAt +
                ", createdBy='" + createdBy + '\'' +
                ", updatedBy='" + updatedBy + '\'' +
                ", version=" + version +
                '}';
    }
}
