package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.LinqWorkflowExecution;
import org.lite.gateway.model.ExecutionStatus;
import org.lite.gateway.repository.LinqWorkflowExecutionRepository;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.service.LinqWorkflowExecutionService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.LinqLlmModelService;
import org.lite.gateway.service.LinqMicroService;
import org.lite.gateway.service.QueuedWorkflowService;
import org.lite.gateway.service.WorkflowExecutionContext;
import org.lite.gateway.service.LlmCostService;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class LinqWorkflowExecutionServiceImpl implements LinqWorkflowExecutionService {
    private final LinqWorkflowExecutionRepository executionRepository;
    private final TeamContextService teamContextService;
    private final LinqLlmModelRepository linqLlmModelRepository;
    private final LinqLlmModelService linqLlmModelService;
    private final LinqMicroService linqMicroService;
    private final QueuedWorkflowService queuedWorkflowService;
    private final LlmCostService llmCostService;

    @Override
    public Mono<LinqResponse> executeWorkflow(LinqRequest request) {
        List<LinqRequest.Query.WorkflowStep> steps = request.getQuery().getWorkflow();
        Map<Integer, Object> stepResults = new HashMap<>();
        List<LinqResponse.WorkflowStepMetadata> stepMetadata = new ArrayList<>();
        Map<String, Object> globalParams = request.getQuery().getParams();
        WorkflowExecutionContext context = new WorkflowExecutionContext(stepResults, globalParams);

        // Prefer teamId from params if present; otherwise fallback to auth context
        Mono<String> teamIdMono = Mono.defer(() -> {
            String teamFromParams = null;
            if (request.getQuery() != null && request.getQuery().getParams() != null) {
                Object val = request.getQuery().getParams().get("teamId");
                if (val != null) teamFromParams = String.valueOf(val);
            }
            if (teamFromParams != null && !teamFromParams.isBlank()) {
                return Mono.just(teamFromParams);
            }
            return teamContextService.getTeamFromContext()
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Team context not found. Please ensure you are authenticated with a valid team.")));
        });

        // Execute steps synchronously or asynchronously based on configuration
        Mono<LinqResponse> workflowMono = Mono.just(new LinqResponse());
        for (LinqRequest.Query.WorkflowStep step : steps) {
            workflowMono = workflowMono.flatMap(response -> {
                Instant start = Instant.now();
                
                // Check if step should be executed asynchronously
                if (step.getAsync() != null && step.getAsync()) {
                    // Create a WorkflowStep for async execution
                    LinqResponse.WorkflowStep asyncStep = new LinqResponse.WorkflowStep();
                    asyncStep.setStep(step.getStep());
                    asyncStep.setTarget(step.getTarget());
                    asyncStep.setParams(step.getParams());
                    asyncStep.setAction(step.getAction());
                    asyncStep.setIntent(step.getIntent());
                    asyncStep.setAsync(true);
                    
                    // Queue the step for async execution
                    return queuedWorkflowService.queueAsyncStep(request.getQuery().getWorkflowId(),step.getStep(), asyncStep)
                            .doOnSuccess(v -> log.info("Async step queued successfully for workflow {} step {}", 
                                request.getQuery().getWorkflowId(), step.getStep()))
                            .doOnError(e -> {
                                log.error("Failed to queue async step for workflow {} step {}: {}", 
                                    request.getQuery().getWorkflowId(), step.getStep(), e.getMessage(), e);
                                // Create error metadata for failed queueing
                                LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                                meta.setStep(step.getStep());
                                meta.setStatus("error");
                                meta.setDurationMs(0);
                                meta.setTarget(step.getTarget());
                                meta.setExecutedAt(LocalDateTime.now());
                                meta.setAsync(true);
                                stepMetadata.add(meta);
                            })
                            .then(Mono.defer(() -> {
                                // Create metadata for async step
                                LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                                meta.setStep(step.getStep());
                                meta.setStatus("queued");
                                meta.setDurationMs(0);
                                meta.setTarget(step.getTarget());
                                meta.setExecutedAt(LocalDateTime.now());
                                meta.setAsync(true);  // Set isAsync to true for async steps
                                stepMetadata.add(meta);
                                
                                return Mono.just(response);
                            }));
                }

                // Create a single-step LinqRequest
                LinqRequest stepRequest = new LinqRequest();
                LinqRequest.Link stepLink = new LinqRequest.Link();
                stepLink.setTarget(step.getTarget());
                stepLink.setAction(step.getAction());
                stepRequest.setLink(stepLink);

                LinqRequest.Query stepQuery = new LinqRequest.Query();
                stepQuery.setIntent(step.getIntent());
                stepQuery.setParams(resolvePlaceholdersForMap(step.getParams(), context));
                stepQuery.setPayload(resolvePlaceholders(step.getPayload(), context));
                stepQuery.setLlmConfig(step.getLlmConfig());

                // Merge global params (e.g., teamId, userId) so downstream services see them
                Map<String, Object> mergedParams = new HashMap<>();
                if (globalParams != null) {
                    mergedParams.putAll(globalParams);
                }
                if (stepQuery.getParams() != null) {
                    mergedParams.putAll(stepQuery.getParams());
                }
                stepQuery.setParams(mergedParams);

                // Set the workflow steps in the query to enable caching
                stepQuery.setWorkflow(steps);
                stepRequest.setQuery(stepQuery);
                
                // Copy the executedBy field to maintain user context
                stepRequest.setExecutedBy(request.getExecutedBy());

                // Execute the step
                return teamIdMono
                    .flatMap(teamId -> {
                        log.info("🔍 Searching for LLM model configuration: target={}, teamId={}", step.getTarget(), teamId);
                        return linqLlmModelRepository.findByTargetAndTeamId(step.getTarget(), teamId)
                            .doOnNext(llmModel -> log.info("✅ Found LLM model configuration: target={}, modelType={}", 
                                llmModel.getTarget(), llmModel.getModelType()))
                            .doOnError(error -> log.error("❌ Error finding LLM model for target {}: {}", step.getTarget(), error.getMessage()))
                            .doOnSuccess(llmModel -> {
                                if (llmModel == null) {
                                    log.warn("⚠️ No LLM model configuration found for target: {}, will try microservice", step.getTarget());
                                }
                            });
                    })
                    .doOnNext(llmModel -> log.info("🚀 About to execute LLM request for step {}", step.getStep()))
                    .flatMap(llmModel -> linqLlmModelService.executeLlmRequest(stepRequest, llmModel))
                    .doOnNext(stepResponse -> log.info("✅ LLM request executed successfully for step {}", step.getStep()))
                    .switchIfEmpty(Mono.<LinqResponse>defer(() -> {
                        log.info("📡 No LLM model found, executing microservice request for target: {}", step.getTarget());
                        return linqMicroService.execute(stepRequest);
                    }))
                    .flatMap(stepResponse -> {
                        // Check if the result contains an error
                        if (stepResponse.getResult() instanceof Map<?, ?> resultMap && 
                            resultMap.containsKey("error")) {
                            return Mono.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR,
                                String.format("Workflow step %d failed: %s", 
                                    step.getStep(), 
                                    resultMap.get("error"))
                            ));
                        }
                        
                        stepResults.put(step.getStep(), stepResponse.getResult());
                        long durationMs = Duration.between(start, Instant.now()).toMillis();
                        LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                        meta.setStep(step.getStep());
                        meta.setStatus("success");
                        meta.setDurationMs(durationMs);
                        meta.setTarget(step.getTarget());
                        meta.setExecutedAt(LocalDateTime.now());
                        stepMetadata.add(meta);
                        return Mono.just(response);
                    })
                    .onErrorResume(error -> {
                        long durationMs = Duration.between(start, Instant.now()).toMillis();
                        LinqResponse.WorkflowStepMetadata meta = new LinqResponse.WorkflowStepMetadata();
                        meta.setStep(step.getStep());
                        meta.setStatus("error");
                        meta.setDurationMs(durationMs);
                        meta.setTarget(step.getTarget());
                        meta.setExecutedAt(LocalDateTime.now());
                        stepMetadata.add(meta);
                        log.error("Error in workflow step {}: {}", step.getStep(), error.getMessage());
                        return Mono.error(new ResponseStatusException(
                            HttpStatus.INTERNAL_SERVER_ERROR,
                            String.format("Workflow step %d failed: %s", step.getStep(), error.getMessage())
                        ));
                    });
            });
        }

        return workflowMono.flatMap(response -> 
            teamIdMono.map(teamId -> {
                // Build WorkflowResult
                LinqResponse.WorkflowResult workflowResult = new LinqResponse.WorkflowResult();
                List<LinqResponse.WorkflowStep> stepResultList = steps.stream()
                        .map(step -> {
                            LinqResponse.WorkflowStep stepResult = new LinqResponse.WorkflowStep();
                            stepResult.setStep(step.getStep());
                            stepResult.setTarget(step.getTarget());
                            stepResult.setResult(stepResults.get(step.getStep()));
                            return stepResult;
                        })
                        .collect(Collectors.toList());
                workflowResult.setSteps(stepResultList);

                // Set final result (from last step)
                Object lastResult = stepResults.get(steps.getLast().getStep());
                workflowResult.setFinalResult(extractFinalResult(lastResult));

                // Set response
                response.setResult(workflowResult);
                LinqResponse.Metadata metadata = new LinqResponse.Metadata();
                
                // Check if any step has failed
                boolean hasFailedStep = stepMetadata.stream()
                    .anyMatch(meta -> "error".equals(meta.getStatus()) || "failed".equals(meta.getStatus()));
                metadata.setStatus(hasFailedStep ? "error" : "success");
                
                metadata.setTeamId(teamId);
                metadata.setCacheHit(false);
                metadata.setWorkflowMetadata(stepMetadata);
                response.setMetadata(metadata);
                return response;
            })
        ).onErrorResume(error -> {
            // Create error response
            LinqResponse errorResponse = new LinqResponse();
            LinqResponse.Metadata metadata = new LinqResponse.Metadata();
            metadata.setSource("workflow");
            metadata.setStatus("error");
            // Try to use teamId from params on error path as well
            String fallbackTeamId = null;
            if (request.getQuery() != null && request.getQuery().getParams() != null) {
                Object val = request.getQuery().getParams().get("teamId");
                if (val != null) fallbackTeamId = String.valueOf(val);
            }
            if (fallbackTeamId == null || fallbackTeamId.isBlank()) {
                fallbackTeamId = teamContextService.getTeamFromContext().block();
            }
            metadata.setTeamId(fallbackTeamId);
            metadata.setCacheHit(false);
            metadata.setWorkflowMetadata(stepMetadata);
            errorResponse.setMetadata(metadata);
            
            // Set error result
            LinqResponse.WorkflowResult errorResult = new LinqResponse.WorkflowResult();
            errorResult.setSteps(steps.stream()
                .map(step -> {
                    LinqResponse.WorkflowStep stepResult = new LinqResponse.WorkflowStep();
                    stepResult.setStep(step.getStep());
                    stepResult.setTarget(step.getTarget());
                    stepResult.setResult(stepResults.get(step.getStep()));
                    return stepResult;
                })
                .collect(Collectors.toList()));
            errorResult.setFinalResult(error.getMessage());
            errorResponse.setResult(errorResult);
            
            return Mono.just(errorResponse);
        });
    }

    @Override
    public Mono<LinqWorkflowExecution> trackExecution(LinqRequest request, LinqResponse response) {
        return trackExecution(request, response, null);
    }
    
    @Override
    public Mono<LinqWorkflowExecution> trackExecutionWithAgentContext(LinqRequest request, LinqResponse response, Map<String, Object> agentContext) {
        return trackExecution(request, response, agentContext);
    }
    
    /**
     * Internal method that handles both regular and agent context tracking
     */
    private Mono<LinqWorkflowExecution> trackExecution(LinqRequest request, LinqResponse response, Map<String, Object> agentContext) {
        LinqWorkflowExecution execution = new LinqWorkflowExecution();
        execution.setTeamId(response.getMetadata().getTeamId());
        execution.setRequest(request);
        execution.setResponse(response);
        execution.setExecutedAt(LocalDateTime.now());
        
        // Set status based on metadata status
        execution.setStatus("success".equals(response.getMetadata().getStatus()) ? 
            ExecutionStatus.SUCCESS : ExecutionStatus.FAILED);
        
        // Set agent context fields if provided
        if (agentContext != null) {
            execution.setAgentId((String) agentContext.get("agentId"));
            execution.setAgentName((String) agentContext.get("agentName"));
            execution.setAgentTaskId((String) agentContext.get("agentTaskId"));
            execution.setAgentTaskName((String) agentContext.get("agentTaskName"));
            execution.setExecutionSource((String) agentContext.get("executionSource"));
            execution.setAgentExecutionId((String) agentContext.get("agentExecutionId"));
        }
        
        // Calculate duration from metadata if available
        if (response.getMetadata() != null && response.getMetadata().getWorkflowMetadata() != null) {
            long totalDuration = response.getMetadata().getWorkflowMetadata().stream()
                .mapToLong(LinqResponse.WorkflowStepMetadata::getDurationMs)
                .sum();
            execution.setDurationMs(totalDuration);
            
            // Set token usage for AI models
            response.getMetadata().getWorkflowMetadata().forEach(stepMetadata -> {
                if (stepMetadata.getTarget().equals("openai") || stepMetadata.getTarget().equals("gemini")) {
                    // Get token usage from the step's result if available
                    if (response.getResult() instanceof LinqResponse.WorkflowResult workflowResult) {
                        workflowResult.getSteps().stream()
                            .filter(step -> step.getStep() == stepMetadata.getStep())
                            .findFirst()
                            .ifPresent(step -> {
                                if (step.getResult() instanceof Map<?, ?> resultMap) {
                                    LinqResponse.WorkflowStepMetadata.TokenUsage tokenUsage = new LinqResponse.WorkflowStepMetadata.TokenUsage();
                                    
                                    if (stepMetadata.getTarget().equals("openai") && resultMap.containsKey("usage")) {
                                        // Handle OpenAI token usage
                                        Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
                                        long promptTokens = ((Number) usage.get("prompt_tokens")).longValue();
                                        long completionTokens = ((Number) usage.get("completion_tokens")).longValue();
                                        long totalTokens = ((Number) usage.get("total_tokens")).longValue();
                                        
                                        tokenUsage.setPromptTokens(promptTokens);
                                        tokenUsage.setCompletionTokens(completionTokens);
                                        tokenUsage.setTotalTokens(totalTokens);
                                        
                                        // Set OpenAI model
                                        String model = (String) resultMap.get("model");
                                        stepMetadata.setModel(model);
                                        
                                        // Calculate and store cost at execution time
                                        double cost = llmCostService.calculateCost(model, promptTokens, completionTokens);
                                        tokenUsage.setCostUsd(cost);
                                        log.debug("Calculated cost for {} ({}p/{}c tokens): ${}", 
                                            model, promptTokens, completionTokens, String.format("%.6f", cost));
                                            
                                    } else if (stepMetadata.getTarget().equals("gemini") && resultMap.containsKey("usageMetadata")) {
                                        // Handle Gemini token usage
                                        Map<?, ?> usageMetadata = (Map<?, ?>) resultMap.get("usageMetadata");
                                        long promptTokens = ((Number) usageMetadata.get("promptTokenCount")).longValue();
                                        long completionTokens = ((Number) usageMetadata.get("candidatesTokenCount")).longValue();
                                        long totalTokens = ((Number) usageMetadata.get("totalTokenCount")).longValue();
                                        
                                        tokenUsage.setPromptTokens(promptTokens);
                                        tokenUsage.setCompletionTokens(completionTokens);
                                        tokenUsage.setTotalTokens(totalTokens);
                                        
                                        // Set Gemini model
                                        String model = (String) resultMap.get("modelVersion");
                                        stepMetadata.setModel(model);
                                        
                                        // Calculate and store cost at execution time
                                        double cost = llmCostService.calculateCost(model, promptTokens, completionTokens);
                                        tokenUsage.setCostUsd(cost);
                                        log.debug("Calculated cost for {} ({}p/{}c tokens): ${}", 
                                            model, promptTokens, completionTokens, String.format("%.6f", cost));
                                    }
                                    
                                    stepMetadata.setTokenUsage(tokenUsage);
                                }
                            });
                    }
                }
            });
        }
        
        if (request.getQuery() != null && request.getQuery().getWorkflowId() != null) {
            execution.setWorkflowId(request.getQuery().getWorkflowId());
        }
        
        // Enhanced logging based on whether it's agent-triggered or not
        if (agentContext != null) {
            return executionRepository.save(execution)
                .doOnSuccess(e -> log.info("Tracked agent workflow execution: {} for agent: {} with status: {}", 
                    e.getId(), e.getAgentName(), e.getStatus()))
                .doOnError(error -> log.error("Error tracking agent workflow execution: {}", error.getMessage()));
        } else {
            return executionRepository.save(execution)
                .doOnSuccess(e -> log.info("Tracked workflow execution: {} with status: {}", e.getId(), e.getStatus()))
                .doOnError(error -> log.error("Error tracking workflow execution: {}", error.getMessage()));
        }
    }

    @Override
    public Flux<LinqWorkflowExecution> getWorkflowExecutions(String workflowId) {
        return executionRepository.findByWorkflowId(workflowId, Sort.by(Sort.Direction.DESC, "executedAt"))
            .doOnError(error -> log.error("Error fetching workflow executions: {}", error.getMessage()));
    }

    @Override
    public Flux<LinqWorkflowExecution> getTeamExecutions() {
        return teamContextService.getTeamFromContext()
            .flatMapMany(teamId -> executionRepository.findByTeamId(teamId, Sort.by(Sort.Direction.DESC, "executedAt")))
            .doOnError(error -> log.error("Error fetching team executions: {}", error.getMessage()));
    }

    @Override
    public Mono<LinqWorkflowExecution> getExecution(String executionId) {
        return executionRepository.findById(executionId)
            .doOnError(error -> log.error("Error fetching execution: {}", error.getMessage()))
            .switchIfEmpty(Mono.error(new RuntimeException("Execution not found")));
    }

    public Mono<LinqWorkflowExecution> getExecutionByAgentExecutionId(String agentExecutionId) {
        log.info("Fetching execution by agentExecutionId: {}", agentExecutionId);
        return executionRepository.findByAgentExecutionId(agentExecutionId)
            .doOnSuccess(exec -> {
                if (exec != null) {
                    log.info("Found execution with _id: {} for agentExecutionId: {}", exec.getId(), agentExecutionId);
                } else {
                    log.warn("No execution found for agentExecutionId: {}", agentExecutionId);
                }
            })
            .doOnError(error -> log.error("Error fetching execution by agentExecutionId: {}", error.getMessage()))
            .switchIfEmpty(Mono.error(new RuntimeException("Execution not found for agentExecutionId: " + agentExecutionId)));
    }

    @Override
    public Mono<Void> deleteExecution(String executionId) {
        return teamContextService.getTeamFromContext()
            .flatMap(teamId -> executionRepository.findById(executionId)
                .filter(execution -> execution.getTeamId().equals(teamId))
                .switchIfEmpty(Mono.error(new ResponseStatusException(
                    HttpStatus.NOT_FOUND, 
                    "Execution not found or access denied")))
                .flatMap(execution -> executionRepository.delete(execution)
                    .doOnSuccess(v -> log.info("Deleted execution: {}", executionId))
                    .doOnError(error -> log.error("Error deleting execution: {}", error.getMessage()))));
    }

    private Map<String, Object> resolvePlaceholdersForMap(Map<String, Object> input, WorkflowExecutionContext context) {
        if (input == null) return new HashMap<>();
        Map<String, Object> resolved = new HashMap<>();
        input.forEach((key, value) -> resolved.put(key, resolvePlaceholders(value, context)));
        return resolved;
    }

    private Object resolvePlaceholders(Object input, WorkflowExecutionContext context) {
        if (input == null) return null;
        if (input instanceof String stringInput) {
            return resolvePlaceholder(stringInput, context);
        }
        if (input instanceof List<?> list) {
            return list.stream()
                    .map(item -> resolvePlaceholders(item, context))
                    .collect(Collectors.toList());
        }
        if (input instanceof Map<?, ?> map) {
            return resolvePlaceholdersForMap(map.entrySet().stream()
                    .collect(Collectors.toMap(
                            entry -> entry.getKey().toString(),
                            Map.Entry::getValue
                    )), context);
        }
        return input;
    }

    private String resolvePlaceholder(String value, WorkflowExecutionContext context) {
        String result = value;
        // Step result pattern - updated to handle complex JSON paths including arrays and nested objects
        Pattern stepPattern = Pattern.compile("\\{\\{step(\\d+)\\.result(?:\\.([^}]+))?\\}\\}");
        Matcher stepMatcher = stepPattern.matcher(value);
        while (stepMatcher.find()) {
            int stepNum = Integer.parseInt(stepMatcher.group(1));
            String path = stepMatcher.group(2);
            Object stepResult = context.getStepResults().get(stepNum);
            String replacement = "";
            if (stepResult != null) {
                replacement = path != null ? extractValue(stepResult, path) : String.valueOf(stepResult);
            }
            result = result.replace(stepMatcher.group(0), replacement);
        }
        // Global params pattern
        Pattern paramsPattern = Pattern.compile("\\{\\{params\\.([\\w.]+)\\}\\}");
        Matcher paramsMatcher = paramsPattern.matcher(result);
        while (paramsMatcher.find()) {
            String paramPath = paramsMatcher.group(1);
            String replacement = "";
            if (context.getGlobalParams() != null) {
                replacement = extractValue(context.getGlobalParams(), paramPath);
            }
            result = result.replace(paramsMatcher.group(0), replacement);
        }
        return result;
    }

    private String extractValue(Object obj, String path) {
        String[] parts = path.split("\\.");
        Object current = obj;
        for (String part : parts) {
            if (current == null) return "";
            
            // Check if this part contains array access (e.g., "choices[0]")
            if (part.contains("[")) {
                String arrayName = part.substring(0, part.indexOf("["));
                String indexStr = part.substring(part.indexOf("[") + 1, part.indexOf("]"));
                
                if (current instanceof Map<?, ?> map) {
                    current = map.get(arrayName);
                    if (current instanceof List<?> list && indexStr.matches("\\d+")) {
                        int index = Integer.parseInt(indexStr);
                        if (index >= 0 && index < list.size()) {
                            current = list.get(index);
                        } else {
                            return "";
                        }
                    } else {
                        return "";
                    }
                } else {
                    return "";
                }
            } else {
                // Regular property access
                if (current instanceof Map<?, ?> map) {
                    current = map.get(part);
                } else if (current instanceof List<?> list && part.matches("\\d+")) {
                    int index = Integer.parseInt(part);
                    if (index >= 0 && index < list.size()) {
                        current = list.get(index);
                    } else {
                        return "";
                    }
                } else {
                    return "";
                }
            }
        }
        return current != null ? String.valueOf(current) : "";
    }

    private String extractFinalResult(Object result) {
        if (result instanceof Map<?, ?> resultMap) {
            Object choices = resultMap.get("choices");
            if (choices instanceof List<?> choiceList && !choiceList.isEmpty()) {
                Object firstChoice = choiceList.getFirst();
                if (firstChoice instanceof Map<?, ?> choiceMap) {
                    Object message = choiceMap.get("message");
                    if (message instanceof Map<?, ?> messageMap) {
                        Object content = messageMap.get("content");
                        return content != null ? content.toString() : "";
                    }
                }
            }
        }
        return result != null ? result.toString() : "";
    }
} 