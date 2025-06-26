package org.lite.gateway.service;

import org.lite.gateway.dto.LinqResponse;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface QueuedWorkflowService {
    /**
     * Queue a workflow step for asynchronous execution
     * @param workflowId The ID of the workflow
     * @param stepNumber The step number to execute (1, 2, 3, etc.)
     * @param step The step data containing execution details
     * @return Mono<Void> indicating completion of the queue operation
     */
    Mono<Void> queueAsyncStep(String workflowId, int stepNumber, LinqResponse.WorkflowStep step);

    /**
     * Get the status of a workflow step
     * @param workflowId The ID of the workflow
     * @param stepNumber The step number (1, 2, 3, etc.)
     * @return Mono<LinqResponse.QueuedWorkflowStep> containing the current status of the step
     */
    Mono<LinqResponse.QueuedWorkflowStep> getAsyncStepStatus(String workflowId, int stepNumber);

    /**
     * Cancel an async step if it's not completed
     * @param workflowId The ID of the workflow
     * @param stepNumber The step number to cancel (1, 2, 3, etc.)
     * @return Mono<Void> indicating completion of the cancel operation
     */
    Mono<Void> cancelAsyncStep(String workflowId, int stepNumber);

    /**
     * Get all async steps for a workflow
     * @param workflowId The ID of the workflow
     * @return Mono containing a map of step numbers to their statuses
     */
    Mono<Map<String, LinqResponse.QueuedWorkflowStep>> getAllAsyncSteps(String workflowId);

    /**
     * Process the async step queue by polling for new tasks and executing them
     * This method is scheduled to run periodically to process queued tasks
     */
    void processQueue();
} 