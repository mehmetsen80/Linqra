package org.lite.gateway.service;

import java.util.List;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqLlmModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqLlmModelService {
    /**
     * Save or update a LinqLlmModel configuration
     * @param linqLlmModel The LinqLlmModel entity to save or update
     * @return Mono of the saved/updated LinqLlmModel
     */
    Mono<LinqLlmModel> saveLinqLlmModel(LinqLlmModel linqLlmModel);
    
    /**
     * Delete a LinqLlmModel configuration by ID
     * @param id The ID of the LinqLlmModel to delete
     * @return Mono of void indicating completion
     */
    Mono<Void> deleteLinqLlmModel(String id);
    
    /**
     * Execute an LLM request using the specified LinqLlmModel configuration
     * @param request The LinqRequest containing the query, payload, and LLM configuration
     * @param llmModel The LinqLlmModel configuration to use for the request
     * @return Mono of LinqResponse containing the LLM response
     */
    Mono<LinqResponse> executeLlmRequest(LinqRequest request, LinqLlmModel llmModel);
    
    /**
     * Find a LinqLlmModel configuration by ID
     * @param id The ID of the LinqLlmModel to find
     * @return Mono of the LinqLlmModel if found, or empty if not found
     */
    Mono<LinqLlmModel> findById(String id);
    
    /**
     * Find all LinqLlmModel configurations for a specific team
     * @param teamId The team ID to filter models
     * @return Flux of all LinqLlmModel configurations for the team
     */
    Flux<LinqLlmModel> findByTeamId(String teamId);
    
    /**
     * Find all LinqLlmModel configurations for a specific model category and team
     * @param modelCategory The model category to filter by (e.g., "openai-chat", "gemini-chat")
     * @param teamId The team ID to filter models
     * @return Flux of all LinqLlmModel configurations matching the category and team
     */
    Flux<LinqLlmModel> findByModelCategoryAndTeamId(String modelCategory, String teamId);
    
    /**
     * Find all LinqLlmModel configurations for multiple model categories and a team
     * @param targets List of model categories to search (e.g., ["openai-chat", "gemini-chat"])
     * @param teamId The team ID to filter models
     * @return Flux of all LinqLlmModel configurations matching any of the categories and team
     */
    Flux<LinqLlmModel> findByModelCategoriesAndTeamId(List<String> targets, String teamId);
    
    /**
     * Find a specific LinqLlmModel configuration by model category, model name, and team
     * @param target The model category (e.g., "openai-chat")
     * @param modelName The model name (e.g., "gpt-4o")
     * @param teamId The team ID to filter models
     * @return Mono of the LinqLlmModel if found, or empty if not found
     */
    Mono<LinqLlmModel> findByModelCategoryAndModelNameAndTeamId(String target, String modelName, String teamId);
    
    /**
     * Find the cheapest available model from the given model categories for a team
     * Prioritizes cheaper providers (Gemini, Cohere) first, then selects based on actual pricing
     * @param modelCategories List of model categories to search (e.g., ["gemini-chat", "cohere-chat", "openai-chat"])
     * @param teamId Team ID to filter models
     * @param estimatedPromptTokens Estimated prompt tokens for cost calculation (default: 1000)
     * @param estimatedCompletionTokens Estimated completion tokens for cost calculation (default: 500)
     * @return Mono of the cheapest available LinqLlmModel
     */
    Mono<LinqLlmModel> findCheapestAvailableModel(List<String> modelCategories, String teamId, long estimatedPromptTokens, long estimatedCompletionTokens);
}

