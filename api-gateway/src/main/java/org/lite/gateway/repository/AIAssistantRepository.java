package org.lite.gateway.repository;

import org.lite.gateway.entity.AIAssistant;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface AIAssistantRepository extends ReactiveMongoRepository<AIAssistant, String> {
    
    // Basic CRUD operations are inherited from ReactiveMongoRepository
    
    // Find assistants by team
    Flux<AIAssistant> findByTeamId(String teamId);
    
    // Find assistants by team and status
    Flux<AIAssistant> findByTeamIdAndStatus(String teamId, String status);
    
    // Find assistant by team and name
    Mono<AIAssistant> findByTeamIdAndName(String teamId, String name);
    
    // Find assistants by public API key
    @Query("{'accessControl.publicApiKey': ?0}")
    Mono<AIAssistant> findByPublicApiKey(String publicApiKey);
    
    // Find public assistants
    @Query("{'accessControl.type': 'PUBLIC', 'status': 'ACTIVE'}")
    Flux<AIAssistant> findPublicAssistants();
}

