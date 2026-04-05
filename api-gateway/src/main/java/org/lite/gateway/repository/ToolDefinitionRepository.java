package org.lite.gateway.repository;

import org.lite.gateway.entity.ToolDefinition;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ToolDefinitionRepository extends ReactiveMongoRepository<ToolDefinition, String> {
    Mono<ToolDefinition> findByToolId(String toolId);
    Flux<ToolDefinition> findByTeamId(String teamId);
    Flux<ToolDefinition> findByVisibility(String visibility);
}
