package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.Conversation;
import org.lite.gateway.entity.ConversationMessage;
import org.lite.gateway.service.ChatExecutionService;
import org.lite.gateway.service.ConversationService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.lite.gateway.service.TeamService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
@Slf4j
public class ConversationController {

        private final ConversationService conversationService;
        private final ChatExecutionService chatExecutionService;
        private final TeamContextService teamContextService;
        private final UserContextService userContextService;
        private final UserService userService;
        private final TeamService teamService;

        // Helper for authorization check
        private Mono<Boolean> checkAccessAuthorization(String conversationId, String username, String teamId,
                        String userId) {
                return conversationService.getConversationById(conversationId)
                                .flatMap(conversation -> {
                                        // Check if user owns the conversation (by username)
                                        if (conversation.getUsername() != null
                                                        && conversation.getUsername().equals(username)) {
                                                return Mono.just(true);
                                        }
                                        // Check if user has team access (by user ID)
                                        if (conversation.getTeamId().equals(teamId)) {
                                                return teamService.hasRole(teamId, userId, "ADMIN")
                                                                .or(teamService.hasRole(teamId, userId, "MEMBER"));
                                        }
                                        return Mono.just(false);
                                })
                                .defaultIfEmpty(false);
        }

        // Start a new conversation
        @PostMapping("/assistants/{assistantId}")
        public Mono<ResponseEntity<Map<String, Object>>> startConversation(
                        @PathVariable String assistantId,
                        @RequestBody Map<String, Object> requestBody,
                        ServerWebExchange exchange) {
                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .flatMap(teamId -> {
                                                        String message = (String) requestBody.getOrDefault("message",
                                                                        "");

                                                        // Build LinqRequest for chat
                                                        LinqRequest linqRequest = new LinqRequest();
                                                        LinqRequest.Link link = new LinqRequest.Link();
                                                        link.setTarget("assistant");
                                                        link.setAction("chat");
                                                        linqRequest.setLink(link);

                                                        LinqRequest.Query query = new LinqRequest.Query();
                                                        query.setIntent("chat");

                                                        Map<String, Object> params = new HashMap<>();
                                                        params.put("teamId", teamId);
                                                        params.put("username", user.getUsername());
                                                        query.setParams(params);

                                                        LinqRequest.Query.ChatConversation chat = new LinqRequest.Query.ChatConversation();
                                                        chat.setAssistantId(assistantId);
                                                        chat.setMessage(message);
                                                        chat.setConversationId(null); // New conversation
                                                        chat.setHistory(null); // No history for new conversation
                                                        chat.setContext((Map<String, Object>) requestBody
                                                                        .getOrDefault("context", new HashMap<>()));
                                                        query.setChat(chat);

                                                        linqRequest.setQuery(query);
                                                        linqRequest.setExecutedBy(user.getUsername());

                                                        return chatExecutionService.executeChat(linqRequest)
                                                                        .map(response -> {
                                                                                Map<String, Object> result = new HashMap<>();
                                                                                if (response.getChatResult() != null) {
                                                                                        result.put("conversationId",
                                                                                                        response.getChatResult()
                                                                                                                        .getConversationId());
                                                                                        result.put("message", response
                                                                                                        .getChatResult()
                                                                                                        .getMessage());
                                                                                        result.put("intent", response
                                                                                                        .getChatResult()
                                                                                                        .getIntent());
                                                                                        result.put("modelCategory",
                                                                                                        response.getChatResult()
                                                                                                                        .getModelCategory());
                                                                                        result.put("modelName", response
                                                                                                        .getChatResult()
                                                                                                        .getModelName());
                                                                                        result.put("executedTasks",
                                                                                                        response.getChatResult()
                                                                                                                        .getExecutedTasks());
                                                                                        result.put("taskResults",
                                                                                                        response.getChatResult()
                                                                                                                        .getTaskResults());
                                                                                        result.put("tokenUsage",
                                                                                                        response.getChatResult()
                                                                                                                        .getTokenUsage());
                                                                                        result.put("metadata", response
                                                                                                        .getChatResult()
                                                                                                        .getMetadata());
                                                                                }
                                                                                return ResponseEntity.ok(result);
                                                                        })
                                                                        .onErrorResume(error -> {
                                                                                log.error("Error starting conversation: {}",
                                                                                                error.getMessage());
                                                                                String errorMessage = error
                                                                                                .getMessage() != null
                                                                                                                ? error.getMessage()
                                                                                                                : "Unknown error occurred";
                                                                                return Mono.just(ResponseEntity.status(
                                                                                                HttpStatus.BAD_REQUEST)
                                                                                                .body(Map.of("error",
                                                                                                                errorMessage)));
                                                                        });
                                                }));
        }

        // Send a message in an existing conversation
        @PostMapping("/{conversationId}/messages")
        public Mono<ResponseEntity<Map<String, Object>>> sendMessage(
                        @PathVariable String conversationId,
                        @RequestBody Map<String, Object> requestBody,
                        ServerWebExchange exchange) {
                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .flatMap(teamId -> checkAccessAuthorization(conversationId,
                                                                user.getUsername(), teamId, user.getId())
                                                                .filter(hasAccess -> hasAccess)
                                                                .switchIfEmpty(Mono.error(new RuntimeException(
                                                                                "Unauthorized access to conversation")))
                                                                .flatMap(hasAccess -> conversationService
                                                                                .getConversationById(conversationId)
                                                                                .flatMap(conversation -> {
                                                                                        String message = (String) requestBody
                                                                                                        .getOrDefault("message",
                                                                                                                        "");

                                                                                        // Get conversation history for
                                                                                        // context
                                                                                        return conversationService
                                                                                                        .getMessagesByConversationId(
                                                                                                                        conversationId)
                                                                                                        .collectList()
                                                                                                        .flatMap(historyMessages -> {
                                                                                                                // Build
                                                                                                                // LinqRequest
                                                                                                                // for
                                                                                                                // chat
                                                                                                                LinqRequest linqRequest = new LinqRequest();
                                                                                                                LinqRequest.Link link = new LinqRequest.Link();
                                                                                                                link.setTarget("assistant");
                                                                                                                link.setAction("chat");
                                                                                                                linqRequest.setLink(
                                                                                                                                link);

                                                                                                                LinqRequest.Query query = new LinqRequest.Query();
                                                                                                                query.setIntent("chat");

                                                                                                                Map<String, Object> params = new HashMap<>();
                                                                                                                params.put("teamId",
                                                                                                                                teamId);
                                                                                                                params.put("username",
                                                                                                                                user.getUsername());
                                                                                                                query.setParams(params);

                                                                                                                LinqRequest.Query.ChatConversation chat = new LinqRequest.Query.ChatConversation();
                                                                                                                chat.setAssistantId(
                                                                                                                                conversation.getAssistantId());
                                                                                                                chat.setMessage(message);
                                                                                                                chat.setConversationId(
                                                                                                                                conversationId);

                                                                                                                // Build
                                                                                                                // history
                                                                                                                // from
                                                                                                                // messages
                                                                                                                java.util.List<LinqRequest.Query.ChatConversation.ChatMessage> history = new java.util.ArrayList<>();
                                                                                                                for (ConversationMessage msg : historyMessages) {
                                                                                                                        LinqRequest.Query.ChatConversation.ChatMessage chatMsg = new LinqRequest.Query.ChatConversation.ChatMessage();
                                                                                                                        chatMsg.setRole(msg
                                                                                                                                        .getRole()
                                                                                                                                        .toLowerCase());
                                                                                                                        chatMsg.setContent(
                                                                                                                                        msg.getContent());
                                                                                                                        chatMsg.setTimestamp(
                                                                                                                                        msg.getTimestamp());
                                                                                                                        history.add(chatMsg);
                                                                                                                }
                                                                                                                chat.setHistory(history);
                                                                                                                chat.setContext((Map<String, Object>) requestBody
                                                                                                                                .getOrDefault("context",
                                                                                                                                                new HashMap<>()));
                                                                                                                query.setChat(chat);

                                                                                                                linqRequest.setQuery(
                                                                                                                                query);
                                                                                                                linqRequest.setExecutedBy(
                                                                                                                                user.getUsername());

                                                                                                                // Execute
                                                                                                                // chat
                                                                                                                return chatExecutionService
                                                                                                                                .executeChat(linqRequest)
                                                                                                                                .map(response -> {
                                                                                                                                        Map<String, Object> result = new HashMap<>();
                                                                                                                                        if (response.getChatResult() != null) {
                                                                                                                                                result.put("conversationId",
                                                                                                                                                                response.getChatResult()
                                                                                                                                                                                .getConversationId());
                                                                                                                                                result.put("message",
                                                                                                                                                                response
                                                                                                                                                                                .getChatResult()
                                                                                                                                                                                .getMessage());
                                                                                                                                                result.put("intent",
                                                                                                                                                                response
                                                                                                                                                                                .getChatResult()
                                                                                                                                                                                .getIntent());
                                                                                                                                                result.put("modelCategory",
                                                                                                                                                                response.getChatResult()
                                                                                                                                                                                .getModelCategory());
                                                                                                                                                result.put("modelName",
                                                                                                                                                                response
                                                                                                                                                                                .getChatResult()
                                                                                                                                                                                .getModelName());
                                                                                                                                                result.put("executedTasks",
                                                                                                                                                                response.getChatResult()
                                                                                                                                                                                .getExecutedTasks());
                                                                                                                                                result.put("taskResults",
                                                                                                                                                                response.getChatResult()
                                                                                                                                                                                .getTaskResults());
                                                                                                                                                result.put("tokenUsage",
                                                                                                                                                                response.getChatResult()
                                                                                                                                                                                .getTokenUsage());
                                                                                                                                                result.put("metadata",
                                                                                                                                                                response
                                                                                                                                                                                .getChatResult()
                                                                                                                                                                                .getMetadata());
                                                                                                                                        }
                                                                                                                                        return ResponseEntity
                                                                                                                                                        .ok(result);
                                                                                                                                })
                                                                                                                                .onErrorResume(error -> {
                                                                                                                                        log.error("Error sending message: {}",
                                                                                                                                                        error.getMessage());
                                                                                                                                        String errorMessage = error
                                                                                                                                                        .getMessage() != null
                                                                                                                                                                        ? error.getMessage()
                                                                                                                                                                        : "Unknown error occurred";
                                                                                                                                        return Mono.just(
                                                                                                                                                        ResponseEntity.status(
                                                                                                                                                                        HttpStatus.BAD_REQUEST)
                                                                                                                                                                        .body(Map.of("error",
                                                                                                                                                                                        errorMessage)));
                                                                                                                                });
                                                                                                        });
                                                                                }))));
        }

        // Get conversation messages
        @GetMapping("/{conversationId}/messages")
        public Mono<ResponseEntity<Flux<ConversationMessage>>> getMessages(
                        @PathVariable String conversationId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "50") int size,
                        ServerWebExchange exchange) {
                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .flatMap(teamId -> checkAccessAuthorization(conversationId,
                                                                user.getUsername(), teamId, user.getId())
                                                                .filter(hasAccess -> hasAccess)
                                                                .switchIfEmpty(Mono.error(new RuntimeException(
                                                                                "Unauthorized access to conversation")))
                                                                .map(hasAccess -> {
                                                                        Pageable pageable = PageRequest.of(page, size,
                                                                                        Sort.by(Sort.Direction.ASC,
                                                                                                        "sequenceNumber"));
                                                                        Flux<ConversationMessage> messages = conversationService
                                                                                        .getMessagesByConversationId(
                                                                                                        conversationId,
                                                                                                        pageable);
                                                                        return ResponseEntity.ok(messages);
                                                                })))
                                .onErrorResume(error -> {
                                        log.error("Error getting messages: {}", error.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                                });
        }

        // Get conversation details
        @GetMapping("/{conversationId}")
        public Mono<ResponseEntity<Conversation>> getConversation(
                        @PathVariable String conversationId,
                        ServerWebExchange exchange) {
                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .flatMap(teamId -> checkAccessAuthorization(conversationId,
                                                                user.getUsername(), teamId, user.getId())
                                                                .filter(hasAccess -> hasAccess)
                                                                .switchIfEmpty(Mono.error(new RuntimeException(
                                                                                "Unauthorized access to conversation")))
                                                                .flatMap(hasAccess -> conversationService
                                                                                .getConversationById(conversationId)
                                                                                .map(ResponseEntity::ok))))
                                .onErrorResume(error -> {
                                        log.error("Error getting conversation: {}", error.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND).build());
                                });
        }

        // List conversations
        @GetMapping
        public Mono<ResponseEntity<Flux<Conversation>>> listConversations(
                        @RequestParam(required = false) String assistantId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        ServerWebExchange exchange) {
                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .map(teamId -> {
                                                        Pageable pageable = PageRequest.of(page, size,
                                                                        Sort.by(Sort.Direction.DESC, "startedAt"));
                                                        Flux<Conversation> conversations;
                                                        if (assistantId != null) {
                                                                // Filter by both assistantId and username to show only
                                                                // current user's conversations
                                                                conversations = conversationService
                                                                                .listConversationsByAssistantAndUser(
                                                                                                assistantId,
                                                                                                user.getUsername(),
                                                                                                pageable);
                                                        } else {
                                                                conversations = conversationService
                                                                                .listConversationsByTeam(teamId,
                                                                                                pageable);
                                                        }
                                                        return ResponseEntity.ok(conversations);
                                                }))
                                .onErrorResume(error -> {
                                        log.error("Error listing conversations: {}", error.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                                });
        }

        // Delete conversation
        @DeleteMapping("/{conversationId}")
        public Mono<ResponseEntity<Void>> deleteConversation(
                        @PathVariable String conversationId,
                        ServerWebExchange exchange) {
                return userContextService.getCurrentUsername(exchange)
                                .flatMap(userService::findByUsername)
                                .flatMap(user -> teamContextService.getTeamFromContext(exchange)
                                                .flatMap(teamId -> checkAccessAuthorization(conversationId,
                                                                user.getUsername(), teamId, user.getId())
                                                                .filter(hasAccess -> hasAccess)
                                                                .switchIfEmpty(Mono.error(new RuntimeException(
                                                                                "Unauthorized access to conversation")))
                                                                .flatMap(hasAccess -> conversationService
                                                                                .deleteConversation(conversationId)
                                                                                .thenReturn(ResponseEntity.noContent()
                                                                                                .<Void>build()))))
                                .onErrorResume(error -> {
                                        log.error("Error deleting conversation: {}", error.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                                });
        }
}
