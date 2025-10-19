package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import org.lite.gateway.entity.LinqLlmModel;
import org.lite.gateway.service.LinqLlmModelService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/linq-llm-models")
@RequiredArgsConstructor
public class LinqLlmModelController {

    private final LinqLlmModelService linqLlmModelService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<LinqLlmModel> createLinqLlmModel(@RequestBody LinqLlmModel linqLlmModel) {
        return linqLlmModelService.saveLinqLlmModel(linqLlmModel);
    }

    @GetMapping("/team/{teamId}")
    public Flux<LinqLlmModel> getTeamConfiguration(@PathVariable String teamId) {
        return linqLlmModelService.findByTeamId(teamId);
    }

    @GetMapping("/team/{teamId}/target/{target}")
    public Mono<LinqLlmModel> getLlmModelByTarget(@PathVariable String teamId, @PathVariable String target) {
        return linqLlmModelService.findByTargetAndTeam(target, teamId);
    }
    
    @GetMapping("/team/{teamId}/target/{target}/model/{modelType}")
    public Mono<LinqLlmModel> getLlmModelByTargetAndType(
            @PathVariable String teamId, 
            @PathVariable String target,
            @PathVariable String modelType) {
        return linqLlmModelService.findByTargetAndModelTypeAndTeamId(target, modelType, teamId);
    }
}

