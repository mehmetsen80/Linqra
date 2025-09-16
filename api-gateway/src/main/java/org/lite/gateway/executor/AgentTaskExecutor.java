package org.lite.gateway.executor;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.service.LinqWorkflowService;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;
import lombok.RequiredArgsConstructor;

import java.time.Duration;
import java.util.Map;

/**
 * Abstract base class for task executors
 * Each task type should have its own executor implementation
 */
@RequiredArgsConstructor
@Slf4j
public abstract class AgentTaskExecutor {

    protected final LinqWorkflowService linqWorkflowService;
    protected final LinqWorkflowExecutionService workflowExecutionService;
    protected final AgentRepository agentRepository;
    protected final AgentTaskRepository agentTaskRepository;
    protected final AgentExecutionRepository agentExecutionRepository;
    protected final ObjectMapper objectMapper;

    /**
     * Execute a task
     * 
     * @param execution The agent execution context
     * @param task The task to execute
     * @param agent The agent executing the task
     * @param exchange The server web exchange for context
     * @return A Mono that completes when the task execution is finished
     */
    public abstract Mono<Void> executeTask(AgentExecution execution, AgentTask task, Agent agent, ServerWebExchange exchange);
    
    /**
     * Get timeout duration based on task type
     */
    protected Duration getTimeoutForTaskType(AgentTask task) {
        int baseTimeoutMinutes = task.getTimeoutMinutes();
        
        return switch (task.getTaskType()) {
            case WORKFLOW_EMBEDDED, WORKFLOW_TRIGGER -> 
                Duration.ofMinutes(baseTimeoutMinutes);
            /*
            // Future timeout adjustments for other task types (commented out for now)
            case LLM_ANALYSIS -> Duration.ofMinutes((long) (baseTimeoutMinutes * 1.5));
            case VECTOR_OPERATIONS -> Duration.ofMinutes((long) (baseTimeoutMinutes * 1.3));
            case DATA_PROCESSING -> Duration.ofMinutes((long) (baseTimeoutMinutes * 1.2));
            case CUSTOM_SCRIPT -> Duration.ofMinutes((long) baseTimeoutMinutes * 2);
            case API_CALL -> Duration.ofMinutes((long) (baseTimeoutMinutes * 0.8));
            case NOTIFICATION -> Duration.ofMinutes((long) (baseTimeoutMinutes * 0.5));
            case DATA_SYNC, MONITORING, REPORTING -> Duration.ofMinutes(baseTimeoutMinutes);
            */
        };
    }
    
    /**
     * Update execution status
     */
    protected Mono<Void> updateExecutionStatus(AgentExecution execution, ExecutionStatus status, String message, String workflowExecutionId) {
        execution.setStatus(status);
        execution.setErrorMessage(message);
        if (workflowExecutionId != null) {
            execution.setWorkflowExecutionId(workflowExecutionId);
        }
        
        return agentExecutionRepository.save(execution).then();
    }
    
    /**
     * Prepare workflow parameters from task and agent context
     */
    protected Map<String, Object> prepareWorkflowParameters(AgentExecution execution, AgentTask task, Agent agent) {
        Map<String, Object> linqConfig = task.getLinqConfig();
        if (linqConfig != null && linqConfig.containsKey("query")) {
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            if (query.containsKey("params")) {
                return (Map<String, Object>) query.get("params");
            }
        }
        return Map.of();
    }
    
    /**
     * Extract workflow ID from task configuration
     */
    protected String extractWorkflowId(AgentTask task) {
        Map<String, Object> linqConfig = task.getLinqConfig();
        if (linqConfig != null && linqConfig.containsKey("query")) {
            Map<String, Object> query = (Map<String, Object>) linqConfig.get("query");
            return (String) query.get("workflowId");
        }
        return null;
    }

    /**
     * Convert a Map to LinqRequest object using ObjectMapper
     */
    protected LinqRequest convertMapToLinqRequest(Map<String, Object> map) {
        try {
            return objectMapper.convertValue(map, LinqRequest.class);
        } catch (Exception e) {
            log.error("Failed to convert map to LinqRequest: {}", e.getMessage());
            throw new RuntimeException("Invalid LinqRequest structure in linq_config", e);
        }
    }
}
