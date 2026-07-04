package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ExternalUsageLog;
import org.lite.gateway.repository.ExternalUsageLogRepository;
import org.lite.gateway.service.ExternalUsageLoggerService;
import org.lite.gateway.service.LlmCostService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalUsageLoggerServiceImpl implements ExternalUsageLoggerService {

    private final ExternalUsageLogRepository repository;
    private final LlmCostService costService;

    @Override
    public Mono<ExternalUsageLog> logUsage(String serviceName, String externalUserId, String modelId, boolean isByok, int promptTokens, int completionTokens) {
        log.info("Logging external usage for user {} via service {}: {} prompt, {} completion", externalUserId, serviceName, promptTokens, completionTokens);
        
        double theoreticalCost = costService.calculateCost(modelId, promptTokens, completionTokens);
        
        ExternalUsageLog.Tokens tokens = ExternalUsageLog.Tokens.builder()
                .prompt(promptTokens)
                .completion(completionTokens)
                .total(promptTokens + completionTokens)
                .build();
                
        ExternalUsageLog logEntry = ExternalUsageLog.builder()
                .id(UUID.randomUUID().toString())
                .serviceName(serviceName)
                .externalUserId(externalUserId)
                .modelId(modelId)
                .isByok(isByok)
                .tokens(tokens)
                .theoreticalCostUsd(theoreticalCost)
                .billedCostUsd(isByok ? 0.0 : theoreticalCost)
                .timestamp(LocalDateTime.now())
                .build();
                
        return repository.save(logEntry);
    }

    @Override
    public Flux<ExternalUsageLog> getUsageByExternalUserId(String externalUserId) {
        return repository.findByExternalUserId(externalUserId);
    }

    @Override
    public Flux<ExternalUsageLog> getUsageByServiceName(String serviceName) {
        return repository.findByServiceName(serviceName);
    }
}
