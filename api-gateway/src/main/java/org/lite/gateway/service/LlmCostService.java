package org.lite.gateway.service;

import org.lite.gateway.dto.LlmUsageStats;
import org.lite.gateway.entity.LlmPricingSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.YearMonth;

public interface LlmCostService {
    /**
     * Get LLM usage statistics and costs for a team
     * @param teamId The team ID
     * @param fromDate Start date (optional)
     * @param toDate End date (optional)
     * @return LLM usage statistics with cost breakdown
     */
    Mono<LlmUsageStats> getTeamLlmUsage(String teamId, String fromDate, String toDate);
    
    /**
     * Calculate cost for a given model and token usage using current pricing
     * @param model The model name (e.g., "gpt-4o", "gemini-2.0-flash")
     * @param promptTokens Number of prompt tokens
     * @param completionTokens Number of completion tokens
     * @return Cost in USD
     */
    double calculateCost(String model, long promptTokens, long completionTokens);
    
    /**
     * Calculate cost using pricing from a specific month
     * @param model The model name
     * @param promptTokens Number of prompt tokens
     * @param completionTokens Number of completion tokens
     * @param yearMonth The year-month to use for pricing
     * @return Mono with cost in USD
     */
    Mono<Double> calculateCostForMonth(String model, long promptTokens, long completionTokens, YearMonth yearMonth);
    
    /**
     * Save or update pricing snapshot for a model
     * @param snapshot The pricing snapshot to save
     * @return Mono with saved snapshot
     */
    Mono<LlmPricingSnapshot> savePricingSnapshot(LlmPricingSnapshot snapshot);
    
    /**
     * Get all pricing snapshots for a specific month
     * @param yearMonth The year-month
     * @return Flux of pricing snapshots
     */
    Flux<LlmPricingSnapshot> getPricingSnapshotsForMonth(YearMonth yearMonth);
    
    /**
     * Initialize current month's pricing snapshots from the static pricing table
     * This should be called at the start of each month or when prices are updated
     * @return Mono indicating completion
     */
    Mono<Void> initializeCurrentMonthPricing();
    
    /**
     * Backfill pricing snapshots for historical months
     * Use this for initial deployment to populate pricing for past executions
     * @param fromYearMonth Start month (e.g., "2024-01")
     * @param toYearMonth End month (e.g., "2025-10")
     * @return Mono indicating completion
     */
    Mono<Void> backfillHistoricalPricing(YearMonth fromYearMonth, YearMonth toYearMonth);
}

