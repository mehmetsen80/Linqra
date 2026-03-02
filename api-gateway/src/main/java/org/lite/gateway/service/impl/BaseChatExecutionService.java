package org.lite.gateway.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.AIAssistant;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.entity.Conversation;
import org.lite.gateway.entity.ConversationMessage;
import org.lite.gateway.entity.LinqLlmModel;
import org.lite.gateway.entity.AIAssistant.SelectedTask;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.enums.AuditResultType;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.repository.AIAssistantRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
import org.lite.gateway.repository.AgentTaskRepository;
import org.lite.gateway.repository.DocReviewAssistantRepository;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.service.*;
import org.lite.gateway.util.AuditLogHelper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
public abstract class BaseChatExecutionService implements ChatExecutionService {

    protected final LinqLlmModelService linqLlmModelService;
    protected final LinqLlmModelRepository linqLlmModelRepository;
    protected final LlmCostService llmCostService;
    protected final AuditLogHelper auditLogHelper;
    protected final DocReviewAssistantRepository docReviewAssistantRepository;
    protected final AIAssistantRepository aiAssistantRepository;
    protected final ConversationService conversationService;
    protected final AgentExecutionService agentExecutionService;
    protected final AgentExecutionRepository agentExecutionRepository;
    protected final AgentTaskRepository agentTaskRepository;
    protected final PIIDetectionService piiDetectionService;
    protected final KnowledgeHubGraphContextService knowledgeHubGraphContextService;
    protected final KnowledgeHubDocumentService knowledgeHubDocumentService;
    protected final ObjectMapper objectMapper;

    protected MessageChannel chatMessageChannel;

    public BaseChatExecutionService(
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
            ObjectMapper objectMapper) {
        this.linqLlmModelService = linqLlmModelService;
        this.linqLlmModelRepository = linqLlmModelRepository;
        this.llmCostService = llmCostService;
        this.auditLogHelper = auditLogHelper;
        this.docReviewAssistantRepository = docReviewAssistantRepository;
        this.aiAssistantRepository = aiAssistantRepository;
        this.conversationService = conversationService;
        this.agentExecutionService = agentExecutionService;
        this.agentExecutionRepository = agentExecutionRepository;
        this.agentTaskRepository = agentTaskRepository;
        this.piiDetectionService = piiDetectionService;
        this.knowledgeHubGraphContextService = knowledgeHubGraphContextService;
        this.knowledgeHubDocumentService = knowledgeHubDocumentService;
        this.objectMapper = objectMapper;
    }

    // Streaming state
    protected final Map<String, Thread> activeStreamingThreads = new ConcurrentHashMap<>();
    protected final Map<String, Boolean> cancellationFlags = new ConcurrentHashMap<>();

    public void setChatMessageChannel(MessageChannel chatMessageChannel) {
        this.chatMessageChannel = chatMessageChannel;
    }

    protected String extractTeamId(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object teamObj = request.getQuery().getParams().get("teamId");
            if (teamObj != null) {
                return String.valueOf(teamObj);
            }
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Team ID must be provided in params");
    }

    protected String extractUsername(LinqRequest request) {
        if (request.getQuery() != null && request.getQuery().getParams() != null) {
            Object usernameObj = request.getQuery().getParams().get("username");
            if (usernameObj != null) {
                return String.valueOf(usernameObj);
            }
        }
        // Fallback to executedBy (username)
        return request.getExecutedBy();
    }

    protected Mono<String> resolveModelCategory(AIAssistant assistant) {
        if (assistant.getDefaultModel() == null) {
            return Mono.error(new IllegalArgumentException("AI Assistant must have a default model configured"));
        }

        AIAssistant.ModelConfig modelConfig = assistant.getDefaultModel();
        String modelName = modelConfig.getModelName();
        String provider = modelConfig.getProvider();
        String modelCategoryFromConfig = modelConfig.getModelCategory();
        String teamId = assistant.getTeamId();

        if (modelCategoryFromConfig != null && !modelCategoryFromConfig.isEmpty()) {
            return Mono.just(modelCategoryFromConfig);
        }

        // Look up from linq_llm_models collection
        log.info("modelCategory missing for modelName '{}', provider '{}', teamId '{}'. Looking up from MongoDB...",
                modelName, provider, teamId);

        if (provider != null && !provider.isEmpty()) {
            return linqLlmModelRepository
                    .findByModelNameAndProviderAndTeamId(modelName, provider, teamId)
                    .map(LinqLlmModel::getModelCategory)
                    .doOnNext(category -> log.info(
                            "Found modelCategory '{}' from MongoDB for modelName '{}', provider '{}', teamId '{}'",
                            category, modelName, provider, teamId))
                    .switchIfEmpty(Mono.defer(() -> {
                        // Fallback to derivation
                        return linqLlmModelService.deriveModelCategory(modelName, provider, teamId)
                                .doOnNext(derivedCategory -> log.warn(
                                        "Could not find modelCategory in MongoDB. Derived '{}' as fallback for modelName '{}', provider '{}', teamId '{}'",
                                        derivedCategory, modelName, provider, teamId))
                                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                        "AI Assistant default model must have a modelCategory configured. " +
                                                "Could not find modelCategory in MongoDB or derive it for modelName: "
                                                + modelName +
                                                ", provider: " + provider + ", teamId: " + teamId)));
                    }));
        } else {
            // Fallback: find by modelName and teamId, take first result
            return linqLlmModelRepository
                    .findByModelNameAndTeamId(modelName, teamId)
                    .next()
                    .map(LinqLlmModel::getModelCategory)
                    .doOnNext(category -> log.info(
                            "Found modelCategory '{}' from MongoDB for modelName '{}', teamId '{}'",
                            category, modelName, teamId))
                    .switchIfEmpty(Mono.defer(() -> {
                        // Fallback to derivation
                        return linqLlmModelService.deriveModelCategory(modelName, provider, teamId)
                                .doOnNext(derivedCategory -> log.warn(
                                        "Could not find modelCategory in MongoDB. Derived '{}' as fallback for modelName '{}', teamId '{}'",
                                        derivedCategory, modelName, teamId))
                                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                        "AI Assistant default model must have a modelCategory configured. " +
                                                "Could not find modelCategory in MongoDB or derive it for modelName: "
                                                + modelName +
                                                ", teamId: " + teamId)));
                    }));
        }
    }

    protected Mono<String> getReviewedDocumentId(AIAssistant assistant, String conversationId) {
        if (assistant.getCategory() == AIAssistant.Category.REVIEW_DOC) {
            return docReviewAssistantRepository.findByConversationId(conversationId)
                    .map(review -> review.getDocumentId() != null ? review.getDocumentId() : "")
                    .defaultIfEmpty("");
        }
        return Mono.just("");
    }

    protected LinqRequest buildLlmRequest(String modelCategory, String modelName, List<Map<String, Object>> messages,
            AIAssistant assistant, LinqLlmModel llmModel, String executedBy,
            String conversationId) {

        LinqRequest chatRequest = new LinqRequest();
        LinqRequest.Link link = new LinqRequest.Link();
        link.setTarget(modelCategory);
        link.setAction("generate");
        chatRequest.setLink(link);

        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent("generate");
        query.setPayload(messages);

        // Add model config
        LinqRequest.Query.LlmConfig llmConfig = new LinqRequest.Query.LlmConfig();
        llmConfig.setModel(modelName);
        if (assistant.getDefaultModel().getSettings() != null) {
            llmConfig.setSettings(assistant.getDefaultModel().getSettings());
        }
        query.setLlmConfig(llmConfig);

        // Add teamId to params
        Map<String, Object> params = new HashMap<>();
        params.put("teamId", assistant.getTeamId());
        query.setParams(params);

        chatRequest.setQuery(query);
        chatRequest.setExecutedBy(executedBy);

        // Publish LLM call started event
        Map<String, Object> llmStartData = new HashMap<>();
        llmStartData.put("conversationId", conversationId);
        llmStartData.put("modelName", modelName);
        llmStartData.put("modelCategory", modelCategory);
        llmStartData.put("provider", llmModel.getProvider());
        publishChatUpdate("LLM_CALL_STARTED", llmStartData);

        return chatRequest;
    }

    protected Mono<LinqResponse> handleChatExecutionError(Throwable error, Conversation conversation,
            AIAssistant assistant, LocalDateTime startTime, String executedBy) {
        long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();
        Map<String, Object> errorContext = new HashMap<>();
        errorContext.put("conversationId", conversation.getId());
        errorContext.put("assistantId", assistant.getId());
        errorContext.put("assistantName", assistant.getName());
        errorContext.put("teamId", assistant.getTeamId());
        errorContext.put("error", error.getMessage());
        errorContext.put("errorType", error.getClass().getSimpleName());
        errorContext.put("durationMs", durationMs);
        errorContext.put("failureTimestamp", LocalDateTime.now().toString());
        if (executedBy != null) {
            errorContext.put("executedBy", executedBy);
        }

        final boolean auditLoggingEnabled = assistant.getGuardrails() != null &&
                Boolean.TRUE.equals(assistant.getGuardrails().getAuditLoggingEnabled());

        Mono<Void> failureAuditLog = auditLoggingEnabled
                ? auditLogHelper.logDetailedEvent(
                        AuditEventType.CHAT_EXECUTION_FAILED,
                        AuditActionType.READ,
                        AuditResourceType.CHAT,
                        conversation.getId(),
                        String.format("Chat execution failed for assistant '%s': %s", assistant.getName(),
                                error.getMessage()),
                        errorContext,
                        null,
                        null,
                        AuditResultType.FAILED)
                        .doOnError(auditError -> log.error("Failed to log audit event (chat execution failed): {}",
                                auditError.getMessage(), auditError))
                        .onErrorResume(auditError -> Mono.empty())
                : Mono.empty();

        return failureAuditLog.then(Mono.error(error));
    }

    protected Mono<Void> logChatExecutionStarted(AIAssistant assistant, Conversation conversation, String executedBy,
            LocalDateTime startTime) {
        // Check if audit logging is enabled for this assistant
        final boolean auditLoggingEnabled = assistant.getGuardrails() != null &&
                Boolean.TRUE.equals(assistant.getGuardrails().getAuditLoggingEnabled());

        if (auditLoggingEnabled) {
            Map<String, Object> startContext = new HashMap<>();
            startContext.put("conversationId", conversation.getId());
            startContext.put("assistantId", assistant.getId());
            startContext.put("assistantName", assistant.getName());
            startContext.put("teamId", assistant.getTeamId());
            startContext.put("hasSelectedTasks",
                    assistant.getSelectedTasks() != null && !assistant.getSelectedTasks().isEmpty());
            startContext.put("taskCount",
                    assistant.getSelectedTasks() != null ? assistant.getSelectedTasks().size() : 0);
            startContext.put("startTimestamp", startTime.toString());
            if (executedBy != null) {
                startContext.put("executedBy", executedBy);
            }

            return auditLogHelper.logDetailedEvent(
                    AuditEventType.CHAT_EXECUTION_STARTED,
                    AuditActionType.READ,
                    AuditResourceType.CHAT,
                    conversation.getId(),
                    String.format("Chat execution started for assistant '%s'", assistant.getName()),
                    startContext,
                    null,
                    null)
                    .doOnError(auditError -> log.error("Failed to log audit event (chat execution started): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> Mono.empty());
        }
        return Mono.empty();
    }

    /**
     * Publish chat update via WebSocket
     */
    protected void publishChatUpdate(String type, Map<String, Object> data) {
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

    protected String extractUserMessage(List<Map<String, Object>> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Map<String, Object> msg = messages.get(i);
            if ("user".equals(msg.get("role"))) {
                return (String) msg.get("content");
            }
        }
        return null;
    }

    protected String extractIntent(String message, AIAssistant assistant) {
        // For now, return a default intent
        // TODO: Implement intent classification using the assistant's default model
        return "user_query";
    }

    protected boolean isSystemField(String key) {
        return key.equals("id") || key.equals("name") || key.equals("type") ||
                key.equals("teamId") || key.equals("documentId") ||
                key.equals("extractedAt") || key.equals("createdAt") || key.equals("updatedAt") ||
                key.equals("relatedEntities") || key.equals("relationships");
    }

    protected String extractMessageContent(LinqResponse response) {
        // Extract message content from response
        if (response.getResult() instanceof Map) {
            Map<String, Object> result = (Map<String, Object>) response.getResult();

            // Try Gemini format
            if (result.containsKey("candidates")) {
                Object candidatesObj = result.get("candidates");
                if (candidatesObj instanceof List) {
                    List<?> candidates = (List<?>) candidatesObj;
                    if (!candidates.isEmpty()) {
                        Object candidateObj = candidates.get(0);
                        if (candidateObj instanceof Map) {
                            Map<?, ?> candidate = (Map<?, ?>) candidateObj;
                            if (candidate.containsKey("content")) {
                                Object contentObj = candidate.get("content");
                                if (contentObj instanceof Map) {
                                    Map<?, ?> content = (Map<?, ?>) contentObj;
                                    if (content.containsKey("parts")) {
                                        Object partsObj = content.get("parts");
                                        if (partsObj instanceof List) {
                                            List<?> parts = (List<?>) partsObj;
                                            if (!parts.isEmpty()) {
                                                Object partObj = parts.get(0);
                                                if (partObj instanceof Map) {
                                                    Map<?, ?> part = (Map<?, ?>) partObj;
                                                    if (part.containsKey("text")) {
                                                        return (String) part.get("text");
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Other formats (Cohere, Claude, OpenAI)
            if (result.containsKey("text") && result.get("text") instanceof String) {
                return (String) result.get("text");
            }

            if (result.containsKey("content") && result.get("content") instanceof String) {
                return (String) result.get("content");
            }

            // OpenAI / Generic
            if (result.containsKey("choices") && result.get("choices") instanceof List) {
                List<?> choices = (List<?>) result.get("choices");
                if (!choices.isEmpty() && choices.get(0) instanceof Map) {
                    Map<?, ?> choice = (Map<?, ?>) choices.get(0);
                    if (choice.containsKey("message") && choice.get("message") instanceof Map) {
                        Map<?, ?> message = (Map<?, ?>) choice.get("message");
                        if (message.containsKey("content")) {
                            return (String) message.get("content");
                        }
                    }
                }
            }
            // Ollama format: { "message": { "role": "assistant", "content": "..." } }
            if (result.containsKey("message")) {
                Object messageObj = result.get("message");
                if (messageObj instanceof Map) {
                    Map<?, ?> message = (Map<?, ?>) messageObj;
                    if (message.containsKey("content")) {
                        return (String) message.get("content");
                    }
                }
            }
        }
        if (response.getResult() instanceof Map) {
            log.warn("❌ Failed to extract message content from response. Result keys: {}",
                    ((Map<?, ?>) response.getResult()).keySet());
            log.warn("❌ Result content: {}", response.getResult());
        } else {
            log.warn("❌ Failed to extract message content from response. Result type: {}",
                    response.getResult() != null ? response.getResult().getClass().getName() : "null");
        }
        return "No response content extracted";
    }

    protected LinqResponse.ChatResult.TokenUsage extractTokenUsageFromResponse(
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

        // Extract token usage based on model category (Simplified logic)
        if (resultMap.containsKey("usage")) {
            Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
            if (usage != null) {
                long promptTokens = usage.containsKey("prompt_tokens")
                        ? ((Number) usage.get("prompt_tokens")).longValue()
                        : (usage.containsKey("input_tokens") ? ((Number) usage.get("input_tokens")).longValue() : 0);
                long completionTokens = usage.containsKey("completion_tokens")
                        ? ((Number) usage.get("completion_tokens")).longValue()
                        : (usage.containsKey("output_tokens") ? ((Number) usage.get("output_tokens")).longValue() : 0);

                tokenUsage.setPromptTokens(promptTokens);
                tokenUsage.setCompletionTokens(completionTokens);
                tokenUsage.setTotalTokens(promptTokens + completionTokens);
                tokenUsage.setCostUsd(llmCostService.calculateCost(modelName, promptTokens, completionTokens));
                hasTokenUsage = true;
            }
        } else if (resultMap.containsKey("prompt_eval_count") || resultMap.containsKey("eval_count")) {
            // Ollama native format
            long promptTokens = resultMap.containsKey("prompt_eval_count")
                    ? ((Number) resultMap.get("prompt_eval_count")).longValue()
                    : 0;
            long completionTokens = resultMap.containsKey("eval_count")
                    ? ((Number) resultMap.get("eval_count")).longValue()
                    : 0;

            tokenUsage.setPromptTokens(promptTokens);
            tokenUsage.setCompletionTokens(completionTokens);
            tokenUsage.setTotalTokens(promptTokens + completionTokens);
            tokenUsage.setCostUsd(llmCostService.calculateCost(modelName, promptTokens, completionTokens));
            hasTokenUsage = true;
        }

        return hasTokenUsage ? tokenUsage : null;
    }

    /**
     * Stream message chunks via WebSocket to simulate ChatGPT-like streaming
     */
    protected void streamMessageChunks(String conversationId, String fullMessage) {
        if (fullMessage == null || fullMessage.isEmpty()) {
            return;
        }

        cancellationFlags.put(conversationId, false);
        publishChatUpdate("LLM_RESPONSE_STREAMING_STARTED", Map.of("conversationId", conversationId));

        String[] words = fullMessage.split("(?<= )", -1);
        StringBuilder accumulated = new StringBuilder();

        Thread streamingThread = new Thread(() -> {
            try {
                for (int i = 0; i < words.length; i++) {
                    if (cancellationFlags.getOrDefault(conversationId, false)) {
                        publishChatUpdate("LLM_RESPONSE_STREAMING_CANCELLED", Map.of(
                                "conversationId", conversationId,
                                "accumulated", accumulated.toString()));
                        return;
                    }

                    String chunk = words[i];
                    accumulated.append(chunk);

                    Map<String, Object> chunkData = new HashMap<>();
                    chunkData.put("conversationId", conversationId);
                    chunkData.put("chunk", chunk);
                    chunkData.put("accumulated", accumulated.toString());
                    chunkData.put("index", i);
                    chunkData.put("total", words.length);

                    publishChatUpdate("LLM_RESPONSE_CHUNK", chunkData);

                    try {
                        Thread.sleep(30);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }

                publishChatUpdate("LLM_RESPONSE_STREAMING_COMPLETE", Map.of(
                        "conversationId", conversationId,
                        "message", accumulated.toString()));
            } finally {
                activeStreamingThreads.remove(conversationId);
                cancellationFlags.remove(conversationId);
            }
        });

        streamingThread.start();
        activeStreamingThreads.put(conversationId, streamingThread);
    }

    public void cancelStreaming(String conversationId) {
        cancellationFlags.put(conversationId, true);
        Thread thread = activeStreamingThreads.get(conversationId);
        if (thread != null && thread.isAlive()) {
            thread.interrupt();
        }
    }

    // ========== Common Helper Methods (to be used by child classes) ==========
    // Note: Child classes must provide conversationService and piiDetectionService
    // as protected fields

    /**
     * Get existing conversation or create a new one
     */
    protected Conversation getOrCreateConversationSync(
            LinqRequest.Query.ChatConversation chat,
            AIAssistant assistant,
            String teamId,
            String username,
            ConversationService conversationService) {
        // This is a helper that child classes can use
        // Actual implementation should be in child classes with proper Mono handling
        throw new UnsupportedOperationException("Use getOrCreateConversation in child class");
    }

    /**
     * Build chat messages from conversation history
     * Common logic for both Standard and DocReview implementations
     */
    protected Mono<List<Map<String, Object>>> buildChatMessages(
            Conversation conversation,
            String userMessage,
            AIAssistant assistant,
            LinqRequest.Query.ChatConversation chatConfig) {

        int maxMessages = (assistant.getContextManagement() != null
                && assistant.getContextManagement().getMaxRecentMessages() != null)
                        ? assistant.getContextManagement().getMaxRecentMessages()
                        : 20;

        return conversationService.getRecentMessages(conversation.getId(), maxMessages)
                .collectList()
                .map(recentMessages -> {
                    List<Map<String, Object>> messages = new ArrayList<>();

                    String systemPrompt = assistant.getSystemPrompt();
                    if (chatConfig != null && chatConfig.getHistory() != null) {
                        for (LinqRequest.Query.ChatConversation.ChatMessage msg : chatConfig.getHistory()) {
                            if ("system".equalsIgnoreCase(msg.getRole())
                                    && msg.getContent() != null
                                    && !msg.getContent().trim().isEmpty()) {
                                systemPrompt = msg.getContent();
                                break;
                            }
                        }
                    }

                    if (systemPrompt != null && !systemPrompt.trim().isEmpty()) {
                        Map<String, Object> systemMessage = new HashMap<>();
                        systemMessage.put("role", "system");
                        systemMessage.put("content", systemPrompt);
                        messages.add(systemMessage);
                    }

                    for (ConversationMessage msg : recentMessages) {
                        Map<String, Object> msgMap = new HashMap<>();
                        msgMap.put("role", msg.getRole().toLowerCase());
                        msgMap.put("content", msg.getContent());
                        messages.add(msgMap);
                    }

                    Map<String, Object> userMsgMap = new HashMap<>();
                    userMsgMap.put("role", "user");
                    userMsgMap.put("content", userMessage);
                    messages.add(userMsgMap);

                    return messages;
                });
    }

    /**
     * Save user message with optional PII detection
     */
    protected Mono<ConversationMessage> saveUserMessage(
            Conversation conversation,
            String message,
            Map<String, Object> context,
            AIAssistant assistant,
            String executedBy) {

        boolean piiDetectionEnabled = assistant.getGuardrails() != null
                && Boolean.TRUE.equals(assistant.getGuardrails().getPiiDetectionEnabled());

        Mono<String> contentMono = piiDetectionEnabled
                ? piiDetectionService.detectPII(message, true)
                        .map(r -> r.isPiiDetected() ? r.getRedactedContent() : message)
                : Mono.just(message);

        return contentMono.flatMap(content -> {
            ConversationMessage msg = ConversationMessage.builder()
                    .conversationId(conversation.getId())
                    .role("USER")
                    .content(content)
                    .timestamp(LocalDateTime.now())
                    .metadata(ConversationMessage.MessageMetadata.builder()
                            .additionalData(context)
                            .build())
                    .build();
            return conversationService.addMessage(msg);
        });
    }

    /**
     * Save assistant message with optional PII detection
     */
    protected Mono<ConversationMessage> saveAssistantMessage(
            Conversation conversation,
            LinqResponse chatResponse,
            AIAssistant assistant,
            String executedBy) {

        String content = chatResponse.getChatResult() != null
                && chatResponse.getChatResult().getMessage() != null
                        ? chatResponse.getChatResult().getMessage()
                        : extractMessageContent(chatResponse);

        boolean piiDetectionEnabled = assistant.getGuardrails() != null
                && Boolean.TRUE.equals(assistant.getGuardrails().getPiiDetectionEnabled());

        Mono<String> contentMono = piiDetectionEnabled
                ? piiDetectionService.detectPII(content, true)
                        .map(r -> r.isPiiDetected() ? r.getRedactedContent() : content)
                : Mono.just(content);

        return contentMono.flatMap(finalContent -> {
            ConversationMessage.MessageMetadata metadata = ConversationMessage.MessageMetadata
                    .builder()
                    .tokenUsage(chatResponse.getChatResult().getTokenUsage() != null
                            ? ConversationMessage.MessageMetadata.TokenUsage.builder()
                                    .totalTokens(chatResponse.getChatResult().getTokenUsage().getTotalTokens())
                                    .costUsd(chatResponse.getChatResult().getTokenUsage().getCostUsd())
                                    .build()
                            : null)
                    .build();

            ConversationMessage msg = ConversationMessage.builder()
                    .conversationId(conversation.getId())
                    .role("ASSISTANT")
                    .content(finalContent)
                    .timestamp(java.time.LocalDateTime.now())
                    .metadata(metadata)
                    .build();
            return conversationService.addMessage(msg);
        });
    }

    /**
     * Execute agent tasks for assistant
     * Common logic for executing selected tasks in parallel
     */
    protected Mono<Map<String, Object>> executeAgentTasksWrapper(
            AIAssistant assistant,
            Conversation conversation,
            List<Map<String, Object>> messages,
            String userMessage,
            String executedBy,
            Map<String, Object> extraContext) {

        if (assistant.getSelectedTasks() != null && !assistant.getSelectedTasks().isEmpty()) {
            Map<String, Object> executingData = new HashMap<>();
            executingData.put("conversationId", conversation.getId());
            executingData.put("taskIds", assistant.getSelectedTasks().stream()
                    .map(SelectedTask::getTaskId)
                    .collect(Collectors.toList()));
            publishChatUpdate("AGENT_TASKS_EXECUTING", executingData);

            // Execute all selected tasks in parallel
            return Flux.fromIterable(assistant.getSelectedTasks())
                    .flatMap(selectedTask -> {
                        log.info("🚀 Starting agent task: {} for assistant: {}",
                                selectedTask.getTaskId(), assistant.getName());

                        // Prepare input overrides from current context
                        Map<String, Object> inputOverrides = new HashMap<>();
                        inputOverrides.put("userMessage", userMessage);
                        inputOverrides.put("conversationId", conversation.getId());
                        inputOverrides.put("assistantId", assistant.getId());
                        if (extraContext != null) {
                            inputOverrides.putAll(extraContext);
                        }

                        // Fetch the task first to get the correct agentId
                        return agentTaskRepository.findById(selectedTask.getTaskId())
                                .flatMap(agentTask -> {
                                    return agentExecutionService.startTaskExecution(
                                            agentTask.getAgentId(),
                                            selectedTask.getTaskId(),
                                            assistant.getTeamId(),
                                            executedBy,
                                            inputOverrides)
                                            .flatMap(execution -> waitForExecutionCompletion(
                                                    execution, agentExecutionRepository))
                                            .map(completedExecution -> {
                                                Map<String, Object> result = new HashMap<>();
                                                result.put("taskId", selectedTask.getTaskId());
                                                result.put("executionId", completedExecution.getExecutionId());
                                                result.put("status", completedExecution.getStatus());

                                                // Extract output from execution result
                                                if (completedExecution.getOutputData() != null) {
                                                    result.put("output", completedExecution.getOutputData());
                                                }
                                                if (completedExecution.getIntermediateResults() != null) {
                                                    result.put("intermediateResults",
                                                            completedExecution.getIntermediateResults());
                                                }

                                                log.info("✅ Agent task completed: {} with status: {}",
                                                        selectedTask.getTaskId(), completedExecution.getStatus());
                                                return result;
                                            });
                                });
                    })
                    .collectList()
                    .map(taskResults -> {
                        Map<String, Object> aggregatedResults = new HashMap<>();
                        for (Map<String, Object> taskResult : taskResults) {
                            String taskId = (String) taskResult.get("taskId");
                            aggregatedResults.put(taskId, taskResult);
                        }

                        Map<String, Object> completedData = new HashMap<>();
                        completedData.put("conversationId", conversation.getId());
                        completedData.put("results", aggregatedResults);
                        publishChatUpdate("AGENT_TASKS_COMPLETED", completedData);

                        return aggregatedResults;
                    })
                    .defaultIfEmpty(Collections.emptyMap());
        } else {
            return Mono.just(Collections.emptyMap());
        }
    }

    /**
     * Wait for agent execution to complete
     * Polls the execution status until it's COMPLETED or FAILED
     */
    protected Mono<AgentExecution> waitForExecutionCompletion(
            AgentExecution execution,
            AgentExecutionRepository agentExecutionRepository) {
        return agentExecutionRepository.findByExecutionId(execution.getExecutionId())
                .flatMap(current -> {
                    if (current.getStatus() == ExecutionStatus.COMPLETED
                            || current.getStatus() == ExecutionStatus.FAILED) {
                        return Mono.just(current);
                    } else {
                        return Mono.delay(java.time.Duration.ofMillis(500))
                                .then(waitForExecutionCompletion(execution, agentExecutionRepository));
                    }
                });
    }

    /**
     * Enrich messages with Knowledge Graph context and task results
     * Unified implementation for both Standard and DocReview chat services
     */
    protected Mono<List<Map<String, Object>>> enrichMessagesWithContext(
            List<Map<String, Object>> messages,
            AIAssistant assistant,
            Conversation conversation,
            Map<String, Object> taskResults,
            String userMessage,
            String reviewedDocId,
            String providedContent,
            String selectedText,
            boolean includeGlobalContext) { // Optional - prioritizes UI content over DB

        reactor.core.publisher.Mono<String> documentTextMono;
        if (providedContent != null && !providedContent.isEmpty()) {
            log.info("Using provided document content from request context (length: {})", providedContent.length());
            documentTextMono = reactor.core.publisher.Mono.just(providedContent);
        } else if (reviewedDocId != null && !reviewedDocId.isEmpty()) {
            log.info("Fetching document content from Knowledge Hub for reviewed document: {}", reviewedDocId);
            documentTextMono = knowledgeHubDocumentService.getDocumentText(reviewedDocId, assistant.getTeamId())
                    .onErrorResume(e -> {
                        log.error("Failed to fetch document text for context enrichment: {}", e.getMessage());
                        return reactor.core.publisher.Mono.just("");
                    });
        } else {
            documentTextMono = reactor.core.publisher.Mono.just("");
        }

        reactor.core.publisher.Mono<Map<String, Object>> graphContextMono = includeGlobalContext
                ? knowledgeHubGraphContextService.enrichContextWithGraph(userMessage, assistant.getTeamId())
                : reactor.core.publisher.Mono.just(Collections.emptyMap());

        return graphContextMono
                .defaultIfEmpty(Collections.emptyMap())
                .zipWith(documentTextMono)
                .map(tuple -> {
                    Map<String, Object> graphContext = tuple.getT1();
                    String documentText = tuple.getT2();

                    // Detect existing system prompt in messages to avoid overwriting it
                    String baseSystemPrompt = assistant.getSystemPrompt();
                    for (Map<String, Object> msg : messages) {
                        if ("system".equals(msg.get("role"))) {
                            baseSystemPrompt = (String) msg.get("content");
                            break;
                        }
                    }

                    return enrichMessagesWithContextInternal(
                            messages, taskResults, graphContext,
                            baseSystemPrompt, reviewedDocId, documentText, selectedText, objectMapper);
                });
    }

    /**
     * Internal method to enrich messages with context
     * Combines Knowledge Graph entities, documents, and task results
     */
    protected List<Map<String, Object>> enrichMessagesWithContextInternal(
            List<Map<String, Object>> messages,
            Map<String, Object> taskResults,
            Map<String, Object> graphContext,
            String systemPrompt,
            String reviewedDocId, // Optional - can be null
            String documentText,
            String selectedText,
            ObjectMapper objectMapper) {

        List<Map<String, Object>> enrichedMessages = new ArrayList<>(messages);
        StringBuilder enrichedContext = new StringBuilder();

        // Start with original system prompt if available
        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            enrichedContext.append(systemPrompt).append("\n\n");
        }

        // Add Selection Context if available (HIGH PRIORITY)
        if (selectedText != null && !selectedText.isEmpty()) {
            enrichedContext.append("=== CURRENT SELECTION (SURGICAL TARGET) ===\n");
            enrichedContext.append("The user has highlighted the following text in the editor:\n");
            enrichedContext.append("\"").append(selectedText).append("\"\n");
            enrichedContext.append("Your edit MUST target this specific text or its immediate context.\n\n");
        }

        // Add document text if available
        if (documentText != null && !documentText.isEmpty()) {
            enrichedContext.append("=== IMPORTANT: DOCUMENT EDITING RULES ===\n");
            enrichedContext.append("- You are a surgical document editor. ONLY change what is requested.\n");
            enrichedContext.append(
                    "- If the user asks to REMOVE or DELETE something that is selected, return an empty string or just a newline within the tags.\n");
            enrichedContext.append(
                    "- DO NOT add preamble, commentary, or extra text inside the tags. Commentary goes OUTSIDE.\n");
            enrichedContext.append("- Use HTML ONLY (e.g., <p>, <strong>, <ul>). NO Markdown.\n\n");

            enrichedContext.append("=== MANDATORY TAGS ===\n");
            enrichedContext.append(
                    "1. <REPLACE_SELECTION>: Use for specific/small changes or deletions. Return ONLY the new content for the selected range.\n");
            enrichedContext.append(
                    "   Example for deletion: <REPLACE_SELECTION></REPLACE_SELECTION>\n\n");
            enrichedContext.append(
                    "2. <UPDATED_DOCUMENT_CONTENT>: Use ONLY for complete document rewrites. \n");
            enrichedContext.append(
                    "   WARNING: This replaces the ENTIRE document. Use <REPLACE_SELECTION> for 99% of cases.\n\n");

            enrichedContext.append("=== CURRENT DOCUMENT CONTENT ===\n");
            enrichedContext.append(documentText).append("\n\n");
        }

        // Add Knowledge Graph context if available
        if (graphContext != null && !graphContext.isEmpty()) {
            Integer entityCount = (Integer) graphContext.getOrDefault("entityCount", 0);

            if (entityCount > 0) {
                enrichedContext.append("=== Knowledge Graph Context ===\n");
                enrichedContext.append(graphContext.getOrDefault("summary", "")).append("\n");

                // Add entities
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> entities = (List<Map<String, Object>>) graphContext
                        .getOrDefault("entities", Collections.emptyList());

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
                                        enrichedContext.append("  - ")
                                                .append(e.getKey())
                                                .append(": ")
                                                .append(value)
                                                .append("\n");
                                    }
                                });
                    }
                }

                // Add relationships
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> relationships = (List<Map<String, Object>>) graphContext
                        .getOrDefault("relationships", Collections.emptyList());

                if (!relationships.isEmpty()) {
                    enrichedContext.append("\nRelationships:\n");
                    for (Map<String, Object> rel : relationships) {
                        String fromName = (String) rel.getOrDefault("fromName", "Unknown");
                        String toName = (String) rel.getOrDefault("toName", "Unknown");
                        String relType = (String) rel.getOrDefault("type", "RELATED_TO");
                        enrichedContext.append("- ").append(fromName).append(" --[")
                                .append(relType).append("]--> ").append(toName).append("\n");
                    }
                }

                // Add documents (filter out reviewed document if specified)
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> documents = (List<Map<String, Object>>) graphContext
                        .getOrDefault("documents", Collections.emptyList());

                // Filter out the reviewed document to avoid duplication
                if (reviewedDocId != null && !documents.isEmpty()) {
                    documents = documents.stream()
                            .filter(doc -> !reviewedDocId.equals(doc.get("documentId")))
                            .collect(Collectors.toList());
                }

                if (!documents.isEmpty()) {
                    enrichedContext.append("\nRelated Documents (")
                            .append(documents.size())
                            .append(" documents):\n");
                    int docIndex = 1;
                    for (Map<String, Object> doc : documents) {
                        String fileName = (String) doc.getOrDefault("fileName", "Unknown");
                        String documentId = (String) doc.getOrDefault("documentId", "");
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
        if (taskResults != null && !taskResults.isEmpty()) {
            enrichedContext.append("=== Agent Task Results ===\n");
            for (java.util.Map.Entry<String, Object> entry : taskResults.entrySet()) {
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

        // Find existing system message or add new one
        boolean hasSystemMessage = false;
        for (java.util.Map<String, Object> msg : enrichedMessages) {
            if ("system".equals(msg.get("role"))) {
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
            java.util.Map<String, Object> systemMsg = new java.util.HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", enrichedContext.toString());
            enrichedMessages.add(0, systemMsg);
        }

        return enrichedMessages;
    }

    /**
     * Log chat completion audit event with comprehensive metrics
     * Common logic for logging chat execution completion
     */
    protected reactor.core.publisher.Mono<Void> logChatCompletionAudit(
            org.lite.gateway.entity.AIAssistant assistant,
            org.lite.gateway.entity.Conversation conversation,
            String executedBy,
            java.time.LocalDateTime startTime,
            java.util.Map<String, Object> taskResults,
            String modelCategory,
            String modelName,
            String intent,
            LinqResponse.ChatResult.TokenUsage tokenUsage,
            org.lite.gateway.util.AuditLogHelper auditLogHelper) {

        // Check if audit logging is enabled for this assistant
        final boolean auditLoggingEnabled = assistant.getGuardrails() != null
                && Boolean.TRUE.equals(assistant.getGuardrails().getAuditLoggingEnabled());

        if (auditLoggingEnabled) {
            long durationMs = java.time.Duration.between(startTime, java.time.LocalDateTime.now()).toMillis();
            java.util.Map<String, Object> completeContext = new java.util.HashMap<>();
            completeContext.put("conversationId", conversation.getId());
            completeContext.put("assistantId", assistant.getId());
            completeContext.put("assistantName", assistant.getName());
            completeContext.put("teamId", assistant.getTeamId());
            completeContext.put("durationMs", durationMs);
            completeContext.put("modelCategory", modelCategory);
            completeContext.put("modelName", modelName);
            completeContext.put("intent", intent);

            if (tokenUsage != null) {
                completeContext.put("totalTokens", tokenUsage.getTotalTokens());
                completeContext.put("costUsd", tokenUsage.getCostUsd());
            }

            if (taskResults != null && !taskResults.isEmpty()) {
                completeContext.put("taskCount", taskResults.size());
                completeContext.put("taskIds", taskResults.keySet());
            }

            if (executedBy != null) {
                completeContext.put("executedBy", executedBy);
            }

            return auditLogHelper.logDetailedEvent(
                    org.lite.gateway.enums.AuditEventType.CHAT_EXECUTION_COMPLETED,
                    org.lite.gateway.enums.AuditActionType.READ,
                    org.lite.gateway.enums.AuditResourceType.CHAT,
                    conversation.getId(),
                    String.format("Chat execution completed for assistant '%s'", assistant.getName()),
                    completeContext,
                    null,
                    null,
                    org.lite.gateway.enums.AuditResultType.SUCCESS)
                    .doOnError(auditError -> log.error(
                            "Failed to log audit event (chat execution completed): {}",
                            auditError.getMessage(), auditError))
                    .onErrorResume(auditError -> reactor.core.publisher.Mono.empty());
        }
        return reactor.core.publisher.Mono.empty();
    }
}
