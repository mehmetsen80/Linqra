package org.lite.gateway.repository;

import org.lite.gateway.entity.LinqWorkflowExecution;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface LinqWorkflowExecutionRepository extends ReactiveMongoRepository<LinqWorkflowExecution, String> {
    Flux<LinqWorkflowExecution> findByWorkflowId(String workflowId, Sort sort);
    Flux<LinqWorkflowExecution> findByTeamId(String teamId, Sort sort);
    Mono<LinqWorkflowExecution> findByIdAndTeamId(String id, String teamId);
    Flux<LinqWorkflowExecution> findByWorkflowIdAndTeamId(String workflowId, String teamId, Sort sort);
}
