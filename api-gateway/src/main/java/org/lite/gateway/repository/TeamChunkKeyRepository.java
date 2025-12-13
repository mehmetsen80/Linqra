package org.lite.gateway.repository;

import org.lite.gateway.entity.TeamChunkKey;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Mono;

public interface TeamChunkKeyRepository extends ReactiveMongoRepository<TeamChunkKey, String> {
    Mono<TeamChunkKey> findByTeamIdAndVersion(String teamId, String version);

    Mono<TeamChunkKey> findByTeamIdAndIsActiveTrue(String teamId);

    reactor.core.publisher.Flux<TeamChunkKey> findAllByTeamId(String teamId);
}
