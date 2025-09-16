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
