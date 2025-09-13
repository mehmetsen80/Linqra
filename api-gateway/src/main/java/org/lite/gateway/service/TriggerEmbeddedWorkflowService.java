package org.lite.gateway.service;
import org.lite.gateway.entity.AgentTask;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;
public interface TriggerEmbeddedWorkflowService {
    Mono<String> triggerWorkflow(AgentTask agentTask, Map<String, Object> parameters, String teamId,
                               String agentId, String agentTaskId, String agentExecutionId, ServerWebExchange exchange);
}
