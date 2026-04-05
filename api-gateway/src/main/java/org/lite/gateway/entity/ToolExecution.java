package org.lite.gateway.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.CallerParams;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.model.ExecutionStatus;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Document(collection = "tool_executions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndexes({
    // Primary: per-tool history sorted by time
    @CompoundIndex(name = "tool_executed_idx", def = "{'toolId': 1, 'executedAt': -1}"),

    // Team-level analytics
    @CompoundIndex(name = "team_executed_idx", def = "{'teamId': 1, 'executedAt': -1}"),

    // Per-tool + team (authorization scope)
    @CompoundIndex(name = "tool_team_executed_idx", def = "{'toolId': 1, 'teamId': 1, 'executedAt': -1}"),

    // Lookup by status for monitoring
    @CompoundIndex(name = "status_executed_idx", def = "{'status': 1, 'executedAt': -1}")
})
public class ToolExecution {

    @Id
    private String id;

    /** Unique execution identifier (UUID) — for direct lookup and audit correlation */
    @Indexed(unique = true)
    private String executionId;

    /** The tool that was executed */
    private String toolId;

    /** Denormalized tool name for easy querying without joins */
    private String toolName;

    /** Team that owns this execution */
    private String teamId;

    /** User or system principal that triggered execution */
    private String executedBy;

    /** Tool visibility at time of execution: PUBLIC or PRIVATE */
    private String visibility;

    /** Full Linq Protocol request sent to the tool (for full replay / debugging) */
    private LinqRequest request;

    /** Complete LinqResponse returned by the tool (for analytics) */
    private LinqResponse response;

    /** Execution outcome: IN_PROGRESS → SUCCESS | FAILED */
    @Builder.Default
    private ExecutionStatus status = ExecutionStatus.IN_PROGRESS;

    /** Error detail when status = FAILED */
    private String errorMessage;

    /** When execution started */
    private LocalDateTime executedAt;

    /** Total wall-clock duration of the tool call in milliseconds */
    private Long durationMs;

    /** Metadata about the trigger (agent, real user, source) */
    private CallerParams callerParams;
}
