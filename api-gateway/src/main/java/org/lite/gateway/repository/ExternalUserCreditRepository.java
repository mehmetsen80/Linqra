package org.lite.gateway.repository;

import org.lite.gateway.entity.ExternalUserCredit;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ExternalUserCreditRepository extends ReactiveMongoRepository<ExternalUserCredit, String> {
    Mono<ExternalUserCredit> findByExternalUserIdAndServiceName(String externalUserId, String serviceName);
}
