package org.lite.gateway.service;

import org.lite.gateway.entity.ToolDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ToolRegistryService {
    Mono<ToolDefinition> getTool(String toolId);
    Mono<ToolDefinition> getTool(String toolId, boolean forceRefresh);
    Mono<ToolDefinition> registerTool(ToolDefinition tool);
    Mono<ToolDefinition> updateTool(String toolId, ToolDefinition tool);
    Flux<ToolDefinition> getAllTools();
    Flux<ToolDefinition> getAllPublicTools();
    Flux<ToolDefinition> getToolsByTeam(String teamId);
    Mono<Void> deleteTool(String toolId);
}
