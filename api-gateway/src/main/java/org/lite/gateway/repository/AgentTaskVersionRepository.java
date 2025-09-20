package org.lite.gateway.repository;

import org.lite.gateway.entity.AgentTaskVersion;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface AgentTaskVersionRepository extends ReactiveMongoRepository<AgentTaskVersion, String> {
    
    /**
     * Find all versions for a specific task, ordered by version number descending
     */
    @Query("{ 'taskId': ?0 }")
    Flux<AgentTaskVersion> findByTaskIdOrderByVersionDesc(String taskId);
    
    /**
     * Find a specific version for a task
     */
    @Query("{ 'taskId': ?0, 'version': ?1 }")
    Mono<AgentTaskVersion> findByTaskIdAndVersion(String taskId, Integer version);
    
    /**
     * Find the latest version for a task (if duplicates exist, get the most recent by createdAt)
     */
    @Query(value = "{ 'taskId': ?0 }", sort = "{ 'version': -1, 'createdAt': -1 }")
    Flux<AgentTaskVersion> findLatestVersionsByTaskId(String taskId);
    
    /**
     * Find all versions for tasks belonging to a specific agent
     */
    @Query("{ 'agentId': ?0 }")
    Flux<AgentTaskVersion> findByAgentId(String agentId);
    
    /**
     * Find all versions for tasks belonging to a specific team
     */
    @Query("{ 'teamId': ?0 }")
    Flux<AgentTaskVersion> findByTeamId(String teamId);
    
    /**
     * Count total versions for a specific task
     */
    @Query(value = "{ 'taskId': ?0 }", count = true)
    Mono<Long> countByTaskId(String taskId);
    
    /**
     * Delete all versions for a specific task
     */
    @Query(value = "{ 'taskId': ?0 }", delete = true)
    Mono<Void> deleteByTaskId(String taskId);
    
    /**
     * Find versions created by a specific user
     */
    @Query("{ 'createdBy': ?0 }")
    Flux<AgentTaskVersion> findByCreatedBy(String createdBy);
} 