package org.lite.gateway.repository;

import org.lite.gateway.entity.Agent;
import org.lite.gateway.enums.AgentIntent;
import org.lite.gateway.enums.AgentCapability;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Repository
public interface AgentRepository extends ReactiveMongoRepository<Agent, String> {

    // Basic CRUD operations are inherited from ReactiveMongoRepository

    // Find agent by ID and team
    Mono<Agent> findByIdAndTeamId(String id, String teamId);

    // Find agents by team
    Flux<Agent> findByTeamId(String teamId);

    // Find enabled agents
    Flux<Agent> findByEnabledTrue();

    // Find enabled agents by team
    Flux<Agent> findByTeamIdAndEnabledTrue(String teamId);

    // Find agents by capability
    @Query("{'capabilities': ?0}")
    Flux<Agent> findByCapability(AgentCapability capability);

    // Find agents by team and capability
    @Query("{'teamId': ?0, 'capabilities': ?1}")
    Flux<Agent> findByTeamIdAndCapability(String teamId, AgentCapability capability);

    // Find agents by supported intent
    @Query("{'supportedIntents': ?0}")
    Flux<Agent> findBySupportedIntent(AgentIntent intent);

    // Find agents by team and supported intent
    @Query("{'teamId': ?0, 'supportedIntents': ?1}")
    Flux<Agent> findByTeamIdAndSupportedIntent(String teamId, AgentIntent intent);

    // Find agents that have specific task
    @Query("{'taskIds': ?0}")
    Flux<Agent> findByTaskId(String taskId);

    // Find agents by team that have specific task
    @Query("{'teamId': ?0, 'taskIds': ?1}")
    Flux<Agent> findByTeamIdAndTaskId(String teamId, String taskId);

    // Find agents by name (case-insensitive)
    @Query("{'name': {$regex: ?0, $options: 'i'}}")
    Flux<Agent> findByNameContainingIgnoreCase(String name);

    // Find agents by team and name (case-insensitive)
    @Query("{'teamId': ?0, 'name': {$regex: ?1, $options: 'i'}}")
    Flux<Agent> findByTeamIdAndNameContainingIgnoreCase(String teamId, String name);

    // Find agents by description (case-insensitive)
    @Query("{'description': {$regex: ?0, $options: 'i'}}")
    Flux<Agent> findByDescriptionContainingIgnoreCase(String description);

    // Find agents by team and description (case-insensitive)
    @Query("{'teamId': ?0, 'description': {$regex: ?1, $options: 'i'}}")
    Flux<Agent> findByTeamIdAndDescriptionContainingIgnoreCase(String teamId, String description);

    // Find agents created by specific user
    Flux<Agent> findByCreatedBy(String createdBy);

    // Find agents by team and created by
    Flux<Agent> findByTeamIdAndCreatedBy(String teamId, String createdBy);

    // Find agents created after specific date
    Flux<Agent> findByCreatedAtAfter(LocalDateTime date);

    // Find agents by team created after specific date
    Flux<Agent> findByTeamIdAndCreatedAtAfter(String teamId, LocalDateTime date);

    // Find agents updated after specific date
    Flux<Agent> findByUpdatedAtAfter(LocalDateTime date);

    // Find agents by team updated after specific date
    Flux<Agent> findByTeamIdAndUpdatedAtAfter(String teamId, LocalDateTime date);

    // Count agents by team
    Mono<Long> countByTeamId(String teamId);

    // Count enabled agents by team
    Mono<Long> countByTeamIdAndEnabledTrue(String teamId);

    // Check if agent exists by name and team (for validation)
    Mono<Boolean> existsByNameAndTeamId(String name, String teamId);

    // Check if agent exists by team
    Mono<Boolean> existsByTeamId(String teamId);

}