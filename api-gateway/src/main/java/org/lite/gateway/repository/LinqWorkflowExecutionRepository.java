package org.lite.gateway.repository;

import org.lite.gateway.entity.LinqWorkflowExecution;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import org.springframework.data.mongodb.repository.Query;
import java.util.Collection;
import java.time.LocalDateTime;

@Repository
public interface LinqWorkflowExecutionRepository extends ReactiveMongoRepository<LinqWorkflowExecution, String> {
    Flux<LinqWorkflowExecution> findByWorkflowId(String workflowId, Sort sort);

    Flux<LinqWorkflowExecution> findByTeamId(String teamId, Sort sort);

    Flux<LinqWorkflowExecution> findByTeamIdIn(java.util.Collection<String> teamIds, Sort sort);

    Mono<LinqWorkflowExecution> findByIdAndTeamId(String id, String teamId);

    @Query("{ '_id': ?0, 'request.query.params.institution': ?1 }")
    Mono<LinqWorkflowExecution> findByIdAndRequestQueryParamsInstitution(String id, String institution);

    Flux<LinqWorkflowExecution> findByWorkflowIdAndTeamId(String workflowId, String teamId, Sort sort);

    Mono<LinqWorkflowExecution> findByAgentExecutionId(String agentExecutionId);
    Flux<LinqWorkflowExecution> findByAgentExecutionIdIn(Collection<String> agentExecutionIds, Sort sort);

    Flux<LinqWorkflowExecution> findByAgentTaskId(String agentTaskId, Sort sort);


    Flux<LinqWorkflowExecution> findByTeamIdAndAgentTaskId(String teamId, String agentTaskId, Sort sort);

    Flux<LinqWorkflowExecution> findByTeamIdInAndAgentTaskId(java.util.Collection<String> teamIds, String agentTaskId,
            Sort sort);

    Flux<LinqWorkflowExecution> findByTeamIdAndExecutedAtBetween(String teamId, LocalDateTime from, LocalDateTime to);

    Mono<Long> countByTeamId(String teamId);

    Mono<Long> countByTeamIdIn(java.util.Collection<String> teamIds);

    Mono<Long> countByTeamIdAndAgentTaskId(String teamId, String agentTaskId);

    Mono<Long> countByTeamIdInAndAgentTaskId(java.util.Collection<String> teamIds, String agentTaskId);

    Mono<Long> countByAgentTaskId(String agentTaskId);

    // Institutional filtering
    @Query("{ 'teamId': { $in: ?0 }, 'request.query.params.institution': ?1 }")
    Flux<LinqWorkflowExecution> findByTeamIdInAndRequestQueryParamsInstitution(
            Collection<String> teamIds, String institution, Sort sort);

    @Query("{ 'teamId': { $in: ?0 }, 'agentTaskId': ?1, 'request.query.params.institution': ?2 }")
    Flux<LinqWorkflowExecution> findByTeamIdInAndAgentTaskIdAndRequestQueryParamsInstitution(
            Collection<String> teamIds, String agentTaskId, String institution, Sort sort);

    @Query("{ 'agentTaskId': ?0, 'request.query.params.institution': ?1 }")
    Flux<LinqWorkflowExecution> findByAgentTaskIdAndRequestQueryParamsInstitution(
            String agentTaskId, String institution, Sort sort);

    @Query("{ 'request.query.params.institution': ?0 }")
    Flux<LinqWorkflowExecution> findByRequestQueryParamsInstitution(
            String institution, Sort sort);

    @Query(value = "{ 'teamId': { $in: ?0 }, 'request.query.params.institution': ?1 }", count = true)
    Mono<Long> countByTeamIdInAndRequestQueryParamsInstitution(
            Collection<String> teamIds, String institution);

    @Query(value = "{ 'teamId': { $in: ?0 }, 'agentTaskId': ?1, 'request.query.params.institution': ?2 }", count = true)
    Mono<Long> countByTeamIdInAndAgentTaskIdAndRequestQueryParamsInstitution(
            Collection<String> teamIds, String agentTaskId, String institution);

    @Query(value = "{ 'agentTaskId': ?0, 'request.query.params.institution': ?1 }", count = true)
    Mono<Long> countByAgentTaskIdAndRequestQueryParamsInstitution(
            String agentTaskId, String institution);

    @Query(value = "{ 'request.query.params.institution': ?0 }", count = true)
    Mono<Long> countByRequestQueryParamsInstitution(String institution);
}
