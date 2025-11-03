package org.lite.gateway.repository;

import org.lite.gateway.entity.LinqWorkflowVersion;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqWorkflowVersionRepository extends ReactiveMongoRepository<LinqWorkflowVersion, String> {
    Flux<LinqWorkflowVersion> findByWorkflowIdAndTeamIdOrderByVersionDesc(String workflowId, String teamId);
    Mono<LinqWorkflowVersion> findByWorkflowIdAndTeamIdAndVersion(String workflowId, String teamId, Integer version);
    Mono<LinqWorkflowVersion> findFirstByWorkflowIdAndTeamIdOrderByVersionDesc(String workflowId, String teamId);
    Mono<LinqWorkflowVersion> findByIdAndWorkflowId(String id, String workflowId);
    Mono<LinqWorkflowVersion> findByIdAndWorkflowIdAndTeamId(String id, String workflowId, String teamId);
} 