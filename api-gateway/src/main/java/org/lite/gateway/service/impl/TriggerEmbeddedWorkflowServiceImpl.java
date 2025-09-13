package org.lite.gateway.service.impl;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.service.TriggerEmbeddedWorkflowService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service for triggering embedded workflows (workflows with steps defined directly in the task)
 * Used for WORKFLOW_EMBEDDED task types
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TriggerEmbeddedWorkflowServiceImpl implements TriggerEmbeddedWorkflowService {

    private final LinqWorkflowExecutionService workflowExecutionService;
    private final AgentRepository agentRepository;
    private final AgentTaskRepository agentTaskRepository;
    private final ObjectMapper objectMapper;

    /**
     * Trigger an embedded workflow using steps defined directly in the task
     */
    public Mono<String> triggerWorkflow(AgentTask task, Map<String, Object> parameters, String teamId,
                                               String agentId, String agentTaskId, String agentExecutionId, ServerWebExchange exchange) {
        log.info("Triggering embedded workflow for team {} with agent context: agent={}, task={}", teamId, agentId, agentTaskId);
        
        try {
            // The linq_config already contains a complete LinqRequest structure
            // We just need to convert it to a LinqRequest object and merge parameters
            Map<String, Object> linqConfig = task.getLinqConfig();
            if (linqConfig == null) {
                return Mono.error(new RuntimeException("Invalid embedded workflow: missing linq_config"));
            }
            
            // Convert linq_config Map to LinqRequest object (same as stored workflows)
            LinqRequest request = convertMapToLinqRequest(linqConfig);
            
            // Ensure it's a workflow request
            if (!request.getLink().getTarget().equals("workflow")) {
                return Mono.error(new RuntimeException("Invalid embedded workflow request: target must be 'workflow'"));
            }
            
            // Merge additional parameters if provided
            if (!parameters.isEmpty() && request.getQuery() != null) {
                Map<String, Object> existingParams = request.getQuery().getParams();
                if (existingParams == null) {
                    request.getQuery().setParams(new HashMap<>(parameters));
                } else {
                    Map<String, Object> mergedParams = new HashMap<>(existingParams);
                    mergedParams.putAll(parameters);
                    request.getQuery().setParams(mergedParams);
                }
            }
            
            // Fix the max.tokens issue in the workflow steps (same as controller)
            List<LinqRequest.Query.WorkflowStep> workflowSteps = request.getQuery().getWorkflow();
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
            
            // Set executedBy from agent context (no user context for agent executions)
            request.setExecutedBy("agent-" + agentId);
            
            // Execute the embedded workflow
            return workflowExecutionService.executeWorkflow(request)
                    .flatMap(response -> {
                        // Get agent and task names for context
                        Mono<String> agentNameMono = agentRepository.findById(agentId)
                                .map(agent -> agent.getName());
                        Mono<String> taskNameMono = agentTaskRepository.findById(agentTaskId)
                                .map(taskEntity -> taskEntity.getName());
                        
                        return Mono.zip(agentNameMono, taskNameMono)
                                .map(tuple -> {
                                    String agentName = tuple.getT1();
                                    String taskName = tuple.getT2();
                                    
                                    // Prepare agent context for tracking
                                    Map<String, Object> agentContext = Map.of(
                                        "agentId", agentId,
                                        "agentName", agentName,
                                        "agentTaskId", agentTaskId,
                                        "agentTaskName", taskName,
                                        "executionSource", "agent_embedded",
                                        "agentExecutionId", agentExecutionId
                                    );
                                    
                                    return agentContext;
                                })
                                .flatMap(agentContext -> 
                                    workflowExecutionService.trackExecutionWithAgentContext(request, response, agentContext)
                                            .map(workflowExecution -> workflowExecution.getId())
                                );
                    });
                    
        } catch (Exception e) {
            log.error("Error executing embedded workflow for task {}: {}", task.getName(), e.getMessage());
            return Mono.error(new RuntimeException("Failed to execute embedded workflow: " + e.getMessage()));
        }
    }

    /**
     * Convert a Map to LinqRequest object using ObjectMapper
     */
    private LinqRequest convertMapToLinqRequest(Map<String, Object> map) {
        try {
            return objectMapper.convertValue(map, LinqRequest.class);
        } catch (Exception e) {
            log.error("Failed to convert map to LinqRequest: {}", e.getMessage());
            throw new RuntimeException("Invalid LinqRequest structure in linq_config", e);
        }
    }
} 