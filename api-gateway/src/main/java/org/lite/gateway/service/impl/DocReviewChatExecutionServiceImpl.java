package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.*;
import org.lite.gateway.repository.AIAssistantRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.DocReviewAssistantRepository;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.service.*;
import org.lite.gateway.util.AuditLogHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Service("docReviewChatExecutionService")
public class DocReviewChatExecutionServiceImpl extends BaseChatExecutionService {

        public DocReviewChatExecutionServiceImpl(
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
                log.info("Executing chat request for assistant (DocReview)");

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
                                                .flatMap(conversation -> buildChatMessages(conversation, message,
                                                                assistant, chat)
                                                                .flatMap(messages -> resolveModelCategory(assistant)
                                                                                .flatMap(modelCategory -> {
                                                                                        String requestReviewId = chat
                                                                                                        .getContext() != null
                                                                                                                        ? (String) chat.getContext()
                                                                                                                                        .get("contractReviewId")
                                                                                                                        : null;
                                                                                        String requestDocId = chat
                                                                                                        .getContext() != null
                                                                                                                        ? (String) chat.getContext()
                                                                                                                                        .get("documentId")
                                                                                                                        : null;
                                                                                        String providedContent = chat
                                                                                                        .getContext() != null
                                                                                                                        ? (String) chat.getContext()
                                                                                                                                        .get("documentContent")
                                                                                                                        : null;
                                                                                        String selectedText = chat
                                                                                                        .getContext() != null
                                                                                                                        ? (String) chat.getContext()
                                                                                                                                        .get("selectedText")
                                                                                                                        : null;

                                                                                        Mono<Void> linkingMono = (requestReviewId != null
                                                                                                        && (chat.getConversationId() == null
                                                                                                                        || chat.getConversationId()
                                                                                                                                        .isEmpty()))
                                                                                                                                                        ? docReviewAssistantRepository
                                                                                                                                                                        .findById(requestReviewId)
                                                                                                                                                                        .flatMap(review -> {
                                                                                                                                                                                log.info("Linking conversation {} to review session {}",
                                                                                                                                                                                                conversation.getId(),
                                                                                                                                                                                                requestReviewId);
                                                                                                                                                                                review.setConversationId(
                                                                                                                                                                                                conversation.getId());
                                                                                                                                                                                return docReviewAssistantRepository
                                                                                                                                                                                                .save(review);
                                                                                                                                                                        })
                                                                                                                                                                        .then()
                                                                                                                                                        : Mono.empty();

                                                                                        return linkingMono.then(
                                                                                                        getReviewedDocumentId(
                                                                                                                        assistant,
                                                                                                                        conversation.getId()))
                                                                                                        .flatMap(reviewedDocId -> {
                                                                                                                final String finalDocId = (reviewedDocId == null
                                                                                                                                || reviewedDocId.isEmpty())
                                                                                                                                                ? (requestDocId != null
                                                                                                                                                                ? requestDocId
                                                                                                                                                                : "")
                                                                                                                                                : reviewedDocId;

                                                                                                                Map<String, Object> context = new HashMap<>();
                                                                                                                context.put("assistantId",
                                                                                                                                assistant.getId());
                                                                                                                context.put("modelCategory",
                                                                                                                                modelCategory);
                                                                                                                if (!finalDocId.isEmpty())
                                                                                                                        context.put("reviewedDocumentId",
                                                                                                                                        finalDocId);

                                                                                                                return saveUserMessage(
                                                                                                                                conversation,
                                                                                                                                message,
                                                                                                                                context,
                                                                                                                                assistant,
                                                                                                                                executedBy)
                                                                                                                                .flatMap(savedUserMsg -> {
                                                                                                                                        Map<String, Object> extraContext = new HashMap<>();
                                                                                                                                        if (providedContent != null)
                                                                                                                                                extraContext.put(
                                                                                                                                                                "documentText",
                                                                                                                                                                providedContent);
                                                                                                                                        if (requestReviewId != null)
                                                                                                                                                extraContext.put(
                                                                                                                                                                "contractReviewId",
                                                                                                                                                                requestReviewId);
                                                                                                                                        if (!finalDocId.isEmpty())
                                                                                                                                                extraContext.put(
                                                                                                                                                                "documentId",
                                                                                                                                                                finalDocId);

                                                                                                                                        return executeAgentTasksWrapper(
                                                                                                                                                        assistant,
                                                                                                                                                        conversation,
                                                                                                                                                        messages,
                                                                                                                                                        message,
                                                                                                                                                        executedBy,
                                                                                                                                                        extraContext)
                                                                                                                                                        .flatMap(taskResults -> enrichMessagesWithContext(
                                                                                                                                                                        messages,
                                                                                                                                                                        assistant,
                                                                                                                                                                        conversation,
                                                                                                                                                                        taskResults,
                                                                                                                                                                        message,
                                                                                                                                                                        finalDocId,
                                                                                                                                                                        providedContent,
                                                                                                                                                                        selectedText,
                                                                                                                                                                        false)
                                                                                                                                                                        .flatMap(enrichedMessages -> {
                                                                                                                                                                                AIAssistant.ModelConfig modelConfig = assistant
                                                                                                                                                                                                .getDefaultModel();
                                                                                                                                                                                if (modelConfig.getSettings() == null)
                                                                                                                                                                                        modelConfig.setSettings(
                                                                                                                                                                                                        new HashMap<>());
                                                                                                                                                                                if (!modelConfig.getSettings()
                                                                                                                                                                                                .containsKey("max.tokens")
                                                                                                                                                                                                && !modelConfig.getSettings()
                                                                                                                                                                                                                .containsKey("max_tokens")) {
                                                                                                                                                                                        modelConfig.getSettings()
                                                                                                                                                                                                        .put("max.tokens",
                                                                                                                                                                                                                        4000);
                                                                                                                                                                                }

                                                                                                                                                                                String modelName = modelConfig
                                                                                                                                                                                                .getModelName();
                                                                                                                                                                                return linqLlmModelRepository
                                                                                                                                                                                                .findByModelNameAndTeamId(
                                                                                                                                                                                                                modelName,
                                                                                                                                                                                                                teamId)
                                                                                                                                                                                                .next()
                                                                                                                                                                                                .defaultIfEmpty(new LinqLlmModel())
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
                                                                                                                                                                                                                        .flatMap(response -> {
                                                                                                                                                                                                                                @SuppressWarnings("unchecked")
                                                                                                                                                                                                                                Map<String, Object> graphCtx = (Map<String, Object>) taskResults
                                                                                                                                                                                                                                                .getOrDefault("_knowledgeGraph",
                                                                                                                                                                                                                                                                new HashMap<>());
                                                                                                                                                                                                                                return buildChatResponse(
                                                                                                                                                                                                                                                conversation,
                                                                                                                                                                                                                                                assistant,
                                                                                                                                                                                                                                                request,
                                                                                                                                                                                                                                                response,
                                                                                                                                                                                                                                                taskResults,
                                                                                                                                                                                                                                                graphCtx,
                                                                                                                                                                                                                                                reviewedDocId,
                                                                                                                                                                                                                                                modelCategory,
                                                                                                                                                                                                                                                modelName,
                                                                                                                                                                                                                                                startTime,
                                                                                                                                                                                                                                                message,
                                                                                                                                                                                                                                                assistant.getTeamId());
                                                                                                                                                                                                                        })
                                                                                                                                                                                                                        .flatMap(chatResponse -> saveAssistantMessage(
                                                                                                                                                                                                                                        conversation,
                                                                                                                                                                                                                                        chatResponse,
                                                                                                                                                                                                                                        assistant,
                                                                                                                                                                                                                                        executedBy)
                                                                                                                                                                                                                                        .thenReturn(chatResponse))
                                                                                                                                                                                                                        .doOnSuccess(chatResponse -> logChatCompletionAudit(
                                                                                                                                                                                                                                        assistant,
                                                                                                                                                                                                                                        conversation,
                                                                                                                                                                                                                                        executedBy,
                                                                                                                                                                                                                                        startTime,
                                                                                                                                                                                                                                        taskResults,
                                                                                                                                                                                                                                        modelCategory,
                                                                                                                                                                                                                                        modelName,
                                                                                                                                                                                                                                        null,
                                                                                                                                                                                                                                        chatResponse.getChatResult() != null
                                                                                                                                                                                                                                                        ? chatResponse.getChatResult()
                                                                                                                                                                                                                                                                        .getTokenUsage()
                                                                                                                                                                                                                                                        : null,
                                                                                                                                                                                                                                        auditLogHelper)
                                                                                                                                                                                                                                        .subscribe());
                                                                                                                                                                                                });
                                                                                                                                                                        }));
                                                                                                                                });
                                                                                                        });
                                                                                }))));
        }

        private Mono<Conversation> getOrCreateConversation(LinqRequest.Query.ChatConversation chat,
                        AIAssistant assistant, String teamId, String username) {
                if (chat.getConversationId() != null && !chat.getConversationId().isEmpty()) {
                        return conversationService.getConversationById(chat.getConversationId())
                                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                                        "Conversation not found: " + chat.getConversationId())));
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
                        LinqRequest request, LinqResponse response, Map<String, Object> taskResults,
                        Map<String, Object> graphContext, String reviewedDocId, String modelCategory, String modelName,
                        LocalDateTime startTime, String userMessage, String teamId) {
                LinqResponse.ChatResult result = new LinqResponse.ChatResult();
                String content = extractMessageContent(response);

                Mono<Void> updateMono = Mono.empty();
                String updatedDocumentForMetadata = null;
                String replacementContentForMetadata = null;

                if (content != null && content.contains("<REPLACE_SELECTION>")) {
                        try {
                                int startTag = content.indexOf("<REPLACE_SELECTION>") + "<REPLACE_SELECTION>".length();
                                int endTag = content.indexOf("</REPLACE_SELECTION>");
                                if (endTag > startTag) {
                                        replacementContentForMetadata = content.substring(startTag, endTag).trim();
                                        content = content.replace(
                                                        "<REPLACE_SELECTION>" + content.substring(startTag, endTag)
                                                                        + "</REPLACE_SELECTION>",
                                                        "\n*(Selected section has been updated)*\n");
                                }
                        } catch (Exception e) {
                                log.error("Failed to parse <REPLACE_SELECTION> tag: {}", e.getMessage());
                        }
                } else if (content != null && replacementContentForMetadata == null
                                && updatedDocumentForMetadata == null) {
                        // Heuristic check: If it looks like it contains HTML tags (fail-safe for
                        // missing tags)
                        // This regex looks for any string starting with < followed by a letter or /
                        if (content.matches(".*<[a-zA-Z/].*>.*")) {
                                try {
                                        int startIdx = content.indexOf("<");
                                        int lastIdx = content.lastIndexOf(">");
                                        if (startIdx != -1 && lastIdx > startIdx) {
                                                replacementContentForMetadata = content.substring(startIdx, lastIdx + 1)
                                                                .trim();
                                                log.info("Heuristic: Extracted possible HTML fragment from tag-less response.");
                                        }
                                } catch (Exception e) {
                                        log.warn("Heuristic extraction failed: {}", e.getMessage());
                                }
                        }
                }

                if (content != null && content.contains("<UPDATED_DOCUMENT_CONTENT>") && reviewedDocId != null
                                && !reviewedDocId.isEmpty()) {
                        try {
                                int startTag = content.indexOf("<UPDATED_DOCUMENT_CONTENT>")
                                                + "<UPDATED_DOCUMENT_CONTENT>".length();
                                int endTag = content.indexOf("</UPDATED_DOCUMENT_CONTENT>");

                                String updatedContent;
                                if (endTag > startTag) {
                                        updatedContent = content.substring(startTag, endTag).trim();
                                        content = content.replace(
                                                        "<UPDATED_DOCUMENT_CONTENT>"
                                                                        + content.substring(startTag, endTag)
                                                                        + "</UPDATED_DOCUMENT_CONTENT>",
                                                        "\n*(Document content has been updated)*\n");
                                } else {
                                        updatedContent = content.substring(startTag).trim();
                                        content = content.substring(0, content.indexOf("<UPDATED_DOCUMENT_CONTENT>"))
                                                        + "\n*(Document content has been updated)*\n";
                                }

                                if (!updatedContent.isEmpty()) {
                                        updatedDocumentForMetadata = updatedContent;
                                        updateMono = knowledgeHubDocumentService
                                                        .updateDocumentContent(reviewedDocId, teamId,
                                                                        updatedContent.getBytes())
                                                        .then(docReviewAssistantRepository
                                                                        .findByConversationId(conversation.getId())
                                                                        .flatMap(review -> knowledgeHubDocumentService
                                                                                        .getDocumentById(reviewedDocId,
                                                                                                        teamId)
                                                                                        .flatMap(doc -> {
                                                                                                review.setDocumentVersion(
                                                                                                                doc.getCurrentVersion());
                                                                                                return docReviewAssistantRepository
                                                                                                                .save(review);
                                                                                        })))
                                                        .then();
                                }
                        } catch (Exception e) {
                                log.error("Failed to parse <UPDATED_DOCUMENT_CONTENT> tag: {}", e.getMessage());
                        }
                }

                streamMessageChunks(conversation.getId(), content);

                result.setMessage(content);
                result.setConversationId(conversation.getId());
                result.setIntent("chat");
                result.setModelCategory(modelCategory);
                result.setModelName(modelName);

                if (taskResults != null && !taskResults.isEmpty()) {
                        result.setExecutedTasks(new ArrayList<>(taskResults.keySet()));
                        result.setTaskResults(taskResults);
                }

                LinqResponse.ChatResult.TokenUsage tokenUsage = extractTokenUsageFromResponse(response, modelCategory,
                                modelName);
                if (tokenUsage != null) {
                        result.setTokenUsage(tokenUsage);
                }

                Map<String, Object> metadata = new HashMap<>();
                if (reviewedDocId != null)
                        metadata.put("reviewedDocumentId", reviewedDocId);
                if (updatedDocumentForMetadata != null)
                        metadata.put("updatedDocument", updatedDocumentForMetadata);
                if (replacementContentForMetadata != null)
                        metadata.put("replacementContent", replacementContentForMetadata);
                result.setMetadata(metadata);

                LinqResponse chatResponse = new LinqResponse();
                chatResponse.setChatResult(result);
                chatResponse.setMetadata(response.getMetadata());

                return updateMono.thenReturn(chatResponse);
        }
}
