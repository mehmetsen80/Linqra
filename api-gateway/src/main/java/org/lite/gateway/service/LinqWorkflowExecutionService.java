package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqWorkflowExecution;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import java.util.Map;

public interface LinqWorkflowExecutionService {
    /**
     * Execute a workflow with the given request
     * @param request The workflow execution request
     * @return The workflow execution response
     */
    Mono<LinqResponse> executeWorkflow(LinqRequest request);

    /**
     * Track a workflow execution
     * @param request The original request
     * @param response The execution response
     * @return The tracked execution
     */
    Mono<LinqWorkflowExecution> trackExecution(LinqRequest request, LinqResponse response);

    /**
     * Track a workflow execution with agent context
     * @param request The original request
     * @param response The execution response
     * @param agentContext Agent execution context information
     * @return The tracked execution
     */
    Mono<LinqWorkflowExecution> trackExecutionWithAgentContext(LinqRequest request, LinqResponse response, Map<String, Object> agentContext);

    /**
     * Get all executions for a specific workflow
     * @param workflowId The workflow ID
     * @return Flux of workflow executions
     */
    Flux<LinqWorkflowExecution> getWorkflowExecutions(String workflowId);

    /**
     * Get all executions for the current team
     * @return Flux of workflow executions
     */
    Flux<LinqWorkflowExecution> getTeamExecutions();

    /**
     * Get a specific execution by ID
     * @param executionId The execution ID
     * @return The workflow execution
     */
    Mono<LinqWorkflowExecution> getExecution(String executionId);

    /**
     * Delete a specific execution by ID
     * @param executionId The execution ID
     * @return Mono<Void> indicating the deletion
     */
    Mono<Void> deleteExecution(String executionId);
} 