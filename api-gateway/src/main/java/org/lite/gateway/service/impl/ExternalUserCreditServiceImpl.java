package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ExternalUserCredit;
import org.lite.gateway.repository.ExternalUserCreditRepository;
import org.lite.gateway.service.ExternalUserCreditService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ExternalUserCreditServiceImpl implements ExternalUserCreditService {

    private static final int DEFAULT_CREDITS = 100;

    private final ExternalUserCreditRepository externalUserCreditRepository;

    @Override
    public Mono<ExternalUserCredit> getOrCreateCredit(String externalUserId, String serviceName, String machineId) {
        if (externalUserId == null || externalUserId.isEmpty()) {
            return Mono.error(new IllegalArgumentException("externalUserId cannot be null or empty"));
        }

        return externalUserCreditRepository.findByExternalUserIdAndServiceName(externalUserId, serviceName)
                .switchIfEmpty(Mono.defer(() -> {
                    ExternalUserCredit newCredit = ExternalUserCredit.builder()
                            .externalUserId(externalUserId)
                            .serviceName(serviceName)
                            .machineId(machineId)
                            .aiCredits(DEFAULT_CREDITS)
                            .maxAiCredits(DEFAULT_CREDITS)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    log.info("Creating new external user credit profile for user {} with machineId {}", externalUserId, machineId);
                    return externalUserCreditRepository.save(newCredit);
                }));
    }

    @Override
    public Mono<Boolean> hasSufficientCredits(String externalUserId, String serviceName, String machineId) {
        return getOrCreateCredit(externalUserId, serviceName, machineId)
                .map(credit -> credit.getAiCredits() != null && credit.getAiCredits() > 0);
    }

    @Override
    public Mono<Void> consumeCredit(String externalUserId, String serviceName, int amount) {
        if (externalUserId == null) return Mono.empty();

        return externalUserCreditRepository.findByExternalUserIdAndServiceName(externalUserId, serviceName)
                .flatMap(credit -> {
                    int currentCredits = credit.getAiCredits() != null ? credit.getAiCredits() : 0;
                    credit.setAiCredits(Math.max(0, currentCredits - amount));
                    credit.setUpdatedAt(LocalDateTime.now());
                    log.info("Consuming {} credits for external user {}. Remaining: {}", amount, externalUserId, credit.getAiCredits());
                    return externalUserCreditRepository.save(credit);
                })
                .then();
    }

    @Override
    public Mono<ExternalUserCredit> getCredits(String externalUserId, String serviceName) {
        if (externalUserId == null || externalUserId.isEmpty()) {
            return Mono.empty();
        }
        return externalUserCreditRepository.findByExternalUserIdAndServiceName(externalUserId, serviceName);
    }
}
