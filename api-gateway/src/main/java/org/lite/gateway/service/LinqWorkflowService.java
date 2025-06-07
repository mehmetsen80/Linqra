package org.lite.gateway.service;

import org.lite.gateway.entity.LinqWorkflow;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqWorkflowService {
    /**
     * Create a new workflow
     * @param workflow The workflow to create
     * @return Mono containing the created workflow
     */
    Mono<LinqWorkflow> createWorkflow(LinqWorkflow workflow);

    /**
     * Get a workflow by ID
     * @param workflowId The ID of the workflow to retrieve
     * @return Mono containing the workflow if found
     */
    Mono<LinqWorkflow> getWorkflow(String workflowId);

    /**
     * Update an existing workflow
     * @param workflowId The ID of the workflow to update
     * @param updatedWorkflow The updated workflow data
     * @return Mono containing the updated workflow
     */
    Mono<LinqWorkflow> updateWorkflow(String workflowId, LinqWorkflow updatedWorkflow);

    /**
     * Delete a workflow
     * @param workflowId The ID of the workflow to delete
     * @return Mono<Void> indicating completion
     */
    Mono<Void> deleteWorkflow(String workflowId);

    /**
     * Get all workflows for the current team
     * @return Flux of workflows
     */
    Flux<LinqWorkflow> getAllWorkflows();

    /**
     * Search workflows by name or description
     * @param query The search query
     * @return Flux of matching workflows
     */
    Flux<LinqWorkflow> searchWorkflows(String query);
}
