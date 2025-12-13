package org.lite.gateway.service;

import org.lite.gateway.entity.LlmModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LlmModelService {
    /**
     * Get all LLM models
     */
    Flux<LlmModel> getAllModels();
    
    /**
     * Get active LLM models only
     */
    Flux<LlmModel> getActiveModels();
    
    /**
     * Get all chat models (category = 'chat')
     */
    Flux<LlmModel> getChatModels();
    
    /**
     * Get active chat models only (category = 'chat' and active = true)
     */
    Flux<LlmModel> getActiveChatModels();
    
    /**
     * Get model by name
     */
    Mono<LlmModel> getModelByName(String modelName);
    
    /**
     * Get models by provider
     */
    Flux<LlmModel> getModelsByProvider(String provider);
    
    /**
     * Create a new LLM model
     */
    Mono<LlmModel> createModel(LlmModel model);
    
    /**
     * Update an existing LLM model
     */
    Mono<LlmModel> updateModel(String id, LlmModel model);
    
    /**
     * Delete an LLM model
     */
    Mono<Void> deleteModel(String id);
    
    /**
     * Initialize default models if database is empty
     */
    Mono<Void> initializeDefaultModels();
    
    /**
     * Clean up all duplicate models (keep first, delete rest)
     * Returns a flux of model names that were cleaned up
     */
    Flux<String> cleanupAllDuplicates();
}

