package org.lite.gateway.service;

import org.lite.gateway.dto.TeamWorkflowStats;
import org.lite.gateway.model.LinqWorkflowStats;
import reactor.core.publisher.Mono;

public interface LinqWorkflowStatsService {
    /**
     * Get statistics for a workflow
     * @param workflowId The ID of the workflow
     * @return Mono containing workflow statistics
     */
    Mono<LinqWorkflowStats> getWorkflowStats(String workflowId);

    /**
     * Get statistics for an embedded workflow in an agent task
     * @param agentTaskId The ID of the agent task
     * @return Mono containing workflow statistics
     */
    Mono<LinqWorkflowStats> getAgentTaskWorkflowStats(String agentTaskId);

    /**
     * Get team-wide workflow statistics
     * @return Mono containing team statistics
     */
    Mono<TeamWorkflowStats> getTeamStats();
} 