package org.lite.gateway.repository;

import org.lite.gateway.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ConversationRepository extends ReactiveMongoRepository<Conversation, String> {
    
    // Find conversations by assistant
    Flux<Conversation> findByAssistantIdOrderByStartedAtDesc(String assistantId);
    
    // Find conversations by assistant with pagination
    Flux<Conversation> findByAssistantIdOrderByStartedAtDesc(String assistantId, Pageable pageable);
    
    // Find conversations by team
    Flux<Conversation> findByTeamIdOrderByStartedAtDesc(String teamId);
    
    // Find conversations by team with pagination
    Flux<Conversation> findByTeamIdOrderByStartedAtDesc(String teamId, Pageable pageable);
    
    // Find conversations by user (username)
    Flux<Conversation> findByUsernameOrderByStartedAtDesc(String username);
    
    // Find conversations by user with pagination
    Flux<Conversation> findByUsernameOrderByStartedAtDesc(String username, Pageable pageable);
    
    // Find conversations by assistant and user (username)
    Flux<Conversation> findByAssistantIdAndUsernameOrderByStartedAtDesc(String assistantId, String username);
    
    // Find conversations by assistant and user with pagination
    Flux<Conversation> findByAssistantIdAndUsernameOrderByStartedAtDesc(String assistantId, String username, Pageable pageable);
    
    // Find active conversations by assistant
    Flux<Conversation> findByAssistantIdAndStatusOrderByStartedAtDesc(String assistantId, String status);
    
    // Find conversation by ID and assistant ID
    Mono<Conversation> findByIdAndAssistantId(String id, String assistantId);
    
    // Find conversation by ID and team ID
    Mono<Conversation> findByIdAndTeamId(String id, String teamId);
    
    // Find conversation by ID and username
    Mono<Conversation> findByIdAndUsername(String id, String username);
    
    // Count conversations by assistant
    Mono<Long> countByAssistantId(String assistantId);
    
    // Count active conversations by assistant
    Mono<Long> countByAssistantIdAndStatus(String assistantId, String status);
}

