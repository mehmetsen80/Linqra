package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LlmUsageStats;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.ConversationMessage;
import org.lite.gateway.entity.KnowledgeHubDocumentMetaData;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.entity.LlmModel;
import org.lite.gateway.entity.LlmPricingSnapshot;
import org.lite.gateway.repository.ConversationMessageRepository;
import org.lite.gateway.repository.ConversationRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentMetaDataRepository;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.lite.gateway.repository.LlmModelRepository;
import org.lite.gateway.repository.LlmPricingSnapshotRepository;
import org.lite.gateway.service.LlmCostService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmCostServiceImpl implements LlmCostService {
    
    private static final String REDIS_PRICING_CACHE_KEY = "llm_model_pricing:cache";
    private static final Duration CACHE_EXPIRATION = Duration.ofHours(24); // Cache expires in 24 hours, refreshed on startup
    
    private final LinqWorkflowExecutionRepository executionRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository conversationMessageRepository;
    private final KnowledgeHubDocumentMetaDataRepository metadataRepository;
    private final LlmPricingSnapshotRepository pricingSnapshotRepository;
    private final LlmModelRepository llmModelRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    /**
     * Initialize pricing cache from database on application startup
     * This ensures we always use the latest model pricing from the database
     * Pricing is cached in Redis for fast access
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializePricingCache() {
        log.info("Initializing LLM pricing cache from database to Redis...");
        
        llmModelRepository.findAll()
            .collectList()
            .doOnNext(models -> {
                if (models == null || models.isEmpty()) {
                    log.error("❌ CRITICAL: No models found in database! LLM cost calculation will fail.");
                    log.error("   Please ensure LlmModelServiceImpl has initialized default models.");
                    return;
                }
                
                try {
                    Map<String, Map<String, Double>> cacheMap = new HashMap<>();
                    for (LlmModel model : models) {
                        Map<String, Double> pricing = new HashMap<>();
                        pricing.put("inputPricePer1M", model.getInputPricePer1M());
                        pricing.put("outputPricePer1M", model.getOutputPricePer1M());
                        cacheMap.put(model.getModelName(), pricing);
                    }
                    
                    // Store in Redis as JSON
                    String cacheJson = objectMapper.writeValueAsString(cacheMap);
                    redisTemplate.opsForValue().set(REDIS_PRICING_CACHE_KEY, cacheJson, CACHE_EXPIRATION);
                    
                    log.info("✅ Loaded {} models into Redis pricing cache", models.size());
                } catch (Exception e) {
                    log.error("❌ Failed to cache models in Redis: {}", e.getMessage(), e);
                }
            })
            .doOnError(error -> {
                log.error("❌ CRITICAL: Failed to load models from database: {}", error.getMessage(), error);
                log.error("   LLM cost calculation may fail. Please check database connectivity.");
            })
            .subscribe();
    }
    
    @Override
    public Mono<LlmUsageStats> getTeamLlmUsage(String teamId, String fromDate, String toDate) {
        log.info("Fetching LLM usage stats for team: {} from {} to {}", teamId, fromDate, toDate);
        
        // Parse dates in yyyy-MM-dd format and convert to LocalDateTime
        LocalDateTime from = fromDate != null ? 
            java.time.LocalDate.parse(fromDate, DateTimeFormatter.ISO_LOCAL_DATE).atStartOfDay() : 
            LocalDateTime.now().minusDays(30).withHour(0).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime to = toDate != null ? 
            java.time.LocalDate.parse(toDate, DateTimeFormatter.ISO_LOCAL_DATE).atTime(23, 59, 59, 999999999) : 
            LocalDateTime.now();
        
        // Query all three sources in parallel
        Mono<java.util.List<LinqWorkflowExecution>> executionsMono = executionRepository.findByTeamIdAndExecutedAtBetween(teamId, from, to)
            .collectList();
        
        // Get conversations for the team, filter by date range, then get all messages
        Mono<java.util.List<ConversationMessage>> chatMessagesMono = conversationRepository.findByTeamIdOrderByStartedAtDesc(teamId)
            .filter(conversation -> {
                LocalDateTime startedAt = conversation.getStartedAt() != null ? conversation.getStartedAt() : conversation.getCreatedAt();
                return startedAt != null && !startedAt.isBefore(from) && !startedAt.isAfter(to);
            })
            .flatMap(conversation -> conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversation.getId()))
            .filter(message -> {
                // Filter messages by timestamp/createdAt within date range
                LocalDateTime messageTime = message.getTimestamp() != null ? message.getTimestamp() : message.getCreatedAt();
                return messageTime != null && !messageTime.isBefore(from) && !messageTime.isAfter(to);
            })
            .collectList();
        
        // Get knowledge hub metadata for the team, filter by extractedAt date range
        Mono<java.util.List<KnowledgeHubDocumentMetaData>> metadataMono = metadataRepository.findAll()
            .filter(metadata -> teamId.equals(metadata.getTeamId()))
            .filter(metadata -> {
                LocalDateTime extractedAt = metadata.getExtractedAt() != null ? metadata.getExtractedAt() : metadata.getCreatedAt();
                return extractedAt != null && !extractedAt.isBefore(from) && !extractedAt.isAfter(to);
            })
            .collectList();
        
        // Combine all three sources and calculate aggregated stats
        return Mono.zip(executionsMono, chatMessagesMono, metadataMono)
            .map(tuple -> {
                java.util.List<LinqWorkflowExecution> executions = tuple.getT1();
                java.util.List<ConversationMessage> chatMessages = tuple.getT2();
                java.util.List<KnowledgeHubDocumentMetaData> metadataList = tuple.getT3();
                
                return calculateUsageStats(teamId, executions, chatMessages, metadataList, from, to);
            });
    }
    
    @Override
    public double calculateCost(String model, long promptTokens, long completionTokens) {
        // Normalize model name (e.g., "gpt-4o-2024-08-06" -> "gpt-4o")
        String normalizedModel = normalizeModelName(model);
        
        // Use cached pricing from database (loaded on startup)
        // Falls back to default if cache is not initialized yet
        return calculateCostFromCache(normalizedModel, promptTokens, completionTokens);
    }
    
    /**
     * Detect provider from model name
     * 
     * @param modelName The model name (normalized or raw)
     * @return Provider name (openai, gemini, anthropic, etc.)
     */
    private String detectProviderFromModelName(String modelName) {
        if (modelName == null || modelName.isEmpty()) {
            return "unknown";
        }
        
        String lowerModel = modelName.toLowerCase();
        
        // OpenAI models
        if (lowerModel.startsWith("gpt-") || 
            lowerModel.startsWith("text-embedding") || 
            lowerModel.startsWith("davinci") || 
            lowerModel.startsWith("curie") || 
            lowerModel.startsWith("babbage") || 
            lowerModel.startsWith("ada")) {
            return "openai";
        }
        
        // Google Gemini models
        if (lowerModel.startsWith("gemini")) {
            return "gemini";
        }
        
        // Anthropic Claude models
        if (lowerModel.startsWith("claude")) {
            return "anthropic";
        }
        
        // Cohere models
        if (lowerModel.startsWith("command") || lowerModel.startsWith("embed")) {
            return "cohere";
        }
        
        // Meta Llama models
        if (lowerModel.startsWith("llama")) {
            return "meta";
        }
        
        // Mistral models
        if (lowerModel.startsWith("mistral") || lowerModel.startsWith("mixtral")) {
            return "mistral";
        }
        
        return "unknown";
    }
    
    /**
     * Normalize model names by removing version suffixes
     * 
     * Examples:
     * OpenAI:
     * - "gpt-4o-2024-08-06" -> "gpt-4o"
     * - "gpt-4-turbo-2024-04-09" -> "gpt-4-turbo"
     * - "gpt-4-0613" -> "gpt-4"
     * - "gpt-3.5-turbo-0125" -> "gpt-3.5-turbo"
     * 
     * Gemini:
     * - "gemini-1.5-pro-001" -> "gemini-1.5-pro"
     * - "gemini-1.5-pro-latest" -> "gemini-1.5-pro"
     * - "gemini-2.0-flash-exp" -> "gemini-2.0-flash"
     * 
     * Anthropic:
     * - "claude-3-opus-20240229" -> "claude-3-opus"
     * - "claude-3-sonnet-20240229" -> "claude-3-sonnet"
     * 
     * Keeps base model names unchanged:
     * - "gpt-4o" -> "gpt-4o"
     * - "gemini-2.0-flash" -> "gemini-2.0-flash"
     * - "text-embedding-3-small" -> "text-embedding-3-small"
     */
    private String normalizeModelName(String model) {
        if (model == null || model.isEmpty()) return model;
        
        // Remove date-based version suffixes (YYYY-MM-DD or YYYYMMDD format)
        // Examples: gpt-4o-2024-08-06, claude-3-opus-20240229
        model = model.replaceAll("-\\d{4}-\\d{2}-\\d{2}$", "");  // YYYY-MM-DD
        model = model.replaceAll("-\\d{8}$", "");                 // YYYYMMDD
        
        // Remove short numeric version suffixes (4 digits or less)
        // Examples: gpt-4-0613, gpt-3.5-turbo-0125
        model = model.replaceAll("-\\d{1,4}$", "");
        
        // Remove text-based version suffixes
        // Examples: gemini-1.5-pro-latest, gemini-2.0-flash-exp
        model = model.replaceAll("-(latest|exp|preview|beta|alpha)$", "");
        
        return model;
    }
    
    private double calculateCostFromCache(String model, long promptTokens, long completionTokens) {
        try {
            // Get pricing from Redis cache (loaded from database on startup)
            String cacheJson = redisTemplate.opsForValue().get(REDIS_PRICING_CACHE_KEY);
            
            if (cacheJson == null || cacheJson.isEmpty()) {
                // Cache is empty - try to reload from database synchronously
                log.warn("Redis pricing cache is empty, attempting to reload from database for model: {}", model);
                reloadCacheFromDatabase();
                cacheJson = redisTemplate.opsForValue().get(REDIS_PRICING_CACHE_KEY);
                
                if (cacheJson == null || cacheJson.isEmpty()) {
                    throw new IllegalStateException(
                        "LLM model pricing cache is empty. Database may not have been initialized with models. " +
                        "Please ensure LlmModelServiceImpl has initialized default models."
                    );
                }
            }
            
            // Parse cache JSON
            Map<String, Map<String, Double>> cacheMap = objectMapper.readValue(
                cacheJson, 
                new TypeReference<Map<String, Map<String, Double>>>() {}
            );
            
            // Get pricing for this model
            Map<String, Double> pricing = cacheMap.get(model);
            if (pricing == null) {
                log.warn("Model {} not found in pricing cache, using $1.0/$2.0 per 1M tokens as fallback", model);
                // Last resort fallback for unknown models
                pricing = Map.of("inputPricePer1M", 1.0, "outputPricePer1M", 2.0);
            }
            
            double promptCost = (promptTokens / 1_000_000.0) * pricing.get("inputPricePer1M");
            double completionCost = (completionTokens / 1_000_000.0) * pricing.get("outputPricePer1M");
            return promptCost + completionCost;
            
        } catch (Exception e) {
            log.error("Error calculating cost for model {}: {}", model, e.getMessage());
            // Throw exception - we cannot calculate cost without database models
            throw new RuntimeException("Failed to calculate LLM cost: " + e.getMessage(), e);
        }
    }
    
    /**
     * Synchronously reload cache from database (used as fallback if cache is empty)
     */
    private void reloadCacheFromDatabase() {
        try {
            List<LlmModel> models = llmModelRepository.findAll().collectList().block();
            
            if (models == null || models.isEmpty()) {
                log.error("❌ Cannot reload cache: No models found in database");
                return;
            }
            
            Map<String, Map<String, Double>> cacheMap = new HashMap<>();
            for (LlmModel model : models) {
                Map<String, Double> pricing = new HashMap<>();
                pricing.put("inputPricePer1M", model.getInputPricePer1M());
                pricing.put("outputPricePer1M", model.getOutputPricePer1M());
                cacheMap.put(model.getModelName(), pricing);
            }
            
            String cacheJson = objectMapper.writeValueAsString(cacheMap);
            redisTemplate.opsForValue().set(REDIS_PRICING_CACHE_KEY, cacheJson, CACHE_EXPIRATION);
            log.info("✅ Reloaded {} models into Redis pricing cache", models.size());
            
        } catch (Exception e) {
            log.error("❌ Failed to reload cache from database: {}", e.getMessage(), e);
        }
    }
    
    private LlmUsageStats calculateUsageStats(String teamId, 
                                               java.util.List<LinqWorkflowExecution> executions,
                                               java.util.List<ConversationMessage> chatMessages,
                                               java.util.List<KnowledgeHubDocumentMetaData> metadataList,
                                               LocalDateTime from, LocalDateTime to) {
        LlmUsageStats stats = new LlmUsageStats();
        stats.setTeamId(teamId);
        
        LlmUsageStats.Period period = new LlmUsageStats.Period();
        period.setFrom(from.toString());
        period.setTo(to.toString());
        stats.setPeriod(period);
        
        LlmUsageStats.TotalUsage totalUsage = new LlmUsageStats.TotalUsage();
        Map<String, LlmUsageStats.ModelUsage> modelBreakdown = new HashMap<>();
        Map<String, LlmUsageStats.ProviderUsage> providerBreakdown = new HashMap<>();
        
        long totalRequests = 0;
        long totalPromptTokens = 0;
        long totalCompletionTokens = 0;
        double totalCost = 0.0;
        
        // 1. Process workflow executions (existing logic)
        for (LinqWorkflowExecution execution : executions) {
            if (execution.getResponse() == null || 
                execution.getResponse().getMetadata() == null || 
                execution.getResponse().getMetadata().getWorkflowMetadata() == null) {
                continue;
            }
            
            for (LinqResponse.WorkflowStepMetadata stepMetadata : execution.getResponse().getMetadata().getWorkflowMetadata()) {
                String modelCategory = stepMetadata.getTarget();
                
                // Only process LLM provider steps (OpenAI, Gemini, Cohere, Claude)
                if (!modelCategory.equals("openai-chat") && !modelCategory.equals("gemini-chat") && !modelCategory.equals("openai-embed") 
                    && !modelCategory.equals("cohere-chat") && !modelCategory.equals("cohere-embed") 
                    && !modelCategory.equals("gemini-embed") && !modelCategory.equals("claude-chat")) {
                    continue;
                }
                
                var tokenUsage = stepMetadata.getTokenUsage();
                if (tokenUsage == null) {
                    continue;
                }
                
                String rawModel = stepMetadata.getModel() != null ? stepMetadata.getModel() : "unknown";
                String model = normalizeModelName(rawModel); // Normalize for grouping and pricing lookup
                
                long promptTokens = tokenUsage.getPromptTokens();
                long completionTokens = tokenUsage.getCompletionTokens();
                long tokens = tokenUsage.getTotalTokens() > 0 ? tokenUsage.getTotalTokens() : (promptTokens + completionTokens);
                
                // Use stored cost if available (from execution time), otherwise calculate with current pricing
                double cost;
                if (tokenUsage.getCostUsd() != null && tokenUsage.getCostUsd() > 0) {
                    cost = tokenUsage.getCostUsd();
                    log.debug("Using stored cost for {} ({}): ${}", model, rawModel, String.format("%.6f", cost));
                } else {
                    cost = calculateCost(model, promptTokens, completionTokens);
                    log.debug("Calculated cost for {} ({}) with current pricing: ${}", model, rawModel, String.format("%.6f", cost));
                }
                
                // Update totals
                totalRequests++;
                totalPromptTokens += promptTokens;
                totalCompletionTokens += completionTokens;
                totalCost += cost;
                
                // Update model breakdown (group by normalized model name)
                LlmUsageStats.ModelUsage modelUsage = modelBreakdown.computeIfAbsent(model, k -> {
                    LlmUsageStats.ModelUsage mu = new LlmUsageStats.ModelUsage();
                    mu.setModelName(model);
                    mu.setModelCategory(modelCategory);
                    mu.setProvider(detectProviderFromModelName(model));
                    mu.setRequests(0);
                    mu.setPromptTokens(0);
                    mu.setCompletionTokens(0);
                    mu.setTotalTokens(0);
                    mu.setCostUsd(0.0);
                    mu.setAverageLatencyMs(0.0);
                    return mu;
                });
                
                // Track the raw version if different from normalized name
                if (!rawModel.equals(model)) {
                    modelUsage.getVersions().add(rawModel);
                }
                
                modelUsage.setRequests(modelUsage.getRequests() + 1);
                modelUsage.setPromptTokens(modelUsage.getPromptTokens() + promptTokens);
                modelUsage.setCompletionTokens(modelUsage.getCompletionTokens() + completionTokens);
                modelUsage.setTotalTokens(modelUsage.getTotalTokens() + tokens);
                modelUsage.setCostUsd(modelUsage.getCostUsd() + cost);
                
                // Update average latency
                double currentAvg = modelUsage.getAverageLatencyMs();
                double newAvg = (currentAvg * (modelUsage.getRequests() - 1) + stepMetadata.getDurationMs()) / modelUsage.getRequests();
                modelUsage.setAverageLatencyMs(newAvg);
                
                // Update provider breakdown (use the same provider as the model)
                String provider = modelUsage.getProvider();
                LlmUsageStats.ProviderUsage providerUsage = providerBreakdown.computeIfAbsent(provider, k -> {
                    LlmUsageStats.ProviderUsage pu = new LlmUsageStats.ProviderUsage();
                    pu.setProvider(provider);
                    pu.setRequests(0);
                    pu.setTotalTokens(0);
                    pu.setCostUsd(0.0);
                    return pu;
                });
                
                providerUsage.setRequests(providerUsage.getRequests() + 1);
                providerUsage.setTotalTokens(providerUsage.getTotalTokens() + tokens);
                providerUsage.setCostUsd(providerUsage.getCostUsd() + cost);
            }
        }
        
        // 2. Process chat conversation messages (ASSISTANT messages with tokenUsage)
        for (ConversationMessage message : chatMessages) {
            if (!"ASSISTANT".equals(message.getRole()) || message.getMetadata() == null || message.getMetadata().getTokenUsage() == null) {
                continue;
            }
            
            ConversationMessage.MessageMetadata.TokenUsage tokenUsage = message.getMetadata().getTokenUsage();
            if (tokenUsage.getCostUsd() == null || tokenUsage.getCostUsd() == 0.0) {
                continue; // Skip if no cost
            }
            
            String modelCategory = message.getMetadata().getModelCategory();
            String rawModel = message.getMetadata().getModelName();
            if (rawModel == null || rawModel.isEmpty() || modelCategory == null || modelCategory.isEmpty()) {
                continue;
            }
            
            String model = normalizeModelName(rawModel);
            long promptTokens = tokenUsage.getPromptTokens() != null ? tokenUsage.getPromptTokens() : 0;
            long completionTokens = tokenUsage.getCompletionTokens() != null ? tokenUsage.getCompletionTokens() : 0;
            long tokens = tokenUsage.getTotalTokens() != null ? tokenUsage.getTotalTokens() : (promptTokens + completionTokens);
            double cost = tokenUsage.getCostUsd();
            
            // Update totals
            totalRequests++;
            totalPromptTokens += promptTokens;
            totalCompletionTokens += completionTokens;
            totalCost += cost;
            
            // Update model breakdown
            LlmUsageStats.ModelUsage modelUsage = modelBreakdown.computeIfAbsent(model, k -> {
                LlmUsageStats.ModelUsage mu = new LlmUsageStats.ModelUsage();
                mu.setModelName(model);
                mu.setModelCategory(modelCategory);
                mu.setProvider(detectProviderFromModelName(model));
                mu.setRequests(0);
                mu.setPromptTokens(0);
                mu.setCompletionTokens(0);
                mu.setTotalTokens(0);
                mu.setCostUsd(0.0);
                mu.setAverageLatencyMs(0.0);
                return mu;
            });
            
            if (!rawModel.equals(model)) {
                modelUsage.getVersions().add(rawModel);
            }
            
            modelUsage.setRequests(modelUsage.getRequests() + 1);
            modelUsage.setPromptTokens(modelUsage.getPromptTokens() + promptTokens);
            modelUsage.setCompletionTokens(modelUsage.getCompletionTokens() + completionTokens);
            modelUsage.setTotalTokens(modelUsage.getTotalTokens() + tokens);
            modelUsage.setCostUsd(modelUsage.getCostUsd() + cost);
            
            // Update provider breakdown
            String provider = modelUsage.getProvider();
            LlmUsageStats.ProviderUsage providerUsage = providerBreakdown.computeIfAbsent(provider, k -> {
                LlmUsageStats.ProviderUsage pu = new LlmUsageStats.ProviderUsage();
                pu.setProvider(provider);
                pu.setRequests(0);
                pu.setTotalTokens(0);
                pu.setCostUsd(0.0);
                return pu;
            });
            
            providerUsage.setRequests(providerUsage.getRequests() + 1);
            providerUsage.setTotalTokens(providerUsage.getTotalTokens() + tokens);
            providerUsage.setCostUsd(providerUsage.getCostUsd() + cost);
        }
        
        // 3. Process knowledge hub graph extraction costs (entity and relationship extraction)
        for (KnowledgeHubDocumentMetaData metadata : metadataList) {
            if (metadata.getCustomMetadata() == null) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> customMetadata = (Map<String, Object>) metadata.getCustomMetadata();
            if (!customMetadata.containsKey("graphExtraction")) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> graphExtraction = (Map<String, Object>) customMetadata.get("graphExtraction");
            
            // Process entity extraction costs
            if (graphExtraction.containsKey("entityExtraction")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entityExtraction = (Map<String, Object>) graphExtraction.get("entityExtraction");
                if (entityExtraction.containsKey("costUsd") && entityExtraction.get("costUsd") instanceof Number) {
                    long promptTokens = entityExtraction.containsKey("promptTokens") ? 
                        ((Number) entityExtraction.get("promptTokens")).longValue() : 0;
                    long completionTokens = entityExtraction.containsKey("completionTokens") ? 
                        ((Number) entityExtraction.get("completionTokens")).longValue() : 0;
                    long tokens = entityExtraction.containsKey("totalTokens") ? 
                        ((Number) entityExtraction.get("totalTokens")).longValue() : (promptTokens + completionTokens);
                    double cost = ((Number) entityExtraction.get("costUsd")).doubleValue();
                    
                    if (cost > 0) {
                        // Use a default model name for graph extraction (could be improved to store actual model used)
                        String model = "gpt-4o"; // Default - graph extraction typically uses chat models
                        String modelCategory = "openai-chat";
                        
                        // Update totals
                        totalRequests++;
                        totalPromptTokens += promptTokens;
                        totalCompletionTokens += completionTokens;
                        totalCost += cost;
                        
                        // Update model breakdown
                        LlmUsageStats.ModelUsage modelUsage = modelBreakdown.computeIfAbsent(model, k -> {
                            LlmUsageStats.ModelUsage mu = new LlmUsageStats.ModelUsage();
                            mu.setModelName(model);
                            mu.setModelCategory(modelCategory);
                            mu.setProvider(detectProviderFromModelName(model));
                            mu.setRequests(0);
                            mu.setPromptTokens(0);
                            mu.setCompletionTokens(0);
                            mu.setTotalTokens(0);
                            mu.setCostUsd(0.0);
                            mu.setAverageLatencyMs(0.0);
                            return mu;
                        });
                        
                        modelUsage.setRequests(modelUsage.getRequests() + 1);
                        modelUsage.setPromptTokens(modelUsage.getPromptTokens() + promptTokens);
                        modelUsage.setCompletionTokens(modelUsage.getCompletionTokens() + completionTokens);
                        modelUsage.setTotalTokens(modelUsage.getTotalTokens() + tokens);
                        modelUsage.setCostUsd(modelUsage.getCostUsd() + cost);
                        
                        // Update provider breakdown
                        String provider = modelUsage.getProvider();
                        LlmUsageStats.ProviderUsage providerUsage = providerBreakdown.computeIfAbsent(provider, k -> {
                            LlmUsageStats.ProviderUsage pu = new LlmUsageStats.ProviderUsage();
                            pu.setProvider(provider);
                            pu.setRequests(0);
                            pu.setTotalTokens(0);
                            pu.setCostUsd(0.0);
                            return pu;
                        });
                        
                        providerUsage.setRequests(providerUsage.getRequests() + 1);
                        providerUsage.setTotalTokens(providerUsage.getTotalTokens() + tokens);
                        providerUsage.setCostUsd(providerUsage.getCostUsd() + cost);
                    }
                }
            }
            
            // Process relationship extraction costs
            if (graphExtraction.containsKey("relationshipExtraction")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> relationshipExtraction = (Map<String, Object>) graphExtraction.get("relationshipExtraction");
                if (relationshipExtraction.containsKey("costUsd") && relationshipExtraction.get("costUsd") instanceof Number) {
                    long promptTokens = relationshipExtraction.containsKey("promptTokens") ? 
                        ((Number) relationshipExtraction.get("promptTokens")).longValue() : 0;
                    long completionTokens = relationshipExtraction.containsKey("completionTokens") ? 
                        ((Number) relationshipExtraction.get("completionTokens")).longValue() : 0;
                    long tokens = relationshipExtraction.containsKey("totalTokens") ? 
                        ((Number) relationshipExtraction.get("totalTokens")).longValue() : (promptTokens + completionTokens);
                    double cost = ((Number) relationshipExtraction.get("costUsd")).doubleValue();
                    
                    if (cost > 0) {
                        // Use a default model name for graph extraction (could be improved to store actual model used)
                        String model = "gpt-4o"; // Default - graph extraction typically uses chat models
                        String modelCategory = "openai-chat";
                        
                        // Update totals
                        totalRequests++;
                        totalPromptTokens += promptTokens;
                        totalCompletionTokens += completionTokens;
                        totalCost += cost;
                        
                        // Update model breakdown
                        LlmUsageStats.ModelUsage modelUsage = modelBreakdown.computeIfAbsent(model, k -> {
                            LlmUsageStats.ModelUsage mu = new LlmUsageStats.ModelUsage();
                            mu.setModelName(model);
                            mu.setModelCategory(modelCategory);
                            mu.setProvider(detectProviderFromModelName(model));
                            mu.setRequests(0);
                            mu.setPromptTokens(0);
                            mu.setCompletionTokens(0);
                            mu.setTotalTokens(0);
                            mu.setCostUsd(0.0);
                            mu.setAverageLatencyMs(0.0);
                            return mu;
                        });
                        
                        modelUsage.setRequests(modelUsage.getRequests() + 1);
                        modelUsage.setPromptTokens(modelUsage.getPromptTokens() + promptTokens);
                        modelUsage.setCompletionTokens(modelUsage.getCompletionTokens() + completionTokens);
                        modelUsage.setTotalTokens(modelUsage.getTotalTokens() + tokens);
                        modelUsage.setCostUsd(modelUsage.getCostUsd() + cost);
                        
                        // Update provider breakdown
                        String provider = modelUsage.getProvider();
                        LlmUsageStats.ProviderUsage providerUsage = providerBreakdown.computeIfAbsent(provider, k -> {
                            LlmUsageStats.ProviderUsage pu = new LlmUsageStats.ProviderUsage();
                            pu.setProvider(provider);
                            pu.setRequests(0);
                            pu.setTotalTokens(0);
                            pu.setCostUsd(0.0);
                            return pu;
                        });
                        
                        providerUsage.setRequests(providerUsage.getRequests() + 1);
                        providerUsage.setTotalTokens(providerUsage.getTotalTokens() + tokens);
                        providerUsage.setCostUsd(providerUsage.getCostUsd() + cost);
                    }
                }
            }
        }
        
        totalUsage.setTotalRequests(totalRequests);
        totalUsage.setTotalPromptTokens(totalPromptTokens);
        totalUsage.setTotalCompletionTokens(totalCompletionTokens);
        totalUsage.setTotalTokens(totalPromptTokens + totalCompletionTokens);
        totalUsage.setTotalCostUsd(totalCost);
        
        stats.setTotalUsage(totalUsage);
        
        // Sort modelBreakdown by provider, then by model name
        Map<String, LlmUsageStats.ModelUsage> sortedModelBreakdown = new java.util.LinkedHashMap<>();
        modelBreakdown.entrySet().stream()
            .sorted((e1, e2) -> {
                LlmUsageStats.ModelUsage m1 = e1.getValue();
                LlmUsageStats.ModelUsage m2 = e2.getValue();
                // Sort by provider first
                int providerCompare = m1.getProvider().compareTo(m2.getProvider());
                if (providerCompare != 0) {
                    return providerCompare;
                }
                // Then by model name
                return m1.getModelName().compareTo(m2.getModelName());
            })
            .forEach(e -> sortedModelBreakdown.put(e.getKey(), e.getValue()));
        
        stats.setModelBreakdown(sortedModelBreakdown);
        stats.setProviderBreakdown(providerBreakdown);
        
        // Calculate daily breakdown (includes all three sources)
        Map<String, LlmUsageStats.DailyUsage> dailyMap = new HashMap<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        // Daily breakdown from workflow executions
        for (LinqWorkflowExecution execution : executions) {
            if (execution.getResponse() == null || 
                execution.getResponse().getMetadata() == null || 
                execution.getResponse().getMetadata().getWorkflowMetadata() == null) {
                continue;
            }
            
            for (LinqResponse.WorkflowStepMetadata stepMetadata : execution.getResponse().getMetadata().getWorkflowMetadata()) {
                String modelCategory = stepMetadata.getTarget();
                
                // Only process LLM provider steps
                if (!modelCategory.equals("openai-chat") && !modelCategory.equals("gemini-chat") && !modelCategory.equals("openai-embed") 
                    && !modelCategory.equals("cohere-chat") && !modelCategory.equals("claude-chat")  && !modelCategory.equals("cohere-embed") 
                    && !modelCategory.equals("gemini-embed")) {
                    continue;
                }
                
                var tokenUsage = stepMetadata.getTokenUsage();
                if (tokenUsage == null) {
                    continue;
                }
                
                // Get the execution date (use executedAt from step metadata if available, otherwise from execution)
                LocalDateTime executedAt = stepMetadata.getExecutedAt() != null 
                    ? stepMetadata.getExecutedAt() 
                    : execution.getExecutedAt();
                String dateKey = executedAt.format(dateFormatter);
                
                LlmUsageStats.DailyUsage dailyUsage = dailyMap.computeIfAbsent(dateKey, k -> {
                    LlmUsageStats.DailyUsage du = new LlmUsageStats.DailyUsage();
                    du.setDate(dateKey);
                    du.setRequests(0);
                    du.setTotalTokens(0);
                    du.setCostUsd(0.0);
                    return du;
                });
                
                long tokens = tokenUsage.getTotalTokens() > 0 ? tokenUsage.getTotalTokens() : 
                    (tokenUsage.getPromptTokens() + tokenUsage.getCompletionTokens());
                double cost = tokenUsage.getCostUsd() != null ? tokenUsage.getCostUsd() : 0.0;
                
                dailyUsage.setRequests(dailyUsage.getRequests() + 1);
                dailyUsage.setTotalTokens(dailyUsage.getTotalTokens() + tokens);
                dailyUsage.setCostUsd(dailyUsage.getCostUsd() + cost);
            }
        }
        
        // Daily breakdown from chat conversation messages
        for (ConversationMessage message : chatMessages) {
            if (!"ASSISTANT".equals(message.getRole()) || message.getMetadata() == null || message.getMetadata().getTokenUsage() == null) {
                continue;
            }
            
            ConversationMessage.MessageMetadata.TokenUsage tokenUsage = message.getMetadata().getTokenUsage();
            if (tokenUsage.getCostUsd() == null || tokenUsage.getCostUsd() == 0.0) {
                continue;
            }
            
            // Get the message date
            LocalDateTime messageTime = message.getTimestamp() != null ? message.getTimestamp() : message.getCreatedAt();
            if (messageTime == null) {
                continue;
            }
            String dateKey = messageTime.format(dateFormatter);
            
            LlmUsageStats.DailyUsage dailyUsage = dailyMap.computeIfAbsent(dateKey, k -> {
                LlmUsageStats.DailyUsage du = new LlmUsageStats.DailyUsage();
                du.setDate(dateKey);
                du.setRequests(0);
                du.setTotalTokens(0);
                du.setCostUsd(0.0);
                return du;
            });
            
            long tokens = tokenUsage.getTotalTokens() != null ? tokenUsage.getTotalTokens() : 
                ((tokenUsage.getPromptTokens() != null ? tokenUsage.getPromptTokens() : 0) + 
                 (tokenUsage.getCompletionTokens() != null ? tokenUsage.getCompletionTokens() : 0));
            double cost = tokenUsage.getCostUsd();
            
            dailyUsage.setRequests(dailyUsage.getRequests() + 1);
            dailyUsage.setTotalTokens(dailyUsage.getTotalTokens() + tokens);
            dailyUsage.setCostUsd(dailyUsage.getCostUsd() + cost);
        }
        
        // Daily breakdown from knowledge hub graph extraction
        for (KnowledgeHubDocumentMetaData metadata : metadataList) {
            if (metadata.getCustomMetadata() == null) {
                continue;
            }
            
            @SuppressWarnings("unchecked")
            Map<String, Object> customMetadata = (Map<String, Object>) metadata.getCustomMetadata();
            if (!customMetadata.containsKey("graphExtraction")) {
                continue;
            }
            
            // Get the extraction date
            LocalDateTime extractedAt = metadata.getExtractedAt() != null ? metadata.getExtractedAt() : metadata.getCreatedAt();
            if (extractedAt == null) {
                continue;
            }
            String dateKey = extractedAt.format(dateFormatter);
            
            @SuppressWarnings("unchecked")
            Map<String, Object> graphExtraction = (Map<String, Object>) customMetadata.get("graphExtraction");
            
            // Process entity extraction
            if (graphExtraction.containsKey("entityExtraction")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> entityExtraction = (Map<String, Object>) graphExtraction.get("entityExtraction");
                if (entityExtraction.containsKey("costUsd") && entityExtraction.get("costUsd") instanceof Number) {
                    long tokens = entityExtraction.containsKey("totalTokens") ? 
                        ((Number) entityExtraction.get("totalTokens")).longValue() : 
                        ((entityExtraction.containsKey("promptTokens") ? ((Number) entityExtraction.get("promptTokens")).longValue() : 0) +
                         (entityExtraction.containsKey("completionTokens") ? ((Number) entityExtraction.get("completionTokens")).longValue() : 0));
                    double cost = ((Number) entityExtraction.get("costUsd")).doubleValue();
                    
                    if (cost > 0) {
                        LlmUsageStats.DailyUsage dailyUsage = dailyMap.computeIfAbsent(dateKey, k -> {
                            LlmUsageStats.DailyUsage du = new LlmUsageStats.DailyUsage();
                            du.setDate(dateKey);
                            du.setRequests(0);
                            du.setTotalTokens(0);
                            du.setCostUsd(0.0);
                            return du;
                        });
                        
                        dailyUsage.setRequests(dailyUsage.getRequests() + 1);
                        dailyUsage.setTotalTokens(dailyUsage.getTotalTokens() + tokens);
                        dailyUsage.setCostUsd(dailyUsage.getCostUsd() + cost);
                    }
                }
            }
            
            // Process relationship extraction
            if (graphExtraction.containsKey("relationshipExtraction")) {
                @SuppressWarnings("unchecked")
                Map<String, Object> relationshipExtraction = (Map<String, Object>) graphExtraction.get("relationshipExtraction");
                if (relationshipExtraction.containsKey("costUsd") && relationshipExtraction.get("costUsd") instanceof Number) {
                    long tokens = relationshipExtraction.containsKey("totalTokens") ? 
                        ((Number) relationshipExtraction.get("totalTokens")).longValue() : 
                        ((relationshipExtraction.containsKey("promptTokens") ? ((Number) relationshipExtraction.get("promptTokens")).longValue() : 0) +
                         (relationshipExtraction.containsKey("completionTokens") ? ((Number) relationshipExtraction.get("completionTokens")).longValue() : 0));
                    double cost = ((Number) relationshipExtraction.get("costUsd")).doubleValue();
                    
                    if (cost > 0) {
                        LlmUsageStats.DailyUsage dailyUsage = dailyMap.computeIfAbsent(dateKey, k -> {
                            LlmUsageStats.DailyUsage du = new LlmUsageStats.DailyUsage();
                            du.setDate(dateKey);
                            du.setRequests(0);
                            du.setTotalTokens(0);
                            du.setCostUsd(0.0);
                            return du;
                        });
                        
                        dailyUsage.setRequests(dailyUsage.getRequests() + 1);
                        dailyUsage.setTotalTokens(dailyUsage.getTotalTokens() + tokens);
                        dailyUsage.setCostUsd(dailyUsage.getCostUsd() + cost);
                    }
                }
            }
        }
        
        // Convert to sorted list (by date)
        List<LlmUsageStats.DailyUsage> dailyBreakdown = new ArrayList<>(dailyMap.values());
        dailyBreakdown.sort(Comparator.comparing(LlmUsageStats.DailyUsage::getDate));
        stats.setDailyBreakdown(dailyBreakdown);
        
        log.info("Calculated LLM usage for team {}: {} requests, ${} total cost, {} days (workflows: {}, chat: {}, graph: {})", 
            teamId, totalRequests, String.format("%.4f", totalCost), dailyBreakdown.size(),
            executions.size(), chatMessages.size(), metadataList.size());
        
        return stats;
    }
    
    @Override
    public Mono<Double> calculateCostForMonth(String teamId, String model, long promptTokens, long completionTokens, YearMonth yearMonth) {
        return pricingSnapshotRepository.findByTeamIdAndYearMonthAndModel(teamId, yearMonth.toString(), model)
            .switchIfEmpty(pricingSnapshotRepository.findByYearMonthAndModel(yearMonth.toString(), model)) // Fallback to global pricing
            .onErrorResume(org.springframework.dao.IncorrectResultSizeDataAccessException.class, error -> {
                // If duplicates exist, log warning and use the first one we can find
                log.warn("Found duplicate pricing snapshots for {} in {}, using first match", model, yearMonth);
                return pricingSnapshotRepository.findByYearMonth(yearMonth.toString())
                    .filter(snapshot -> snapshot.getModel().equals(model))
                    .next(); // Get the first one
            })
            .map(snapshot -> {
                double promptCost = (promptTokens / 1_000_000.0) * snapshot.getInputPricePer1M();
                double completionCost = (completionTokens / 1_000_000.0) * snapshot.getOutputPricePer1M();
                return promptCost + completionCost;
            })
            .switchIfEmpty(Mono.fromSupplier(() -> {
                // Fallback to current pricing if no snapshot exists for that month
                log.warn("No pricing snapshot found for model {} in {}, using current pricing", model, yearMonth);
                return calculateCost(model, promptTokens, completionTokens);
            }));
    }
    
    @Override
    public Mono<LlmPricingSnapshot> savePricingSnapshot(LlmPricingSnapshot snapshot) {
        log.info("Saving pricing snapshot for model {} in {}", snapshot.getModel(), snapshot.getYearMonth());
        snapshot.setSnapshotDate(LocalDateTime.now());
        return pricingSnapshotRepository.save(snapshot);
    }
    
    @Override
    public Flux<LlmPricingSnapshot> getPricingSnapshotsForMonth(String teamId, YearMonth yearMonth) {
        return pricingSnapshotRepository.findByTeamIdAndYearMonth(teamId, yearMonth.toString())
            .switchIfEmpty(pricingSnapshotRepository.findByYearMonth(yearMonth.toString())); // Fallback to global pricing
    }
    
    @Override
    public Mono<Void> initializeCurrentMonthPricing(String teamId) {
        YearMonth currentMonth = YearMonth.now();
        return initializePricingForMonth(teamId, currentMonth);
    }
    
    @Override
    public Mono<Void> backfillHistoricalPricing(String teamId, YearMonth fromYearMonth, YearMonth toYearMonth) {
        log.info("Backfilling pricing snapshots for team {} from {} to {}", teamId, fromYearMonth, toYearMonth);
        
        // First clean up any duplicates
        return cleanupAllPricingDuplicates()
            .flatMap(deletedCount -> {
                if (deletedCount > 0) {
                    log.info("✅ Cleaned up {} duplicate pricing snapshots before backfill", deletedCount);
                }
                
                java.util.List<YearMonth> months = new java.util.ArrayList<>();
                YearMonth current = fromYearMonth;
                while (!current.isAfter(toYearMonth)) {
                    months.add(current);
                    current = current.plusMonths(1);
                }
                
                // Use concatMap to process months sequentially, preventing race conditions
                return Flux.fromIterable(months)
                    .concatMap(month -> initializePricingForMonth(teamId, month))
                    .then();
            })
            .doOnSuccess(v -> log.info("Historical pricing backfill complete for team {} from {} to {}", teamId, fromYearMonth, toYearMonth));
    }
    
    @Override
    public Mono<Integer> cleanupAllPricingDuplicates() {
        log.info("Starting cleanup of all duplicate pricing snapshots...");
        
        return pricingSnapshotRepository.findAll()
            .groupBy(snapshot -> snapshot.getYearMonth() + "-" + snapshot.getModel())
            .flatMap(group -> 
                group.collectList()
                    .flatMap(snapshots -> {
                        if (snapshots.size() <= 1) {
                            return Mono.just(0);
                        }
                        
                        log.info("Found {} duplicates for {}, keeping most recent", snapshots.size(), group.key());
                        
                        // Keep the most recent one, delete the rest
                        LlmPricingSnapshot mostRecent = snapshots.stream()
                            .max((s1, s2) -> s1.getSnapshotDate().compareTo(s2.getSnapshotDate()))
                            .orElse(snapshots.get(0));
                        
                        return Flux.fromIterable(snapshots)
                            .filter(s -> !s.getId().equals(mostRecent.getId()))
                            .flatMap(pricingSnapshotRepository::delete)
                            .count()
                            .map(Long::intValue);
                    })
            )
            .reduce(0, Integer::sum)
            .doOnSuccess(total -> {
                if (total > 0) {
                    log.info("✅ Pricing snapshot cleanup completed: {} duplicates removed", total);
                } else {
                    log.info("✅ No duplicate pricing snapshots found");
                }
            });
    }
    
    /**
     * Cleanup duplicate pricing snapshots by keeping first, deleting rest
     */
    private Mono<LlmPricingSnapshot> cleanupDuplicatesAndSave(String yearMonth, String modelName, LlmPricingSnapshot templateSnapshot) {
        return pricingSnapshotRepository.findByYearMonth(yearMonth)
            .filter(snapshot -> snapshot.getModel().equals(modelName))
            .collectList()
            .flatMap(duplicates -> {
                if (duplicates.isEmpty()) {
                    // No duplicates found, save new snapshot
                    return pricingSnapshotRepository.save(templateSnapshot);
                }
                
                // Keep the first one, delete the rest
                LlmPricingSnapshot toKeep = duplicates.get(0);
                
                if (duplicates.size() > 1) {
                    log.info("Found {} duplicate pricing snapshots for {} in {}, keeping first and deleting rest", 
                        duplicates.size(), modelName, yearMonth);
                    
                    return Flux.fromIterable(duplicates.subList(1, duplicates.size()))
                        .flatMap(duplicate -> pricingSnapshotRepository.delete(duplicate))
                        .then(Mono.just(toKeep))
                        .doOnSuccess(kept -> log.info("Cleaned up {} duplicates for {} in {}, kept one", 
                            duplicates.size() - 1, modelName, yearMonth));
                }
                
                // Only one entry exists, just return it
                log.debug("Single pricing snapshot found for {} in {}, using it", modelName, yearMonth);
                return Mono.just(toKeep);
            });
    }
    
    /**
     * Initialize pricing snapshots for a specific month
     * Uses database models if available, falls back to static pricing
     * Uses concatMap to ensure sequential processing and avoid race conditions
     */
    private Mono<Void> initializePricingForMonth(String teamId, YearMonth yearMonth) {
        log.info("Checking if pricing snapshots exist for team {} in {}", teamId, yearMonth);
        
        // First check if this team+month already has pricing snapshots
        return pricingSnapshotRepository.findByTeamIdAndYearMonth(teamId, yearMonth.toString())
            .hasElements()
            .flatMap(hasSnapshots -> {
                if (hasSnapshots) {
                    log.info("Pricing snapshots already exist for team {} in {}, skipping initialization", teamId, yearMonth);
                    return Mono.empty();
                }
                
                log.info("No pricing snapshots found for team {} in {}, initializing...", teamId, yearMonth);
                return initializePricingForMonthInternal(teamId, yearMonth);
            });
    }
    
    /**
     * Internal method to actually initialize pricing for a month
     */
    private Mono<Void> initializePricingForMonthInternal(String teamId, YearMonth yearMonth) {
        // Always use models from database - never fall back to defaults
        return llmModelRepository.findByActive(true)
            .switchIfEmpty(Mono.error(new IllegalStateException(
                "No active LLM models found in database. Please ensure LlmModelServiceImpl has initialized default models."
            )))
            .concatMap(model -> {
                LlmPricingSnapshot snapshot = new LlmPricingSnapshot();
                snapshot.setTeamId(teamId); // ← SET TEAM ID HERE
                snapshot.setYearMonth(yearMonth.toString());
                snapshot.setModel(model.getModelName());
                snapshot.setProvider(model.getProvider());
                snapshot.setInputPricePer1M(model.getInputPricePer1M());
                snapshot.setOutputPricePer1M(model.getOutputPricePer1M());
                snapshot.setSnapshotDate(LocalDateTime.now());
                snapshot.setSource("database");
                
                String modelName = model.getModelName();
                
                // Check if already exists for this team (team-specific check with fallback to global)
                return pricingSnapshotRepository.findByTeamIdAndYearMonthAndModel(teamId, yearMonth.toString(), modelName)
                    .flatMap(existing -> {
                        log.debug("Pricing snapshot already exists for team {} / {} in {}, skipping", teamId, modelName, yearMonth);
                        return Mono.empty();
                    })
                    .switchIfEmpty(
                        pricingSnapshotRepository.save(snapshot)
                            .doOnSuccess(saved -> log.info("Saved pricing snapshot for team {} / {} in {}: ${}/{}", 
                                teamId, modelName, yearMonth, model.getInputPricePer1M(), model.getOutputPricePer1M()))
                            .onErrorResume(error -> {
                                // If save fails due to duplicate key violation, just skip it
                                if (error.getMessage() != null && error.getMessage().contains("duplicate key")) {
                                    log.debug("Pricing snapshot already exists (duplicate key), skipping");
                                    return Mono.empty();
                                }
                                log.error("Error saving pricing snapshot: {}", error.getMessage());
                                return Mono.error(error);
                            })
                    );
            })
            .then()
            .doOnSuccess(v -> log.info("Pricing snapshot initialization complete for {}", yearMonth));
    }
    
    @Override
    public Mono<Integer> backfillExecutionCosts(String teamId, boolean dryRun) {
        log.info("🔄 Starting cost backfill for executions, chat conversations, and graph extraction (dryRun: {}, teamId: {})", dryRun, teamId);
        
        // 1. Backfill workflow executions
        Mono<Integer> executionsCount = backfillWorkflowExecutionCosts(teamId, dryRun);
        
        // 2. Backfill chat conversation costs
        Mono<Integer> chatCount = backfillChatConversationCosts(teamId, dryRun);
        
        // 3. Backfill graph extraction costs
        Mono<Integer> graphCount = backfillGraphExtractionCosts(teamId, dryRun);
        
        // Combine all counts
        return Mono.zip(executionsCount, chatCount, graphCount)
            .map(tuple -> {
                int execCount = tuple.getT1();
                int chatCountResult = tuple.getT2();
                int graphCountResult = tuple.getT3();
                int total = execCount + chatCountResult + graphCountResult;
                
                if (dryRun) {
                    log.info("✅ [DRY RUN] Would update {} executions, {} chat messages, {} graph extractions (total: {})", 
                        execCount, chatCountResult, graphCountResult, total);
                } else {
                    log.info("✅ Successfully backfilled costs: {} executions, {} chat messages, {} graph extractions (total: {})", 
                        execCount, chatCountResult, graphCountResult, total);
                }
                return total;
            })
            .doOnError(error -> log.error("❌ Error during cost backfill: {}", error.getMessage()));
    }
    
    /**
     * Backfill costs for workflow executions
     */
    private Mono<Integer> backfillWorkflowExecutionCosts(String teamId, boolean dryRun) {
        Flux<LinqWorkflowExecution> executionsFlux;
        
        // Get executions based on teamId filter
        if (teamId != null && !teamId.isEmpty()) {
            executionsFlux = executionRepository.findByTeamId(teamId, Sort.by(Sort.Direction.DESC, "executedAt"));
        } else {
            executionsFlux = executionRepository.findAll(Sort.by(Sort.Direction.DESC, "executedAt"));
        }
        
        return executionsFlux
            .filter(execution -> {
                // Only process executions that have response metadata
                if (execution.getResponse() == null || 
                    execution.getResponse().getMetadata() == null || 
                    execution.getResponse().getMetadata().getWorkflowMetadata() == null) {
                    return false;
                }
                
                // Check if any LLM step is missing cost calculation
                return execution.getResponse().getMetadata().getWorkflowMetadata().stream()
                    .anyMatch(stepMetadata -> {
                        String modelCategory = stepMetadata.getTarget();
                        boolean isLlmStep = modelCategory.equals("openai-chat") || modelCategory.equals("gemini-chat") 
                            || modelCategory.equals("cohere-chat") || modelCategory.equals("claude-chat") || modelCategory.equals("openai-embed") 
                            || modelCategory.equals("gemini-embed") || modelCategory.equals("cohere-embed");
                        
                        // Has token usage but missing cost
                        if (isLlmStep && stepMetadata.getTokenUsage() != null) {
                            return stepMetadata.getTokenUsage().getCostUsd() == null 
                                || stepMetadata.getTokenUsage().getCostUsd() == 0.0;
                        }
                        return false;
                    });
            })
            .flatMap(execution -> {
                boolean updated = false;
                
                // Process each workflow step
                for (LinqResponse.WorkflowStepMetadata stepMetadata : execution.getResponse().getMetadata().getWorkflowMetadata()) {
                    String modelCategory = stepMetadata.getTarget();
                    
                    // Check if it's an LLM step
                    if (!modelCategory.equals("openai-chat") && !modelCategory.equals("gemini-chat") && !modelCategory.equals("cohere-chat") 
                        && !modelCategory.equals("claude-chat") && !modelCategory.equals("openai-embed") && !modelCategory.equals("gemini-embed") && !modelCategory.equals("cohere-embed")) {
                        continue;
                    }
                    
                    // Check if token usage exists but cost is missing OR model is missing
                    var tokenUsage = stepMetadata.getTokenUsage();
                    if (tokenUsage == null) {
                        continue;
                    }
                    
                    boolean needsCostUpdate = (tokenUsage.getCostUsd() == null || tokenUsage.getCostUsd() == 0.0);
                    boolean needsModelUpdate = (stepMetadata.getModel() == null || stepMetadata.getModel().isEmpty());
                    
                    if (!needsCostUpdate && !needsModelUpdate) {
                        continue; // Skip if both cost and model are already set
                    }
                    
                    // Extract model from the request's workflow step
                    String model = null;
                    if (execution.getRequest() != null && 
                        execution.getRequest().getQuery() != null && 
                        execution.getRequest().getQuery().getWorkflow() != null) {
                        
                        model = execution.getRequest().getQuery().getWorkflow().stream()
                            .filter(ws -> ws.getStep() == stepMetadata.getStep())
                            .findFirst()
                            .map(ws -> ws.getLlmConfig() != null ? ws.getLlmConfig().getModel() : null)
                            .orElse(null);
                    }
                    
                    // Use model from step metadata if available, or try to extract from result
                    if (model == null) {
                        model = stepMetadata.getModel();
                    }
                    
                    // Set default model if still not found
                    if (model == null) {
                        if (modelCategory.equals("openai-chat") || modelCategory.equals("openai-embed")) {
                            model = "gpt-4o-mini"; // Default OpenAI model
                        } else if (modelCategory.equals("gemini-chat") || modelCategory.equals("gemini-embed")) {
                            model = "gemini-2.0-flash"; // Default Gemini model
                        } else if (modelCategory.equals("cohere-chat") || modelCategory.equals("cohere-embed")) {
                            model = "command-r-08-2024"; // Default Cohere model
                        } else if (modelCategory.equals("claude-chat")) {
                            model = "claude-sonnet-4-5"; // Default Claude model
                        }
                    }
                    
                    // Calculate cost
                    long promptTokens = tokenUsage.getPromptTokens();
                    long completionTokens = tokenUsage.getCompletionTokens();
                    double cost = needsCostUpdate ? calculateCost(model, promptTokens, completionTokens) : tokenUsage.getCostUsd();
                    
                    log.info("📊 {} execution {} - step {} ({}): model={}, tokens={}p/{}c, cost=${}, updates=[{}]", 
                        dryRun ? "[DRY RUN]" : "Updating",
                        execution.getId(), 
                        stepMetadata.getStep(), 
                        modelCategory, 
                        model, 
                        promptTokens, 
                        completionTokens, 
                        String.format("%.6f", cost),
                        (needsCostUpdate ? "cost" : "") + (needsCostUpdate && needsModelUpdate ? "," : "") + (needsModelUpdate ? "model" : ""));
                    
                    // Update the fields that need updating
                    if (!dryRun) {
                        if (needsCostUpdate) {
                            tokenUsage.setCostUsd(cost);
                        }
                        if (needsModelUpdate) {
                            stepMetadata.setModel(model);
                        }
                        updated = true;
                    }
                }
                
                // Save the updated execution if not in dry run mode
                if (!dryRun && updated) {
                    return executionRepository.save(execution).thenReturn(1);
                } else {
                    return Mono.just(dryRun ? 1 : 0);
                }
            })
            .reduce(0, Integer::sum);
    }
    
    /**
     * Backfill costs for chat conversation messages
     */
    private Mono<Integer> backfillChatConversationCosts(String teamId, boolean dryRun) {
        Flux<ConversationMessage> messagesFlux;
        
        // Get all ASSISTANT messages with token usage
        if (teamId != null && !teamId.isEmpty()) {
            // Get conversations for team, then get messages
            messagesFlux = conversationRepository.findByTeamIdOrderByStartedAtDesc(teamId)
                .flatMap(conversation -> conversationMessageRepository.findByConversationIdOrderBySequenceNumberAsc(conversation.getId()));
        } else {
            messagesFlux = conversationMessageRepository.findAll(Sort.by(Sort.Direction.DESC, "timestamp"));
        }
        
        return messagesFlux
            .filter(message -> {
                // Only process ASSISTANT messages with token usage
                if (!"ASSISTANT".equals(message.getRole())) {
                    return false;
                }
                
                if (message.getMetadata() == null || message.getMetadata().getTokenUsage() == null) {
                    return false;
                }
                
                ConversationMessage.MessageMetadata.TokenUsage tokenUsage = message.getMetadata().getTokenUsage();
                // Has token usage but missing cost
                return tokenUsage.getCostUsd() == null || tokenUsage.getCostUsd() == 0.0;
            })
            .flatMap(message -> {
                ConversationMessage.MessageMetadata.TokenUsage tokenUsage = message.getMetadata().getTokenUsage();
                String modelCategory = message.getMetadata().getModelCategory();
                String modelName = message.getMetadata().getModelName();
                
                // Use model from metadata, or default based on category
                String model = modelName != null && !modelName.isEmpty() ? modelName : getDefaultModelForCategory(modelCategory);
                
                long promptTokens = tokenUsage.getPromptTokens() != null ? tokenUsage.getPromptTokens() : 0;
                long completionTokens = tokenUsage.getCompletionTokens() != null ? tokenUsage.getCompletionTokens() : 0;
                double cost = calculateCost(model, promptTokens, completionTokens);
                
                log.info("💬 {} chat message {} (conversation {}): model={}, tokens={}p/{}c, cost=${}", 
                    dryRun ? "[DRY RUN]" : "Updating",
                    message.getId(), 
                    message.getConversationId(),
                    model, 
                    promptTokens, 
                    completionTokens, 
                    String.format("%.6f", cost));
                
                if (!dryRun) {
                    tokenUsage.setCostUsd(cost);
                    return conversationMessageRepository.save(message).thenReturn(1);
                } else {
                    return Mono.just(1);
                }
            })
            .reduce(0, Integer::sum);
    }
    
    /**
     * Backfill costs for knowledge graph extraction (entity and relationship extraction)
     */
    private Mono<Integer> backfillGraphExtractionCosts(String teamId, boolean dryRun) {
        Flux<KnowledgeHubDocumentMetaData> metadataFlux;
        
        if (teamId != null && !teamId.isEmpty()) {
            metadataFlux = metadataRepository.findAll()
                .filter(metadata -> teamId.equals(metadata.getTeamId()));
        } else {
            metadataFlux = metadataRepository.findAll();
        }
        
        return metadataFlux
            .filter(metadata -> {
                // Only process metadata with graphExtraction
                if (metadata.getCustomMetadata() == null || !metadata.getCustomMetadata().containsKey("graphExtraction")) {
                    return false;
                }
                
                @SuppressWarnings("unchecked")
                Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata().get("graphExtraction");
                
                // Check if entityExtraction or relationshipExtraction has token usage but missing cost
                boolean entityNeedsCost = false;
                boolean relationshipNeedsCost = false;
                
                if (graphExtraction.containsKey("entityExtraction")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entityExtraction = (Map<String, Object>) graphExtraction.get("entityExtraction");
                    if (entityExtraction.containsKey("promptTokens") || entityExtraction.containsKey("completionTokens")) {
                        Object costObj = entityExtraction.get("costUsd");
                        entityNeedsCost = costObj == null || (costObj instanceof Number && ((Number) costObj).doubleValue() == 0.0);
                    }
                }
                
                if (graphExtraction.containsKey("relationshipExtraction")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> relationshipExtraction = (Map<String, Object>) graphExtraction.get("relationshipExtraction");
                    if (relationshipExtraction.containsKey("promptTokens") || relationshipExtraction.containsKey("completionTokens")) {
                        Object costObj = relationshipExtraction.get("costUsd");
                        relationshipNeedsCost = costObj == null || (costObj instanceof Number && ((Number) costObj).doubleValue() == 0.0);
                    }
                }
                
                return entityNeedsCost || relationshipNeedsCost;
            })
            .flatMap(metadata -> {
                boolean updated = false;
                @SuppressWarnings("unchecked")
                Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata().get("graphExtraction");
                
                // Default model for graph extraction (typically uses chat models)
                String defaultModel = "gpt-4o";
                
                // Process entity extraction
                if (graphExtraction.containsKey("entityExtraction")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entityExtraction = (Map<String, Object>) graphExtraction.get("entityExtraction");
                    
                    Object costObj = entityExtraction.get("costUsd");
                    boolean needsCost = costObj == null || (costObj instanceof Number && ((Number) costObj).doubleValue() == 0.0);
                    
                    if (needsCost) {
                        long promptTokens = entityExtraction.containsKey("promptTokens") ? 
                            ((Number) entityExtraction.get("promptTokens")).longValue() : 0;
                        long completionTokens = entityExtraction.containsKey("completionTokens") ? 
                            ((Number) entityExtraction.get("completionTokens")).longValue() : 0;
                        
                        if (promptTokens > 0 || completionTokens > 0) {
                            double cost = calculateCost(defaultModel, promptTokens, completionTokens);
                            entityExtraction.put("costUsd", cost);
                            updated = true;
                            
                            log.info("🔗 {} graph entity extraction (document {}): model={}, tokens={}p/{}c, cost=${}", 
                                dryRun ? "[DRY RUN]" : "Updating",
                                metadata.getDocumentId(),
                                defaultModel, 
                                promptTokens, 
                                completionTokens, 
                                String.format("%.6f", cost));
                        }
                    }
                }
                
                // Process relationship extraction
                if (graphExtraction.containsKey("relationshipExtraction")) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> relationshipExtraction = (Map<String, Object>) graphExtraction.get("relationshipExtraction");
                    
                    Object costObj = relationshipExtraction.get("costUsd");
                    boolean needsCost = costObj == null || (costObj instanceof Number && ((Number) costObj).doubleValue() == 0.0);
                    
                    if (needsCost) {
                        long promptTokens = relationshipExtraction.containsKey("promptTokens") ? 
                            ((Number) relationshipExtraction.get("promptTokens")).longValue() : 0;
                        long completionTokens = relationshipExtraction.containsKey("completionTokens") ? 
                            ((Number) relationshipExtraction.get("completionTokens")).longValue() : 0;
                        
                        if (promptTokens > 0 || completionTokens > 0) {
                            double cost = calculateCost(defaultModel, promptTokens, completionTokens);
                            relationshipExtraction.put("costUsd", cost);
                            updated = true;
                            
                            log.info("🔗 {} graph relationship extraction (document {}): model={}, tokens={}p/{}c, cost=${}", 
                                dryRun ? "[DRY RUN]" : "Updating",
                                metadata.getDocumentId(),
                                defaultModel, 
                                promptTokens, 
                                completionTokens, 
                                String.format("%.6f", cost));
                        }
                    }
                }
                
                if (!dryRun && updated) {
                    return metadataRepository.save(metadata).thenReturn(1);
                } else {
                    return Mono.just(dryRun && updated ? 1 : 0);
                }
            })
            .reduce(0, Integer::sum);
    }
    
    /**
     * Get default model name based on model category
     */
    private String getDefaultModelForCategory(String modelCategory) {
        if (modelCategory == null || modelCategory.isEmpty()) {
            return "gpt-4o-mini"; // Default fallback
        }
        
        if (modelCategory.equals("openai-chat") || modelCategory.equals("openai-embed")) {
            return "gpt-4o-mini";
        } else if (modelCategory.equals("gemini-chat") || modelCategory.equals("gemini-embed")) {
            return "gemini-2.0-flash";
        } else if (modelCategory.equals("cohere-chat") || modelCategory.equals("cohere-embed")) {
            return "command-r-08-2024";
        } else if (modelCategory.equals("claude-chat")) {
            return "claude-sonnet-4-5";
        }
        
        return "gpt-4o-mini"; // Default fallback
    }
    
    @Override
    public Mono<Integer> backfillTokenUsageFromResponses(String teamId, boolean dryRun) {
        log.info("🔄 Starting ADVANCED token usage backfill from response data (dryRun: {}, teamId: {})", dryRun, teamId);
        
        Flux<LinqWorkflowExecution> executionsFlux;
        
        // Get executions based on teamId filter
        if (teamId != null && !teamId.isEmpty()) {
            executionsFlux = executionRepository.findByTeamId(teamId, Sort.by(Sort.Direction.DESC, "executedAt"));
        } else {
            executionsFlux = executionRepository.findAll(Sort.by(Sort.Direction.DESC, "executedAt"));
        }
        
        return executionsFlux
            .filter(execution -> {
                // Only process executions that have response with result
                return execution.getResponse() != null && 
                       execution.getResponse().getResult() instanceof LinqResponse.WorkflowResult &&
                       execution.getResponse().getMetadata() != null &&
                       execution.getResponse().getMetadata().getWorkflowMetadata() != null;
            })
            .flatMap(execution -> {
                boolean updated = false;
                LinqResponse.WorkflowResult workflowResult = (LinqResponse.WorkflowResult) execution.getResponse().getResult();
                
                if (workflowResult.getSteps() == null) {
                    return Mono.just(0);
                }
                
                // Process each workflow step
                for (LinqResponse.WorkflowStepMetadata stepMetadata : execution.getResponse().getMetadata().getWorkflowMetadata()) {
                    String modelCategory = stepMetadata.getTarget();
                    
                    // Check if it's an LLM step that needs token usage extraction
                    boolean isLlmStep = modelCategory.equals("openai-chat") || modelCategory.equals("gemini-chat") 
                        || modelCategory.equals("cohere-chat") || modelCategory.equals("claude-chat") 
                        || modelCategory.equals("openai-embed") || modelCategory.equals("gemini-embed") 
                        || modelCategory.equals("cohere-embed");
                    
                    if (!isLlmStep) {
                        continue;
                    }
                    
                    // Skip if already has token usage
                    if (stepMetadata.getTokenUsage() != null) {
                        continue;
                    }
                    
                    // Find the corresponding step in the result
                    LinqResponse.WorkflowStep resultStep = workflowResult.getSteps().stream()
                        .filter(s -> s.getStep() == stepMetadata.getStep())
                        .findFirst()
                        .orElse(null);
                    
                    if (resultStep == null || !(resultStep.getResult() instanceof Map)) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> resultMap = (Map<String, Object>) resultStep.getResult();
                    
                    long promptTokens = 0;
                    long completionTokens = 0;
                    long totalTokens = 0;
                    
                    // Extract token usage based on provider
                    if (modelCategory.equals("cohere-chat") || modelCategory.equals("cohere-embed")) {
                        // Cohere token usage from meta.billed_units
                        if (resultMap.containsKey("meta")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> meta = (Map<String, Object>) resultMap.get("meta");
                            if (meta.containsKey("billed_units")) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> billedUnits = (Map<String, Object>) meta.get("billed_units");
                                promptTokens = billedUnits.containsKey("input_tokens") 
                                    ? ((Number) billedUnits.get("input_tokens")).longValue() : 0;
                                completionTokens = billedUnits.containsKey("output_tokens") 
                                    ? ((Number) billedUnits.get("output_tokens")).longValue() : 0;
                                totalTokens = promptTokens + completionTokens;
                            }
                        }
                    } else if (modelCategory.equals("openai-chat") || modelCategory.equals("openai-embed")) {
                        // OpenAI token usage from usage object
                        if (resultMap.containsKey("usage")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> usage = (Map<String, Object>) resultMap.get("usage");
                            promptTokens = usage.containsKey("prompt_tokens") 
                                ? ((Number) usage.get("prompt_tokens")).longValue() : 0;
                            completionTokens = usage.containsKey("completion_tokens") 
                                ? ((Number) usage.get("completion_tokens")).longValue() : 0;
                            totalTokens = usage.containsKey("total_tokens") 
                                ? ((Number) usage.get("total_tokens")).longValue() : 0;
                        }
                    } else if (modelCategory.equals("gemini-chat")) {
                        // Gemini chat token usage from usageMetadata
                        if (resultMap.containsKey("usageMetadata")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> usageMetadata = (Map<String, Object>) resultMap.get("usageMetadata");
                            promptTokens = usageMetadata.containsKey("promptTokenCount") 
                                ? ((Number) usageMetadata.get("promptTokenCount")).longValue() : 0;
                            completionTokens = usageMetadata.containsKey("candidatesTokenCount") 
                                ? ((Number) usageMetadata.get("candidatesTokenCount")).longValue() : 0;
                            totalTokens = usageMetadata.containsKey("totalTokenCount") 
                                ? ((Number) usageMetadata.get("totalTokenCount")).longValue() : 0;
                        }
                    } else if (modelCategory.equals("claude-chat")) {
                        // Claude token usage from usage object
                        if (resultMap.containsKey("usage")) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> usage = (Map<String, Object>) resultMap.get("usage");
                            promptTokens = usage.containsKey("input_tokens") 
                                ? ((Number) usage.get("input_tokens")).longValue() : 0;
                            completionTokens = usage.containsKey("output_tokens") 
                                ? ((Number) usage.get("output_tokens")).longValue() : 0;
                            totalTokens = promptTokens + completionTokens;
                        }
                    }
                    
                    // Skip if no tokens found
                    if (totalTokens == 0) {
                        continue;
                    }
                    
                    // Extract model from request workflow
                    String model = null;
                    if (execution.getRequest() != null && 
                        execution.getRequest().getQuery() != null && 
                        execution.getRequest().getQuery().getWorkflow() != null) {
                        
                        model = execution.getRequest().getQuery().getWorkflow().stream()
                            .filter(ws -> ws.getStep() == stepMetadata.getStep())
                            .findFirst()
                            .map(ws -> ws.getLlmConfig() != null ? ws.getLlmConfig().getModel() : null)
                            .orElse(null);
                    }
                    
                    // Use default model if not found
                    if (model == null) {
                        if (modelCategory.equals("openai-chat") || modelCategory.equals("openai-embed")) {
                            model = "gpt-4o-mini"; // Default OpenAI model
                        } else if (modelCategory.equals("gemini-chat") || modelCategory.equals("gemini-embed")) {
                            model = "gemini-2.0-flash"; // Default Gemini model
                        } else if (modelCategory.equals("cohere-chat") || modelCategory.equals("cohere-embed")) {
                            model = "command-r-08-2024"; // Default Cohere model
                        } else if (modelCategory.equals("claude-chat")) {
                            model = "claude-sonnet-4-5"; // Default Claude model
                        }
                    }
                    
                    // Calculate cost
                    double cost = calculateCost(model, promptTokens, completionTokens);
                    
                    log.info("🔍 {} execution {} - step {} ({}): EXTRACTING tokenUsage from response → model={}, tokens={}p/{}c, cost=${}", 
                        dryRun ? "[DRY RUN]" : "Updating",
                        execution.getId(), 
                        stepMetadata.getStep(), 
                        modelCategory, 
                        model, 
                        promptTokens, 
                        completionTokens, 
                        String.format("%.6f", cost));
                    
                    // Mark as updated (in dry run mode, this just counts what would be updated)
                    updated = true;
                    
                    // Create and set token usage only if not in dry run mode
                    if (!dryRun) {
                        LinqResponse.WorkflowStepMetadata.TokenUsage tokenUsage = new LinqResponse.WorkflowStepMetadata.TokenUsage();
                        tokenUsage.setPromptTokens(promptTokens);
                        tokenUsage.setCompletionTokens(completionTokens);
                        tokenUsage.setTotalTokens(totalTokens);
                        tokenUsage.setCostUsd(cost);
                        
                        stepMetadata.setTokenUsage(tokenUsage);
                        stepMetadata.setModel(model);
                    }
                }
                
                // Save the updated execution if not in dry run mode
                if (!dryRun && updated) {
                    return executionRepository.save(execution).thenReturn(1);
                } else if (updated) {
                    // In dry run mode, count executions that would be updated
                    return Mono.just(1);
                } else {
                    return Mono.just(0);
                }
            })
            .reduce(0, Integer::sum)
            .doOnSuccess(count -> {
                if (dryRun) {
                    log.info("✅ [DRY RUN] Would extract and populate tokenUsage for {} executions", count);
                } else {
                    log.info("✅ Successfully extracted and populated tokenUsage for {} executions", count);
                }
            })
            .doOnError(error -> log.error("❌ Error during advanced token usage backfill: {}", error.getMessage()));
    }
    
    private static class ModelPricing {
        final double inputPricePer1M;
        final double outputPricePer1M;
        
        ModelPricing(double inputPricePer1M, double outputPricePer1M) {
            this.inputPricePer1M = inputPricePer1M;
            this.outputPricePer1M = outputPricePer1M;
        }
    }
}

