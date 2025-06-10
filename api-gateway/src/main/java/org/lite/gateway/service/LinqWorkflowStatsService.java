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
     * Get team-wide workflow statistics
     * @return Mono containing team statistics
     */
    Mono<TeamWorkflowStats> getTeamStats();
} 