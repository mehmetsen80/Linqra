package org.lite.gateway.repository;

import org.lite.gateway.entity.LlmPricingSnapshot;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LlmPricingSnapshotRepository extends ReactiveMongoRepository<LlmPricingSnapshot, String> {
    Mono<LlmPricingSnapshot> findByYearMonthAndModel(String yearMonth, String model);
    Flux<LlmPricingSnapshot> findByYearMonth(String yearMonth);
    Flux<LlmPricingSnapshot> findByModel(String model);
}

