package org.lite.gateway.entity;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.model.ExecutionStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Document(collection = "linq_workflow_executions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    // Primary: workflow history (with sort for findByWorkflowId with Sort parameter)
    @CompoundIndex(name = "workflow_executed_idx", def = "{'workflowId': 1, 'executedAt': -1}"),
    
    // Team queries (with sort for findByTeamId with Sort parameter)
    @CompoundIndex(name = "team_executed_idx", def = "{'teamId': 1, 'executedAt': -1}"),
    
    // Combined: workflow + team (for findByWorkflowIdAndTeamId with Sort)
    @CompoundIndex(name = "workflow_team_executed_idx", def = "{'workflowId': 1, 'teamId': 1, 'executedAt': -1}"),
    
    // Lookup by ID + team (authorization check in findByIdAndTeamId)
    @CompoundIndex(name = "id_team_idx", def = "{'_id': 1, 'teamId': 1}")
})
public class LinqWorkflowExecution {
    @Id
    private String id;
    private String workflowId;              // Reference to LinqWorkflow
    private String teamId;                    // Team that executed this
    private LinqRequest request;            // The actual request that was executed
    private LinqResponse response;          // The complete response
    private LocalDateTime executedAt;
    private String executedBy;
    private ExecutionStatus status;         // SUCCESS, FAILED, etc.
    private Map<String, Object> variables;  // For storing any variables used in the execution
    private Long durationMs;  // Add this field to track execution duration
    
    // Agent execution tracking fields
    private String agentId;                 // ID of the agent that triggered this execution
    private String agentName;               // Name of the agent
    private String agentTaskId;             // ID of the specific agent task
    private String agentTaskName;           // Name of the specific agent task
    private String executionSource;         // "agent", "manual", "cron", "api", "scheduled"
    private String agentExecutionId;        // Reference to AgentExecution entity
}
