package org.lite.gateway.service;

import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface TriggerWorkflowByIdService {
    Mono<String> triggerWorkflow(String workflowId, Map<String, Object> parameters, String teamId,
                               String agentId, String agentTaskId, String agentExecutionId, ServerWebExchange exchange);
}
