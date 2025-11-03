package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.EndpointLatencyStats;
import org.lite.gateway.dto.LlmUsageStats;
import org.lite.gateway.dto.ServiceUsageStats;
import org.lite.gateway.dto.StatDTO;
import org.lite.gateway.service.DashboardService;
import org.lite.gateway.service.LlmCostService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@Slf4j
public class DashboardController {
    private final DashboardService dashboardService;
    private final LlmCostService llmCostService;
    private final TeamContextService teamContextService;

    @GetMapping("/stats")
    public Flux<StatDTO> getDashboardStats(@RequestParam String teamId) {
        return dashboardService.getDashboardStats(teamId);
    }

    @GetMapping("/latency")
    public Flux<EndpointLatencyStats> getLatencyStats(
        @RequestParam String teamId,
        @RequestParam(defaultValue = "30d") String timeRange
    ) {
        return dashboardService.getLatencyStats(teamId, timeRange);
    }

    @GetMapping("/service-usage")
    public Flux<ServiceUsageStats> getServiceUsage(@RequestParam String teamId) {
        return dashboardService.getServiceUsage(teamId);
    }
    
    @GetMapping("/llm-usage")
    public Mono<LlmUsageStats> getLlmUsage(
        ServerWebExchange exchange,
        @RequestParam(required = false) String fromDate,
        @RequestParam(required = false) String toDate
    ) {
        return teamContextService.getTeamFromContext(exchange)
            .doOnNext(teamId -> log.info("Fetching LLM usage for team: {}", teamId))
            .flatMap(teamId -> llmCostService.getTeamLlmUsage(teamId, fromDate, toDate))
            .doOnSuccess(stats -> log.info("Successfully fetched LLM usage stats"))
            .doOnError(error -> log.error("Error fetching LLM usage: {}", error.getMessage()));
    }
    
    /**
     * Backfill cost calculation for existing workflow executions
     * This will recalculate costs for executions that have token usage but no cost data
     * 
     * Usage:
     * - Dry run (no changes): POST /api/dashboard/backfill-costs?dryRun=true
     * - Actual update: POST /api/dashboard/backfill-costs?dryRun=false
     * - Specific team: POST /api/dashboard/backfill-costs?teamId=TEAM_ID&dryRun=false
     */
    @PostMapping("/backfill-costs")
    public Mono<Map<String, Object>> backfillExecutionCosts(
        @RequestParam(required = false) String teamId,
        @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        log.info("🔧 Backfill costs endpoint called - teamId: {}, dryRun: {}", teamId, dryRun);
        
        return llmCostService.backfillExecutionCosts(teamId, dryRun)
            .map(count -> {
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("success", true);
                response.put("message", dryRun ? "Dry run completed - no changes made" : "Cost backfill completed successfully");
                response.put("updatedExecutions", count);
                response.put("dryRun", dryRun);
                response.put("teamId", teamId != null ? teamId : "all teams");
                return response;
            })
            .onErrorResume(error -> {
                log.error("Error during cost backfill: {}", error.getMessage(), error);
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("success", false);
                response.put("message", "Error during cost backfill: " + error.getMessage());
                response.put("updatedExecutions", 0);
                response.put("dryRun", dryRun);
                return Mono.just(response);
            });
    }
    
    /**
     * Advanced backfill that extracts token usage from response data
     * This is for Cohere executions where token data exists in response but wasn't extracted to metadata
     * 
     * Usage:
     * - Dry run: POST /api/dashboard/backfill-token-usage?dryRun=true
     * - Actual update: POST /api/dashboard/backfill-token-usage?dryRun=false
     * - Specific team: POST /api/dashboard/backfill-token-usage?teamId=TEAM_ID&dryRun=false
     */
    @PostMapping("/backfill-token-usage")
    public Mono<Map<String, Object>> backfillTokenUsageFromResponses(
        @RequestParam(required = false) String teamId,
        @RequestParam(defaultValue = "true") boolean dryRun
    ) {
        log.info("🔧 Advanced token usage backfill endpoint called - teamId: {}, dryRun: {}", teamId, dryRun);
        
        return llmCostService.backfillTokenUsageFromResponses(teamId, dryRun)
            .map(count -> {
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("success", true);
                response.put("message", dryRun ? "Dry run completed - no changes made" : "Token usage backfill completed successfully");
                response.put("updatedExecutions", count);
                response.put("dryRun", dryRun);
                response.put("teamId", teamId != null ? teamId : "all teams");
                return response;
            })
            .onErrorResume(error -> {
                log.error("Error during token usage backfill: {}", error.getMessage(), error);
                Map<String, Object> response = new java.util.HashMap<>();
                response.put("success", false);
                response.put("message", "Error during token usage backfill: " + error.getMessage());
                response.put("updatedExecutions", 0);
                response.put("dryRun", dryRun);
                return Mono.just(response);
            });
    }
} 