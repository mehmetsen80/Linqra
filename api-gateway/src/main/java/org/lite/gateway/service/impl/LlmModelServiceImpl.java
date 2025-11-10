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
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        log.info("Application ready, checking LLM models initialization...");
        initializeDefaultModels()
            .doOnSuccess(v -> log.info("LLM models check completed"))
            .doOnError(error -> log.error("Error during LLM models initialization: {}", error.getMessage()))
            .subscribe();
    }
    
    @Override
    public Mono<Void> initializeDefaultModels() {
        log.info("Checking default models and cleaning up any duplicates...");
        
        // First, clean up any duplicates that might exist
        // Use count() to ensure all deletions complete before proceeding
        return cleanupAllDuplicates()
            .count()
            .flatMap(cleanedCount -> {
                if (cleanedCount > 0) {
                    log.info("✅ Cleaned up {} duplicate model entries", cleanedCount);
                }
                // Always recount after cleanup (with or without deletions)
                return llmModelRepository.count();
            })
            .flatMap(count -> {
                int expectedCount = getDefaultModels().size();
                log.info("Database has {} models, expecting {}", count, expectedCount);
                
                if (count == expectedCount) {
                    // We have exactly the right number of models, skip initialization
                    log.info("✅ All {} default models exist, skipping initialization", expectedCount);
                    return Mono.empty();
                }
                
                if (count > expectedCount) {
                    log.warn("⚠️ Database has MORE models than expected ({} > {}), skipping to avoid duplicates", 
                        count, expectedCount);
                    return Mono.empty();
                }
                
                // We have fewer models than expected, run initialization
                log.info("Missing {} models, running initialization...", expectedCount - count);
                return initializeDefaultModelsInternal();
            })
            .doOnSuccess(v -> log.info("✅ Default models check completed"))
            .doOnError(error -> log.error("❌ Error during models initialization: {}", error.getMessage()));
    }
    
    /**
     * Internal method to actually initialize default models
     */
    private Mono<Void> initializeDefaultModelsInternal() {
        // Upsert logic: only add models that don't exist
        // Use concatMap to prevent race conditions
        return Flux.fromIterable(getDefaultModels())
            .concatMap(defaultModel -> 
                llmModelRepository.findByModelName(defaultModel.getModelName())
                    .onErrorResume(org.springframework.dao.IncorrectResultSizeDataAccessException.class, error -> {
                        // Duplicates exist - skip this model, cleanup already ran
                        log.warn("Found duplicate models for {}, skipping (cleanup will handle this)", defaultModel.getModelName());
                        return Mono.empty();
                    })
                    .flatMap(existing -> {
                        log.debug("Model {} already exists, skipping", defaultModel.getModelName());
                        return Mono.empty();
                    })
                    .switchIfEmpty(
                        llmModelRepository.save(defaultModel)
                            .onErrorResume(org.springframework.dao.DuplicateKeyException.class, error -> {
                                // Race condition: another thread created it between check and save
                                log.debug("Model {} was created by another thread, skipping", defaultModel.getModelName());
                                return Mono.empty();
                            })
                            .doOnSuccess(saved -> log.info("✅ Added new model: {} ({})", 
                                defaultModel.getModelName(), defaultModel.getProvider()))
                            .then()
                    )
            )
            .then()
            .doOnSuccess(v -> log.info("✅ Default models initialization completed"));
    }
    
    private List<LlmModel> getDefaultModels() {
        List<LlmModel> models = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        
        // OpenAI GPT-4 Models
        models.add(createModel("gpt-4o", "GPT-4 Optimized", "openai", "chat",
            2.50, 10.00, true, "Latest GPT-4 optimized model with improved performance",
            "https://api.openai.com/v1/chat/completions", null, 128_000, now));
        models.add(createModel("gpt-4o-mini", "GPT-4 Optimized Mini", "openai", "chat",
            0.150, 0.600, true, "Smaller, faster version of GPT-4 optimized",
            "https://api.openai.com/v1/chat/completions", null, 128_000, now));
        models.add(createModel("gpt-4-turbo", "GPT-4 Turbo", "openai", "chat",
            10.00, 30.00, true, "Fast GPT-4 model with extended context",
            "https://api.openai.com/v1/chat/completions", null, 128_000, now));
        models.add(createModel("gpt-4", "GPT-4", "openai", "chat",
            30.00, 60.00, true, "Original GPT-4 model",
            "https://api.openai.com/v1/chat/completions", null, 8_192, now));
        models.add(createModel("gpt-3.5-turbo", "GPT-3.5 Turbo", "openai", "chat",
            0.50, 1.50, true, "Fast and cost-effective model",
            "https://api.openai.com/v1/chat/completions", null, 16_384, now));
        
        // OpenAI Embeddings
        models.add(createModel("text-embedding-3-small", "Text Embedding 3 Small", "openai", "embedding",
            0.020, 0.0, true, "Small embedding model for semantic search",
            "https://api.openai.com/v1/embeddings", 1536, 8_192, now));
        models.add(createModel("text-embedding-3-large", "Text Embedding 3 Large", "openai", "embedding",
            0.130, 0.0, true, "Large embedding model with higher accuracy",
            "https://api.openai.com/v1/embeddings", 3072, 8_192, now));
        models.add(createModel("text-embedding-ada-002", "Text Embedding Ada", "openai", "embedding",
            0.100, 0.0, true, "Ada embedding model for semantic search",
            "https://api.openai.com/v1/embeddings", 1536, 8_192, now));
        
        // Google Gemini Models
        models.add(createModel("gemini-2.0-flash", "Gemini 2.0 Flash", "gemini", "chat",
            0.075, 0.30, true, "Fast and efficient Gemini model",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent", null, 1_000_000, now));
        models.add(createModel("gemini-1.5-pro", "Gemini 1.5 Pro", "gemini", "chat",
            1.25, 5.00, true, "Professional Gemini model with advanced capabilities",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-pro:generateContent", null, 2_000_000, now));
        models.add(createModel("gemini-1.5-flash", "Gemini 1.5 Flash", "gemini", "chat",
            0.075, 0.30, true, "Fast Gemini model for quick responses",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent", null, 1_000_000, now));
        
        // Google Gemini Embeddings (text-embedding-004 is being deprecated Jan 2026)
        models.add(createModel("text-embedding-004", "Gemini Text Embedding 004 (Legacy)", "gemini", "embedding",
            0.00001, 0.0, false, "Legacy Gemini embedding model - deprecated Jan 14, 2026",
            "https://generativelanguage.googleapis.com/v1beta/models/text-embedding-004:embedContent", 768, 3_072, now));
        models.add(createModel("embedding-001", "Gemini Embedding 001", "gemini", "embedding",
            0.00001, 0.0, false, "Legacy Gemini embedding model - deprecated Jan 14, 2026",
            "https://generativelanguage.googleapis.com/v1beta/models/embedding-001:embedContent", 768, 3_072, now));
        
        // New Gemini Embedding Model (current)
        models.add(createModel("gemini-embedding-001", "Gemini Embedding 001 (Current)", "gemini", "embedding",
            0.15, 0.0, true, "Current Gemini embedding model with enhanced performance",
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-embedding-001:embedContent", 768, 3_072, now));
        
        // Anthropic Claude Models - Latest 2025
        models.add(createModel("claude-sonnet-4-5", "Claude Sonnet 4.5 (Latest)", "anthropic", "chat",
            3.00, 15.00, true, "Latest smartest model for complex agents and coding (claude-sonnet-4-5-20250929)",
            "https://api.anthropic.com/v1/messages", null, 200_000, now));
        models.add(createModel("claude-haiku-4-5", "Claude Haiku 4.5 (Latest)", "anthropic", "chat",
            0.80, 4.00, true, "Fastest model with near-frontier intelligence (claude-haiku-4-5-20251001)",
            "https://api.anthropic.com/v1/messages", null, 200_000, now));
        models.add(createModel("claude-opus-4-1", "Claude Opus 4.1 (Premium)", "anthropic", "chat",
            15.00, 75.00, true, "Premium model for specialized reasoning tasks. Cost: $15/$75 per 1M tokens (claude-opus-4-1-20250805)",
            "https://api.anthropic.com/v1/messages", null, 200_000, now));
        
        // Legacy Claude 3 models
        models.add(createModel("claude-3-opus", "Claude 3 Opus (Legacy)", "anthropic", "chat",
            15.00, 75.00, true, "Legacy most powerful Claude model",
            "https://api.anthropic.com/v1/messages", null, 200_000, now));
        models.add(createModel("claude-3-sonnet", "Claude 3 Sonnet (Legacy)", "anthropic", "chat",
            3.00, 15.00, true, "Legacy balanced Claude model",
            "https://api.anthropic.com/v1/messages", null, 200_000, now));
        models.add(createModel("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet (Legacy)", "anthropic", "chat",
            3.00, 15.00, true, "Legacy Claude 3.5 Sonnet - high performance",
            "https://api.anthropic.com/v1/messages", null, 200_000, now));
        models.add(createModel("claude-3-haiku", "Claude 3 Haiku (Legacy)", "anthropic", "chat",
            0.25, 1.25, true, "Legacy fast and efficient Claude model",
            "https://api.anthropic.com/v1/messages", null, 200_000, now));
        
        // Cohere Command Models (Chat/Completion)
        models.add(createModel("command-a-reasoning-08-2025", "Command A Reasoning", "cohere", "chat",
            0.0, 0.0, true, "First reasoning model from Cohere (111B params, 256K context, 32K max output) - Free until rate limits. Excels at agentic tasks, tool use, and multilingual reasoning",
            "https://api.cohere.ai/v2/chat", null, 256_000, now));
        models.add(createModel("command-a-03-2025", "Command A", "cohere", "chat",
            2.50, 10.00, true, "Most performant Cohere model (111B params, 256K context) for enterprise agents, RAG, and multilingual tasks",
            "https://api.cohere.ai/v2/chat", null, 256_000, now));
        models.add(createModel("command-r-plus-08-2024", "Command R+", "cohere", "chat",
            2.50, 10.00, true, "Optimized for conversational interaction and long-context tasks (128K context) with 50% higher throughput. Excellent for complex RAG and multi-step tool use",
            "https://api.cohere.ai/v2/chat", null, 128_000, now));
        models.add(createModel("command-r-08-2024", "Command R", "cohere", "chat",
            0.15, 0.60, true, "Scalable model balancing high performance with strong accuracy (128K context) - 50% higher throughput. Optimized for RAG, tool use, and multilingual tasks",
            "https://api.cohere.ai/v2/chat", null, 128_000, now));
        models.add(createModel("command-r7b-12-2024", "Command R7B", "cohere", "chat",
            0.0375, 0.15, true, "Smallest and fastest R family model (7B params, 128K context) - Ideal for high throughput, chatbots, and on-device inference",
            "https://api.cohere.ai/v2/chat", null, 128_000, now));
        
        // Cohere Embedding Models
        // Note: Embedding models only have input pricing (no output tokens)
        models.add(createModel("embed-v4.0", "Embed v4", "cohere", "embedding",
            0.120, 0.0, true, "Latest Cohere embedding model with configurable dimensions (256/512/1024/1536, default 1536) and 128k context. Supports text, images, and PDFs",
            "https://api.cohere.ai/v1/embed", 1536, 128_000, now));
        models.add(createModel("embed-english-v3.0", "Embed English v3", "cohere", "embedding",
            0.100, 0.0, true, "English-only embedding model. Supports text and images",
            "https://api.cohere.ai/v1/embed", 1024, 65_536, now));
        models.add(createModel("embed-multilingual-v3.0", "Embed Multilingual v3", "cohere", "embedding",
            0.100, 0.0, true, "Multilingual embedding model supporting multiple languages. Supports text and images",
            "https://api.cohere.ai/v1/embed", 1024, 65_536, now));
        models.add(createModel("embed-english-light-v3.0", "Embed English Light v3", "cohere", "embedding",
            0.100, 0.0, true, "Smaller, faster version of embed-english-v3.0. Almost as capable, but a lot faster. English only. Supports text and images",
            "https://api.cohere.ai/v1/embed", 384, 65_536, now));
        models.add(createModel("embed-multilingual-light-v3.0", "Embed Multilingual Light v3", "cohere", "embedding",
            0.100, 0.0, true, "Smaller, faster version of embed-multilingual-v3.0. Almost as capable, but a lot faster. Supports multiple languages and images",
            "https://api.cohere.ai/v1/embed", 384, 65_536, now));
        
        return models;
    }
    
    private LlmModel createModel(String modelName, String displayName, String provider, String category,
                                 double inputPrice, double outputPrice, boolean active,
                                 String description, String endpoint, Integer dimensions, Integer contextWindowTokens, LocalDateTime now) {
        LlmModel model = new LlmModel();
        model.setModelName(modelName);
        model.setDisplayName(displayName);
        model.setProvider(provider);
        model.setCategory(category);
        model.setEndpoint(endpoint);
        model.setDimensions(dimensions);
        model.setContextWindowTokens(contextWindowTokens);
        model.setInputPricePer1M(inputPrice);
        model.setOutputPricePer1M(outputPrice);
        model.setActive(active);
        model.setDescription(description);
        model.setCreatedAt(now);
        model.setUpdatedAt(now);
        model.setUpdatedBy("system");
        return model;
    }
    
    @Override
    public Flux<String> cleanupAllDuplicates() {
        log.info("Starting cleanup of all duplicate LLM models...");
        
        // Get all models grouped by modelName
        return llmModelRepository.findAll()
            .collectList()  // Collect all models first
            .flatMapMany(allModels -> {
                // Group by modelName in memory
                java.util.Map<String, java.util.List<LlmModel>> grouped = allModels.stream()
                    .collect(java.util.stream.Collectors.groupingBy(LlmModel::getModelName));
                
                // Process each group sequentially
                return Flux.fromIterable(grouped.entrySet())
                    .concatMap(entry -> {
                        String modelName = entry.getKey();
                        java.util.List<LlmModel> models = entry.getValue();
                        
                        if (models.size() <= 1) {
                            log.debug("Model {} has no duplicates, skipping", modelName);
                            return Flux.empty();
                        }
                        
                        log.info("Found {} duplicates for model {}, cleaning up", models.size(), modelName);
                        
                        // Keep first, delete rest sequentially
                        return Flux.fromIterable(models.subList(1, models.size()))
                            .concatMap(duplicate -> {
                                log.info("Deleting duplicate {} (ID: {})", modelName, duplicate.getId());
                                return llmModelRepository.deleteById(duplicate.getId())
                                    .thenReturn(modelName);
                            });
                    });
            })
            .doOnComplete(() -> log.info("✅ Duplicate cleanup completed for all models"));
    }
}

