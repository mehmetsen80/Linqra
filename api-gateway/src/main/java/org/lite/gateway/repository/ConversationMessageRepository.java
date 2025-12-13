package org.lite.gateway.repository;

import org.lite.gateway.entity.ConversationMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Repository
public interface ConversationMessageRepository extends ReactiveMongoRepository<ConversationMessage, String> {
    
    // Find messages by conversation ID, ordered by sequence number
    Flux<ConversationMessage> findByConversationIdOrderBySequenceNumberAsc(String conversationId);
    
    // Find messages by conversation ID with pagination
    Flux<ConversationMessage> findByConversationIdOrderBySequenceNumberAsc(String conversationId, Pageable pageable);
    
    // Find messages by conversation ID and role
    Flux<ConversationMessage> findByConversationIdAndRoleOrderBySequenceNumberAsc(String conversationId, String role);
    
    // Find the last N messages by conversation ID
    Flux<ConversationMessage> findTopByConversationIdOrderBySequenceNumberDesc(String conversationId, Pageable pageable);
    
    // Find the last message by conversation ID
    Mono<ConversationMessage> findFirstByConversationIdOrderBySequenceNumberDesc(String conversationId);
    
    // Count messages by conversation ID
    Mono<Long> countByConversationId(String conversationId);
    
    // Find the maximum sequence number for a conversation
    default Mono<Integer> getMaxSequenceNumber(String conversationId) {
        return findFirstByConversationIdOrderBySequenceNumberDesc(conversationId)
                .map(ConversationMessage::getSequenceNumber)
                .defaultIfEmpty(0);
    }
}

