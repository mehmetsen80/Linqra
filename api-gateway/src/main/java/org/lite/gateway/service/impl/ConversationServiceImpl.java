package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.Conversation;
import org.lite.gateway.entity.ConversationMessage;
import org.lite.gateway.repository.ConversationMessageRepository;
import org.lite.gateway.repository.ConversationRepository;
import org.lite.gateway.service.ConversationService;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMessageRepository messageRepository;

    @Override
    public Mono<Conversation> createConversation(Conversation conversation) {
        log.info("Creating conversation for assistant: {}", conversation.getAssistantId());
        if (conversation.getId() == null) {
            conversation.setId(UUID.randomUUID().toString());
        }
        if (conversation.getStartedAt() == null) {
            conversation.setStartedAt(LocalDateTime.now());
        }
        if (conversation.getStatus() == null) {
            conversation.setStatus("ACTIVE");
        }
        if (conversation.getMessageCount() == null) {
            conversation.setMessageCount(0);
        }
        return conversationRepository.save(conversation)
                .doOnSuccess(saved -> log.info("Conversation created with ID: {}", saved.getId()))
                .doOnError(error -> log.error("Error creating conversation: {}", error.getMessage()));
    }

    @Override
    public Mono<Conversation> getConversationById(String conversationId) {
        return conversationRepository.findById(conversationId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversation not found: " + conversationId)));
    }

    @Override
    public Mono<Conversation> getConversationByIdAndTeamId(String conversationId, String teamId) {
        return conversationRepository.findByIdAndTeamId(conversationId, teamId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversation not found: " + conversationId)));
    }

    @Override
    public Mono<Conversation> updateConversation(Conversation conversation) {
        log.info("Updating conversation: {}", conversation.getId());
        return conversationRepository.save(conversation)
                .doOnSuccess(saved -> log.info("Conversation updated: {}", saved.getId()))
                .doOnError(error -> log.error("Error updating conversation: {}", error.getMessage()));
    }

    @Override
    public Mono<Void> deleteConversation(String conversationId) {
        log.info("Deleting conversation: {}", conversationId);
        return conversationRepository.deleteById(conversationId)
                .doOnSuccess(v -> log.info("Conversation deleted: {}", conversationId))
                .doOnError(error -> log.error("Error deleting conversation: {}", error.getMessage()));
    }

    @Override
    public Flux<Conversation> listConversationsByAssistant(String assistantId, Pageable pageable) {
        return conversationRepository.findByAssistantIdOrderByStartedAtDesc(assistantId, pageable);
    }

    @Override
    public Flux<Conversation> listConversationsByAssistantAndUser(String assistantId, String username, Pageable pageable) {
        return conversationRepository.findByAssistantIdAndUsernameOrderByStartedAtDesc(assistantId, username, pageable);
    }

    @Override
    public Flux<Conversation> listConversationsByTeam(String teamId, Pageable pageable) {
        return conversationRepository.findByTeamIdOrderByStartedAtDesc(teamId, pageable);
    }

    @Override
    public Flux<Conversation> listConversationsByUser(String username, Pageable pageable) {
        return conversationRepository.findByUsernameOrderByStartedAtDesc(username, pageable);
    }

    @Override
    public Mono<ConversationMessage> addMessage(ConversationMessage message) {
        log.info("Adding message to conversation: {}", message.getConversationId());
        if (message.getId() == null) {
            message.setId(UUID.randomUUID().toString());
        }
        if (message.getTimestamp() == null) {
            message.setTimestamp(LocalDateTime.now());
        }
        
        // Get the next sequence number
        return messageRepository.findFirstByConversationIdOrderBySequenceNumberDesc(message.getConversationId())
                .map(lastMessage -> lastMessage.getSequenceNumber() + 1)
                .defaultIfEmpty(1)
                .flatMap(sequenceNumber -> {
                    message.setSequenceNumber(sequenceNumber);
                    
                    // Save message
                    return messageRepository.save(message)
                            .flatMap(savedMessage -> {
                                // Update conversation metadata
                                return conversationRepository.findById(message.getConversationId())
                                        .flatMap(conversation -> {
                                            conversation.setLastMessageAt(savedMessage.getTimestamp());
                                            conversation.setMessageCount((conversation.getMessageCount() != null ? conversation.getMessageCount() : 0) + 1);
                                            return conversationRepository.save(conversation)
                                                    .thenReturn(savedMessage);
                                        });
                            });
                })
                .doOnSuccess(saved -> log.info("Message added to conversation: {}", message.getConversationId()))
                .doOnError(error -> log.error("Error adding message: {}", error.getMessage()));
    }

    @Override
    public Flux<ConversationMessage> getMessagesByConversationId(String conversationId, Pageable pageable) {
        return messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId, pageable);
    }

    @Override
    public Flux<ConversationMessage> getMessagesByConversationId(String conversationId) {
        return messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId);
    }

    @Override
    public Flux<ConversationMessage> getRecentMessages(String conversationId, int limit) {
        return messageRepository.findByConversationIdOrderBySequenceNumberAsc(conversationId)
                .collectList()
                .flatMapMany(messages -> {
                    // Reverse to get most recent first, then take limit
                    messages.sort((a, b) -> Integer.compare(b.getSequenceNumber(), a.getSequenceNumber()));
                    List<ConversationMessage> recent = messages.stream()
                            .limit(limit)
                            .sorted((a, b) -> Integer.compare(a.getSequenceNumber(), b.getSequenceNumber())) // Sort back ascending
                            .collect(java.util.stream.Collectors.toList());
                    return Flux.fromIterable(recent);
                });
    }
}

