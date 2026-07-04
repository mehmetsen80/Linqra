package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.lite.gateway.entity.ExternalUsageLog;
import org.lite.gateway.service.ExternalUsageLoggerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/external-usage")
@RequiredArgsConstructor
public class ExternalUsageController {

    private final ExternalUsageLoggerService usageLoggerService;

    @GetMapping
    public Flux<ExternalUsageLog> getUsageLogs(
            @RequestParam(required = false) String externalUserId,
            @RequestParam(required = false) String serviceName) {
            
        if (externalUserId != null) {
            return usageLoggerService.getUsageByExternalUserId(externalUserId);
        } else if (serviceName != null) {
            return usageLoggerService.getUsageByServiceName(serviceName);
        }
        return Flux.empty();
    }

    @GetMapping("/summary")
    public Mono<Map<String, Object>> getUsageSummary(
            @RequestParam(required = false) String externalUserId,
            @RequestParam(required = false) String serviceName) {
            
        Flux<ExternalUsageLog> logs = getUsageLogs(externalUserId, serviceName);
        
        return logs.collectList().map(list -> {
            double totalCost = list.stream().mapToDouble(ExternalUsageLog::getTheoreticalCostUsd).sum();
            long totalTokens = list.stream().mapToLong(l -> l.getTokens().getTotal()).sum();
            
            return Map.of(
                    "totalTheoreticalCostUsd", totalCost,
                    "totalTokens", totalTokens,
                    "count", list.size()
            );
        });
    }
}
