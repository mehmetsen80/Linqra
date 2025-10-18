package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.LlmModel;
import org.lite.gateway.repository.LlmModelRepository;
import org.lite.gateway.service.LlmModelService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmModelServiceImpl implements LlmModelService {
    
    private final LlmModelRepository llmModelRepository;
    
    @Override
    public Flux<LlmModel> getAllModels() {
        return llmModelRepository.findAllSorted();
    }
    
    @Override
    public Flux<LlmModel> getActiveModels() {
        return llmModelRepository.findByActiveSorted(true);
    }
    
    @Override
    public Mono<LlmModel> getModelByName(String modelName) {
        return llmModelRepository.findByModelName(modelName);
    }
    
    @Override
    public Flux<LlmModel> getModelsByProvider(String provider) {
        return llmModelRepository.findByProvider(provider);
    }
    
    @Override
    public Mono<LlmModel> createModel(LlmModel model) {
        model.setCreatedAt(LocalDateTime.now());
        model.setUpdatedAt(LocalDateTime.now());
        return llmModelRepository.save(model);
    }
    
    @Override
    public Mono<LlmModel> updateModel(String id, LlmModel model) {
        return llmModelRepository.findById(id)
            .flatMap(existing -> {
                existing.setDisplayName(model.getDisplayName());
                existing.setProvider(model.getProvider());
                existing.setCategory(model.getCategory());
                existing.setInputPricePer1M(model.getInputPricePer1M());
                existing.setOutputPricePer1M(model.getOutputPricePer1M());
                existing.setActive(model.isActive());
                existing.setDescription(model.getDescription());
                existing.setUpdatedAt(LocalDateTime.now());
                existing.setUpdatedBy(model.getUpdatedBy());
                return llmModelRepository.save(existing);
            });
    }
    
    @Override
    public Mono<Void> deleteModel(String id) {
        return llmModelRepository.deleteById(id);
    }
    
    @Override
    @EventListener(ApplicationReadyEvent.class)
    public Mono<Void> initializeDefaultModels() {
        return llmModelRepository.count()
            .flatMap(count -> {
                if (count == 0) {
                    log.info("No LLM models found in database, initializing default models...");
                    return Flux.fromIterable(getDefaultModels())
                        .flatMap(llmModelRepository::save)
                        .then()
                        .doOnSuccess(v -> log.info("Default LLM models initialized successfully"))
                        .doOnError(error -> log.error("Error initializing default models: {}", error.getMessage()));
                }
                log.info("LLM models already exist in database (count: {})", count);
                return Mono.empty();
            });
    }
    
    private List<LlmModel> getDefaultModels() {
        List<LlmModel> models = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // OpenAI GPT-4 Models
        models.add(createModel("gpt-4o", "GPT-4 Optimized", "openai", "chat", 
            2.50, 10.00, true, "Latest GPT-4 optimized model with improved performance", now));
        models.add(createModel("gpt-4o-mini", "GPT-4 Optimized Mini", "openai", "chat", 
            0.150, 0.600, true, "Smaller, faster version of GPT-4 optimized", now));
        models.add(createModel("gpt-4-turbo", "GPT-4 Turbo", "openai", "chat", 
            10.00, 30.00, true, "Fast GPT-4 model with extended context", now));
        models.add(createModel("gpt-4", "GPT-4", "openai", "chat", 
            30.00, 60.00, true, "Original GPT-4 model", now));
        models.add(createModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "openai", "chat", 
            0.50, 1.50, true, "Fast and cost-effective model", now));
        
        // OpenAI Embeddings
        models.add(createModel("text-embedding-3-small", "Text Embedding 3 Small", "openai", "embedding", 
            0.020, 0.0, true, "Small embedding model for semantic search", now));
        models.add(createModel("text-embedding-3-large", "Text Embedding 3 Large", "openai", "embedding", 
            0.130, 0.0, true, "Large embedding model with higher accuracy", now));
        models.add(createModel("text-embedding-ada-002", "Text Embedding Ada", "openai", "embedding", 
            0.100, 0.0, true, "Ada embedding model for semantic search", now));
        
        // Google Gemini Models
        models.add(createModel("gemini-2.0-flash", "Gemini 2.0 Flash", "gemini", "chat", 
            0.075, 0.30, true, "Fast and efficient Gemini model", now));
        models.add(createModel("gemini-1.5-pro", "Gemini 1.5 Pro", "gemini", "chat", 
            1.25, 5.00, true, "Professional Gemini model with advanced capabilities", now));
        models.add(createModel("gemini-1.5-flash", "Gemini 1.5 Flash", "gemini", "chat", 
            0.075, 0.30, true, "Fast Gemini model for quick responses", now));
        
        // Anthropic Claude Models
        models.add(createModel("claude-3-opus", "Claude 3 Opus", "anthropic", "chat", 
            15.00, 75.00, true, "Most powerful Claude model", now));
        models.add(createModel("claude-3-sonnet", "Claude 3 Sonnet", "anthropic", "chat", 
            3.00, 15.00, true, "Balanced Claude model", now));
        models.add(createModel("claude-3-haiku", "Claude 3 Haiku", "anthropic", "chat", 
            0.25, 1.25, true, "Fast and efficient Claude model", now));
        
        return models;
    }
    
    private LlmModel createModel(String modelName, String displayName, String provider, String category,
                                   double inputPrice, double outputPrice, boolean active, 
                                   String description, LocalDateTime now) {
        LlmModel model = new LlmModel();
        model.setModelName(modelName);
        model.setDisplayName(displayName);
        model.setProvider(provider);
        model.setCategory(category);
        model.setInputPricePer1M(inputPrice);
        model.setOutputPricePer1M(outputPrice);
        model.setActive(active);
        model.setDescription(description);
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        model.setUpdatedBy("system");
        return model;
    }
}

