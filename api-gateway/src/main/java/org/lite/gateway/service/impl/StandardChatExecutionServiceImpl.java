package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.AIAssistant;
import org.lite.gateway.entity.Conversation;
import org.lite.gateway.entity.LinqLlmModel;
import org.lite.gateway.repository.AIAssistantRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.DocReviewAssistantRepository;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.service.*;
import org.lite.gateway.util.AuditLogHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service("standardChatExecutionService")
@Primary
public class StandardChatExecutionServiceImpl extends BaseChatExecutionService {

        public StandardChatExecutionServiceImpl(
                        LinqLlmModelService linqLlmModelService,
                        LinqLlmModelRepository linqLlmModelRepository,
                        LlmCostService llmCostService,
                        AuditLogHelper auditLogHelper,
                        DocReviewAssistantRepository docReviewAssistantRepository,
                        AIAssistantRepository aiAssistantRepository,
                        ConversationService conversationService,
                        AgentExecutionService agentExecutionService,
                        AgentExecutionRepository agentExecutionRepository,
                        AgentTaskRepository agentTaskRepository,
                        PIIDetectionService piiDetectionService,
                        KnowledgeHubGraphContextService knowledgeHubGraphContextService,
                        KnowledgeHubDocumentService knowledgeHubDocumentService,
                        ObjectMapper objectMapper,
                        @Qualifier("chatMessageChannel") MessageChannel chatMessageChannel) {
                super(linqLlmModelService, linqLlmModelRepository, llmCostService, auditLogHelper,
                                docReviewAssistantRepository, aiAssistantRepository, conversationService,
                                agentExecutionService, agentExecutionRepository, agentTaskRepository,
                                piiDetectionService, knowledgeHubGraphContextService,
                                knowledgeHubDocumentService, objectMapper);
                this.setChatMessageChannel(chatMessageChannel);
        }

        @Override
        public Mono<LinqResponse> executeChat(LinqRequest request) {
                log.info("Executing chat request for assistant (Standard)");

                if (request.getQuery() == null || request.getQuery().getChat() == null) {
                        return Mono.error(new IllegalArgumentException("Chat conversation is required"));
                }

                LinqRequest.Query.ChatConversation chat = request.getQuery().getChat();
                String assistantId = chat.getAssistantId();
                String message = chat.getMessage();
                if (assistantId == null || message == null || message.trim().isEmpty()) {
                        return Mono.error(new IllegalArgumentException("Assistant ID and message are required"));
                }

                String teamId = extractTeamId(request);
                String executedBy = extractUsername(request);
                LocalDateTime startTime = LocalDateTime.now();

                return aiAssistantRepository.findById(assistantId)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND,
                                                "AI Assistant not found: " + assistantId)))
                                .flatMap(assistant -> getOrCreateConversation(chat, assistant, teamId, executedBy)
                                                .flatMap(conversation -> logChatExecutionStarted(assistant,
                                                                conversation, executedBy, startTime)
                                                                .then(Mono.defer(() -> buildChatMessages(conversation,
                                                                                message, assistant, chat)
                                                                                .flatMap(messages -> resolveModelCategory(
                                                                                                assistant)
                                                                                                .flatMap(modelCategory -> {
                                                                                                        Map<String, Object> context = new HashMap<>();
                                                                                                        context.put("assistantId",
                                                                                                                        assistant.getId());
                                                                                                        context.put("modelCategory",
                                                                                                                        modelCategory);

                                                                                                        return saveUserMessage(
                                                                                                                        conversation,
                                                                                                                        message,
                                                                                                                        context,
                                                                                                                        assistant,
                                                                                                                        executedBy)
                                                                                                                        .flatMap(savedUserMessage -> executeAgentTasksWrapper(
                                                                                                                                        assistant,
                                                                                                                                        conversation,
                                                                                                                                        messages,
                                                                                                                                        message,
                                                                                                                                        executedBy,
                                                                                                                                        Collections.emptyMap())
                                                                                                                                        .flatMap(taskResults -> enrichMessagesWithContext(
                                                                                                                                                        messages,
                                                                                                                                                        assistant,
                                                                                                                                                        conversation,
                                                                                                                                                        taskResults,
                                                                                                                                                        message,
                                                                                                                                                        null,
                                                                                                                                                        null,
                                                                                                                                                        null,
                                                                                                                                                        true)
                                                                                                                                                        .flatMap(enrichedMessages -> {
                                                                                                                                                                AIAssistant.ModelConfig modelConfig = assistant
                                                                                                                                                                                .getDefaultModel();
                                                                                                                                                                String modelName = modelConfig
                                                                                                                                                                                .getModelName();

                                                                                                                                                                return linqLlmModelRepository
                                                                                                                                                                                .findByModelNameAndTeamId(
                                                                                                                                                                                                modelName,
                                                                                                                                                                                                teamId)
                                                                                                                                                                                .next()
                                                                                                                                                                                .switchIfEmpty(Mono
                                                                                                                                                                                                .defer(() -> {
                                                                                                                                                                                                        LinqLlmModel fallback = new LinqLlmModel();
                                                                                                                                                                                                        fallback.setProvider(
                                                                                                                                                                                                                        modelConfig.getProvider());
                                                                                                                                                                                                        return Mono.just(
                                                                                                                                                                                                                        fallback);
                                                                                                                                                                                                }))
                                                                                                                                                                                .flatMap(llmModel -> {
                                                                                                                                                                                        LinqRequest chatRequest = buildLlmRequest(
                                                                                                                                                                                                        modelCategory,
                                                                                                                                                                                                        modelName,
                                                                                                                                                                                                        enrichedMessages,
                                                                                                                                                                                                        assistant,
                                                                                                                                                                                                        llmModel,
                                                                                                                                                                                                        executedBy,
                                                                                                                                                                                                        conversation.getId());
                                                                                                                                                                                        return linqLlmModelService
                                                                                                                                                                                                        .executeLlmRequest(
                                                                                                                                                                                                                        chatRequest,
                                                                                                                                                                                                                        llmModel)
                                                                                                                                                                                                        .flatMap(response -> getReviewedDocumentId(
                                                                                                                                                                                                                        assistant,
                                                                                                                                                                                                                        conversation.getId())
                                                                                                                                                                                                                        .flatMap(reviewedDocId -> {
                                                                                                                                                                                                                                @SuppressWarnings("unchecked")
                                                                                                                                                                                                                                Map<String, Object> graphContext = (Map<String, Object>) taskResults
                                                                                                                                                                                                                                                .getOrDefault("_knowledgeGraph",
                                                                                                                                                                                                                                                                new HashMap<>());
                                                                                                                                                                                                                                return buildChatResponse(
                                                                                                                                                                                                                                                conversation,
                                                                                                                                                                                                                                                assistant,
                                                                                                                                                                                                                                                request,
                                                                                                                                                                                                                                                response,
                                                                                                                                                                                                                                                taskResults,
                                                                                                                                                                                                                                                graphContext,
                                                                                                                                                                                                                                                reviewedDocId,
                                                                                                                                                                                                                                                modelCategory,
                                                                                                                                                                                                                                                modelName,
                                                                                                                                                                                                                                                startTime,
                                                                                                                                                                                                                                                message)
                                                                                                                                                                                                                                                .flatMap(chatResponse -> saveAssistantMessage(
                                                                                                                                                                                                                                                                conversation,
                                                                                                                                                                                                                                                                chatResponse,
                                                                                                                                                                                                                                                                assistant,
                                                                                                                                                                                                                                                                executedBy)
                                                                                                                                                                                                                                                                .map(savedAssistantMessage -> {
                                                                                                                                                                                                                                                                        publishChatUpdate(
                                                                                                                                                                                                                                                                                        "MESSAGE_SAVED",
                                                                                                                                                                                                                                                                                        Map.of(
                                                                                                                                                                                                                                                                                                        "conversationId",
                                                                                                                                                                                                                                                                                                        conversation.getId(),
                                                                                                                                                                                                                                                                                                        "messageId",
                                                                                                                                                                                                                                                                                                        savedAssistantMessage
                                                                                                                                                                                                                                                                                                                        .getId(),
                                                                                                                                                                                                                                                                                                        "role",
                                                                                                                                                                                                                                                                                                        "ASSISTANT"));

                                                                                                                                                                                                                                                                        if (chatResponse.getMetadata() == null) {
                                                                                                                                                                                                                                                                                LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                                                                                                                                                                                                                                                                                metadata.setSource(
                                                                                                                                                                                                                                                                                                "assistant");
                                                                                                                                                                                                                                                                                metadata.setStatus(
                                                                                                                                                                                                                                                                                                "success");
                                                                                                                                                                                                                                                                                metadata.setTeamId(
                                                                                                                                                                                                                                                                                                teamId);
                                                                                                                                                                                                                                                                                metadata.setCacheHit(
                                                                                                                                                                                                                                                                                                false);
                                                                                                                                                                                                                                                                                chatResponse.setMetadata(
                                                                                                                                                                                                                                                                                                metadata);
                                                                                                                                                                                                                                                                        }
                                                                                                                                                                                                                                                                        return chatResponse;
                                                                                                                                                                                                                                                                }));
                                                                                                                                                                                                                        }));
                                                                                                                                                                                });
                                                                                                                                                        })));
                                                                                                })))))
                                                .onErrorResume(e -> {
                                                        log.error("Error in executeChat: {}", e.getMessage(), e);
                                                        return Mono.error(e);
                                                }));
        }

        private Mono<Conversation> getOrCreateConversation(LinqRequest.Query.ChatConversation chat,
                        AIAssistant assistant,
                        String teamId, String username) {
                if (chat.getConversationId() != null && !chat.getConversationId().isEmpty()) {
                        return conversationService.getConversationById(chat.getConversationId())
                                        .switchIfEmpty(Mono.error(
                                                        new IllegalArgumentException("Conversation not found: "
                                                                        + chat.getConversationId())));
                } else {
                        Conversation conversation = Conversation.builder()
                                        .assistantId(assistant.getId())
                                        .teamId(teamId)
                                        .username(username)
                                        .isPublic(assistant.getAccessControl() != null
                                                        && "PUBLIC".equals(assistant.getAccessControl().getType()))
                                        .source("web_app")
                                        .status("ACTIVE")
                                        .title(chat.getMessage().length() > 50
                                                        ? chat.getMessage().substring(0, 50) + "..."
                                                        : chat.getMessage())
                                        .startedAt(LocalDateTime.now())
                                        .messageCount(0)
                                        .metadata(Conversation.ConversationMetadata.builder()
                                                        .totalTokens(0L)
                                                        .totalCost(0.0)
                                                        .taskExecutions(0)
                                                        .successfulTasks(0)
                                                        .failedTasks(0)
                                                        .build())
                                        .build();
                        return conversationService.createConversation(conversation);
                }
        }

        private Mono<LinqResponse> buildChatResponse(Conversation conversation, AIAssistant assistant,
                        LinqRequest request,
                        LinqResponse response, Map<String, Object> taskResults, Map<String, Object> graphContext,
                        String reviewedDocId, String modelCategory, String modelName, LocalDateTime startTime,
                        String userMessage) {

                String fullMessage = extractMessageContent(response);

                streamMessageChunks(conversation.getId(), fullMessage);

                LinqResponse.ChatResult chatResult = new LinqResponse.ChatResult();
                chatResult.setConversationId(conversation.getId());
                chatResult.setAssistantId(assistant.getId());
                chatResult.setMessage(fullMessage);
                chatResult.setModelCategory(modelCategory);
                chatResult.setModelName(modelName);
                chatResult.setTaskResults(taskResults);

                LinqResponse.ChatResult.TokenUsage tokenUsage = extractTokenUsageFromResponse(response, modelCategory,
                                modelName);
                if (tokenUsage != null) {
                        chatResult.setTokenUsage(tokenUsage);
                }

                // Metadata building
                Map<String, Object> metadata = new HashMap<>();
                if (graphContext != null && graphContext.containsKey("documents")) {
                        metadata.put("documents", graphContext.get("documents"));
                }
                chatResult.setMetadata(metadata);

                LinqResponse finalResponse = new LinqResponse();
                finalResponse.setChatResult(chatResult);

                Map<String, Object> updateData = new HashMap<>();
                updateData.put("conversationId", conversation.getId());
                updateData.put("message", fullMessage);
                publishChatUpdate("LLM_RESPONSE_COMPLETE", updateData);

                return logChatCompletionAudit(assistant, conversation, request.getExecutedBy(), startTime,
                                taskResults, modelCategory, modelName, "user_query", tokenUsage, auditLogHelper)
                                .then(Mono.just(finalResponse));
        }

}
