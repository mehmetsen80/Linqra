package org.lite.gateway.repository;

import org.lite.gateway.entity.LinqWorkflowExecution;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LinqWorkflowExecutionRepository extends ReactiveMongoRepository<LinqWorkflowExecution, String> {
    Flux<LinqWorkflowExecution> findByWorkflowId(String workflowId);
    Flux<LinqWorkflowExecution> findByTeam(String teamId);
    Mono<LinqWorkflowExecution> findByIdAndTeam(String id, String teamId);
    Flux<LinqWorkflowExecution> findByWorkflowIdAndTeam(String workflowId, String team);
}
