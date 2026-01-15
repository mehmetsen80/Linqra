package org.lite.gateway.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;

import org.lite.gateway.repository.TeamRepository;
import org.lite.gateway.service.LlmCostService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Scheduler to automatically save monthly pricing snapshots for all teams
 * Runs on the 1st day of each month at 00:01 AM
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class LlmPricingScheduler {

    private final LlmCostService llmCostService;
    private final TeamRepository teamRepository;

    /**
     * Initialize pricing snapshots for the current month for all teams
     * Runs on the 1st of each month at 00:01 AM
     */
    @Scheduled(cron = "0 1 0 1 * ?") // At 00:01 AM on the 1st day of every month
    @SchedulerLock(name = "llmPricingInit", lockAtLeastFor = "1m", lockAtMostFor = "30m")
    public void initializeMonthlyPricing() {
        log.info("🗓️ Starting monthly LLM pricing snapshot initialization for all teams");

        teamRepository.findAll()
                .flatMap(team -> {
                    log.info("Initializing pricing for team: {} ({})", team.getName(), team.getId());
                    return llmCostService.initializeCurrentMonthPricing(team.getId())
                            .doOnSuccess(v -> log.info("✅ Pricing initialized for team: {}", team.getName()))
                            .doOnError(error -> log.error("❌ Error initializing pricing for team {}: {}",
                                    team.getName(), error.getMessage(), error))
                            .onErrorResume(e -> Mono.empty()); // Continue with other teams even if one fails
                })
                .then()
                .doOnSuccess(v -> log.info("✅ Monthly LLM pricing snapshot initialization completed for all teams"))
                .doOnError(error -> log.error("❌ Error initializing monthly pricing snapshots: {}", error.getMessage(),
                        error))
                .subscribe();
    }

    /**
     * Initialize pricing on application startup for all teams
     * This ensures current month always has pricing data
     * Also back-fills last 12 months for historical accuracy
     */
    @Scheduled(initialDelay = 5000, fixedDelay = Long.MAX_VALUE) // Run once 5 seconds after startup
    @SchedulerLock(name = "llmPricingStartup", lockAtLeastFor = "1m", lockAtMostFor = "10m")
    public void initializePricingOnStartup() {
        log.info("🚀 Initializing LLM pricing snapshots on application startup for all teams");

        // Calculate date range for back-fill (last 12 months + current month)
        java.time.YearMonth currentMonth = java.time.YearMonth.now();
        java.time.YearMonth startMonth = currentMonth.minusMonths(12);

        log.info("Back-filling pricing snapshots from {} to {} for all teams", startMonth, currentMonth);

        teamRepository.findAll()
                .flatMap(team -> {
                    log.info("Back-filling pricing for team: {} ({})", team.getName(), team.getId());
                    return llmCostService.backfillHistoricalPricing(team.getId(), startMonth, currentMonth)
                            .doOnSuccess(
                                    v -> log.info("✅ Pricing back-filled for team: {} (13 months)", team.getName()))
                            .doOnError(error -> log.error("❌ Error back-filling pricing for team {}: {}",
                                    team.getName(), error.getMessage(), error))
                            .onErrorResume(e -> Mono.empty()); // Continue with other teams even if one fails
                })
                .then()
                .doOnSuccess(
                        v -> log.info("✅ Startup pricing snapshot back-fill completed for all teams (13 months each)"))
                .doOnError(error -> log.error("❌ Error back-filling pricing on startup: {}", error.getMessage(), error))
                .subscribe();
    }
}
