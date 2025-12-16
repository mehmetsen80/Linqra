package org.lite.gateway.service;

import org.lite.gateway.dto.LlmUsageStats;
import org.lite.gateway.entity.LlmPricingSnapshot;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.YearMonth;

public interface LlmCostService {
    /**
     * Get LLM usage statistics and costs for a team
     * 
     * @param teamId   The team ID
     * @param fromDate Start date (optional)
     * @param toDate   End date (optional)
     * @return LLM usage statistics with cost breakdown
     */
    Mono<LlmUsageStats> getTeamLlmUsage(String teamId, String fromDate, String toDate);

    /**
     * Calculate cost for a given model and token usage using current pricing
     * 
     * @param model            The model name (e.g., "gpt-4o", "gemini-2.0-flash")
     * @param promptTokens     Number of prompt tokens
     * @param completionTokens Number of completion tokens
     * @return Cost in USD
     */
    double calculateCost(String model, long promptTokens, long completionTokens);

    /**
     * Calculate cost using pricing from a specific month
     * 
     * @param teamId           The team ID
     * @param model            The model name
     * @param promptTokens     Number of prompt tokens
     * @param completionTokens Number of completion tokens
     * @param yearMonth        The year-month to use for pricing
     * @return Mono with cost in USD
     */
    Mono<Double> calculateCostForMonth(String teamId, String model, long promptTokens, long completionTokens,
            YearMonth yearMonth);

    /**
     * Save or update pricing snapshot for a model
     * 
     * @param snapshot The pricing snapshot to save (must include teamId)
     * @return Mono with saved snapshot
     */
    Mono<LlmPricingSnapshot> savePricingSnapshot(LlmPricingSnapshot snapshot);

    /**
     * Get all pricing snapshots for a specific team and month
     * 
     * @param teamId    The team ID
     * @param yearMonth The year-month
     * @return Flux of pricing snapshots
     */
    Flux<LlmPricingSnapshot> getPricingSnapshotsForMonth(String teamId, YearMonth yearMonth);

    /**
     * Initialize current month's pricing snapshots for a team from the static
     * pricing table
     * This should be called at the start of each month or when prices are updated
     * 
     * @param teamId The team ID
     * @return Mono indicating completion
     */
    Mono<Void> initializeCurrentMonthPricing(String teamId);

    /**
     * Backfill pricing snapshots for historical months for a team
     * Use this for initial deployment to populate pricing for past executions
     * 
     * @param teamId        The team ID
     * @param fromYearMonth Start month (e.g., "2024-01")
     * @param toYearMonth   End month (e.g., "2025-10")
     * @return Mono indicating completion
     */
    Mono<Void> backfillHistoricalPricing(String teamId, YearMonth fromYearMonth, YearMonth toYearMonth);

    /**
     * Clean up all duplicate pricing snapshots
     * Keeps the most recent snapshot for each yearMonth-model combination
     * 
     * @return Mono with count of deleted duplicates
     */
    Mono<Integer> cleanupAllPricingDuplicates();

    /**
     * Backfill cost calculation for existing workflow executions that are missing
     * cost data
     * This will:
     * 1. Find all executions with LLM steps that have token usage but no cost
     * 2. Extract the model from the workflow request
     * 3. Calculate cost using current pricing
     * 4. Update the execution record with the calculated cost
     * 
     * @param teamId Optional team ID to limit backfill (null for all teams)
     * @param dryRun If true, only logs what would be updated without making changes
     * @return Mono with count of updated executions
     */
    Mono<Integer> backfillExecutionCosts(String teamId, boolean dryRun);

    /**
     * Advanced backfill that extracts token usage from response data for executions
     * missing tokenUsage
     * This is for Cohere (and other providers) where token data exists in the
     * response but wasn't extracted to metadata
     * 
     * @param teamId Optional team ID to limit backfill (null for all teams)
     * @param dryRun If true, only logs what would be updated without making changes
     * @return Mono with count of updated executions
     */
    Mono<Integer> backfillTokenUsageFromResponses(String teamId, boolean dryRun);

    /**
     * Refresh the pricing cache from database.
     * Call this method when models are added, updated, or deleted.
     * This is safe to call from reactive context (returns Mono, no blocking).
     * 
     * @return Mono indicating completion
     */
    Mono<Void> refreshPricingCache();
}
