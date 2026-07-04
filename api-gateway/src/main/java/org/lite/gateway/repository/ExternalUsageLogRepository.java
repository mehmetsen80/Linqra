package org.lite.gateway.repository;

import org.lite.gateway.entity.ExternalUsageLog;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import java.time.LocalDateTime;

@Repository
public interface ExternalUsageLogRepository extends ReactiveMongoRepository<ExternalUsageLog, String> {
    Flux<ExternalUsageLog> findByExternalUserId(String externalUserId);
    Flux<ExternalUsageLog> findByServiceName(String serviceName);
    Flux<ExternalUsageLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
