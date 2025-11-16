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
import org.lite.gateway.entity.Agent;
import org.lite.gateway.entity.AgentExecution;
import org.lite.gateway.entity.AgentTask;
import org.lite.gateway.enums.ExecutionStatus;
import org.lite.gateway.repository.AgentRepository;
import org.lite.gateway.repository.AgentExecutionRepository;
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
                                    "username", username
                                ));
                                
                                // Build chat messages with history
                                return buildChatMessages(conversation, message, assistant)
                                        .flatMap(messages -> {
                                            // Publish user message sent update
                                            publishChatUpdate("USER_MESSAGE_SENT", Map.of(
                                                "conversationId", conversation.getId(),
                                                "message", message
                                            ));
                                            
                                            // Execute chat with LLM (which will handle streaming events)
                                            return executeChatWithLLM(request, messages, assistant, conversation)
                                                    .flatMap(chatResponse -> {
                                                        // NOTE: We intentionally do NOT publish an LLM_RESPONSE_RECEIVED
                                                        // event here anymore, because executeChatWithLLM already publishes
                                                        // streaming updates (LLM_RESPONSE_STREAMING_*). Emitting both caused
                                                        // duplicate assistant messages on the frontend.
                                                        
                                                        // Save user message
                                                        return saveUserMessage(conversation, message, chat.getContext())
                                                                .flatMap(savedUserMessage -> {
                                                                    // Publish user message saved update
                                                                    publishChatUpdate("MESSAGE_SAVED", Map.of(
                                                                        "conversationId", conversation.getId(),
                                                                        "messageId", savedUserMessage.getId(),
                                                                        "role", "USER"
                                                                    ));
                                                                    
                                                                    // Save assistant response
                                                                    return saveAssistantMessage(conversation, chatResponse, assistant)
                                                                            .map(savedAssistantMessage -> {
                                                                                // Publish assistant message saved update
                                                                                publishChatUpdate("MESSAGE_SAVED", Map.of(
                                                                                    "conversationId", conversation.getId(),
                                                                                    "messageId", savedAssistantMessage.getId(),
                                                                                    "role", "ASSISTANT"
                                                                                ));
                                                                                
                                                                                // Return the chat response as-is (already has chatResult set)
                                                                                if (chatResponse.getMetadata() == null) {
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
                    .switchIfEmpty(Mono.error(new IllegalArgumentException("Conversation not found: " + chat.getConversationId())));
        } else {
            // Create new conversation
            Conversation conversation = Conversation.builder()
                    .assistantId(assistant.getId())
                    .teamId(teamId)
                    .username(username)  // Username (e.g., "timursen")
                    .isPublic("PUBLIC".equals(assistant.getAccessControl() != null ? assistant.getAccessControl().getType() : null))
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
        int maxMessages = assistant.getContextManagement() != null && assistant.getContextManagement().getMaxRecentMessages() != null
                ? assistant.getContextManagement().getMaxRecentMessages()
                : 10;
        
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
        
        // Extract user message
        final String userMessage = extractUserMessage(messages);
        
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
            
            taskResultsMono = executeAgentTasks(assistant.getSelectedTasks(), userMessage, assistant.getTeamId(), request.getExecutedBy())
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
        
        // Get assistant's default model
        if (assistant.getDefaultModel() == null) {
            return Mono.error(new IllegalArgumentException("AI Assistant must have a default model configured"));
        }
        
        AIAssistant.ModelConfig modelConfig = assistant.getDefaultModel();
        String modelName = modelConfig.getModelName();
        String modelCategory = modelConfig.getModelCategory();
        
        if (modelCategory == null || modelCategory.isEmpty()) {
            return Mono.error(new IllegalArgumentException("AI Assistant default model must have a modelCategory configured"));
        }
        
        // Load LLM model and execute with task results
        return linqLlmModelRepository.findByModelCategoryAndModelNameAndTeamId(
                        modelCategory, modelName, assistant.getTeamId())
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "LLM model not found: " + modelCategory + "/" + modelName + " for team " + assistant.getTeamId())))
                .flatMap(llmModel -> taskResultsMono.flatMap(taskResults -> {
                    // Build enriched messages with task results in context
                    List<Map<String, Object>> enrichedMessages = enrichMessagesWithTaskResults(messages, taskResults, assistant.getSystemPrompt());
                    
                    // Build chat request
                    LinqRequest chatRequest = new LinqRequest();
                    LinqRequest.Link link = new LinqRequest.Link();
                    link.setTarget(modelCategory);
                    link.setAction("generate");
                    chatRequest.setLink(link);
                    
                    LinqRequest.Query query = new LinqRequest.Query();
                    // Use "generate" intent which is the standard intent for chat models
                    query.setIntent("generate");
                    query.setPayload(enrichedMessages); // Messages array with enriched context
                    
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
                    
                    // Execute LLM request
                    return linqLlmModelService.executeLlmRequest(chatRequest, llmModel)
                            .flatMap(response -> {
                                String intent = extractIntent(userMessage != null ? userMessage : "", assistant);
                                String fullMessage = extractMessageContent(response);
                                
                                // Stream message chunks via WebSocket
                                streamMessageChunks(conversation.getId(), fullMessage);
                                
                                // Build chat result
                                LinqResponse.ChatResult chatResult = new LinqResponse.ChatResult();
                                chatResult.setConversationId(conversation.getId());
                                chatResult.setAssistantId(assistant.getId());
                                chatResult.setMessage(fullMessage);
                                chatResult.setIntent(intent);
                                chatResult.setModelCategory(modelCategory);
                                chatResult.setModelName(modelName);
                                chatResult.setExecutedTasks(new ArrayList<>(taskResults.keySet()));
                                chatResult.setTaskResults(taskResults);
                                
                                // Extract token usage from raw LLM response and calculate cost
                                LinqResponse.ChatResult.TokenUsage tokenUsage = extractTokenUsageFromResponse(response, modelCategory, modelName);
                                if (tokenUsage != null) {
                                    chatResult.setTokenUsage(tokenUsage);
                                    log.info("📊 Chat token usage - prompt: {}, completion: {}, total: {}, cost: ${}",
                                            tokenUsage.getPromptTokens(), tokenUsage.getCompletionTokens(),
                                            tokenUsage.getTotalTokens(), String.format("%.6f", tokenUsage.getCostUsd()));
                                } else {
                                    log.warn("⚠️ Could not extract token usage from LLM response");
                                }
                                
                                chatResult.setMetadata(new HashMap<>());
                                
                                LinqResponse chatResponse = new LinqResponse();
                                chatResponse.setChatResult(chatResult);
                                chatResponse.setMetadata(response.getMetadata());
                                
                                // Publish complete response
                                publishChatUpdate("LLM_RESPONSE_COMPLETE", Map.of(
                                    "conversationId", conversation.getId(),
                                    "message", fullMessage,
                                    "tokenUsage", tokenUsage != null ? Map.of(
                                        "promptTokens", tokenUsage.getPromptTokens(),
                                        "completionTokens", tokenUsage.getCompletionTokens(),
                                        "totalTokens", tokenUsage.getTotalTokens(),
                                        "costUsd", tokenUsage.getCostUsd()
                                    ) : Map.of()
                                ));
                                
                                return Mono.just(chatResponse);
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
                                    return Mono.just(Map.entry(selectedTask.getTaskId(), (Object) "Task has no configuration"));
                                }
                                
                                // Get agent for execution context
                                return agentRepository.findById(task.getAgentId())
                                        .flatMap(agent -> {
                                            // Execute task with proper AgentExecution tracking
                                            return executeTaskWithTracking(task, agent, userMessage, teamId, executedBy)
                                                    .map(result -> {
                                                        log.info("Task {} executed successfully with proper tracking, result: {}", 
                                                                task.getName(), result != null ? "present" : "null");
                                                        return Map.entry(selectedTask.getTaskId(), result != null ? result : "No result");
                                                    })
                                                    .onErrorResume(error -> {
                                                        log.error("Failed to execute task {}: {}", task.getName(), error.getMessage());
                                                        return Mono.just(Map.entry(selectedTask.getTaskId(), (Object) ("Error: " + error.getMessage())));
                                                    });
                                        })
                                        .switchIfEmpty(Mono.defer(() -> {
                                            log.error("Agent not found for task {}: agentId={}", task.getName(), task.getAgentId());
                                            return Mono.just(Map.entry(selectedTask.getTaskId(), (Object) "Agent not found"));
                                        }))
                                        .onErrorResume(error -> {
                                            log.error("Error executing task {}: {}", task.getName(), error.getMessage());
                                            return Mono.just(Map.entry(selectedTask.getTaskId(), (Object) ("Error: " + error.getMessage())));
                                        });
                            })
                            .onErrorResume(error -> {
                                log.error("Failed to get task {}: {}", selectedTask.getTaskId(), error.getMessage());
                                return Mono.just(Map.entry(selectedTask.getTaskId(), (Object) ("Task not found: " + error.getMessage())));
                            });
                })
                .toList();
        
        // Combine all task results
        return Flux.fromIterable(taskMonoList)
                .flatMap(Mono::from)
                .collectMap(Map.Entry::getKey, Map.Entry::getValue);
    }
    
    /**
     * Enrich messages with task results in context
     */
    private List<Map<String, Object>> enrichMessagesWithTaskResults(
            List<Map<String, Object>> messages,
            Map<String, Object> taskResults,
            String systemPrompt) {
        
        // Create a copy of messages
        List<Map<String, Object>> enrichedMessages = new ArrayList<>(messages);
        
        // Add task results to system message or create a new system message
        if (!taskResults.isEmpty()) {
            StringBuilder taskContext = new StringBuilder();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                taskContext.append(systemPrompt).append("\n\n");
            }
            taskContext.append("Agent Task Results:\n");
            for (Map.Entry<String, Object> entry : taskResults.entrySet()) {
                taskContext.append("- Task ").append(entry.getKey()).append(": ");
                if (entry.getValue() instanceof String) {
                    taskContext.append(entry.getValue());
                } else {
                    taskContext.append(objectMapper.valueToTree(entry.getValue()).toString());
                }
                taskContext.append("\n");
            }
            
            // Find existing system message or add new one at the beginning
            boolean hasSystemMessage = false;
            for (Map<String, Object> msg : enrichedMessages) {
                if ("system".equals(msg.get("role"))) {
                    msg.put("content", taskContext.toString());
                    hasSystemMessage = true;
                    break;
                }
            }
            
            if (!hasSystemMessage) {
                Map<String, Object> systemMsg = new HashMap<>();
                systemMsg.put("role", "system");
                systemMsg.put("content", taskContext.toString());
                enrichedMessages.add(0, systemMsg);
            }
        }
        
        return enrichedMessages;
    }
    
    /**
     * Execute Agent Task with proper AgentExecution tracking (for chat context)
     * Uses startTaskExecution for proper validation and tracking, then waits for completion synchronously
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
                                    return workflowExecutionService.getExecution(completedExecution.getWorkflowExecutionId())
                                            .map(workflowExecution -> {
                                                // Extract structured result from workflow response:
                                                // - answer: finalResult (string)
                                                // - documents: step 1 results (e.g. Milvus search results) when available
                                                Object result = "No result";
                                                if (workflowExecution.getResponse() != null) {
                                                    LinqResponse response = workflowExecution.getResponse();
                                                    Object rawResult = response.getResult();
                                                    
                                                    if (rawResult instanceof LinqResponse.WorkflowResult workflowResult) {
                                                        String answer = workflowResult.getFinalResult();
                                                        Object documents = null;

                                                        // Try to extract documents from step 1 (or first api-gateway step)
                                                        if (workflowResult.getSteps() != null) {
                                                            for (LinqResponse.WorkflowStep step : workflowResult.getSteps()) {
                                                                Object stepResult = step.getResult();
                                                                if (stepResult instanceof java.util.Map<?, ?> stepMap) {
                                                                    // Prefer the "results" field when present (Milvus search convention)
                                                                    if (stepMap.containsKey("results")) {
                                                                        documents = stepMap.get("results");
                                                                        break;
                                                                    }
                                                                }
                                                            }
                                                        }

                                                        java.util.Map<String, Object> structured = new java.util.HashMap<>();
                                                        structured.put("answer", answer);
                                                        if (documents != null) {
                                                            structured.put("documents", documents);
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
                                    log.warn("Execution {} has no workflowExecutionId", completedExecution.getExecutionId());
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
                    log.error("Error waiting for execution {} to complete: {}", execution.getExecutionId(), error.getMessage());
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
                        ? ((Number) usage.get("prompt_tokens")).longValue() : 0;
                long completionTokens = usage.containsKey("completion_tokens")
                        ? ((Number) usage.get("completion_tokens")).longValue() : 0;
                long totalTokens = usage.containsKey("total_tokens")
                        ? ((Number) usage.get("total_tokens")).longValue() : (promptTokens + completionTokens);
                
                String model = resultMap.containsKey("model") 
                        ? (String) resultMap.get("model") : modelName;
                
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
                        ? ((Number) usageMetadata.get("promptTokenCount")).longValue() : 0;
                long completionTokens = usageMetadata.containsKey("candidatesTokenCount")
                        ? ((Number) usageMetadata.get("candidatesTokenCount")).longValue() : 0;
                long totalTokens = usageMetadata.containsKey("totalTokenCount")
                        ? ((Number) usageMetadata.get("totalTokenCount")).longValue() : (promptTokens + completionTokens);
                
                String model = resultMap.containsKey("modelVersion")
                        ? (String) resultMap.get("modelVersion") : modelName;
                
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
                        ? ((Number) usage.get("input_tokens")).longValue() : 0;
                long completionTokens = usage.containsKey("output_tokens")
                        ? ((Number) usage.get("output_tokens")).longValue() : 0;
                long totalTokens = promptTokens + completionTokens;
                
                String model = resultMap.containsKey("model")
                        ? (String) resultMap.get("model") : modelName;
                
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
                            ? ((Number) billedUnits.get("input_tokens")).longValue() : 0;
                    long completionTokens = billedUnits.containsKey("output_tokens")
                            ? ((Number) billedUnits.get("output_tokens")).longValue() : 0;
                    long totalTokens = promptTokens + completionTokens;
                    
                    String model = resultMap.containsKey("model")
                            ? (String) resultMap.get("model") : modelName;
                    
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
            // Try to extract content from common response formats
            if (result.containsKey("content")) {
                Object content = result.get("content");
                if (content instanceof String) {
                    return (String) content;
                } else if (content instanceof List) {
                    List<?> contentList = (List<?>) content;
                    if (!contentList.isEmpty() && contentList.get(0) instanceof Map) {
                        Map<String, Object> firstContent = (Map<String, Object>) contentList.get(0);
                        if (firstContent.containsKey("text")) {
                            return (String) firstContent.get("text");
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
        return "No response received";
    }

    private Mono<ConversationMessage> saveUserMessage(
            Conversation conversation,
            String message,
            Map<String, Object> context) {
        ConversationMessage userMessage = ConversationMessage.builder()
                .conversationId(conversation.getId())
                .role("USER")
                .content(message)
                .timestamp(LocalDateTime.now())
                .metadata(ConversationMessage.MessageMetadata.builder()
                        .intent("user_query")
                        .additionalData(context != null ? context : new HashMap<>())
                        .build())
                .build();
        
        return conversationService.addMessage(userMessage);
    }

    private Mono<ConversationMessage> saveAssistantMessage(
            Conversation conversation,
            LinqResponse chatResponse,
            AIAssistant assistant) {
        LinqResponse.ChatResult chatResult = chatResponse.getChatResult();
        
        ConversationMessage.MessageMetadata metadata = ConversationMessage.MessageMetadata.builder()
                .intent(chatResult.getIntent())
                .executedTasks(chatResult.getExecutedTasks())
                .taskResults(chatResult.getTaskResults())
                .modelCategory(chatResult.getModelCategory())
                .modelName(chatResult.getModelName())
                                .tokenUsage(chatResult.getTokenUsage() != null 
                        ? ConversationMessage.MessageMetadata.TokenUsage.builder()
                                .promptTokens(chatResult.getTokenUsage().getPromptTokens())
                                .completionTokens(chatResult.getTokenUsage().getCompletionTokens())
                                .totalTokens(chatResult.getTokenUsage().getTotalTokens())
                                .costUsd(chatResult.getTokenUsage().getCostUsd())
                                .build()
                        : null)
                .additionalData(chatResult.getMetadata() != null ? chatResult.getMetadata() : new HashMap<>())
                .build();
        
        ConversationMessage assistantMessage = ConversationMessage.builder()
                .conversationId(conversation.getId())
                .role("ASSISTANT")
                .content(chatResult.getMessage())
                .timestamp(LocalDateTime.now())
                .metadata(metadata)
                .build();
        
        return conversationService.addMessage(assistantMessage);
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
            "conversationId", conversationId
        ));
        
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
                            "accumulated", accumulated.toString()
                        ));
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
                            "accumulated", accumulated.toString()
                        ));
                        return;
                    }
                }
                
                // Publish streaming complete
                publishChatUpdate("LLM_RESPONSE_STREAMING_COMPLETE", Map.of(
                    "conversationId", conversationId,
                    "message", accumulated.toString()
                ));
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
}

