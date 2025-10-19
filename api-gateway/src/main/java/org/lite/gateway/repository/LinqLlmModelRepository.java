package org.lite.gateway.repository;

import org.lite.gateway.entity.LinqLlmModel;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqLlmModelRepository extends ReactiveMongoRepository<LinqLlmModel, String> {
    Mono<LinqLlmModel> findByTargetAndTeamId(String target, String teamId);
    Mono<LinqLlmModel> findByTargetAndModelTypeAndTeamId(String target, String modelType, String teamId);
    Flux<LinqLlmModel> findByTeamId(String teamId);
}

