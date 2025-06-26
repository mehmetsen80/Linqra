package org.lite.gateway.repository;

import org.lite.gateway.entity.LinqWorkflow;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LinqWorkflowRepository extends ReactiveMongoRepository<LinqWorkflow, String> {
    Flux<LinqWorkflow> findByTeamId(String teamId);
    Flux<LinqWorkflow> findByIsPublicTrue();
    Mono<LinqWorkflow> findByIdAndTeamId(String id, String teamId);
    Flux<LinqWorkflow> findByNameContainingIgnoreCaseOrDescriptionContainingIgnoreCase(
        String name, String description);
    Flux<LinqWorkflow> findByTeamIdOrderByCreatedAtDesc(String teamId);
}
