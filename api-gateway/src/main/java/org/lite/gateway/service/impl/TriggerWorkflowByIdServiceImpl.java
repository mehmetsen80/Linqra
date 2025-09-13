package org.lite.gateway.service.impl;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.LinqWorkflowService;
import org.lite.gateway.service.TriggerWorkflowByIdService;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

/**
 * Service for triggering workflows by ID (reference-based workflows)
 * Used for WORKFLOW_TRIGGER task types
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TriggerWorkflowByIdServiceImpl implements TriggerWorkflowByIdService {

    private final LinqWorkflowService linqWorkflowService;
    private final LinqWorkflowExecutionService workflowExecutionService;
    private final AgentRepository agentRepository;
    private final AgentTaskRepository agentTaskRepository;

    /**
     * Trigger a workflow by its ID with agent context
     */
    public Mono<String> triggerWorkflow(String workflowId, Map<String, Object> parameters, String teamId,
                                      String agentId, String agentTaskId, String agentExecutionId, ServerWebExchange exchange) {
        log.info("Triggering workflow {} for team {} with agent context: agent={}, task={}", workflowId, teamId, agentId, agentTaskId);
        
        return linqWorkflowService.getWorkflow(workflowId)
                .flatMap(workflow -> {
                    // Use the workflow's request directly
                    LinqRequest request = workflow.getRequest();
                    
                    // Ensure it's a workflow request
                    if (!request.getLink().getTarget().equals("workflow")) {
                        return Mono.error(new RuntimeException("Invalid workflow request: target must be 'workflow'"));
                    }
                    
                    // Set the workflowId in the request
                    if (request.getQuery() == null) {
                        request.setQuery(new LinqRequest.Query());
                    }
                    request.getQuery().setWorkflowId(workflowId);
                    
                    // Store the workflow steps for each individual request
                    List<LinqRequest.Query.WorkflowStep> workflowSteps = request.getQuery().getWorkflow();
                    
                    // Fix the max.tokens issue in the workflow steps
                    if (workflowSteps != null) {
                        workflowSteps.forEach(step -> {
                            if (step.getToolConfig() != null && step.getToolConfig().getSettings() != null) {
                                Map<String, Object> settings = step.getToolConfig().getSettings();
                                if (settings.containsKey("max.tokens")) {
                                    Object value = settings.remove("max.tokens");
                                    settings.put("max_tokens", value);
                                }
                            }
                        });
                    }
                    
                    // Merge additional parameters if provided
                    if (!parameters.isEmpty()) {
                        Map<String, Object> existingParams = request.getQuery().getParams();
                        if (existingParams != null) {
                            existingParams.putAll(parameters);
                        } else {
                            request.getQuery().setParams(parameters);
                        }
                    }
                    
                    // Set executedBy from agent context
                    request.setExecutedBy("agent-" + agentId);
                    
                    // Execute the workflow
                    return workflowExecutionService.executeWorkflow(request)
                            .flatMap(response -> 
                                // Get agent and task names by looking up both
                                Mono.zip(
                                    agentRepository.findById(agentId).map(agent -> agent.getName()),
                                    agentTaskRepository.findById(agentTaskId).map(task -> task.getName())
                                )
                                .map(tuple -> {
                                    String agentName = tuple.getT1();
                                    String taskName = tuple.getT2();
                                    
                                    // Prepare agent context for tracking

                                    return Map.<String, Object>of(
                                        "agentId", agentId,
                                        "agentName", agentName,
                                        "agentTaskId", agentTaskId,
                                        "agentTaskName", taskName,
                                        "executionSource", "agent",
                                        "agentExecutionId", agentExecutionId
                                    );
                                })
                                .flatMap(agentContext -> 
                                    workflowExecutionService.trackExecutionWithAgentContext(request, response, agentContext)
                                            .map(LinqWorkflowExecution::getId)
                                )
                            );
                })
                .doOnSuccess(executionId -> log.info("Workflow {} triggered successfully with execution ID: {} for agent: {}", workflowId, executionId, agentId))
                .doOnError(error -> log.error("Failed to trigger workflow {} for agent {}: {}", workflowId, agentId, error.getMessage()));
    }
} 