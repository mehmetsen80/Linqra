package org.lite.gateway.entity;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.model.ExecutionStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "linq_workflow_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LinqWorkflowExecution {
    @Id
    private String id;
    private String workflowId;              // Reference to LinqWorkflow
    private String team;                    // Team that executed this
    private LinqRequest request;            // The actual request that was executed
    private LinqResponse response;          // The complete response
    private LocalDateTime executedAt;
    private String executedBy;
    private ExecutionStatus status;         // SUCCESS, FAILED, etc.
    private Map<String, Object> variables;  // For storing any variables used in the execution
    private Long durationMs;  // Add this field to track execution duration
}
