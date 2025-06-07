package org.lite.gateway.service;

import org.lite.gateway.entity.LinqWorkflow;
import org.lite.gateway.entity.LinqWorkflowVersion;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqWorkflowVersionService {
    /**
     * Create a new version of a workflow
     * @param workflowId The ID of the workflow to version
     * @param updatedWorkflow The updated workflow data
     * @return Mono containing the new workflow version
     */
    Mono<LinqWorkflow> createNewVersion(String workflowId, LinqWorkflow updatedWorkflow);

    /**
     * Rollback a workflow to a specific version
     * @param workflowId The ID of the workflow
     * @param versionId The ID of the version to rollback to
     * @return Mono containing the rolled back workflow
     */
    Mono<LinqWorkflow> rollbackToVersion(String workflowId, String versionId);

    /**
     * Get the version history of a workflow
     * @param workflowId The ID of the workflow
     * @return Flux containing all versions of the workflow
     */
    Flux<LinqWorkflowVersion> getVersionHistory(String workflowId);

    /**
     * Get a specific version of a workflow
     * @param workflowId The ID of the workflow
     * @param versionId The ID of the version to retrieve
     * @return Mono containing the requested workflow version
     */
    Mono<LinqWorkflowVersion> getVersion(String workflowId, String versionId);
} 