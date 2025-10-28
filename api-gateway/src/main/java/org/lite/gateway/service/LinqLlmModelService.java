package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqLlmModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqLlmModelService {
    Mono<LinqLlmModel> saveLinqLlmModel(LinqLlmModel linqLlmModel);
    Mono<Void> deleteLinqLlmModel(String id);
    Mono<LinqResponse> executeLlmRequest(LinqRequest request, LinqLlmModel llmModel);
    Mono<LinqLlmModel> findById(String id);
    Flux<LinqLlmModel> findByTeamId(String teamId);
    Flux<LinqLlmModel> findByModelCategoryAndTeamId(String modelCategory, String teamId);
    Flux<LinqLlmModel> findByModelCategoriesAndTeamId(java.util.List<String> targets, String teamId);
    Mono<LinqLlmModel> findByModelCategoryAndModelNameAndTeamId(String target, String modelName, String teamId);
}

