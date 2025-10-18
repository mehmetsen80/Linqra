package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LlmUsageStats;
import org.lite.gateway.service.LlmCostService;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

/**
 * REST Controller for LLM cost and usage analytics
 */
@RestController
@RequestMapping("/api/llm-costs")
@RequiredArgsConstructor
@Slf4j
public class LlmCostController {
    
    private final LlmCostService llmCostService;
    
    /**
     * Get LLM usage statistics and costs for a team
     * 
     * @param teamId The team ID
     * @param fromDate Start date (optional, format: yyyy-MM-dd)
     * @param toDate End date (optional, format: yyyy-MM-dd)
     * @return LLM usage statistics with cost breakdown
     */
    @GetMapping("/team/{teamId}/usage")
    public Mono<LlmUsageStats> getTeamLlmUsage(
        @PathVariable String teamId,
        @RequestParam(required = false) String fromDate,
        @RequestParam(required = false) String toDate
    ) {
        log.info("Fetching LLM usage for team: {}, fromDate: {}, toDate: {}", teamId, fromDate, toDate);
        return llmCostService.getTeamLlmUsage(teamId, fromDate, toDate)
            .doOnSuccess(stats -> {
                double totalCost = stats.getTotalUsage() != null ? stats.getTotalUsage().getTotalCostUsd() : 0.0;
                log.info("Successfully fetched LLM usage for team: {}, total cost: ${}", teamId, totalCost);
            })
            .doOnError(error -> log.error("Error fetching LLM usage for team {}: {}", teamId, error.getMessage()));
    }
}

