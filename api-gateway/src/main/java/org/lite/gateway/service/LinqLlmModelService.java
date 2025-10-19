package org.lite.gateway.service;

import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqLlmModel;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqLlmModelService {
    Mono<LinqLlmModel> saveLinqLlmModel(LinqLlmModel linqLlmModel);
    Mono<LinqResponse> executeLlmRequest(LinqRequest request, LinqLlmModel llmModel);
    Flux<LinqLlmModel> findByTeamId(String teamId);
    Mono<LinqLlmModel> findByTargetAndTeam(String target, String teamId);
    Mono<LinqLlmModel> findByTargetAndModelTypeAndTeamId(String target, String modelType, String teamId);
}

