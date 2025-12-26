package org.lite.gateway.service;

import org.lite.gateway.entity.AIAssistant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

public interface AIAssistantService {

    /**
     * Create a new AI Assistant
     */
    Mono<AIAssistant> createAssistant(AIAssistant assistant, String teamId, String createdBy);

    /**
     * Update an existing AI Assistant
     */
    Mono<AIAssistant> updateAssistant(String assistantId, AIAssistant assistantUpdates, String updatedBy);

    /**
     * Delete an AI Assistant
     */
    Mono<Boolean> deleteAssistant(String assistantId, String teamId);

    /**
     * Get AI Assistant by ID
     */
    Mono<AIAssistant> getAssistantById(String assistantId);

    /**
     * Get all AI Assistants for a team
     */
    Flux<AIAssistant> getAssistantsByTeam(String teamId);

    /**
     * Update access control for an AI Assistant
     */
    Mono<AIAssistant> updateAccessControl(String assistantId, AIAssistant.AccessControl accessControl,
            String updatedBy);

    /**
     * Generate public API key for an AI Assistant
     */
    Mono<Map<String, String>> generatePublicApiKey(String assistantId, String updatedBy);

    /**
     * Get widget script URL for an AI Assistant
     */
    Mono<String> getWidgetScriptUrl(String assistantId);

    /**
     * Update widget configuration for an AI Assistant
     */
    Mono<AIAssistant> updateWidgetConfig(String assistantId, AIAssistant.WidgetConfig widgetConfig, String updatedBy);

    /**
     * Get AI Assistant by public API key
     */
    Mono<AIAssistant> getAssistantByPublicApiKey(String publicApiKey);

    /**
     * Get AI Assistants by category for a team
     */
    Flux<AIAssistant> getAssistantsByCategory(String teamId, AIAssistant.Category category);
}
