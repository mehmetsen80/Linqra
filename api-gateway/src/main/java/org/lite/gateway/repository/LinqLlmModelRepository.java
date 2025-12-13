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
        // Sort by priority first (ascending - lower number = higher priority), then by
        // provider/category/name
        return findByTeamId(teamId, Sort.by(Sort.Direction.ASC, "priority", "provider", "modelCategory", "modelName"));
    }

    /**
     * Find LinqLlmModel by modelName and teamId (optionally filter by provider)
     * Used to look up modelCategory when it's missing from AI Assistant
     * defaultModel
     */
    @Query("{ 'modelName': ?0, 'teamId': ?1 }")
    Flux<LinqLlmModel> findByModelNameAndTeamId(String modelName, String teamId);

    /**
     * Find LinqLlmModel by modelName, provider, and teamId
     * More specific lookup when provider is known
     */
    @Query("{ 'modelName': ?0, 'provider': ?1, 'teamId': ?2 }")
    Mono<LinqLlmModel> findByModelNameAndProviderAndTeamId(String modelName, String provider, String teamId);

    /**
     * Find the maximum priority value for a team
     * Returns the model with the highest priority number (lowest priority)
     */
    Mono<LinqLlmModel> findFirstByTeamIdAndPriorityNotNullOrderByPriorityDesc(String teamId);
}
