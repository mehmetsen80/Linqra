package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.dto.ValidationResult;
import org.lite.gateway.model.LinqWorkflowStats;
import org.lite.gateway.entity.LinqWorkflow;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.entity.LinqWorkflowVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqWorkflowService {
    Mono<LinqResponse> executeWorkflow(LinqRequest request);
    Mono<LinqWorkflow> createWorkflow(LinqWorkflow workflow);
    Mono<LinqWorkflow> updateWorkflow(String workflowId, LinqWorkflow updatedWorkflow);
    Mono<Void> deleteWorkflow(String workflowId);
    Flux<LinqWorkflow> getWorkflows();
    Mono<LinqWorkflow> getWorkflow(String workflowId);
    Mono<LinqWorkflowExecution> trackExecution(LinqRequest request, LinqResponse response);
    Flux<LinqWorkflowExecution> getWorkflowExecutions(String workflowId);
    Flux<LinqWorkflowExecution> getTeamExecutions();
    Mono<LinqWorkflowExecution> getExecution(String executionId);
    Flux<LinqWorkflow> searchWorkflows(String searchTerm);
    Mono<LinqWorkflowStats> getWorkflowStats(String workflowId);
    Mono<LinqWorkflow> createNewVersion(String workflowId, LinqWorkflow updatedWorkflow);
    Mono<LinqWorkflow> rollbackToVersion(String workflowId, String versionId);
    Flux<LinqWorkflowVersion> getVersionHistory(String workflowId);
    Mono<LinqWorkflowVersion> getVersion(String workflowId, String versionId);
}
