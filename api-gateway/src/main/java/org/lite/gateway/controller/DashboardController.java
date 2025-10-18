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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
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
        return teamContextService.getTeamFromContext()
            .doOnNext(teamId -> log.info("Fetching LLM usage for team: {}", teamId))
            .flatMap(teamId -> llmCostService.getTeamLlmUsage(teamId, fromDate, toDate))
            .doOnSuccess(stats -> log.info("Successfully fetched LLM usage stats"))
            .doOnError(error -> log.error("Error fetching LLM usage: {}", error.getMessage()));
    }
} 