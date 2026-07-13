package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ExternalUserCredit;
import org.lite.gateway.service.ExternalUserCreditService;
import org.lite.gateway.service.ExternalUsageLoggerService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/external-users")
@RequiredArgsConstructor
@Slf4j
public class ExternalUserCreditController {

    private final ExternalUserCreditService externalUserCreditService;
    private final ExternalUsageLoggerService externalUsageLoggerService;

    @GetMapping("/{externalUserId}/credits")
    public Mono<ResponseEntity<Map<String, Object>>> getCredits(
            @PathVariable String externalUserId,
            @RequestHeader(value = "X-Service-Name", required = false, defaultValue = "unknown") String serviceName) {
        
        Mono<ExternalUserCredit> creditMono = externalUserCreditService.getCredits(externalUserId, serviceName)
                .defaultIfEmpty(ExternalUserCredit.builder().aiCredits(100).maxAiCredits(100).build());

        Mono<Double> totalSpentMono = externalUsageLoggerService.getUsageByExternalUserId(externalUserId)
                .map(log -> log.getBilledCostUsd() != null ? log.getBilledCostUsd() : 0.0)
                .reduce(0.0, Double::sum)
                .defaultIfEmpty(0.0);

        return Mono.zip(creditMono, totalSpentMono)
                .map(tuple -> {
                    ExternalUserCredit credit = tuple.getT1();
                    Double totalSpent = tuple.getT2();
                    return ResponseEntity.ok(Map.<String, Object>of(
                            "aiCredits", credit.getAiCredits(),
                            "maxAiCredits", credit.getMaxAiCredits(),
                            "totalSpentUsd", totalSpent
                    ));
                });
    }
}
