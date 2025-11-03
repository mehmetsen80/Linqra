package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.LlmPricingSnapshot;
import org.lite.gateway.repository.LlmPricingSnapshotRepository;
import org.lite.gateway.service.LlmCostService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.YearMonth;
import java.util.Map;

@RestController
@RequestMapping("/api/llm-pricing")
@RequiredArgsConstructor
@Slf4j
public class LlmPricingController {
    
    private final LlmCostService llmCostService;
    private final LlmPricingSnapshotRepository pricingSnapshotRepository;
    private final TeamContextService teamContextService;
    
    /**
     * Get all pricing snapshots for a specific month for the current team
     */
    @GetMapping("/{yearMonth}")
    public Flux<LlmPricingSnapshot> getPricingForMonth(
        @PathVariable @DateTimeFormat(pattern = "yyyy-MM") YearMonth yearMonth,
        ServerWebExchange exchange
    ) {
        return teamContextService.getTeamFromContext(exchange)
            .doOnNext(teamId -> log.info("Fetching pricing snapshots for team {} in {}", teamId, yearMonth))
            .flatMapMany(teamId -> llmCostService.getPricingSnapshotsForMonth(teamId, yearMonth));
    }
    
    /**
     * Save or update a pricing snapshot
     */
    @PostMapping
    public Mono<LlmPricingSnapshot> savePricingSnapshot(@RequestBody LlmPricingSnapshot snapshot) {
        log.info("Saving pricing snapshot for model: {} in {}", snapshot.getModel(), snapshot.getYearMonth());
        return llmCostService.savePricingSnapshot(snapshot);
    }
    
    /**
     * Initialize current month's pricing from the static table for the current team
     * Useful for manual refresh or when prices are updated
     */
    @PostMapping("/initialize-current-month")
    public Mono<Map<String, String>> initializeCurrentMonthPricing(ServerWebExchange exchange) {
        return teamContextService.getTeamFromContext(exchange)
            .doOnNext(teamId -> log.info("Manually initializing current month pricing snapshots for team {}", teamId))
            .flatMap(teamId -> llmCostService.initializeCurrentMonthPricing(teamId)
                .thenReturn(Map.of(
                    "status", "success",
                    "message", "Current month pricing snapshots initialized successfully for team",
                    "teamId", teamId,
                    "month", YearMonth.now().toString()
                ))
            )
            .doOnSuccess(result -> log.info("Current month pricing initialization completed"))
            .doOnError(error -> log.error("Error initializing current month pricing: {}", error.getMessage()));
    }
    
    /**
     * Backfill pricing snapshots for historical months for the current team
     * Use this for initial deployment to populate pricing for all past months
     * 
     * Example: POST /api/llm-pricing/backfill?fromMonth=2024-01&toMonth=2025-10
     */
    @PostMapping("/backfill")
    public Mono<Map<String, Object>> backfillHistoricalPricing(
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth fromMonth,
        @RequestParam @DateTimeFormat(pattern = "yyyy-MM") YearMonth toMonth,
        ServerWebExchange exchange
    ) {
        // Calculate number of months
        long monthCount = java.time.temporal.ChronoUnit.MONTHS.between(fromMonth, toMonth) + 1;
        
        return teamContextService.getTeamFromContext(exchange)
            .doOnNext(teamId -> log.info("Starting backfill of pricing snapshots for team {} from {} to {}", 
                teamId, fromMonth, toMonth))
            .flatMap(teamId -> llmCostService.backfillHistoricalPricing(teamId, fromMonth, toMonth)
                .then(Mono.fromSupplier(() -> {
                    Map<String, Object> response = new java.util.HashMap<>();
                    response.put("status", "success");
                    response.put("message", "Historical pricing snapshots backfilled successfully for team");
                    response.put("teamId", teamId);
                    response.put("fromMonth", fromMonth.toString());
                    response.put("toMonth", toMonth.toString());
                    response.put("monthsProcessed", monthCount);
                    return response;
                }))
            )
            .doOnSuccess(result -> log.info("Historical pricing backfill completed: {} months", monthCount))
            .doOnError(error -> log.error("Error backfilling historical pricing: {}", error.getMessage()));
    }
    
    /**
     * Cleanup duplicate pricing snapshots
     * This endpoint will find and remove duplicate entries for each yearMonth-model combination
     * 
     * Example: POST /api/llm-pricing/cleanup-duplicates
     */
    @PostMapping("/cleanup-duplicates")
    public Mono<Map<String, Object>> cleanupDuplicates() {
        log.info("Starting cleanup of duplicate pricing snapshots");
        
        return pricingSnapshotRepository.findAll()
            .groupBy(snapshot -> snapshot.getYearMonth() + "-" + snapshot.getModel())
            .flatMap(group -> 
                group.collectList()
                    .flatMap(snapshots -> {
                        if (snapshots.size() > 1) {
                            // Keep the most recent one, delete the rest
                            log.warn("Found {} duplicates for {}, keeping most recent", snapshots.size(), group.key());
                            LlmPricingSnapshot mostRecent = snapshots.stream()
                                .max((s1, s2) -> s1.getSnapshotDate().compareTo(s2.getSnapshotDate()))
                                .orElse(snapshots.getFirst());
                            
                            return Flux.fromIterable(snapshots)
                                .filter(s -> !s.getId().equals(mostRecent.getId()))
                                .flatMap(pricingSnapshotRepository::delete)
                                .count()
                                .map(deleted -> Map.of("key", group.key(), "deleted", deleted));
                        }
                        return Mono.just(Map.of("key", group.key(), "deleted", 0));
                    })
            )
            .reduce(
                new java.util.HashMap<String, Object>(),
                (acc, result) -> {
                    int deleted = ((Number) result.get("deleted")).intValue();
                    if (deleted > 0) {
                        int totalDeleted = acc.containsKey("totalDeleted") 
                            ? ((Number) acc.get("totalDeleted")).intValue() + deleted
                            : deleted;
                        acc.put("totalDeleted", totalDeleted);
                        
                        @SuppressWarnings("unchecked")
                        java.util.List<String> cleanedKeys = (java.util.List<String>) acc.getOrDefault("cleanedKeys", new java.util.ArrayList<>());
                        cleanedKeys.add((String) result.get("key"));
                        acc.put("cleanedKeys", cleanedKeys);
                    }
                    return acc;
                }
            )
            .map(hashMap -> {
                Map<String, Object> result = new java.util.HashMap<>(hashMap);
                result.put("status", "success");
                result.put("message", "Duplicate pricing snapshots cleanup completed");
                result.putIfAbsent("totalDeleted", 0);
                result.putIfAbsent("cleanedKeys", java.util.Collections.emptyList());
                return result;
            })
            .doOnSuccess(result -> log.info("Duplicate cleanup completed: {} duplicates removed", result.get("totalDeleted")))
            .doOnError(error -> log.error("Error during duplicate cleanup: {}", error.getMessage()));
    }
}

