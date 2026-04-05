package org.lite.gateway.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Metadata about the caller/trigger of a request, 
 * used for auditing and tracking autonomous agent activity.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CallerParams {
    private String triggeredBy;      // Actual user who initiated the request
    private String agentId;          // ID of the agent if triggered by one
    private String agentName;        // Name of the agent
    private String agentTaskId;      // Task ID
    private String agentTaskName;    // Task Name
    private String agentExecutionId; // Execution ID of the agent task
    private String executionSource;  // e.g., "manual", "agent", "system"
}
