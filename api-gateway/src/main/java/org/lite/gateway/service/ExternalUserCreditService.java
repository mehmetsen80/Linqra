package org.lite.gateway.service;

import org.lite.gateway.entity.ExternalUserCredit;
import reactor.core.publisher.Mono;

public interface ExternalUserCreditService {
    Mono<ExternalUserCredit> getOrCreateCredit(String externalUserId, String serviceName, String machineId);
    Mono<Boolean> hasSufficientCredits(String externalUserId, String serviceName, String machineId);
    Mono<Void> consumeCredit(String externalUserId, String serviceName, int amount);
    Mono<ExternalUserCredit> getCredits(String externalUserId, String serviceName);
}
