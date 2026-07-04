package org.lite.gateway.service;

import org.lite.gateway.entity.ExternalUsageLog;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ExternalUsageLoggerService {
    Mono<ExternalUsageLog> logUsage(String serviceName, String externalUserId, String modelId, boolean isByok, int promptTokens, int completionTokens);
    Flux<ExternalUsageLog> getUsageByExternalUserId(String externalUserId);
    Flux<ExternalUsageLog> getUsageByServiceName(String serviceName);
}
