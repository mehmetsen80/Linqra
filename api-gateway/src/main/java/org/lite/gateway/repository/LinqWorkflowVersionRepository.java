package org.lite.gateway.repository;

import org.lite.gateway.entity.LinqWorkflowVersion;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface LinqWorkflowVersionRepository extends ReactiveMongoRepository<LinqWorkflowVersion, String> {
    Flux<LinqWorkflowVersion> findByWorkflowIdAndTeamOrderByVersionDesc(String workflowId, String team);
    Mono<LinqWorkflowVersion> findByWorkflowIdAndTeamAndVersion(String workflowId, String team, Integer version);
    Mono<LinqWorkflowVersion> findFirstByWorkflowIdAndTeamOrderByVersionDesc(String workflowId, String team);
} 