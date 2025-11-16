package org.lite.gateway.service;

import org.lite.gateway.entity.Conversation;
import org.lite.gateway.entity.ConversationMessage;
import org.springframework.data.domain.Pageable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ConversationService {
    
    // Create a new conversation
    Mono<Conversation> createConversation(Conversation conversation);
    
    // Get conversation by ID
    Mono<Conversation> getConversationById(String conversationId);
    
    // Get conversation by ID and team ID
    Mono<Conversation> getConversationByIdAndTeamId(String conversationId, String teamId);
    
    // Update conversation
    Mono<Conversation> updateConversation(Conversation conversation);
    
    // Delete conversation
    Mono<Void> deleteConversation(String conversationId);
    
    // List conversations by assistant
    Flux<Conversation> listConversationsByAssistant(String assistantId, Pageable pageable);
    
    // List conversations by assistant and user (username)
    Flux<Conversation> listConversationsByAssistantAndUser(String assistantId, String username, Pageable pageable);
    
    // List conversations by team
    Flux<Conversation> listConversationsByTeam(String teamId, Pageable pageable);
    
    // List conversations by user (username)
    Flux<Conversation> listConversationsByUser(String username, Pageable pageable);
    
    // Add message to conversation
    Mono<ConversationMessage> addMessage(ConversationMessage message);
    
    // Get messages by conversation ID
    Flux<ConversationMessage> getMessagesByConversationId(String conversationId, Pageable pageable);
    
    // Get messages by conversation ID (all)
    Flux<ConversationMessage> getMessagesByConversationId(String conversationId);
    
    // Get the last N messages for context
    Flux<ConversationMessage> getRecentMessages(String conversationId, int limit);
}

