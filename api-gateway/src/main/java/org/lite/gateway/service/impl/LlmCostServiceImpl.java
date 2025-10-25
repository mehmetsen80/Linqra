package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LlmUsageStats;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.entity.LlmModel;
import org.lite.gateway.entity.LlmPricingSnapshot;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.lite.gateway.repository.LlmModelRepository;
import org.lite.gateway.repository.LlmPricingSnapshotRepository;
import org.lite.gateway.service.LlmCostService;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmCostServiceImpl implements LlmCostService {
    
    private final LinqWorkflowExecutionRepository executionRepository;
    private final LlmPricingSnapshotRepository pricingSnapshotRepository;
    private final LlmModelRepository llmModelRepository;
    
    // Pricing per 1M tokens (as of October 2024 - update as needed)
    private static final Map<String, ModelPricing> MODEL_PRICING = new HashMap<>();
    
    static {
        // OpenAI GPT-4 models
        MODEL_PRICING.put("gpt-4o", new ModelPricing(2.50, 10.00)); // $2.50 input, $10.00 output per 1M tokens
        MODEL_PRICING.put("gpt-4o-mini", new ModelPricing(0.150, 0.600)); // $0.15 input, $0.60 output per 1M tokens
        MODEL_PRICING.put("gpt-4-turbo", new ModelPricing(10.00, 30.00));
        MODEL_PRICING.put("gpt-4", new ModelPricing(30.00, 60.00));
        MODEL_PRICING.put("gpt-3.5-turbo", new ModelPricing(0.50, 1.50));
        
        // OpenAI Embeddings
        MODEL_PRICING.put("text-embedding-3-small", new ModelPricing(0.020, 0.0));
        MODEL_PRICING.put("text-embedding-3-large", new ModelPricing(0.130, 0.0));
        MODEL_PRICING.put("text-embedding-ada-002", new ModelPricing(0.100, 0.0));
        
        // Google Gemini models
        MODEL_PRICING.put("gemini-2.0-flash", new ModelPricing(0.075, 0.30)); // $0.075 input, $0.30 output per 1M tokens
        MODEL_PRICING.put("gemini-1.5-pro", new ModelPricing(1.25, 5.00));
        MODEL_PRICING.put("gemini-1.5-flash", new ModelPricing(0.075, 0.30));
        
        // Default pricing for unknown models
        MODEL_PRICING.put("default", new ModelPricing(1.0, 2.0));
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
        
        return executionRepository.findByTeamIdAndExecutedAtBetween(teamId, from, to)
            .collectList()
            .map(executions -> calculateUsageStats(teamId, executions, from, to));
    }
    
    @Override
    public double calculateCost(String model, long promptTokens, long completionTokens) {
        // Normalize model name (e.g., "gpt-4o-2024-08-06" -> "gpt-4o")
        String normalizedModel = normalizeModelName(model);
        
        // Use static pricing to avoid blocking in reactive context
        // Database pricing is used for pricing snapshots
        return calculateCostFromStaticPricing(normalizedModel, promptTokens, completionTokens);
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
    
    private double calculateCostFromStaticPricing(String model, long promptTokens, long completionTokens) {
        ModelPricing pricing = MODEL_PRICING.getOrDefault(model, MODEL_PRICING.get("default"));
        double promptCost = (promptTokens / 1_000_000.0) * pricing.inputPricePer1M;
        double completionCost = (completionTokens / 1_000_000.0) * pricing.outputPricePer1M;
        return promptCost + completionCost;
    }
    
    private LlmUsageStats calculateUsageStats(String teamId, java.util.List<LinqWorkflowExecution> executions, 
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
        
        for (LinqWorkflowExecution execution : executions) {
            if (execution.getResponse() == null || 
                execution.getResponse().getMetadata() == null || 
                execution.getResponse().getMetadata().getWorkflowMetadata() == null) {
                continue;
            }
            
            for (LinqResponse.WorkflowStepMetadata stepMetadata : execution.getResponse().getMetadata().getWorkflowMetadata()) {
                String target = stepMetadata.getTarget();
                
                // Only process LLM provider steps (OpenAI, Gemini, Cohere, Claude)
                if (!target.equals("openai") && !target.equals("gemini") && !target.equals("openai-embed") 
                    && !target.equals("cohere") && !target.equals("cohere-embed") 
                    && !target.equals("gemini-embed") && !target.equals("claude")) {
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
        
        // Calculate daily breakdown
        Map<String, LlmUsageStats.DailyUsage> dailyMap = new HashMap<>();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        
        for (LinqWorkflowExecution execution : executions) {
            if (execution.getResponse() == null || 
                execution.getResponse().getMetadata() == null || 
                execution.getResponse().getMetadata().getWorkflowMetadata() == null) {
                continue;
            }
            
            for (LinqResponse.WorkflowStepMetadata stepMetadata : execution.getResponse().getMetadata().getWorkflowMetadata()) {
                String target = stepMetadata.getTarget();
                
                // Only process LLM provider steps
                if (!target.equals("openai") && !target.equals("gemini") && !target.equals("openai-embed") 
                    && !target.equals("cohere") && !target.equals("cohere-embed") 
                    && !target.equals("gemini-embed")) {
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
        
        // Convert to sorted list (by date)
        List<LlmUsageStats.DailyUsage> dailyBreakdown = new ArrayList<>(dailyMap.values());
        dailyBreakdown.sort(Comparator.comparing(LlmUsageStats.DailyUsage::getDate));
        stats.setDailyBreakdown(dailyBreakdown);
        
        log.info("Calculated LLM usage for team {}: {} requests, ${} total cost, {} days", 
            teamId, totalRequests, String.format("%.4f", totalCost), dailyBreakdown.size());
        
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
        return llmModelRepository.findByActive(true)
            .switchIfEmpty(Flux.fromIterable(MODEL_PRICING.entrySet())
                .filter(entry -> !entry.getKey().equals("default"))
                .map(entry -> {
                    String modelName = entry.getKey();
                    ModelPricing pricing = entry.getValue();
                    String provider = detectProviderFromModelName(modelName);
                    
                    LlmModel model = new LlmModel();
                    model.setModelName(modelName);
                    model.setProvider(provider);
                    model.setInputPricePer1M(pricing.inputPricePer1M);
                    model.setOutputPricePer1M(pricing.outputPricePer1M);
                    return model;
                })
            )
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
        log.info("🔄 Starting cost backfill for executions (dryRun: {}, teamId: {})", dryRun, teamId);
        
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
                        String target = stepMetadata.getTarget();
                        boolean isLlmStep = target.equals("openai") || target.equals("gemini") 
                            || target.equals("cohere") || target.equals("openai-embed") 
                            || target.equals("gemini-embed") || target.equals("cohere-embed");
                        
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
                    String target = stepMetadata.getTarget();
                    
                    // Check if it's an LLM step
                    if (!target.equals("openai") && !target.equals("gemini") && !target.equals("cohere") 
                        && !target.equals("openai-embed") && !target.equals("gemini-embed") && !target.equals("cohere-embed")) {
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
                        if (target.equals("openai") || target.equals("openai-embed")) {
                            model = "gpt-4o-mini"; // Default OpenAI model
                        } else if (target.equals("gemini") || target.equals("gemini-embed")) {
                            model = "gemini-2.0-flash"; // Default Gemini model
                        } else if (target.equals("cohere") || target.equals("cohere-embed")) {
                            model = "command-r-08-2024"; // Default Cohere model
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
                        target, 
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
            .reduce(0, Integer::sum)
            .doOnSuccess(count -> {
                if (dryRun) {
                    log.info("✅ [DRY RUN] Would update {} executions with backfilled costs", count);
                } else {
                    log.info("✅ Successfully backfilled costs for {} executions", count);
                }
            })
            .doOnError(error -> log.error("❌ Error during cost backfill: {}", error.getMessage()));
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
                    String target = stepMetadata.getTarget();
                    
                    // Check if it's a Cohere step (can be extended for other providers later)
                    if (!target.equals("cohere") && !target.equals("cohere-embed")) {
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
                    
                    // Extract Cohere token usage from meta.billed_units
                    if (!resultMap.containsKey("meta")) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> meta = (Map<String, Object>) resultMap.get("meta");
                    
                    if (!meta.containsKey("billed_units")) {
                        continue;
                    }
                    
                    @SuppressWarnings("unchecked")
                    Map<String, Object> billedUnits = (Map<String, Object>) meta.get("billed_units");
                    
                    long promptTokens = billedUnits.containsKey("input_tokens") 
                        ? ((Number) billedUnits.get("input_tokens")).longValue() : 0;
                    long completionTokens = billedUnits.containsKey("output_tokens") 
                        ? ((Number) billedUnits.get("output_tokens")).longValue() : 0;
                    long totalTokens = promptTokens + completionTokens;
                    
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
                        model = "command-r-08-2024"; // Default Cohere model
                    }
                    
                    // Calculate cost
                    double cost = calculateCost(model, promptTokens, completionTokens);
                    
                    log.info("🔍 {} execution {} - step {} ({}): EXTRACTING tokenUsage from response → model={}, tokens={}p/{}c, cost=${}", 
                        dryRun ? "[DRY RUN]" : "Updating",
                        execution.getId(), 
                        stepMetadata.getStep(), 
                        target, 
                        model, 
                        promptTokens, 
                        completionTokens, 
                        String.format("%.6f", cost));
                    
                    // Create and set token usage
                    if (!dryRun) {
                        LinqResponse.WorkflowStepMetadata.TokenUsage tokenUsage = new LinqResponse.WorkflowStepMetadata.TokenUsage();
                        tokenUsage.setPromptTokens(promptTokens);
                        tokenUsage.setCompletionTokens(completionTokens);
                        tokenUsage.setTotalTokens(totalTokens);
                        tokenUsage.setCostUsd(cost);
                        
                        stepMetadata.setTokenUsage(tokenUsage);
                        stepMetadata.setModel(model);
                        updated = true;
                    }
                }
                
                // Save the updated execution if not in dry run mode
                if (!dryRun && updated) {
                    return executionRepository.save(execution).thenReturn(1);
                } else {
                    return Mono.just(dryRun && updated ? 1 : 0);
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

