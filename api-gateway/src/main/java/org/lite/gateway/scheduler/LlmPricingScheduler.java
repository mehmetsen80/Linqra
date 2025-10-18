package org.lite.gateway.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.LlmCostService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler to automatically save monthly pricing snapshots
 * Runs on the 1st day of each month at 00:01 AM
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmPricingScheduler {
    
    private final LlmCostService llmCostService;
    
    /**
     * Initialize pricing snapshots for the current month
     * Runs on the 1st of each month at 00:01 AM
     * Also runs on application startup
     */
    @Scheduled(cron = "0 1 0 1 * ?") // At 00:01 AM on the 1st day of every month
    public void initializeMonthlyPricing() {
        log.info("Starting monthly LLM pricing snapshot initialization");
        llmCostService.initializeCurrentMonthPricing()
            .doOnSuccess(v -> log.info("Monthly LLM pricing snapshot initialization completed successfully"))
            .doOnError(error -> log.error("Error initializing monthly pricing snapshots: {}", error.getMessage(), error))
            .subscribe();
    }
    
    /**
     * Initialize pricing on application startup
     * This ensures current month always has pricing data
     * Also back-fills last 12 months for historical accuracy
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE) // Run once 5 seconds after startup
    public void initializePricingOnStartup() {
        log.info("Initializing LLM pricing snapshots on application startup");
        
        // Calculate date range for back-fill (last 12 months + current month)
        java.time.YearMonth currentMonth = java.time.YearMonth.now();
        java.time.YearMonth startMonth = currentMonth.minusMonths(12);
        
        log.info("Back-filling pricing snapshots from {} to {}", startMonth, currentMonth);
        
        llmCostService.backfillHistoricalPricing(startMonth, currentMonth)
            .doOnSuccess(v -> log.info("Startup pricing snapshot back-fill completed successfully (13 months)"))
            .doOnError(error -> log.error("Error back-filling pricing on startup: {}", error.getMessage(), error))
            .subscribe();
    }
}

