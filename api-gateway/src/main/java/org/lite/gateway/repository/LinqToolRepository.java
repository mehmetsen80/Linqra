package org.lite.gateway.repository;

import org.lite.gateway.entity.LinqTool;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqToolRepository extends ReactiveMongoRepository<LinqTool, String> {
    Mono<LinqTool> findByTargetAndTeamId(String target, String teamId);
    Flux<LinqTool> findByTeamId(String teamId);
}
