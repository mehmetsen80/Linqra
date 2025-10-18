package org.lite.gateway.repository;

import org.lite.gateway.entity.LlmModel;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LlmModelRepository extends ReactiveMongoRepository<LlmModel, String> {
    Mono<LlmModel> findByModelName(String modelName);
    Flux<LlmModel> findByProvider(String provider);
    
    // Sorted queries
    @Query(value = "{}", sort = "{ 'provider': 1, 'modelName': 1 }")
    Flux<LlmModel> findAllSorted();
    
    @Query(value = "{ 'active': ?0 }", sort = "{ 'provider': 1, 'modelName': 1 }")
    Flux<LlmModel> findByActiveSorted(boolean active);
    
    // Original unsorted methods (kept for backward compatibility)
    Flux<LlmModel> findByActive(boolean active);
    Flux<LlmModel> findByProviderAndActive(String provider, boolean active);
}

