package org.lite.gateway.repository;

import org.lite.gateway.entity.LlmModel;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LlmModelRepository extends ReactiveMongoRepository<LlmModel, String> {
    Mono<LlmModel> findByModelName(String modelName);
    Flux<LlmModel> findByProvider(String provider);
    Flux<LlmModel> findByActive(boolean active);
    Flux<LlmModel> findByProviderAndActive(String provider, boolean active);
}

