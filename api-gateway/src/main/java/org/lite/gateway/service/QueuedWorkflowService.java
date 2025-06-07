package org.lite.gateway.service;

import org.lite.gateway.dto.LinqResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface QueuedWorkflowService {
    /**
     * Queue a workflow step for asynchronous execution
     * @param workflowId The ID of the workflow
     * @param stepId The ID of the step to execute
     * @param step The step data containing execution details
     * @return Mono<Void> indicating completion of the queue operation
     */
    Mono<Void> queueAsyncStep(String workflowId, String stepId, LinqResponse.WorkflowStep step);

    /**
     * Get the status of a workflow step
     * @param workflowId The ID of the workflow
     * @param stepId The ID of the step
     * @return Mono<AsyncStepStatus> containing the current status of the step
     */
    Mono<LinqResponse.AsyncStepStatus> getStepStatus(String workflowId, String stepId);

    /**
     * Get all async steps for a workflow
     * @param workflowId The ID of the workflow
     * @return Mono containing a map of step IDs to their statuses
     */
    Mono<Map<String, LinqResponse.AsyncStepStatus>> getAllAsyncSteps(String workflowId);

    /**
     * Cancel an async step if it's not completed
     * @param workflowId The ID of the workflow
     * @param stepId The ID of the step to cancel
     * @return Mono<Void> indicating completion of the cancel operation
     */
    Mono<Void> cancelAsyncStep(String workflowId, String stepId);

    /**
     * Process the async step queue by polling for new tasks and executing them
     * This method is scheduled to run periodically to process queued tasks
     */
    void processQueue();
} 