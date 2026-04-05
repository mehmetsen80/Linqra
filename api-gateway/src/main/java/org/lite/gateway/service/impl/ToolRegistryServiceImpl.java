package org.lite.gateway.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ToolDefinition;
import org.lite.gateway.repository.ToolDefinitionRepository;
import org.lite.gateway.service.CacheService;
import org.lite.gateway.service.ToolRegistryService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
@RequiredArgsConstructor
public class ToolRegistryServiceImpl implements ToolRegistryService {

    private final ToolDefinitionRepository toolDefinitionRepository;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;

    private static final String CACHE_PREFIX = "tool:";
    private static final Duration CACHE_TTL = Duration.ofHours(1);

    @Override
    public Mono<ToolDefinition> getTool(String toolId) {
        return getTool(toolId, false);
    }

    public Mono<ToolDefinition> getTool(String toolId, boolean forceRefresh) {
        String cacheKey = CACHE_PREFIX + toolId;

        Mono<ToolDefinition> dbFetch = Mono.defer(() -> {
            log.info("GATEWAY DB FETCH: Loading tool '{}' from MongoDB...", toolId);
            return toolDefinitionRepository.findByToolId(toolId)
                    .flatMap(tool -> cacheTool(tool).thenReturn(tool));
        });

        if (forceRefresh) {
            return dbFetch;
        }

        return cacheService.get(cacheKey)
                .flatMap(json -> {
                    log.debug("GATEWAY CACHE HIT: Tool '{}'", toolId);
                    return deserialize(json);
                })
                .switchIfEmpty(dbFetch);
    }

    @Override
    public Mono<ToolDefinition> registerTool(ToolDefinition tool) {
        log.info("Registering tool: {}", tool.getToolId());
        return toolDefinitionRepository.save(tool)
                .flatMap(savedTool -> cacheTool(savedTool).thenReturn(savedTool));
    }

    @Override
    public Mono<ToolDefinition> updateTool(String toolId, ToolDefinition tool) {
        log.info("Updating tool: {}", toolId);
        return toolDefinitionRepository.findByToolId(toolId)
                .flatMap(existing -> {
                    String oldId = existing.getToolId();
                    String newId = tool.getToolId();

                    Mono<Void> cacheCleanup = Mono.empty();
                    if (newId != null && !newId.equals(oldId)) {
                        log.warn("Tool ID changing from {} to {}", oldId, newId);
                        existing.setToolId(newId);
                        cacheCleanup = cacheService.delete(CACHE_PREFIX + oldId).then();
                    }

                    existing.setName(tool.getName());
                    existing.setDescription(tool.getDescription());
                    existing.setCategory(tool.getCategory());
                    existing.setPricing(tool.getPricing());
                    existing.setType(tool.getType());
                    existing.setLinqConfig(tool.getLinqConfig());
                    existing.setInputSchema(tool.getInputSchema());
                    existing.setOutputSchema(tool.getOutputSchema());
                    existing.setExamples(tool.getExamples());
                    existing.setInstructions(tool.getInstructions());
                    existing.setVisibility(tool.getVisibility());

                    return cacheCleanup.then(toolDefinitionRepository.save(existing));
                })
                .flatMap(savedTool -> cacheTool(savedTool).thenReturn(savedTool));
    }

    @Override
    public Flux<ToolDefinition> getAllTools() {
        return toolDefinitionRepository.findAll();
    }

    @Override
    public Flux<ToolDefinition> getAllPublicTools() {
        return toolDefinitionRepository.findByVisibility("PUBLIC");
    }

    @Override
    public Flux<ToolDefinition> getToolsByTeam(String teamId) {
        log.info("Fetching tools for team: {}", teamId);
        return toolDefinitionRepository.findByTeamId(teamId);
    }

    @Override
    public Mono<Void> deleteTool(String toolId) {
        log.info("Deleting tool: {}", toolId);
        return toolDefinitionRepository.findByToolId(toolId)
                .flatMap(tool -> toolDefinitionRepository.delete(tool)
                        .then(cacheService.delete(CACHE_PREFIX + toolId)))
                .then();
    }

    private Mono<Void> cacheTool(ToolDefinition tool) {
        try {
            String json = objectMapper.writeValueAsString(tool);
            return cacheService.set(CACHE_PREFIX + tool.getToolId(), json, CACHE_TTL);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize tool for cache: {}", e.getMessage());
            return Mono.empty();
        }
    }

    private Mono<ToolDefinition> deserialize(String json) {
        try {
            return Mono.just(objectMapper.readValue(json, ToolDefinition.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize tool from cache: {}", e.getMessage());
            return Mono.empty();
        }
    }
}
