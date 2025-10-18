package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.LlmModel;
import org.lite.gateway.service.LlmModelService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * REST Controller for managing LLM models and pricing
 */
@RestController
@RequestMapping("/api/llm-models")
@RequiredArgsConstructor
@Slf4j
public class LlmModelController {
    
    private final LlmModelService llmModelService;
    
    /**
     * Get all LLM models
     */
    @GetMapping
    public Flux<LlmModel> getAllModels(@RequestParam(required = false) Boolean active) {
        log.info("Fetching LLM models, active filter: {}", active);
        if (active != null && active) {
            return llmModelService.getActiveModels();
        }
        return llmModelService.getAllModels();
    }
    
    /**
     * Get model by ID
     */
    @GetMapping("/{id}")
    public Mono<LlmModel> getModelById(@PathVariable String id) {
        log.info("Fetching LLM model by ID: {}", id);
        return llmModelService.getModelByName(id)
            .switchIfEmpty(Mono.error(new RuntimeException("Model not found: " + id)));
    }
    
    /**
     * Get models by provider
     */
    @GetMapping("/provider/{provider}")
    public Flux<LlmModel> getModelsByProvider(@PathVariable String provider) {
        log.info("Fetching LLM models for provider: {}", provider);
        return llmModelService.getModelsByProvider(provider);
    }
    
    /**
     * Create a new LLM model
     */
    @PostMapping
    public Mono<LlmModel> createModel(@RequestBody LlmModel model) {
        log.info("Creating new LLM model: {}", model.getModelName());
        return llmModelService.createModel(model)
            .doOnSuccess(created -> log.info("Successfully created LLM model: {}", created.getModelName()))
            .doOnError(error -> log.error("Error creating LLM model: {}", error.getMessage()));
    }
    
    /**
     * Update an existing LLM model
     */
    @PutMapping("/{id}")
    public Mono<LlmModel> updateModel(@PathVariable String id, @RequestBody LlmModel model) {
        log.info("Updating LLM model: {}", id);
        return llmModelService.updateModel(id, model)
            .doOnSuccess(updated -> log.info("Successfully updated LLM model: {}", updated.getModelName()))
            .doOnError(error -> log.error("Error updating LLM model: {}", error.getMessage()));
    }
    
    /**
     * Delete an LLM model
     */
    @DeleteMapping("/{id}")
    public Mono<Map<String, String>> deleteModel(@PathVariable String id) {
        log.info("Deleting LLM model: {}", id);
        return llmModelService.deleteModel(id)
            .then(Mono.fromSupplier(() -> {
                Map<String, String> response = new java.util.HashMap<>();
                response.put("status", "success");
                response.put("message", "Model deleted successfully");
                response.put("id", id);
                return response;
            }))
            .doOnSuccess(result -> log.info("Successfully deleted LLM model: {}", id))
            .doOnError(error -> log.error("Error deleting LLM model: {}", error.getMessage()));
    }
    
    /**
     * Initialize default models (manual trigger)
     */
    @PostMapping("/initialize-defaults")
    public Mono<Map<String, String>> initializeDefaults() {
        log.info("Manually triggering default models initialization");
        return llmModelService.initializeDefaultModels()
            .then(Mono.fromSupplier(() -> {
                Map<String, String> response = new java.util.HashMap<>();
                response.put("status", "success");
                response.put("message", "Default models initialization triggered");
                return response;
            }));
    }
}

