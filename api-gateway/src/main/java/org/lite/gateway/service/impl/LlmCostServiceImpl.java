package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LlmUsageStats;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.entity.LlmPricingSnapshot;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.lite.gateway.repository.LlmPricingSnapshotRepository;
import org.lite.gateway.service.LlmCostService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class LlmCostServiceImpl implements LlmCostService {
    
    private final LinqWorkflowExecutionRepository executionRepository;
    private final LlmPricingSnapshotRepository pricingSnapshotRepository;
    
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
                
                // Only process OpenAI and Gemini steps
                if (!target.equals("openai") && !target.equals("gemini") && !target.equals("openai-embed")) {
                    continue;
                }
                
                var tokenUsage = stepMetadata.getTokenUsage();
                if (tokenUsage == null) {
                    continue;
                }
                
                String model = stepMetadata.getModel() != null ? stepMetadata.getModel() : "unknown";
                long promptTokens = tokenUsage.getPromptTokens();
                long completionTokens = tokenUsage.getCompletionTokens();
                long tokens = tokenUsage.getTotalTokens() > 0 ? tokenUsage.getTotalTokens() : (promptTokens + completionTokens);
                
                // Use stored cost if available (from execution time), otherwise calculate with current pricing
                double cost;
                if (tokenUsage.getCostUsd() != null && tokenUsage.getCostUsd() > 0) {
                    cost = tokenUsage.getCostUsd();
                    log.debug("Using stored cost for {}: ${}", model, String.format("%.6f", cost));
                } else {
                    cost = calculateCost(model, promptTokens, completionTokens);
                    log.debug("Calculated cost for {} with current pricing: ${}", model, String.format("%.6f", cost));
                }
                
                // Update totals
                totalRequests++;
                totalPromptTokens += promptTokens;
                totalCompletionTokens += completionTokens;
                totalCost += cost;
                
                // Update model breakdown
                LlmUsageStats.ModelUsage modelUsage = modelBreakdown.computeIfAbsent(model, k -> {
                    LlmUsageStats.ModelUsage mu = new LlmUsageStats.ModelUsage();
                    mu.setModelName(model);
                    mu.setProvider(target.equals("openai") || target.equals("openai-embed") ? "openai" : "gemini");
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
                
                // Update average latency
                double currentAvg = modelUsage.getAverageLatencyMs();
                double newAvg = (currentAvg * (modelUsage.getRequests() - 1) + stepMetadata.getDurationMs()) / modelUsage.getRequests();
                modelUsage.setAverageLatencyMs(newAvg);
                
                // Update provider breakdown
                String provider = target.equals("openai") || target.equals("openai-embed") ? "openai" : "gemini";
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
        stats.setModelBreakdown(modelBreakdown);
        stats.setProviderBreakdown(providerBreakdown);
        
        log.info("Calculated LLM usage for team {}: {} requests, ${} total cost", 
            teamId, totalRequests, String.format("%.4f", totalCost));
        
        return stats;
    }
    
    @Override
    public Mono<Double> calculateCostForMonth(String model, long promptTokens, long completionTokens, YearMonth yearMonth) {
        return pricingSnapshotRepository.findByYearMonthAndModel(yearMonth.toString(), model)
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
    public Flux<LlmPricingSnapshot> getPricingSnapshotsForMonth(YearMonth yearMonth) {
        return pricingSnapshotRepository.findByYearMonth(yearMonth.toString());
    }
    
    @Override
    public Mono<Void> initializeCurrentMonthPricing() {
        YearMonth currentMonth = YearMonth.now();
        return initializePricingForMonth(currentMonth);
    }
    
    @Override
    public Mono<Void> backfillHistoricalPricing(YearMonth fromYearMonth, YearMonth toYearMonth) {
        log.info("Backfilling pricing snapshots from {} to {}", fromYearMonth, toYearMonth);
        
        java.util.List<YearMonth> months = new java.util.ArrayList<>();
        YearMonth current = fromYearMonth;
        while (!current.isAfter(toYearMonth)) {
            months.add(current);
            current = current.plusMonths(1);
        }
        
        return Flux.fromIterable(months)
            .flatMap(this::initializePricingForMonth)
            .then()
            .doOnSuccess(v -> log.info("Historical pricing backfill complete from {} to {}", fromYearMonth, toYearMonth));
    }
    
    /**
     * Cleanup duplicate pricing snapshots and save a new one
     */
    private Mono<LlmPricingSnapshot> cleanupDuplicatesAndSave(String yearMonth, String modelName, LlmPricingSnapshot newSnapshot) {
        return pricingSnapshotRepository.findByYearMonth(yearMonth)
            .filter(snapshot -> snapshot.getModel().equals(modelName))
            .collectList()
            .flatMap(duplicates -> {
                if (duplicates.isEmpty()) {
                    // No duplicates found, just save
                    return pricingSnapshotRepository.save(newSnapshot);
                }
                
                log.info("Deleting {} duplicate pricing snapshots for {} in {}", duplicates.size(), modelName, yearMonth);
                
                // Delete all duplicates
                return Flux.fromIterable(duplicates)
                    .flatMap(duplicate -> pricingSnapshotRepository.delete(duplicate))
                    .then(pricingSnapshotRepository.save(newSnapshot))
                    .doOnSuccess(saved -> log.info("Cleaned up duplicates and saved new pricing snapshot for {} in {}", modelName, yearMonth));
            });
    }
    
    /**
     * Initialize pricing snapshots for a specific month
     */
    private Mono<Void> initializePricingForMonth(YearMonth yearMonth) {
        log.info("Initializing pricing snapshots for {}", yearMonth);
        
        return Flux.fromIterable(MODEL_PRICING.entrySet())
            .filter(entry -> !entry.getKey().equals("default")) // Skip default pricing
            .flatMap(entry -> {
                String modelName = entry.getKey();
                ModelPricing pricing = entry.getValue();
                
                // Determine provider from model name
                String provider = modelName.startsWith("gpt-") || modelName.startsWith("text-embedding") ? "openai" : "gemini";
                
                LlmPricingSnapshot snapshot = new LlmPricingSnapshot();
                snapshot.setYearMonth(yearMonth.toString());
                snapshot.setModel(modelName);
                snapshot.setProvider(provider);
                snapshot.setInputPricePer1M(pricing.inputPricePer1M);
                snapshot.setOutputPricePer1M(pricing.outputPricePer1M);
                snapshot.setSnapshotDate(LocalDateTime.now());
                snapshot.setSource("system");
                
                // Check if already exists and handle duplicates
                return pricingSnapshotRepository.findByYearMonthAndModel(yearMonth.toString(), modelName)
                    .onErrorResume(org.springframework.dao.IncorrectResultSizeDataAccessException.class, error -> {
                        // If duplicates exist, delete all and recreate
                        log.warn("Found duplicate pricing snapshots for {} in {}, cleaning up", modelName, yearMonth);
                        return cleanupDuplicatesAndSave(yearMonth.toString(), modelName, snapshot);
                    })
                    .flatMap(existing -> {
                        log.debug("Pricing snapshot already exists for {} in {}, skipping", modelName, yearMonth);
                        return Mono.empty();
                    })
                    .switchIfEmpty(
                        pricingSnapshotRepository.save(snapshot)
                            .doOnSuccess(saved -> log.info("Saved pricing snapshot for {} in {}: ${}/{}", 
                                modelName, yearMonth, pricing.inputPricePer1M, pricing.outputPricePer1M))
                            .onErrorResume(error -> {
                                // If save fails due to duplicate, try cleanup
                                log.warn("Error saving pricing snapshot, attempting cleanup: {}", error.getMessage());
                                return cleanupDuplicatesAndSave(yearMonth.toString(), modelName, snapshot);
                            })
                    );
            })
            .then()
            .doOnSuccess(v -> log.info("Pricing snapshot initialization complete for {}", yearMonth));
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

