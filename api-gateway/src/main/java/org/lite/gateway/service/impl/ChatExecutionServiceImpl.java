package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.AIAssistant;
import org.lite.gateway.entity.Conversation;
import org.lite.gateway.entity.ConversationMessage;
import org.lite.gateway.repository.AIAssistantRepository;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.service.AgentTaskService;
import org.lite.gateway.service.AgentExecutionService;
import org.lite.gateway.service.ChatExecutionService;
import org.lite.gateway.service.ConversationService;
import org.lite.gateway.service.LinqLlmModelService;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.service.LlmCostService;
import org.lite.gateway.service.KnowledgeHubGraphContextService;
import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.util.AuditLogHelper;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.enums.AuditResultType;
import org.lite.gateway.service.PIIDetectionService;
import reactor.core.publisher.Flux;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatExecutionServiceImpl implements ChatExecutionService {

    private final AIAssistantRepository aiAssistantRepository;
    private final ConversationService conversationService;
    private final LinqLlmModelService linqLlmModelService;
    private final LinqLlmModelRepository linqLlmModelRepository;
    private final AgentTaskService agentTaskService;
    private final AgentExecutionService agentExecutionService;
    private final AgentRepository agentRepository;
    private final AgentExecutionRepository agentExecutionRepository;
    private final LinqWorkflowExecutionService workflowExecutionService;
    private final LlmCostService llmCostService;
    private final ObjectMapper objectMapper;
    private final KnowledgeHubGraphContextService knowledgeHubGraphContextService;
    private final AuditLogHelper auditLogHelper;
    private final PIIDetectionService piiDetectionService;

    @Autowired(required = false)
    @Qualifier("chatMessageChannel")
    private MessageChannel chatMessageChannel;

    // Track active streaming threads for cancellation
    private final Map<String, Thread> activeStreamingThreads = new ConcurrentHashMap<>();
    private final Map<String, Boolean> cancellationFlags = new ConcurrentHashMap<>();

    @Override
    public Mono<LinqResponse> executeChat(LinqRequest request) {
        log.info("Executing chat request for assistant");

        if (request.getQuery() == null || request.getQuery().getChat() == null) {
            return Mono.error(new IllegalArgumentException("Chat conversation is required"));
        }

        LinqRequest.Query.ChatConversation chat = request.getQuery().getChat();
        String assistantId = chat.getAssistantId();
        String message = chat.getMessage();

        if (assistantId == null || message == null || message.trim().isEmpty()) {
            return Mono.error(new IllegalArgumentException("Assistant ID and message are required"));
        }

        // Extract teamId from params
        String teamId = extractTeamId(request);

        // Load AI Assistant
        return aiAssistantRepository.findById(assistantId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "AI Assistant not found: " + assistantId)))
                .flatMap(assistant -> {
                    // Verify team access
                    if (!assistant.getTeamId().equals(teamId)) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.FORBIDDEN,
                                "AI Assistant does not belong to this team"));
                    }

                    // Extract username from params
                    String username = extractUsername(request);

                    // Get or create conversation (use username from params or executedBy)
                    return getOrCreateConversation(chat, assistant, teamId, username)
                            .flatMap(conversation -> {
                                // Publish conversation started/loaded update
                                publishChatUpdate("CONVERSATION_STARTED", Map.of(
                                        "conversationId", conversation.getId(),
                                        "assistantId", assistant.getId(),
                                        "teamId", teamId,
                                        "username", username));

                                // Build chat messages with history
                                return buildChatMessages(conversation, message, assistant)
                                        .flatMap(messages -> {
                                            // Publish user message sent update
                                            publishChatUpdate("USER_MESSAGE_SENT", Map.of(
                                                    "conversationId", conversation.getId(),
                                                    "message", message));

                                            // Execute chat with LLM (which will handle streaming events)
                                            return executeChatWithLLM(request, messages, assistant, conversation)
                                                    .flatMap(chatResponse -> {
                                                        // NOTE: We intentionally do NOT publish an
                                                        // LLM_RESPONSE_RECEIVED
                                                        // event here anymore, because executeChatWithLLM already
                                                        // publishes
                                                        // streaming updates (LLM_RESPONSE_STREAMING_*). Emitting both
                                                        // caused
                                                        // duplicate assistant messages on the frontend.

                                                        // Save user message
                                                        return saveUserMessage(conversation, message, chat.getContext(),
                                                                assistant, request.getExecutedBy())
                                                                .flatMap(savedUserMessage -> {
                                                                    // Publish user message saved update
                                                                    publishChatUpdate("MESSAGE_SAVED", Map.of(
                                                                            "conversationId", conversation.getId(),
                                                                            "messageId", savedUserMessage.getId(),
                                                                            "role", "USER"));

                                                                    // Save assistant response
                                                                    return saveAssistantMessage(conversation,
                                                                            chatResponse, assistant,
                                                                            request.getExecutedBy())
                                                                            .map(savedAssistantMessage -> {
                                                                                // Publish assistant message saved
                                                                                // update
                                                                                publishChatUpdate("MESSAGE_SAVED",
                                                                                        Map.of(
                                                                                                "conversationId",
                                                                                                conversation.getId(),
                                                                                                "messageId",
                                                                                                savedAssistantMessage
                                                                                                        .getId(),
                                                                                                "role", "ASSISTANT"));

                                                                                // Return the chat response as-is
                                                                                // (already has chatResult set)
                                                                                if (chatResponse
                                                                                        .getMetadata() == null) {
                                                                                    LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                                                                                    metadata.setSource("assistant");
                                                                                    metadata.setStatus("success");
                                                                                    metadata.setTeamId(teamId);
                                                                                    metadata.setCacheHit(false);
                                                                                    chatResponse.setMetadata(metadata);
                                                                                }
                                                                                return chatResponse;
                                                                            });
                                                                });
                                                    });
                                        });
                            });
                });
    }

    private String extractTeamId(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object teamObj = request.getQuery().getParams().get("teamId");
            if (teamObj != null) {
                return String.valueOf(teamObj);
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Team ID must be provided in params");
    }

    private String extractUsername(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object usernameObj = request.getQuery().getParams().get("username");
            if (usernameObj != null) {
                return String.valueOf(usernameObj);
            }
        }
        // Fallback to executedBy (username)
        return request.getExecutedBy();
    }

    private Mono<Conversation> getOrCreateConversation(
            LinqRequest.Query.ChatConversation chat,
            AIAssistant assistant,
            String teamId,
            String username) {

        if (chat.getConversationId() != null && !chat.getConversationId().isEmpty()) {
            // Get existing conversation
            return conversationService.getConversationById(chat.getConversationId())
                    .switchIfEmpty(Mono.error(
                            new IllegalArgumentException("Conversation not found: " + chat.getConversationId())));
        } else {
            // Create new conversation
            Conversation conversation = Conversation.builder()
                    .assistantId(assistant.getId())
                    .teamId(teamId)
                    .username(username) // Username (e.g., "timursen")
                    .isPublic("PUBLIC".equals(
                            assistant.getAccessControl() != null ? assistant.getAccessControl().getType() : null))
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

    private Mono<List<Map<String, Object>>> buildChatMessages(
            Conversation conversation,
            String userMessage,
            AIAssistant assistant) {

        // Get recent messages for context
        int maxMessages = assistant.getContextManagement() != null
                && assistant.getContextManagement().getMaxRecentMessages() != null
                        ? assistant.getContextManagement().getMaxRecentMessages()
                        : 20;

        return conversationService.getRecentMessages(conversation.getId(), maxMessages)
                .collectList()
                .map(recentMessages -> {
                    List<Map<String, Object>> messages = new ArrayList<>();

                    // Add system prompt if available
                    if (assistant.getSystemPrompt() != null && !assistant.getSystemPrompt().trim().isEmpty()) {
                        Map<String, Object> systemMessage = new HashMap<>();
                        systemMessage.put("role", "system");
                        systemMessage.put("content", assistant.getSystemPrompt());
                        messages.add(systemMessage);
                    }

                    // Add conversation history
                    for (ConversationMessage msg : recentMessages) {
                        Map<String, Object> msgMap = new HashMap<>();
                        msgMap.put("role", msg.getRole().toLowerCase());
                        msgMap.put("content", msg.getContent());
                        messages.add(msgMap);
                    }

                    // Add current user message
                    Map<String, Object> userMsgMap = new HashMap<>();
                    userMsgMap.put("role", "user");
                    userMsgMap.put("content", userMessage);
                    messages.add(userMsgMap);

                    return messages;
                });
    }

    private Mono<LinqResponse> executeChatWithLLM(
            LinqRequest request,
            List<Map<String, Object>> messages,
            AIAssistant assistant,
            Conversation conversation) {

        LocalDateTime startTime = LocalDateTime.now();
        String conversationId = conversation.getId();
        String assistantId = assistant.getId();
        String teamId = assistant.getTeamId();
        String executedBy = request.getExecutedBy();

        // Check if audit logging is enabled for this assistant
        final boolean auditLoggingEnabled = assistant.getGuardrails() != null &&
                Boolean.TRUE.equals(assistant.getGuardrails().getAuditLoggingEnabled());

        // Extract user message
        final String userMessage = extractUserMessage(messages);

        // Log chat execution started (only if audit logging is enabled)
        Mono<Void> startAuditLog;
        if (auditLoggingEnabled) {
            Map<String, Object> startContext = new HashMap<>();
            startContext.put("conversationId", conversationId);
            startContext.put("assistantId", assistantId);
            startContext.put("assistantName", assistant.getName());
            startContext.put("teamId", teamId);
            startContext.put("hasSelectedTasks",
                    assistant.getSelectedTasks() != null && !assistant.getSelectedTasks().isEmpty());
            startContext.put("taskCount",
                    assistant.getSelectedTasks() != null ? assistant.getSelectedTasks().size() : 0);
            startContext.put("startTimestamp", startTime.toString());
            if (executedBy != null) {
                startContext.put("executedBy", executedBy);
            }

            startAuditLog = auditLogHelper.logDetailedEvent(
                    AuditEventType.CHAT_EXECUTION_STARTED,
                    AuditActionType.READ,
                    AuditResourceType.CHAT,
                    conversationId,
                    String.format("Chat execution started for assistant '%s'", assistant.getName()),
                    startContext,
                    null,
                    null)
                    .doOnError(auditError -> log.error("Failed to log audit event (chat execution started): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty());
        } else {
            // Audit logging disabled, return empty Mono to continue chain
            startAuditLog = Mono.empty();
        }

        // Enrich context with Knowledge Graph information
        Mono<Map<String, Object>> graphContextMono = knowledgeHubGraphContextService
                .enrichContextWithGraph(userMessage, assistant.getTeamId())
                .onErrorResume(error -> {
                    log.warn("Failed to enrich context with Knowledge Graph: {}", error.getMessage());
                    return Mono.just(Map.of()); // Return empty context on error
                });

        // Execute Agent Tasks first if assistant has selectedTasks
        Mono<Map<String, Object>> taskResultsMono;
        if (assistant.getSelectedTasks() != null && !assistant.getSelectedTasks().isEmpty()) {
            // Publish agent tasks executing update
            Map<String, Object> executingData = new HashMap<>();
            executingData.put("conversationId", conversation.getId());
            executingData.put("taskIds", assistant.getSelectedTasks().stream()
                    .map(AIAssistant.SelectedTask::getTaskId)
                    .toList());
            publishChatUpdate("AGENT_TASKS_EXECUTING", executingData);

            taskResultsMono = executeAgentTasks(assistant.getSelectedTasks(), userMessage, assistant.getTeamId(),
                    request.getExecutedBy())
                    .doOnSuccess(taskResults -> {
                        // Publish agent tasks completed update
                        Map<String, Object> completedData = new HashMap<>();
                        completedData.put("conversationId", conversation.getId());
                        completedData.put("executedTasks", new ArrayList<>(taskResults.keySet()));
                        completedData.put("taskCount", taskResults.size());
                        publishChatUpdate("AGENT_TASKS_COMPLETED", completedData);
                    })
                    .doOnError(error -> {
                        // Publish agent tasks failed update
                        Map<String, Object> failedData = new HashMap<>();
                        failedData.put("conversationId", conversation.getId());
                        failedData.put("error", error.getMessage());
                        publishChatUpdate("AGENT_TASKS_FAILED", failedData);
                    });
        } else {
            taskResultsMono = Mono.just(new HashMap<>());
        }

        // Chain start audit log, then execute chat
        return startAuditLog.then(Mono.defer(() -> {
            // Get assistant's default model
            if (assistant.getDefaultModel() == null) {
                return Mono.error(new IllegalArgumentException("AI Assistant must have a default model configured"));
            }

            AIAssistant.ModelConfig modelConfig = assistant.getDefaultModel();
            String modelName = modelConfig.getModelName();
            String provider = modelConfig.getProvider();
            String modelCategoryFromConfig = modelConfig.getModelCategory();

            // Look up modelCategory from MongoDB if missing
            Mono<String> modelCategoryMono;
            if (modelCategoryFromConfig != null && !modelCategoryFromConfig.isEmpty()) {
                modelCategoryMono = Mono.just(modelCategoryFromConfig);
            } else {
                // Look up from linq_llm_models collection
                log.info(
                        "modelCategory missing for modelName '{}', provider '{}', teamId '{}'. Looking up from MongoDB...",
                        modelName, provider, teamId);

                if (provider != null && !provider.isEmpty()) {
                    // More specific lookup with provider
                    modelCategoryMono = linqLlmModelRepository
                            .findByModelNameAndProviderAndTeamId(modelName, provider, teamId)
                            .map(org.lite.gateway.entity.LinqLlmModel::getModelCategory)
                            .doOnNext(category -> log.info(
                                    "Found modelCategory '{}' from MongoDB for modelName '{}', provider '{}', teamId '{}'",
                                    category, modelName, provider, teamId))
                            .switchIfEmpty(Mono.defer(() -> {
                                // Fallback to derivation if not found
                                String derivedCategory = deriveModelCategory(modelName, provider);
                                if (derivedCategory != null && !derivedCategory.isEmpty()) {
                                    log.warn(
                                            "Could not find modelCategory in MongoDB. Derived '{}' as fallback for modelName '{}', provider '{}', teamId '{}'",
                                            derivedCategory, modelName, provider, teamId);
                                    return Mono.just(derivedCategory);
                                } else {
                                    return Mono.error(new IllegalArgumentException(
                                            "AI Assistant default model must have a modelCategory configured. " +
                                                    "Could not find modelCategory in MongoDB or derive it for modelName: "
                                                    + modelName +
                                                    ", provider: " + provider + ", teamId: " + teamId));
                                }
                            }));
                } else {
                    // Fallback: find by modelName and teamId, take first result
                    modelCategoryMono = linqLlmModelRepository
                            .findByModelNameAndTeamId(modelName, teamId)
                            .next()
                            .map(org.lite.gateway.entity.LinqLlmModel::getModelCategory)
                            .doOnNext(category -> log.info(
                                    "Found modelCategory '{}' from MongoDB for modelName '{}', teamId '{}'",
                                    category, modelName, teamId))
                            .switchIfEmpty(Mono.defer(() -> {
                                // Fallback to derivation if not found
                                String derivedCategory = deriveModelCategory(modelName, provider);
                                if (derivedCategory != null && !derivedCategory.isEmpty()) {
                                    log.warn(
                                            "Could not find modelCategory in MongoDB. Derived '{}' as fallback for modelName '{}', teamId '{}'",
                                            derivedCategory, modelName, teamId);
                                    return Mono.just(derivedCategory);
                                } else {
                                    return Mono.error(new IllegalArgumentException(
                                            "AI Assistant default model must have a modelCategory configured. " +
                                                    "Could not find modelCategory in MongoDB or derive it for modelName: "
                                                    + modelName +
                                                    ", teamId: " + teamId));
                                }
                            }));
                }
            }

            // Load LLM model and execute with task results
            return modelCategoryMono
                    .flatMap(modelCategory -> {
                        final String finalModelCategory = modelCategory;
                        return linqLlmModelRepository.findByModelCategoryAndModelNameAndTeamId(
                                finalModelCategory, modelName, teamId)
                                .switchIfEmpty(Mono.error(new ResponseStatusException(
                                        HttpStatus.NOT_FOUND,
                                        "LLM model not found: " + finalModelCategory + "/" + modelName + " for team "
                                                + teamId)))
                                .flatMap(llmModel -> {
                                    return Mono.zip(taskResultsMono, graphContextMono)
                                            .flatMap(tuple -> {
                                                Map<String, Object> taskResults = tuple.getT1();
                                                Map<String, Object> graphContext = tuple.getT2();

                                                // Build enriched messages with task results and graph context
                                                List<Map<String, Object>> enrichedMessages = enrichMessagesWithContext(
                                                        messages, taskResults, graphContext,
                                                        assistant.getSystemPrompt());

                                                // Build chat request
                                                LinqRequest chatRequest = new LinqRequest();
                                                LinqRequest.Link link = new LinqRequest.Link();
                                                link.setTarget(finalModelCategory);
                                                link.setAction("generate");
                                                chatRequest.setLink(link);

                                                LinqRequest.Query query = new LinqRequest.Query();
                                                // Use "generate" intent which is the standard intent for chat models
                                                query.setIntent("generate");
                                                query.setPayload(enrichedMessages); // Messages array with enriched
                                                                                    // context

                                                // Add model config
                                                LinqRequest.Query.LlmConfig llmConfig = new LinqRequest.Query.LlmConfig();
                                                llmConfig.setModel(modelName);
                                                if (modelConfig.getSettings() != null) {
                                                    llmConfig.setSettings(modelConfig.getSettings());
                                                }
                                                query.setLlmConfig(llmConfig);

                                                // Add teamId to params
                                                Map<String, Object> params = new HashMap<>();
                                                params.put("teamId", assistant.getTeamId());
                                                query.setParams(params);

                                                chatRequest.setQuery(query);
                                                chatRequest.setExecutedBy(request.getExecutedBy());

                                                // Publish LLM call started event
                                                Map<String, Object> llmStartData = new HashMap<>();
                                                llmStartData.put("conversationId", conversation.getId());
                                                llmStartData.put("modelName", modelName);
                                                llmStartData.put("modelCategory", finalModelCategory);
                                                llmStartData.put("provider", llmModel.getProvider());
                                                publishChatUpdate("LLM_CALL_STARTED", llmStartData);

                                                // Execute LLM request
                                                return linqLlmModelService.executeLlmRequest(chatRequest, llmModel)
                                                        .flatMap(response -> {
                                                            String intent = extractIntent(
                                                                    userMessage != null ? userMessage : "", assistant);
                                                            String fullMessage = extractMessageContent(response);

                                                            // Stream message chunks via WebSocket
                                                            streamMessageChunks(conversation.getId(), fullMessage);

                                                            // Build chat result
                                                            LinqResponse.ChatResult chatResult = new LinqResponse.ChatResult();
                                                            chatResult.setConversationId(conversation.getId());
                                                            chatResult.setAssistantId(assistant.getId());
                                                            chatResult.setMessage(fullMessage);
                                                            chatResult.setIntent(intent);
                                                            chatResult.setModelCategory(finalModelCategory);
                                                            chatResult.setModelName(modelName);
                                                            chatResult.setExecutedTasks(
                                                                    new ArrayList<>(taskResults.keySet()));
                                                            chatResult.setTaskResults(taskResults);

                                                            // Extract token usage from raw LLM response and calculate
                                                            // cost
                                                            LinqResponse.ChatResult.TokenUsage tokenUsage = extractTokenUsageFromResponse(
                                                                    response, finalModelCategory, modelName);
                                                            if (tokenUsage != null) {
                                                                chatResult.setTokenUsage(tokenUsage);
                                                                log.info(
                                                                        "📊 Chat token usage - prompt: {}, completion: {}, total: {}, cost: ${}",
                                                                        tokenUsage.getPromptTokens(),
                                                                        tokenUsage.getCompletionTokens(),
                                                                        tokenUsage.getTotalTokens(),
                                                                        String.format("%.6f", tokenUsage.getCostUsd()));
                                                            } else {
                                                                log.warn(
                                                                        "⚠️ Could not extract token usage from LLM response");
                                                            }

                                                            // Add Knowledge Graph documents to metadata for frontend
                                                            // display
                                                            Map<String, Object> metadata = new HashMap<>();
                                                            @SuppressWarnings("unchecked")
                                                            List<Map<String, Object>> graphDocuments = (List<Map<String, Object>>) graphContext
                                                                    .getOrDefault("documents", Collections.emptyList());

                                                            if (!graphDocuments.isEmpty()) {
                                                                // Format documents for frontend - deduplicate by
                                                                // documentId
                                                                // One documentId = one document, regardless of how many
                                                                // entities were extracted
                                                                // Knowledge Graph may extract multiple form entities
                                                                // from the same document,
                                                                // but they all reference the same documentId/fileName
                                                                // Let the frontend decide whether to show documents
                                                                // based on user intent,
                                                                // rather than trying to parse the LLM response (which
                                                                // is brittle)
                                                                Map<String, Map<String, Object>> uniqueDocs = new LinkedHashMap<>();
                                                                for (Map<String, Object> doc : graphDocuments) {
                                                                    String docId = (String) doc.get("documentId");
                                                                    if (docId != null && !docId.isEmpty()
                                                                            && !uniqueDocs.containsKey(docId)) {
                                                                        String fileName = (String) doc.get("fileName");

                                                                        Map<String, Object> formatted = new HashMap<>();
                                                                        formatted.put("documentId", docId);
                                                                        formatted.put("fileName", fileName);
                                                                        formatted.put("name", fileName); // Frontend
                                                                                                         // expects
                                                                                                         // 'name'

                                                                        // Use fileName as the primary identifier and
                                                                        // title
                                                                        // Don't join entity names - they can be
                                                                        // variations/duplicates of the same form
                                                                        formatted.put("title", fileName);
                                                                        formatted.put("formName", null); // Not used -
                                                                                                         // frontend
                                                                                                         // displays
                                                                                                         // fileName

                                                                        uniqueDocs.put(docId, formatted);
                                                                    }
                                                                }

                                                                // Convert to list
                                                                List<Map<String, Object>> formattedDocs = new ArrayList<>(
                                                                        uniqueDocs.values());
                                                                metadata.put("documents", formattedDocs);

                                                                // Also add to taskResults so frontend can display them
                                                                // alongside RAG documents
                                                                // Create a synthetic task result for Knowledge Graph
                                                                // documents
                                                                Map<String, Object> graphTaskResult = new HashMap<>();
                                                                graphTaskResult.put("documents", formattedDocs);
                                                                graphTaskResult.put("source", "knowledge_graph");
                                                                taskResults.put("_knowledgeGraph", graphTaskResult);

                                                                log.debug(
                                                                        "Added {} Knowledge Graph documents to chat response metadata and taskResults",
                                                                        formattedDocs.size());
                                                            }
                                                            chatResult.setMetadata(metadata);
                                                            chatResult.setTaskResults(taskResults); // Update
                                                                                                    // taskResults with
                                                                                                    // Knowledge Graph
                                                                                                    // documents

                                                            LinqResponse chatResponse = new LinqResponse();
                                                            chatResponse.setChatResult(chatResult);
                                                            chatResponse.setMetadata(response.getMetadata());

                                                            // Publish complete response
                                                            publishChatUpdate("LLM_RESPONSE_COMPLETE", Map.of(
                                                                    "conversationId", conversation.getId(),
                                                                    "message", fullMessage,
                                                                    "tokenUsage", tokenUsage != null ? Map.of(
                                                                            "promptTokens",
                                                                            tokenUsage.getPromptTokens(),
                                                                            "completionTokens",
                                                                            tokenUsage.getCompletionTokens(),
                                                                            "totalTokens", tokenUsage.getTotalTokens(),
                                                                            "costUsd", tokenUsage.getCostUsd())
                                                                            : Map.of()));

                                                            // Log chat execution completed
                                                            long durationMs = java.time.Duration
                                                                    .between(startTime, LocalDateTime.now()).toMillis();
                                                            Map<String, Object> completionContext = new HashMap<>();
                                                            completionContext.put("conversationId", conversationId);
                                                            completionContext.put("assistantId", assistantId);
                                                            completionContext.put("assistantName", assistant.getName());
                                                            completionContext.put("teamId", teamId);
                                                            completionContext.put("modelCategory", finalModelCategory);
                                                            completionContext.put("modelName", modelName);
                                                            completionContext.put("intent", intent);
                                                            completionContext.put("durationMs", durationMs);
                                                            completionContext.put("completionTimestamp",
                                                                    LocalDateTime.now().toString());
                                                            completionContext.put("executedTasks",
                                                                    new ArrayList<>(taskResults.keySet()));
                                                            completionContext.put("taskCount", taskResults.size());
                                                            if (tokenUsage != null) {
                                                                completionContext.put("tokenUsage", Map.of(
                                                                        "promptTokens", tokenUsage.getPromptTokens(),
                                                                        "completionTokens",
                                                                        tokenUsage.getCompletionTokens(),
                                                                        "totalTokens", tokenUsage.getTotalTokens(),
                                                                        "costUsd", tokenUsage.getCostUsd()));
                                                            }
                                                            if (executedBy != null) {
                                                                completionContext.put("executedBy", executedBy);
                                                            }

                                                            // Log audit event only if audit logging is enabled
                                                            boolean isAuditEnabled = assistant.getGuardrails() != null
                                                                    &&
                                                                    Boolean.TRUE.equals(assistant.getGuardrails()
                                                                            .getAuditLoggingEnabled());
                                                            Mono<Void> completionAuditLog = isAuditEnabled
                                                                    ? auditLogHelper.logDetailedEvent(
                                                                            AuditEventType.CHAT_EXECUTION_COMPLETED,
                                                                            AuditActionType.READ,
                                                                            AuditResourceType.CHAT,
                                                                            conversationId,
                                                                            String.format(
                                                                                    "Chat execution completed successfully for assistant '%s'",
                                                                                    assistant.getName()),
                                                                            completionContext,
                                                                            null,
                                                                            null,
                                                                            AuditResultType.SUCCESS)
                                                                            .doOnError(auditError -> log.error(
                                                                                    "Failed to log audit event (chat execution completed): {}",
                                                                                    auditError.getMessage(),
                                                                                    auditError))
                                                                            .onErrorResume(auditError -> Mono.empty())
                                                                    : Mono.empty();

                                                            return completionAuditLog.thenReturn(chatResponse);
                                                        })
                                                        .onErrorResume(error -> {
                                                            // Log chat execution failure
                                                            long durationMs = java.time.Duration
                                                                    .between(startTime, LocalDateTime.now()).toMillis();
                                                            Map<String, Object> errorContext = new HashMap<>();
                                                            errorContext.put("conversationId", conversationId);
                                                            errorContext.put("assistantId", assistantId);
                                                            errorContext.put("assistantName", assistant.getName());
                                                            errorContext.put("teamId", teamId);
                                                            errorContext.put("modelCategory", finalModelCategory);
                                                            errorContext.put("modelName", modelName);
                                                            errorContext.put("error", error.getMessage());
                                                            errorContext.put("errorType",
                                                                    error.getClass().getSimpleName());
                                                            errorContext.put("durationMs", durationMs);
                                                            errorContext.put("failureTimestamp",
                                                                    LocalDateTime.now().toString());
                                                            if (executedBy != null) {
                                                                errorContext.put("executedBy", executedBy);
                                                            }

                                                            // Log audit event only if audit logging is enabled
                                                            boolean isAuditEnabled = assistant.getGuardrails() != null
                                                                    &&
                                                                    Boolean.TRUE.equals(assistant.getGuardrails()
                                                                            .getAuditLoggingEnabled());
                                                            Mono<Void> failureAuditLog = isAuditEnabled
                                                                    ? auditLogHelper.logDetailedEvent(
                                                                            AuditEventType.CHAT_EXECUTION_FAILED,
                                                                            AuditActionType.READ,
                                                                            AuditResourceType.CHAT,
                                                                            conversationId,
                                                                            String.format(
                                                                                    "Chat execution failed for assistant '%s': %s",
                                                                                    assistant.getName(),
                                                                                    error.getMessage()),
                                                                            errorContext,
                                                                            null,
                                                                            null,
                                                                            AuditResultType.FAILED)
                                                                            .doOnError(auditError -> log.error(
                                                                                    "Failed to log audit event (chat execution failed): {}",
                                                                                    auditError.getMessage(),
                                                                                    auditError))
                                                                            .onErrorResume(auditError -> Mono.empty())
                                                                    : Mono.empty();

                                                            return failureAuditLog.then(Mono.error(error));
                                                        });
                                            });
                                });
                    });
        }));
    }

    /**
     * Execute Agent Tasks from assistant's selectedTasks
     * Returns a map of taskId -> taskResult
     */
    private Mono<Map<String, Object>> executeAgentTasks(
            List<AIAssistant.SelectedTask> selectedTasks,
            String userMessage,
            String teamId,
            String executedBy) {

        if (selectedTasks == null || selectedTasks.isEmpty()) {
            return Mono.just(new HashMap<>());
        }

        // Execute all tasks in parallel
        List<Mono<Map.Entry<String, Object>>> taskMonoList = selectedTasks.stream()
                .map(selectedTask -> {
                    return agentTaskService.getTaskById(selectedTask.getTaskId())
                            .flatMap(task -> {
                                // Only execute if task is enabled
                                if (!task.isEnabled()) {
                                    log.debug("Skipping disabled task: {}", task.getName());
                                    return Mono.just(Map.entry(selectedTask.getTaskId(), (Object) "Task is disabled"));
                                }

                                // Build LinqRequest from task's linqConfig
                                if (task.getLinqConfig() == null) {
                                    log.warn("Task {} has no linqConfig, skipping", task.getName());
                                    return Mono.just(
                                            Map.entry(selectedTask.getTaskId(), (Object) "Task has no configuration"));
                                }

                                // Get agent for execution context
                                return agentRepository.findById(task.getAgentId())
                                        .flatMap(agent -> {
                                            // Execute task with proper AgentExecution tracking
                                            return executeTaskWithTracking(task, agent, userMessage, teamId, executedBy)
                                                    .map(result -> {
                                                        log.info(
                                                                "Task {} executed successfully with proper tracking, result: {}",
                                                                task.getName(), result != null ? "present" : "null");
                                                        return Map.entry(selectedTask.getTaskId(),
                                                                result != null ? result : "No result");
                                                    })
                                                    .onErrorResume(error -> {
                                                        log.error("Failed to execute task {}: {}", task.getName(),
                                                                error.getMessage());
                                                        return Mono.just(Map.entry(selectedTask.getTaskId(),
                                                                (Object) ("Error: " + error.getMessage())));
                                                    });
                                        })
                                        .switchIfEmpty(Mono.defer(() -> {
                                            log.error("Agent not found for task {}: agentId={}", task.getName(),
                                                    task.getAgentId());
                                            return Mono.just(
                                                    Map.entry(selectedTask.getTaskId(), (Object) "Agent not found"));
                                        }))
                                        .onErrorResume(error -> {
                                            log.error("Error executing task {}: {}", task.getName(),
                                                    error.getMessage());
                                            return Mono.just(Map.entry(selectedTask.getTaskId(),
                                                    (Object) ("Error: " + error.getMessage())));
                                        });
                            })
                            .onErrorResume(error -> {
                                log.error("Failed to get task {}: {}", selectedTask.getTaskId(), error.getMessage());
                                return Mono.just(Map.entry(selectedTask.getTaskId(),
                                        (Object) ("Task not found: " + error.getMessage())));
                            });
                })
                .toList();

        // Combine all task results
        return Flux.fromIterable(taskMonoList)
                .flatMap(Mono::from)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }

    /**
     * Enrich messages with task results and Knowledge Graph context
     */
    private List<Map<String, Object>> enrichMessagesWithContext(
            List<Map<String, Object>> messages,
            Map<String, Object> taskResults,
            Map<String, Object> graphContext,
            String systemPrompt) {

        // Create a copy of messages
        List<Map<String, Object>> enrichedMessages = new ArrayList<>(messages);

        // Build enriched system prompt with task results and graph context
        StringBuilder enrichedContext = new StringBuilder();

        // Start with original system prompt if available
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            enrichedContext.append(systemPrompt).append("\n\n");
        }

        // Add Knowledge Graph context if available
        if (graphContext != null && !graphContext.isEmpty()) {
            Integer entityCount = (Integer) graphContext.getOrDefault("entityCount", 0);
            if (entityCount > 0) {
                enrichedContext.append("=== Knowledge Graph Context ===\n");
                enrichedContext.append(graphContext.getOrDefault("summary", "")).append("\n");

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entities = (List<Map<String, Object>>) graphContext.getOrDefault("entities",
                        Collections.emptyList());
                if (!entities.isEmpty()) {
                    enrichedContext.append("\nRelevant Entities:\n");
                    for (Map<String, Object> entity : entities) {
                        String type = (String) entity.getOrDefault("type", "Unknown");
                        String name = (String) entity.getOrDefault("name",
                                (String) entity.getOrDefault("id", "Unnamed"));
                        String id = (String) entity.getOrDefault("id", "");

                        enrichedContext.append("- ").append(name).append(" (").append(type);
                        if (!id.isEmpty() && !id.equals(name)) {
                            enrichedContext.append(", ID: ").append(id);
                        }
                        enrichedContext.append(")\n");

                        // Add key properties (exclude system fields)
                        entity.entrySet().stream()
                                .filter(e -> !isSystemField(e.getKey()))
                                .forEach(e -> {
                                    Object value = e.getValue();
                                    if (value != null && !(value instanceof Map)) {
                                        // Handle List values (arrays) by converting to string representation
                                        if (value instanceof List) {
                                            enrichedContext.append("  - ").append(e.getKey()).append(": ").append(value)
                                                    .append("\n");
                                        } else {
                                            enrichedContext.append("  - ").append(e.getKey()).append(": ").append(value)
                                                    .append("\n");
                                        }
                                    }
                                });
                    }
                }

                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relationships = (List<Map<String, Object>>) graphContext
                        .getOrDefault("relationships", Collections.emptyList());
                if (!relationships.isEmpty() && relationships.size() <= 5) {
                    enrichedContext.append("\nRelevant Relationships:\n");
                    for (Map<String, Object> rel : relationships) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> from = (Map<String, Object>) rel.getOrDefault("fromEntity", Map.of());
                        @SuppressWarnings("unchecked")
                        Map<String, Object> to = (Map<String, Object>) rel.getOrDefault("toEntity", Map.of());
                        String relType = (String) rel.getOrDefault("relationshipType", "RELATED_TO");

                        enrichedContext.append("- ")
                                .append(from.getOrDefault("name", from.get("id")))
                                .append(" [").append(relType).append("] ")
                                .append(to.getOrDefault("name", to.get("id")))
                                .append("\n");
                    }
                }

                // Add document information from Knowledge Graph
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> documents = (List<Map<String, Object>>) graphContext.getOrDefault("documents",
                        Collections.emptyList());
                if (!documents.isEmpty()) {
                    enrichedContext.append("\n=== Available Documents from Knowledge Graph (")
                            .append(documents.size())
                            .append(" documents) ===\n");
                    enrichedContext.append(
                            "The following documents are available and may be relevant to answer the user's question:\n\n");
                    int docIndex = 1;
                    for (Map<String, Object> doc : documents) {
                        String documentId = (String) doc.getOrDefault("documentId", "");
                        String fileName = (String) doc.getOrDefault("fileName", "Unknown");

                        enrichedContext.append(docIndex++).append(". ").append(fileName);
                        if (!documentId.isEmpty()) {
                            enrichedContext.append(" (Document ID: ").append(documentId).append(")");
                        }
                        enrichedContext.append("\n");
                    }

                }
                enrichedContext.append("\n");
            }
        }

        // Add task results if available
        if (!taskResults.isEmpty()) {
            enrichedContext.append("=== Agent Task Results ===\n");
            for (Map.Entry<String, Object> entry : taskResults.entrySet()) {
                enrichedContext.append("- Task ").append(entry.getKey()).append(": ");
                if (entry.getValue() instanceof String) {
                    enrichedContext.append(entry.getValue());
                } else {
                    try {
                        enrichedContext.append(objectMapper.valueToTree(entry.getValue()).toString());
                    } catch (Exception e) {
                        enrichedContext.append(entry.getValue().toString());
                    }
                }
                enrichedContext.append("\n");
            }
            enrichedContext.append("\n");
        }

        // Find existing system message or add new one at the beginning
        boolean hasSystemMessage = false;
        for (Map<String, Object> msg : enrichedMessages) {
            if ("system".equals(msg.get("role"))) {
                // Append to existing system message if there's new context
                String existingContent = (String) msg.get("content");
                if (enrichedContext.length() > 0
                        && (systemPrompt == null || !existingContent.contains(enrichedContext.toString()))) {
                    msg.put("content", enrichedContext.toString());
                }
                hasSystemMessage = true;
                break;
            }
        }

        if (!hasSystemMessage && enrichedContext.length() > 0) {
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", enrichedContext.toString());
            enrichedMessages.add(0, systemMsg);
        }

        return enrichedMessages;
    }

    private boolean isSystemField(String key) {
        return key.equals("id") || key.equals("name") || key.equals("type") ||
                key.equals("teamId") || key.equals("documentId") ||
                key.equals("extractedAt") || key.equals("createdAt") || key.equals("updatedAt") ||
                key.equals("relatedEntities") || key.equals("relationships");
    }

    /**
     * Execute Agent Task with proper AgentExecution tracking (for chat context)
     * Uses startTaskExecution for proper validation and tracking, then waits for
     * completion synchronously
     */
    private Mono<Object> executeTaskWithTracking(
            AgentTask task,
            Agent agent,
            String userMessage,
            String teamId,
            String executedBy) {

        // Prepare input overrides (question parameter from user message)
        Map<String, Object> inputOverrides = new HashMap<>();
        if (userMessage != null && !userMessage.trim().isEmpty()) {
            inputOverrides.put("question", userMessage);
        }

        // Use startTaskExecution for proper validation, tracking, and execution flow
        return agentExecutionService.startTaskExecution(
                agent.getId(),
                task.getId(),
                teamId,
                executedBy,
                inputOverrides)
                .flatMap(execution -> {
                    // Wait for execution to complete and get the workflow result
                    return waitForExecutionCompletion(execution)
                            .flatMap(completedExecution -> {
                                // Get workflow result from LinqWorkflowExecution
                                if (completedExecution.getWorkflowExecutionId() != null) {
                                    return workflowExecutionService
                                            .getExecution(completedExecution.getWorkflowExecutionId())
                                            .map(workflowExecution -> {
                                                // Extract structured result from workflow response:
                                                // - answer: finalResult (string)
                                                // - documents: step 1 results (e.g. Milvus search results) when
                                                // available
                                                Object result = "No result";
                                                if (workflowExecution.getResponse() != null) {
                                                    LinqResponse response = workflowExecution.getResponse();
                                                    Object rawResult = response.getResult();

                                                    if (rawResult instanceof LinqResponse.WorkflowResult workflowResult) {
                                                        String answer = workflowResult.getFinalResult();
                                                        Object rawDocuments = null;

                                                        // Try to extract documents from step 1 (or first api-gateway
                                                        // step)
                                                        if (workflowResult.getSteps() != null) {
                                                            for (LinqResponse.WorkflowStep step : workflowResult
                                                                    .getSteps()) {
                                                                Object stepResult = step.getResult();
                                                                if (stepResult instanceof java.util.Map<?, ?> stepMap) {
                                                                    // Prefer the "results" field when present (Milvus
                                                                    // search convention)
                                                                    if (stepMap.containsKey("results")) {
                                                                        rawDocuments = stepMap.get("results");
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        java.util.Map<String, Object> structured = new java.util.HashMap<>();
                                                        structured.put("answer", answer);

                                                        // Extract and deduplicate unique documents from RAG search
                                                        // results
                                                        // RAG results contain multiple chunks per document, so we need
                                                        // to deduplicate by documentId
                                                        if (rawDocuments instanceof List<?> rawResultsList) {
                                                            Map<String, Map<String, Object>> uniqueDocs = new LinkedHashMap<>();

                                                            log.debug(
                                                                    "Extracting unique documents from {} RAG search results",
                                                                    rawResultsList.size());

                                                            for (Object rawResultObj : rawResultsList) {
                                                                if (rawResultObj instanceof Map<?, ?> record) {
                                                                    @SuppressWarnings("unchecked")
                                                                    Map<String, Object> recordMap = (Map<String, Object>) record;

                                                                    // Extract documentId and fileName from RAG search
                                                                    // result record
                                                                    String docId = recordMap.get("documentId") != null
                                                                            ? recordMap.get("documentId").toString()
                                                                            : null;
                                                                    String fileName = recordMap.get("fileName") != null
                                                                            ? recordMap.get("fileName").toString()
                                                                            : null;

                                                                    log.debug(
                                                                            "RAG result record - documentId: {}, fileName: {}",
                                                                            docId, fileName);

                                                                    // Deduplicate by documentId (one documentId = one
                                                                    // document)
                                                                    if (docId != null && !docId.isEmpty()
                                                                            && !uniqueDocs.containsKey(docId)) {
                                                                        Map<String, Object> doc = new HashMap<>();
                                                                        doc.put("documentId", docId);
                                                                        doc.put("fileName", fileName);
                                                                        doc.put("title", fileName);
                                                                        doc.put("source", "rag_search");
                                                                        uniqueDocs.put(docId, doc);
                                                                        log.debug(
                                                                                "Added unique document from RAG: documentId={}, fileName={}",
                                                                                docId, fileName);
                                                                    } else if (fileName != null && !fileName.isEmpty()
                                                                            && docId == null) {
                                                                        // Fallback: deduplicate by fileName if no
                                                                        // documentId
                                                                        String fileNameKey = fileName.toLowerCase();
                                                                        if (!uniqueDocs.containsKey(
                                                                                "_fileName_" + fileNameKey)) {
                                                                            Map<String, Object> doc = new HashMap<>();
                                                                            doc.put("documentId", null);
                                                                            doc.put("fileName", fileName);
                                                                            doc.put("title", fileName);
                                                                            doc.put("source", "rag_search");
                                                                            uniqueDocs.put("_fileName_" + fileNameKey,
                                                                                    doc);
                                                                            log.debug(
                                                                                    "Added unique document from RAG (by fileName): fileName={}",
                                                                                    fileName);
                                                                        }
                                                                    }
                                                                }
                                                            }

                                                            if (!uniqueDocs.isEmpty()) {
                                                                List<Map<String, Object>> formattedDocs = new ArrayList<>(
                                                                        uniqueDocs.values());
                                                                structured.put("documents", formattedDocs);
                                                                log.info(
                                                                        "Extracted {} unique documents from RAG search results",
                                                                        formattedDocs.size());
                                                            } else {
                                                                log.warn(
                                                                        "No unique documents extracted from RAG search results - rawResultsList size: {}",
                                                                        rawResultsList.size());
                                                            }
                                                        } else if (rawDocuments != null) {
                                                            // Fallback: keep as-is if not a list
                                                            structured.put("documents", rawDocuments);
                                                            log.debug("RAG documents not a list, keeping as-is: {}",
                                                                    rawDocuments.getClass().getSimpleName());
                                                        } else {
                                                            log.debug("No RAG documents found in workflow result");
                                                        }

                                                        result = structured;
                                                    } else if (rawResult != null) {
                                                        // Non-workflow result, keep as-is
                                                        result = rawResult;
                                                    }
                                                }
                                                return result;
                                            })
                                            .onErrorResume(error -> {
                                                log.error("Failed to get workflow result for execution {}: {}",
                                                        completedExecution.getExecutionId(), error.getMessage());
                                                return Mono.just("Error retrieving result: " + error.getMessage());
                                            });
                                } else {
                                    log.warn("Execution {} has no workflowExecutionId",
                                            completedExecution.getExecutionId());
                                    return Mono.just("No workflow execution linked");
                                }
                            });
                });
    }

    /**
     * Wait for AgentExecution to complete by polling status
     */
    private Mono<AgentExecution> waitForExecutionCompletion(AgentExecution execution) {
        return agentExecutionRepository.findByExecutionId(execution.getExecutionId())
                .flatMap(currentExecution -> {
                    ExecutionStatus status = currentExecution.getStatus();
                    if (status == ExecutionStatus.COMPLETED || status == ExecutionStatus.FAILED
                            || status == ExecutionStatus.TIMEOUT || status == ExecutionStatus.CANCELLED) {
                        // Execution completed (successfully or with error)
                        log.info("Execution {} completed with status: {}", execution.getExecutionId(), status);
                        return Mono.just(currentExecution);
                    } else {
                        // Still running, wait and check again
                        log.debug("Execution {} still running (status: {}), waiting 500ms before checking again",
                                execution.getExecutionId(), status);
                        return Mono.delay(java.time.Duration.ofMillis(500))
                                .then(waitForExecutionCompletion(execution));
                    }
                })
                .timeout(java.time.Duration.ofMinutes(10)) // Max 10 minutes timeout
                .onErrorResume(error -> {
                    log.error("Error waiting for execution {} to complete: {}", execution.getExecutionId(),
                            error.getMessage());
                    // Return the execution as-is (might be in error state)
                    return agentExecutionRepository.findByExecutionId(execution.getExecutionId())
                            .switchIfEmpty(Mono.just(execution));
                });
    }

    /**
     * Extract user message from messages list
     */
    private String extractUserMessage(List<Map<String, Object>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            if ("user".equals(msg.get("role"))) {
                return (String) msg.get("content");
            }
        }
        return null;
    }

    private String extractIntent(String message, AIAssistant assistant) {
        // For now, return a default intent
        // TODO: Implement intent classification using the assistant's default model
        return "user_query";
    }

    /**
     * Extract token usage from raw LLM response and calculate cost
     */
    private LinqResponse.ChatResult.TokenUsage extractTokenUsageFromResponse(
            LinqResponse response, String modelCategory, String modelName) {

        if (response.getResult() == null) {
            return null;
        }

        Object result = response.getResult();
        if (!(result instanceof Map)) {
            return null;
        }

        Map<String, Object> resultMap = (Map<String, Object>) result;
        LinqResponse.ChatResult.TokenUsage tokenUsage = new LinqResponse.ChatResult.TokenUsage();
        boolean hasTokenUsage = false;

        // Extract token usage based on model category
        if ("openai-chat".equals(modelCategory) && resultMap.containsKey("usage")) {
            Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
            if (usage != null) {
                long promptTokens = usage.containsKey("prompt_tokens")
                        ? ((Number) usage.get("prompt_tokens")).longValue()
                        : 0;
                long completionTokens = usage.containsKey("completion_tokens")
                        ? ((Number) usage.get("completion_tokens")).longValue()
                        : 0;
                long totalTokens = usage.containsKey("total_tokens")
                        ? ((Number) usage.get("total_tokens")).longValue()
                        : (promptTokens + completionTokens);

                String model = resultMap.containsKey("model")
                        ? (String) resultMap.get("model")
                        : modelName;

                tokenUsage.setPromptTokens(promptTokens);
                tokenUsage.setCompletionTokens(completionTokens);
                tokenUsage.setTotalTokens(totalTokens);

                // Calculate cost
                double cost = llmCostService.calculateCost(model, promptTokens, completionTokens);
                tokenUsage.setCostUsd(cost);

                hasTokenUsage = true;
            }
        } else if ("gemini-chat".equals(modelCategory) && resultMap.containsKey("usageMetadata")) {
            Map<?, ?> usageMetadata = (Map<?, ?>) resultMap.get("usageMetadata");
            if (usageMetadata != null) {
                long promptTokens = usageMetadata.containsKey("promptTokenCount")
                        ? ((Number) usageMetadata.get("promptTokenCount")).longValue()
                        : 0;
                long completionTokens = usageMetadata.containsKey("candidatesTokenCount")
                        ? ((Number) usageMetadata.get("candidatesTokenCount")).longValue()
                        : 0;
                long totalTokens = usageMetadata.containsKey("totalTokenCount")
                        ? ((Number) usageMetadata.get("totalTokenCount")).longValue()
                        : (promptTokens + completionTokens);

                String model = resultMap.containsKey("modelVersion")
                        ? (String) resultMap.get("modelVersion")
                        : modelName;

                tokenUsage.setPromptTokens(promptTokens);
                tokenUsage.setCompletionTokens(completionTokens);
                tokenUsage.setTotalTokens(totalTokens);

                // Calculate cost
                double cost = llmCostService.calculateCost(model, promptTokens, completionTokens);
                tokenUsage.setCostUsd(cost);

                hasTokenUsage = true;
            }
        } else if ("claude-chat".equals(modelCategory) && resultMap.containsKey("usage")) {
            Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
            if (usage != null) {
                long promptTokens = usage.containsKey("input_tokens")
                        ? ((Number) usage.get("input_tokens")).longValue()
                        : 0;
                long completionTokens = usage.containsKey("output_tokens")
                        ? ((Number) usage.get("output_tokens")).longValue()
                        : 0;
                long totalTokens = promptTokens + completionTokens;

                String model = resultMap.containsKey("model")
                        ? (String) resultMap.get("model")
                        : modelName;

                tokenUsage.setPromptTokens(promptTokens);
                tokenUsage.setCompletionTokens(completionTokens);
                tokenUsage.setTotalTokens(totalTokens);

                // Calculate cost
                double cost = llmCostService.calculateCost(model, promptTokens, completionTokens);
                tokenUsage.setCostUsd(cost);

                hasTokenUsage = true;
            }
        } else if ("cohere-chat".equals(modelCategory) && resultMap.containsKey("meta")) {
            Map<?, ?> meta = (Map<?, ?>) resultMap.get("meta");
            if (meta != null && meta.containsKey("billed_units")) {
                Map<?, ?> billedUnits = (Map<?, ?>) meta.get("billed_units");
                if (billedUnits != null) {
                    long promptTokens = billedUnits.containsKey("input_tokens")
                            ? ((Number) billedUnits.get("input_tokens")).longValue()
                            : 0;
                    long completionTokens = billedUnits.containsKey("output_tokens")
                            ? ((Number) billedUnits.get("output_tokens")).longValue()
                            : 0;
                    long totalTokens = promptTokens + completionTokens;

                    String model = resultMap.containsKey("model")
                            ? (String) resultMap.get("model")
                            : modelName;

                    tokenUsage.setPromptTokens(promptTokens);
                    tokenUsage.setCompletionTokens(completionTokens);
                    tokenUsage.setTotalTokens(totalTokens);

                    // Calculate cost
                    double cost = llmCostService.calculateCost(model, promptTokens, completionTokens);
                    tokenUsage.setCostUsd(cost);

                    hasTokenUsage = true;
                }
            }
        }

        return hasTokenUsage ? tokenUsage : null;
    }

    private String extractMessageContent(LinqResponse response) {
        // Extract message content from response
        if (response.getResult() instanceof Map) {
            Map<String, Object> result = (Map<String, Object>) response.getResult();

            // Try Gemini format: { "candidates": [ { "content": { "parts": [ { "text":
            // "..." } ] } } ] }
            if (result.containsKey("candidates")) {
                Object candidatesObj = result.get("candidates");
                if (candidatesObj instanceof List) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> candidates = (List<Map<String, Object>>) candidatesObj;
                    if (!candidates.isEmpty()) {
                        Map<String, Object> candidate = candidates.get(0);
                        if (candidate != null && candidate.containsKey("content")) {
                            Object contentObj = candidate.get("content");
                            if (contentObj instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> content = (Map<String, Object>) contentObj;
                                if (content.containsKey("parts")) {
                                    Object partsObj = content.get("parts");
                                    if (partsObj instanceof List) {
                                        @SuppressWarnings("unchecked")
                                        List<Map<String, Object>> parts = (List<Map<String, Object>>) partsObj;
                                        if (!parts.isEmpty() && parts.get(0) != null
                                                && parts.get(0).containsKey("text")) {
                                            String text = (String) parts.get(0).get("text");
                                            if (text != null && !text.trim().isEmpty()) {
                                                return text;
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Try Cohere format: { "text": "..." } (text field at root level)
            if (result.containsKey("text")) {
                Object textObj = result.get("text");
                if (textObj instanceof String) {
                    String text = (String) textObj;
                    if (text != null && !text.trim().isEmpty()) {
                        return text;
                    }
                }
            }

            // Try Claude/Anthropic format: { "content": [ { "text": "..." } ] } or {
            // "content": "..." }
            if (result.containsKey("content")) {
                Object contentObj = result.get("content");
                if (contentObj == null) {
                    log.warn("Content field exists but is null in response with keys: {}", result.keySet());
                } else if (contentObj instanceof String) {
                    String contentStr = (String) contentObj;
                    if (!contentStr.trim().isEmpty()) {
                        return contentStr;
                    }
                } else if (contentObj instanceof List) {
                    List<?> contentList = (List<?>) contentObj;
                    if (!contentList.isEmpty()) {
                        Object firstItem = contentList.get(0);
                        if (firstItem instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> firstContent = (Map<String, Object>) firstItem;
                            if (firstContent.containsKey("text")) {
                                String text = (String) firstContent.get("text");
                                if (text != null && !text.trim().isEmpty()) {
                                    return text;
                                }
                            }
                        } else if (firstItem instanceof String) {
                            String text = (String) firstItem;
                            if (!text.trim().isEmpty()) {
                                return text;
                            }
                        }
                    }
                }
            }

            // Try Cohere V2 format (top-level message object)
            if (result.containsKey("message")) {
                Object messageObj = result.get("message");
                if (messageObj instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> messageMap = (Map<String, Object>) messageObj;
                    if (messageMap.containsKey("content")) {
                        Object contentObj = messageMap.get("content");
                        if (contentObj instanceof List) {
                            List<?> contentList = (List<?>) contentObj;
                            if (!contentList.isEmpty()) {
                                Object firstItem = contentList.get(0);
                                if (firstItem instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> firstContent = (Map<String, Object>) firstItem;
                                    if (firstContent.containsKey("text")) {
                                        String text = (String) firstContent.get("text");
                                        if (text != null && !text.trim().isEmpty()) {
                                            return text;
                                        }
                                    }
                                }
                            }
                        } else if (contentObj instanceof String) {
                            return (String) contentObj;
                        }
                    }
                }
            }

            // Try OpenAI format
            if (result.containsKey("choices") && result.get("choices") instanceof List) {
                List<?> choices = (List<?>) result.get("choices");
                if (!choices.isEmpty() && choices.get(0) instanceof Map) {
                    Map<String, Object> choice = (Map<String, Object>) choices.get(0);
                    if (choice.containsKey("message") && choice.get("message") instanceof Map) {
                        Map<String, Object> message = (Map<String, Object>) choice.get("message");
                        if (message.containsKey("content")) {
                            return (String) message.get("content");
                        }
                    }
                }
            }
        }

        log.warn("Could not extract message content from LLM response. Response result type: {}, keys: {}",
                response.getResult() != null ? response.getResult().getClass().getName() : "null",
                response.getResult() instanceof Map ? ((Map<?, ?>) response.getResult()).keySet() : "N/A");
        return "No response received";
    }

    private Mono<ConversationMessage> saveUserMessage(
            Conversation conversation,
            String message,
            Map<String, Object> context,
            AIAssistant assistant,
            String executedBy) {
        // Check if PII detection is enabled
        boolean piiDetectionEnabled = assistant.getGuardrails() != null &&
                Boolean.TRUE.equals(assistant.getGuardrails().getPiiDetectionEnabled());
        boolean auditLoggingEnabled = assistant.getGuardrails() != null &&
                Boolean.TRUE.equals(assistant.getGuardrails().getAuditLoggingEnabled());

        if (!piiDetectionEnabled) {
            // PII detection disabled, save message as-is
            ConversationMessage userMessage = ConversationMessage.builder()
                    .conversationId(conversation.getId())
                    .role("USER")
                    .content(message)
                    .timestamp(LocalDateTime.now())
                    .metadata(ConversationMessage.MessageMetadata.builder()
                            .intent("user_query")
                            .piiDetected(false)
                            .additionalData(context != null ? context : new HashMap<>())
                            .build())
                    .build();
            return conversationService.addMessage(userMessage);
        }

        // PII detection enabled - detect and automatically redact if PII is found
        // Redaction is automatic when PII detection is enabled for compliance
        return piiDetectionService.detectPII(message, true)
                .flatMap(piiResult -> {
                    String finalContent = piiResult.isPiiDetected()
                            ? piiResult.getRedactedContent()
                            : message;

                    // Build message metadata
                    ConversationMessage.MessageMetadata.MessageMetadataBuilder metadataBuilder = ConversationMessage.MessageMetadata
                            .builder()
                            .intent("user_query")
                            .piiDetected(piiResult.isPiiDetected());

                    // Add additional context data
                    Map<String, Object> additionalData = context != null ? new HashMap<>(context) : new HashMap<>();
                    metadataBuilder.additionalData(additionalData);

                    ConversationMessage userMessage = ConversationMessage.builder()
                            .conversationId(conversation.getId())
                            .role("USER")
                            .content(finalContent)
                            .timestamp(LocalDateTime.now())
                            .metadata(metadataBuilder.build())
                            .build();

                    // Audit log PII detection if enabled
                    Mono<Void> auditLogMono = Mono.empty();
                    if (piiResult.isPiiDetected() && auditLoggingEnabled) {
                        auditLogMono = logPIIDetection(
                                conversation.getId(),
                                assistant.getId(),
                                assistant.getTeamId(),
                                executedBy != null ? executedBy : "system",
                                "USER",
                                piiResult);
                    }

                    return auditLogMono.then(conversationService.addMessage(userMessage));
                });
    }

    private Mono<ConversationMessage> saveAssistantMessage(
            Conversation conversation,
            LinqResponse chatResponse,
            AIAssistant assistant,
            String executedBy) {
        LinqResponse.ChatResult chatResult = chatResponse.getChatResult();
        String assistantMessageContent = chatResult.getMessage();

        // Check if PII detection is enabled
        boolean piiDetectionEnabled = assistant.getGuardrails() != null &&
                Boolean.TRUE.equals(assistant.getGuardrails().getPiiDetectionEnabled());
        boolean auditLoggingEnabled = assistant.getGuardrails() != null &&
                Boolean.TRUE.equals(assistant.getGuardrails().getAuditLoggingEnabled());

        if (!piiDetectionEnabled) {
            Map<String, Object> additionalData = chatResult.getMetadata() != null
                    ? new HashMap<>(chatResult.getMetadata())
                    : new HashMap<>();
            Object documents = additionalData.remove("documents"); // Extract and remove

            // PII detection disabled, save message as-is
            ConversationMessage.MessageMetadata metadata = ConversationMessage.MessageMetadata.builder()
                    .intent(chatResult.getIntent())
                    .executedTasks(chatResult.getExecutedTasks())
                    .taskResults(chatResult.getTaskResults())
                    .modelCategory(chatResult.getModelCategory())
                    .modelName(chatResult.getModelName())
                    .piiDetected(false)
                    .tokenUsage(chatResult.getTokenUsage() != null
                            ? ConversationMessage.MessageMetadata.TokenUsage.builder()
                                    .promptTokens(chatResult.getTokenUsage().getPromptTokens())
                                    .completionTokens(chatResult.getTokenUsage().getCompletionTokens())
                                    .totalTokens(chatResult.getTokenUsage().getTotalTokens())
                                    .costUsd(chatResult.getTokenUsage().getCostUsd())
                                    .build()
                            : null)
                    .additionalData(additionalData)
                    .documents(documents)
                    .build();

            ConversationMessage assistantMessage = ConversationMessage.builder()
                    .conversationId(conversation.getId())
                    .role("ASSISTANT")
                    .content(assistantMessageContent)
                    .timestamp(LocalDateTime.now())
                    .metadata(metadata)
                    .build();

            return conversationService.addMessage(assistantMessage);
        }

        // PII detection enabled - detect and automatically redact if PII is found
        // Redaction is automatic when PII detection is enabled for compliance
        return piiDetectionService.detectPII(assistantMessageContent, true).flatMap(piiResult ->

        {
            String finalContent = piiResult.isPiiDetected()
                    ? piiResult.getRedactedContent()
                    : assistantMessageContent;

            Map<String, Object> additionalData = chatResult.getMetadata() != null
                    ? new HashMap<>(chatResult.getMetadata())
                    : new HashMap<>();
            Object documents = additionalData.remove("documents"); // Extract and remove

            // Build message metadata
            ConversationMessage.MessageMetadata.MessageMetadataBuilder metadataBuilder = ConversationMessage.MessageMetadata
                    .builder()
                    .intent(chatResult.getIntent())
                    .executedTasks(chatResult.getExecutedTasks())
                    .taskResults(chatResult.getTaskResults())
                    .modelCategory(chatResult.getModelCategory())
                    .modelName(chatResult.getModelName())
                    .piiDetected(piiResult.isPiiDetected())
                    .tokenUsage(chatResult.getTokenUsage() != null
                            ? ConversationMessage.MessageMetadata.TokenUsage.builder()
                                    .promptTokens(chatResult.getTokenUsage().getPromptTokens())
                                    .completionTokens(chatResult.getTokenUsage().getCompletionTokens())
                                    .totalTokens(chatResult.getTokenUsage().getTotalTokens())
                                    .costUsd(chatResult.getTokenUsage().getCostUsd())
                                    .build()
                            : null)
                    .additionalData(additionalData)
                    .documents(documents);

            ConversationMessage assistantMessage = ConversationMessage.builder()
                    .conversationId(conversation.getId())
                    .role("ASSISTANT")
                    .content(finalContent)
                    .timestamp(LocalDateTime.now())
                    .metadata(metadataBuilder.build())
                    .build();

            // Audit log PII detection if enabled
            Mono<Void> auditLogMono = Mono.empty();
            if (piiResult.isPiiDetected() && auditLoggingEnabled) {
                auditLogMono = logPIIDetection(
                        conversation.getId(),
                        assistant.getId(),
                        assistant.getTeamId(),
                        executedBy != null ? executedBy : "system",
                        "ASSISTANT",
                        piiResult);
            }

            return auditLogMono.then(conversationService.addMessage(assistantMessage));
        });
    }

    /**
     * Log PII detection event to audit log
     */
    private Mono<Void> logPIIDetection(
            String conversationId,
            String assistantId,
            String teamId,
            String executedBy,
            String messageRole,
            PIIDetectionService.PIIDetectionResult piiResult) {

        Map<String, Object> context = new HashMap<>();
        context.put("conversationId", conversationId);
        context.put("assistantId", assistantId);
        context.put("teamId", teamId);
        context.put("executedBy", executedBy);
        context.put("messageRole", messageRole);

        // Add PII summary metadata (types and counts only, no actual PII values)
        Map<String, Object> piiMetadata = piiDetectionService.getPIISummaryMetadata(piiResult);
        context.putAll(piiMetadata);

        int typeCount = piiMetadata.containsKey("piiTypes") && piiMetadata.get("piiTypes") instanceof List
                ? ((List<?>) piiMetadata.get("piiTypes")).size()
                : 0;
        int totalMatches = piiMetadata.containsKey("totalMatches") && piiMetadata.get("totalMatches") instanceof Number
                ? ((Number) piiMetadata.get("totalMatches")).intValue()
                : 0;

        return auditLogHelper.logDetailedEvent(
                AuditEventType.PII_DETECTED,
                AuditActionType.READ,
                AuditResourceType.CHAT,
                conversationId,
                String.format("PII detected in %s message: %d type(s), %d total matches",
                        messageRole, typeCount, totalMatches),
                context,
                null, // documentId
                null, // collectionId
                AuditResultType.SUCCESS)
                .doOnError(error -> log.error("Failed to log PII detection audit event: {}", error.getMessage(), error))
                .onErrorResume(error -> Mono.empty()); // Don't fail if audit logging fails
    }

    /**
     * Publish chat update via WebSocket
     */
    private void publishChatUpdate(String type, Map<String, Object> data) {
        try {
            if (chatMessageChannel == null) {
                log.debug("💬 Chat message channel not available, skipping update: {}", type);
                return;
            }

            Map<String, Object> update = new HashMap<>();
            update.put("type", type);
            update.put("timestamp", LocalDateTime.now().toString());
            update.putAll(data);

            boolean sent = chatMessageChannel.send(MessageBuilder.withPayload(update).build());
            if (sent) {
                log.debug("💬 Published chat update via WebSocket: {} for conversationId: {}",
                        type, data.get("conversationId"));
            } else {
                log.warn("💬 Failed to publish chat update via WebSocket: {} for conversationId: {}",
                        type, data.get("conversationId"));
            }
        } catch (Exception e) {
            log.error("💬 Error publishing chat update via WebSocket: {} - {}", type, e.getMessage(), e);
        }
    }

    /**
     * Stream message chunks via WebSocket to simulate ChatGPT-like streaming
     */
    private void streamMessageChunks(String conversationId, String fullMessage) {
        if (fullMessage == null || fullMessage.isEmpty()) {
            return;
        }

        // Clear any existing cancellation flag for this conversation
        cancellationFlags.put(conversationId, false);

        // Publish streaming started
        publishChatUpdate("LLM_RESPONSE_STREAMING_STARTED", Map.of(
                "conversationId", conversationId));

        // Split message into chunks (words + spaces)
        String[] words = fullMessage.split("(?<= )", -1); // Split on spaces but keep them
        StringBuilder accumulated = new StringBuilder();

        // Stream chunks asynchronously
        Thread streamingThread = new Thread(() -> {
            try {
                for (int i = 0; i < words.length; i++) {
                    // Check for cancellation
                    if (cancellationFlags.getOrDefault(conversationId, false)) {
                        log.info("💬 Streaming cancelled for conversation: {}", conversationId);
                        // Publish streaming cancelled
                        publishChatUpdate("LLM_RESPONSE_STREAMING_CANCELLED", Map.of(
                                "conversationId", conversationId,
                                "accumulated", accumulated.toString()));
                        return;
                    }

                    String chunk = words[i];
                    accumulated.append(chunk);

                    // Publish chunk
                    Map<String, Object> chunkData = new HashMap<>();
                    chunkData.put("conversationId", conversationId);
                    chunkData.put("chunk", chunk);
                    chunkData.put("accumulated", accumulated.toString());
                    chunkData.put("index", i);
                    chunkData.put("total", words.length);

                    publishChatUpdate("LLM_RESPONSE_CHUNK", chunkData);

                    // Small delay between chunks (30ms per word for realistic typing speed)
                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        // Publish streaming cancelled
                        publishChatUpdate("LLM_RESPONSE_STREAMING_CANCELLED", Map.of(
                                "conversationId", conversationId,
                                "accumulated", accumulated.toString()));
                        return;
                    }
                }

                // Publish streaming complete
                publishChatUpdate("LLM_RESPONSE_STREAMING_COMPLETE", Map.of(
                        "conversationId", conversationId,
                        "message", accumulated.toString()));
            } finally {
                // Clean up
                activeStreamingThreads.remove(conversationId);
                cancellationFlags.remove(conversationId);
            }
        });

        streamingThread.start();
        activeStreamingThreads.put(conversationId, streamingThread);
    }

    /**
     * Cancel streaming for a conversation
     */
    public void cancelStreaming(String conversationId) {
        log.info("💬 Cancelling streaming for conversation: {}", conversationId);
        cancellationFlags.put(conversationId, true);

        Thread thread = activeStreamingThreads.get(conversationId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    /**
     * Derive modelCategory from modelName and provider
     * 
     * @param modelName The model name (e.g., "gemini-2.0-flash", "gpt-4o")
     * @param provider  The provider (e.g., "gemini", "openai")
     * @return The derived modelCategory (e.g., "gemini-chat", "openai-chat") or
     *         null if cannot be derived
     */
    private String deriveModelCategory(String modelName, String provider) {
        if (modelName == null || modelName.isEmpty()) {
            return null;
        }

        String lowerModelName = modelName.toLowerCase();
        String lowerProvider = provider != null ? provider.toLowerCase() : "";

        // Try to detect provider from modelName if provider is not provided
        if (lowerProvider.isEmpty()) {
            if (lowerModelName.startsWith("gemini") || lowerModelName.startsWith("embedding-001") ||
                    lowerModelName.startsWith("text-embedding-004")) {
                lowerProvider = "gemini";
            } else if (lowerModelName.startsWith("gpt-") || lowerModelName.startsWith("text-embedding")) {
                lowerProvider = "openai";
            } else if (lowerModelName.startsWith("claude")) {
                lowerProvider = "anthropic";
            } else if (lowerModelName.startsWith("command") || lowerModelName.startsWith("embed")) {
                lowerProvider = "cohere";
            }
        }

        // Determine if it's an embedding model or chat model
        boolean isEmbedding = lowerModelName.contains("embedding") ||
                lowerModelName.contains("embed") ||
                lowerModelName.startsWith("text-embedding");

        // Build modelCategory based on provider and type
        if ("gemini".equals(lowerProvider)) {
            return isEmbedding ? "gemini-embed" : "gemini-chat";
        } else if ("openai".equals(lowerProvider)) {
            return isEmbedding ? "openai-embed" : "openai-chat";
        } else if ("anthropic".equals(lowerProvider)) {
            return "claude-chat"; // Anthropic models are typically chat models
        } else if ("cohere".equals(lowerProvider)) {
            return isEmbedding ? "cohere-embed" : "cohere-chat";
        }

        return null;
    }
}
