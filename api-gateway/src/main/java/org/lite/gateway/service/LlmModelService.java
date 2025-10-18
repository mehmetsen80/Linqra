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
}

