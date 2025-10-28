package org.lite.gateway.repository;

import org.lite.gateway.entity.LinqLlmModel;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqLlmModelRepository extends ReactiveMongoRepository<LinqLlmModel, String> {
    Flux<LinqLlmModel> findByModelCategoryAndTeamId(String modelCategory, String teamId);
    Mono<LinqLlmModel> findByModelCategoryAndModelNameAndTeamId(String modelCategory, String modelName, String teamId);
    
    @Query("{ 'teamId': ?0 }")
    Flux<LinqLlmModel> findByTeamId(String teamId, Sort sort);
    
    default Flux<LinqLlmModel> findByTeamId(String teamId) {
        return findByTeamId(teamId, Sort.by(Sort.Direction.ASC, "provider", "modelCategory", "modelName"));
    }
}

