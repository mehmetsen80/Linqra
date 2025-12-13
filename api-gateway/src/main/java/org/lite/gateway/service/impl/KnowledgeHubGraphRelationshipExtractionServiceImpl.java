package org.lite.gateway.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.repository.KnowledgeHubDocumentMetaDataRepository;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.service.GraphExtractionJobService;
import org.lite.gateway.service.KnowledgeHubGraphRelationshipExtractionService;
import org.lite.gateway.service.LinqLlmModelService;
import org.lite.gateway.service.LlmCostService;
import org.lite.gateway.service.Neo4jGraphService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeHubGraphRelationshipExtractionServiceImpl implements KnowledgeHubGraphRelationshipExtractionService {
    
    private final Neo4jGraphService graphService;
    private final KnowledgeHubDocumentMetaDataRepository metadataRepository;
    private final LinqLlmModelRepository linqLlmModelRepository;
    private final LinqLlmModelService linqLlmModelService;
    private final LlmCostService llmCostService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Optional: for progress reporting during extraction jobs
    // Use @Lazy to break circular dependency with GraphExtractionJobServiceImpl
    @Autowired(required = false)
    @Lazy
    private GraphExtractionJobService graphExtractionJobService;
    
    // Chat model categories for relationship extraction
    // Ordered by cost priority: cheapest first (Gemini, Cohere) to most expensive
    private static final List<String> CHAT_MODEL_CATEGORIES = List.of(
            "gemini-chat", "cohere-chat", "openai-chat", "claude-chat", "mistral-chat"
    );
    private static final int MAX_ENTITIES_PER_BATCH = 10; // Process entities in batches
    
    // Estimate average tokens per request for cost comparison (1500 prompt + 1000 completion = 2500 total)
    private static final long ESTIMATED_PROMPT_TOKENS = 1500;
    private static final long ESTIMATED_COMPLETION_TOKENS = 1000;
    
    @Override
    public Mono<Integer> extractRelationshipsFromDocument(String documentId, String teamId) {
        return extractRelationshipsFromDocument(documentId, teamId, false);
    }
    
    public Mono<Integer> extractRelationshipsFromDocument(String documentId, String teamId, boolean force) {
        log.info("Starting relationship extraction for document: {}, team: {}, force: {}", documentId, teamId, force);
        
        // Record start time for duration tracking
        final long startedAt = System.currentTimeMillis();
        
        // Check if relationships were already extracted (unless force=true)
        Mono<Boolean> shouldProceed = force 
                ? Mono.just(true)
                : metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                        .flatMap(metadata -> {
                            if (metadata.getCustomMetadata() != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata().get("graphExtraction");
                                if (graphExtraction != null) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> relationshipExtraction = (Map<String, Object>) graphExtraction.get("relationshipExtraction");
                                    if (relationshipExtraction != null) {
                                        // Relationships already extracted - check if any exist in Neo4j
                                        // Query for any relationships with this documentId
                                        String cypherQuery = "MATCH ()-[r]-() WHERE r.documentId = $documentId AND r.teamId = $teamId RETURN count(r) as count";
                                        Map<String, Object> params = Map.of("documentId", documentId, "teamId", teamId);
                                        return graphService.executeQuery(cypherQuery, params)
                                                .next()
                                                .map(result -> {
                                                    Long count = result.containsKey("count") ? ((Number) result.get("count")).longValue() : 0L;
                                                    return count == 0; // Proceed only if no relationships exist
                                                })
                                                .defaultIfEmpty(true);
                                    }
                                }
                            }
                            return Mono.just(true); // No previous extraction, proceed
                        })
                        .defaultIfEmpty(true);
        
        return shouldProceed
                .flatMap(proceed -> {
                    if (!proceed) {
                        log.warn("Relationships already extracted for document: {}. Use force=true to re-extract.", documentId);
                        return getPreviousRelationshipExtractionCost(documentId, teamId)
                                .flatMap(previousCost -> Mono.<Integer>error(new RuntimeException(
                                        String.format("Relationships already extracted for this document. Previous extraction cost: $%.6f. Use force=true parameter to re-extract (will incur additional costs).", previousCost))));
                    }
        
                    // Find all entities for this document
                    return graphService.findEntities("Form", Map.of("documentId", documentId), teamId)
                .mergeWith(graphService.findEntities("Organization", Map.of("documentId", documentId), teamId))
                .mergeWith(graphService.findEntities("Person", Map.of("documentId", documentId), teamId))
                .mergeWith(graphService.findEntities("Document", Map.of("documentId", documentId), teamId))
                .mergeWith(graphService.findEntities("Location", Map.of("documentId", documentId), teamId))
                .mergeWith(graphService.findEntities("Date", Map.of("documentId", documentId), teamId))
                .collectList()
                .flatMap(allEntities -> {
                    if (allEntities.isEmpty()) {
                        log.warn("No entities found for document: {}", documentId);
                        return Mono.just(0);
                    }
                    
                    log.info("Found {} entities for document: {}", allEntities.size(), documentId);
                    
                    // Process entities in batches
                    List<List<Map<String, Object>>> batches = partitionList(allEntities, MAX_ENTITIES_PER_BATCH);
                    log.info("Processing {} batches of entities for relationship extraction", batches.size());
                    
                    // Track total token usage and cost across all batches
                    AtomicLong totalPromptTokens = new AtomicLong(0);
                    AtomicLong totalCompletionTokens = new AtomicLong(0);
                    AtomicLong totalTokens = new AtomicLong(0);
                    AtomicReference<Double> totalCost = new AtomicReference<>(0.0);
                    AtomicInteger totalRelationshipsExtracted = new AtomicInteger(0);
                    
                    // Track model information (use first model encountered)
                    AtomicReference<String> modelName = new AtomicReference<>(null);
                    AtomicReference<String> modelCategory = new AtomicReference<>(null);
                    AtomicReference<String> provider = new AtomicReference<>(null);
                    
                    // Extract jobId from thread-local or context if available
                    // For now, we'll look it up from documentId if graphExtractionJobService is available
                    Mono<String> jobIdMono = graphExtractionJobService != null
                            ? graphExtractionJobService.getJobsForDocument(documentId, teamId)
                                    .filter(job -> "QUEUED".equals(job.getStatus()) || "RUNNING".equals(job.getStatus()))
                                    .filter(job -> "relationships".equals(job.getExtractionType()) || "all".equals(job.getExtractionType()))
                                    .next()
                                    .map(job -> job.getJobId())
                                    .defaultIfEmpty("")
                            : Mono.just("");
                    
                    return jobIdMono
                            .flatMap(jobId -> Flux.fromIterable(batches)
                                    .index()
                                    .concatMap(tuple -> {
                                        long batchIndex = tuple.getT1();
                                        List<Map<String, Object>> batch = tuple.getT2();
                                        
                                        log.debug("Processing relationship batch {}/{} with {} entities", 
                                                batchIndex + 1, batches.size(), batch.size());
                                        
                                        // Add delay between batches to respect rate limits and prevent hitting API rate limits
                                        // Use longer delay to prevent rate limit errors (3 seconds between batches)
                                        // This ensures we don't overwhelm the LLM API with rapid requests
                                        Mono<RelationshipExtractionResult> extractionMono = batchIndex > 0 
                                                ? Mono.delay(Duration.ofSeconds(3))
                                                        .then(extractRelationshipsFromEntitiesWithCostTracking(batch, documentId, teamId))
                                                : extractRelationshipsFromEntitiesWithCostTracking(batch, documentId, teamId);
                                        
                                        // Extract relationships from this batch
                                        // Note: Model fallback (handled in extractRelationshipsFromEntitiesWithCostTracking) will automatically
                                        // try next available model on rate limit errors, so we don't retry rate limits here
                                        return extractionMono
                                                .flatMap(result -> {
                                                    // Accumulate token usage and cost from this batch
                                                    if (result.tokenUsage != null) {
                                                        totalPromptTokens.addAndGet(result.tokenUsage.promptTokens);
                                                        totalCompletionTokens.addAndGet(result.tokenUsage.completionTokens);
                                                        totalTokens.addAndGet(result.tokenUsage.totalTokens);
                                                        totalCost.updateAndGet(cost -> cost + result.tokenUsage.costUsd);
                                                    }
                                                    
                                                    // Track model information (use first model encountered)
                                                    if (result.modelName != null && modelName.get() == null) {
                                                        modelName.set(result.modelName);
                                                        modelCategory.set(result.modelCategory);
                                                        provider.set(result.provider);
                                                    }
                                                    
                                                    log.debug("Extracted {} relationships from batch {}/{}", 
                                                            result.relationships.size(), batchIndex + 1, batches.size());
                                                    
                                                    // Store relationships in Neo4j
                                                    // Use concatMap instead of flatMap to process sequentially and avoid deadlocks
                                                    // when multiple relationships share the same nodes
                                                    return Flux.fromIterable(result.relationships)
                                                            .concatMap(rel -> {
                                                                String fromType = (String) rel.get("fromType");
                                                                String fromId = (String) rel.get("fromId");
                                                                String relationshipType = (String) rel.get("type");
                                                                String toType = (String) rel.get("toType");
                                                                String toId = (String) rel.get("toId");
                                                                
                                                                // Create a mutable copy of properties map to avoid UnsupportedOperationException
                                                                // when the original map from LLM response is immutable (e.g., Map.of(), Collections.emptyMap())
                                                                Map<String, Object> properties = new HashMap<>();
                                                                
                                                                @SuppressWarnings("unchecked")
                                                                Object propertiesObj = rel.get("properties");
                                                                if (propertiesObj instanceof Map) {
                                                                    Map<String, Object> originalProperties = (Map<String, Object>) propertiesObj;
                                                                    // Copy all properties to new mutable map
                                                                    properties.putAll(originalProperties);
                                                                }
                                                                
                                                                // Add our metadata fields
                                                                properties.put("documentId", documentId);
                                                                properties.put("extractedAt", System.currentTimeMillis());
                                                                
                                                                return graphService.upsertRelationship(
                                                                        fromType, fromId,
                                                                        relationshipType,
                                                                        toType, toId,
                                                                        properties,
                                                                        teamId)
                                                                        .doOnSuccess(success -> 
                                                                                log.debug("Upserted relationship {} from {}:{} to {}:{}", 
                                                                                        relationshipType, fromType, fromId, toType, toId))
                                                                        .onErrorContinue((error, obj) -> 
                                                                                log.error("Error upserting relationship {}: {}", relationshipType, error.getMessage()));
                                                            })
                                                            .then(Mono.just(result.relationships.size()));
                                                })
                                                .doOnNext(batchRelationshipCount -> {
                                                    // Update total relationships extracted
                                                    totalRelationshipsExtracted.addAndGet(batchRelationshipCount);
                                                    
                                                    // Report progress after storing relationships
                                                    if (graphExtractionJobService != null && !jobId.isEmpty()) {
                                                        graphExtractionJobService.updateJobProgress(
                                                                jobId, 
                                                                (int)(batchIndex + 1), 
                                                                batches.size(), 
                                                                null, 
                                                                totalRelationshipsExtracted.get(), 
                                                                totalCost.get()
                                                        ).subscribe(
                                                                null,
                                                                error -> log.warn("Failed to update job progress: {}", error.getMessage())
                                                        );
                                                    }
                                                });
                                    })
                                    .collectList()
                                    .map(sizes -> sizes.stream().mapToInt(Integer::intValue).sum())
                                    .flatMap(relationshipCount -> {
                                        // Save token usage and cost to document metadata
                                        final long completedAt = System.currentTimeMillis();
                                        return saveRelationshipExtractionCosts(documentId, teamId, 
                                                totalPromptTokens.get(), totalCompletionTokens.get(), 
                                                totalTokens.get(), totalCost.get(),
                                                modelName.get(), modelCategory.get(), provider.get(),
                                                startedAt, completedAt)
                                                .thenReturn(relationshipCount);
                                    })
                                    .doOnSuccess(total -> log.info("Extracted {} total relationships from document: {}", total, documentId)));
                });
                    })
                .doOnError(error -> log.error("Error extracting relationships from document {}: {}", documentId, error.getMessage(), error));
    }
    
    /**
     * Get previous relationship extraction cost for a document (for warning messages)
     */
    private Mono<Double> getPreviousRelationshipExtractionCost(String documentId, String teamId) {
        return metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                .map(metadata -> {
                    if (metadata.getCustomMetadata() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata().get("graphExtraction");
                        if (graphExtraction != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> relationshipExtraction = (Map<String, Object>) graphExtraction.get("relationshipExtraction");
                            if (relationshipExtraction != null && relationshipExtraction.containsKey("costUsd")) {
                                Object costObj = relationshipExtraction.get("costUsd");
                                if (costObj instanceof Number) {
                                    return ((Number) costObj).doubleValue();
                                }
                            }
                        }
                    }
                    return 0.0;
                })
                .defaultIfEmpty(0.0);
    }
    
    @Override
    public Mono<List<Map<String, Object>>> extractRelationshipsFromEntities(
            List<Map<String, Object>> entities, String documentId, String teamId) {
        log.debug("Extracting relationships from {} entities for document: {}", entities.size(), documentId);
        
        if (entities.isEmpty() || entities.size() < 2) {
            log.debug("Not enough entities to extract relationships (need at least 2)");
            return Mono.just(Collections.emptyList());
        }
        
        // Find any available chat model for relationship extraction (check categories sequentially to avoid unnecessary queries)
        // Use concatMap with take(1) to process sequentially and stop after finding the first model
        Mono<org.lite.gateway.entity.LinqLlmModel> llmModelMono = Flux.fromIterable(CHAT_MODEL_CATEGORIES)
                .concatMap(category -> linqLlmModelRepository.findByModelCategoryAndTeamId(category, teamId).next())
                .take(1)
                .single()
                .switchIfEmpty(Mono.error(new RuntimeException(
                        "No chat model found for relationship extraction. Please configure a chat model for team: " + teamId)));
        
        return llmModelMono
                .flatMap(llmModel -> {
                    // Build relationship extraction prompt
                    String prompt = buildRelationshipExtractionPrompt(entities);
                    
                    // Build LinqRequest
                    LinqRequest request = new LinqRequest();
                    LinqRequest.Link link = new LinqRequest.Link();
                    link.setTarget(llmModel.getModelCategory());
                    link.setAction("generate");
                    request.setLink(link);
                    
                    LinqRequest.Query query = new LinqRequest.Query();
                    query.setIntent("generate");
                    
                    // Build messages array
                    List<Map<String, Object>> messages = new ArrayList<>();
                    Map<String, Object> systemMessage = new HashMap<>();
                    systemMessage.put("role", "system");
                    systemMessage.put("content", "You are an expert relationship extraction assistant. Analyze entities and identify relationships between them. Return a JSON array of relationships. Each relationship should have: fromType, fromId, toType, toId, type (relationship type), and optional properties. Return ONLY valid JSON, no explanations.");
                    messages.add(systemMessage);
                    
                    Map<String, Object> userMessage = new HashMap<>();
                    userMessage.put("role", "user");
                    userMessage.put("content", prompt);
                    messages.add(userMessage);
                    
                    query.setPayload(messages);
                    
                    LinqRequest.Query.LlmConfig llmConfig = new LinqRequest.Query.LlmConfig();
                    llmConfig.setModel(llmModel.getModelName());
                    Map<String, Object> settings = new HashMap<>();
                    settings.put("temperature", 0.3);
                    settings.put("max_tokens", 8000); // Increased from 2000 to handle large relationship extraction tasks
                    llmConfig.setSettings(settings);
                    query.setLlmConfig(llmConfig);
                    
                    Map<String, Object> params = new HashMap<>();
                    params.put("teamId", teamId);
                    query.setParams(params);
                    
                    request.setQuery(query);
                    
                    // Execute LLM request
                    return linqLlmModelService.executeLlmRequest(request, llmModel)
                            .flatMap(response -> {
                                try {
                                    // Extract and log token usage and cost
                                    TokenUsageResult tokenUsage = extractTokenUsageFromResponse(
                                            response, llmModel.getModelCategory(), llmModel.getModelName());
                                    if (tokenUsage != null) {
                                        log.info("📊 Relationship extraction token usage - prompt: {}, completion: {}, total: {}, cost: ${}",
                                                tokenUsage.promptTokens, tokenUsage.completionTokens,
                                                tokenUsage.totalTokens, String.format("%.6f", tokenUsage.costUsd));
                                    } else {
                                        log.warn("⚠️ Could not extract token usage from LLM response for relationship extraction");
                                    }
                                    
                                    // Extract text from response
                                    String jsonText = extractTextFromResponse(response, llmModel.getModelCategory());
                                    log.debug("LLM response text: {}", jsonText);
                                    
                                    // Parse JSON array of relationships
                                    List<Map<String, Object>> relationships = objectMapper.readValue(
                                            jsonText, 
                                            new TypeReference<List<Map<String, Object>>>() {});
                                    
                                    log.debug("Parsed {} relationships from LLM response", relationships.size());
                                    
                                    // Return relationships only (maintains interface contract)
                                    return Mono.just(relationships);
                                } catch (Exception e) {
                                    log.error("Error parsing LLM response: {}", e.getMessage(), e);
                                    return Mono.error(new RuntimeException("Failed to parse relationship extraction response", e));
                                }
                            });
                })
                .doOnError(error -> log.error("Error extracting relationships from entities: {}", error.getMessage(), error));
    }
    
    /**
     * Extract relationships from entities with cost tracking (internal method that returns both relationships and token usage)
     * Finds cheapest available model first, then tries models with automatic fallback on any error
     */
    private Mono<RelationshipExtractionResult> extractRelationshipsFromEntitiesWithCostTracking(
            List<Map<String, Object>> entities, String documentId, String teamId) {
        log.debug("Extracting relationships from {} entities for document: {}", entities.size(), documentId);
        
        if (entities.isEmpty() || entities.size() < 2) {
            log.debug("Not enough entities to extract relationships (need at least 2)");
            return Mono.just(new RelationshipExtractionResult(Collections.emptyList(), null, null, null, null));
        }
        
        // Build relationship extraction prompt once (same for all models)
        String prompt = buildRelationshipExtractionPrompt(entities);
        
        // Find cheapest available model first
        return linqLlmModelService.findCheapestAvailableModel(CHAT_MODEL_CATEGORIES, teamId, ESTIMATED_PROMPT_TOKENS, ESTIMATED_COMPLETION_TOKENS)
                .flatMap(cheapestModel -> {
                    // Start with cheapest model, but fallback to others if it fails
                    return tryRelationshipExtractionWithModelsStartingWith(entities, prompt, cheapestModel, documentId, teamId);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Fallback to original logic if no cheapest model found
                    log.warn("Could not find cheapest model for relationship extraction, falling back to original model selection logic");
                    return tryRelationshipExtractionWithModels(entities, prompt, documentId, teamId, CHAT_MODEL_CATEGORIES, 0);
                }));
    }
    
    /**
     * Try relationship extraction starting with a specific model, falling back to others on error
     */
    private Mono<RelationshipExtractionResult> tryRelationshipExtractionWithModelsStartingWith(
            List<Map<String, Object>> entities, String prompt, 
            org.lite.gateway.entity.LinqLlmModel preferredModel,
            String documentId, String teamId) {
        
        // Find the index of the preferred model's category
        final int preferredIndex = CHAT_MODEL_CATEGORIES.indexOf(preferredModel.getModelCategory());
        final int startIndex = preferredIndex == -1 ? 0 : preferredIndex + 1; // Start after preferred model
        
        // Try the preferred model first
        return executeRelationshipExtractionWithModel(entities, prompt, preferredModel, documentId, teamId)
                .onErrorResume(error -> {
                    log.warn("Preferred model {} failed with error: {}. Trying other available models...", 
                            preferredModel.getModelName(), error.getMessage());
                    // Try other models in order (starting after the preferred model's category)
                    return tryRelationshipExtractionWithModels(entities, prompt, documentId, teamId, CHAT_MODEL_CATEGORIES, startIndex);
                });
    }
    
    /**
     * Try relationship extraction with models, falling back to next model on any error
     */
    private Mono<RelationshipExtractionResult> tryRelationshipExtractionWithModels(
            List<Map<String, Object>> entities, String prompt, String documentId, String teamId,
            List<String> categories, int startIndex) {
        
        if (startIndex >= categories.size()) {
            return Mono.error(new RuntimeException(
                    "All available chat models failed. Please check your model configurations or try again later."));
        }
        
        String category = categories.get(startIndex);
        log.debug("Trying relationship extraction with model category: {}", category);
        
        return linqLlmModelRepository.findByModelCategoryAndTeamId(category, teamId)
                .next()
                .flatMap(llmModel -> {
                    log.info("Using model {}:{} for relationship extraction", llmModel.getModelCategory(), llmModel.getModelName());
                    return executeRelationshipExtractionWithModel(entities, prompt, llmModel, documentId, teamId);
                })
                .onErrorResume(error -> {
                    // Try next model on ANY error - this provides resilience against:
                    // - Rate limit errors (429, TOO_MANY_REQUESTS, tokens per min)
                    // - Network/API failures (temporary issues)
                    // - Model-specific errors (API down, invalid response)
                    // - Any other transient failures
                    
                    // Robust rate limit detection: check exception type and message format
                    boolean isRateLimit = false;
                    
                    // Check 1: WebClientResponseException with status 429
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientEx) {
                        if (webClientEx.getStatusCode() != null && webClientEx.getStatusCode().value() == 429) {
                            isRateLimit = true;
                        }
                    }
                    
                    // Check 2: Parse error message for HTTP status code format (fallback)
                    // Error messages from LinqLlmModelServiceImpl follow format: "HTTP {statusCode}: {errorMessage}"
                    // But errors from other sources may have different formats
                    if (!isRateLimit) {
                        String errorMsg = error.getMessage();
                        if (errorMsg != null) {
                            // Check if message contains "HTTP 429" anywhere (not just at start)
                            if (errorMsg.contains("HTTP 429") || errorMsg.contains("HTTP 429:")) {
                                isRateLimit = true;
                            } else {
                                // Try to parse status code from "HTTP {statusCode}: ..." format if present
                                try {
                                    int httpIndex = errorMsg.indexOf("HTTP ");
                                    if (httpIndex >= 0) {
                                        String afterHttp = errorMsg.substring(httpIndex + 5).trim();
                                        int colonIndex = afterHttp.indexOf(':');
                                        if (colonIndex > 0) {
                                            int statusCode = Integer.parseInt(afterHttp.substring(0, colonIndex).trim());
                                            if (statusCode == 429) {
                                                isRateLimit = true;
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    // If parsing fails, not a rate limit error - continue normally
                                }
                            }
                        }
                    }
                    
                    // Parse retry-after time from error message if available (e.g., "Please retry in 46.912382516s")
                    Duration delay = Duration.ofSeconds(3); // Default delay for non-rate-limit errors
                    if (isRateLimit) {
                        String errorMsg = error.getMessage();
                        if (errorMsg != null) {
                            // Try to extract retry time from message like "Please retry in 46.912382516s"
                            try {
                                int retryIndex = errorMsg.indexOf("Please retry in");
                                if (retryIndex >= 0) {
                                    String afterRetry = errorMsg.substring(retryIndex + "Please retry in".length()).trim();
                                    // Find the number and 's' (seconds)
                                    StringBuilder secondsStr = new StringBuilder();
                                    for (int i = 0; i < afterRetry.length(); i++) {
                                        char c = afterRetry.charAt(i);
                                        if (Character.isDigit(c) || c == '.') {
                                            secondsStr.append(c);
                                        } else if (c == 's' || c == 'S') {
                                            break;
                                        } else if (secondsStr.length() > 0) {
                                            break; // End of number
                                        }
                                    }
                                    if (secondsStr.length() > 0) {
                                        double seconds = Double.parseDouble(secondsStr.toString());
                                        // Add 1 second buffer and round up to nearest second
                                        long delaySeconds = Math.max(5, (long) Math.ceil(seconds + 1));
                                        delay = Duration.ofSeconds(delaySeconds);
                                        log.warn("⚠️ Rate limit detected for model {} (HTTP 429). Parsed retry-after: {}s. Waiting {} seconds before trying next model...", 
                                                category, seconds, delaySeconds);
                                    }
                                }
                            } catch (Exception e) {
                                log.debug("Could not parse retry-after time from error message: {}", errorMsg);
                            }
                        }
                        
                        if (delay.getSeconds() == 3) {
                            // Fallback if parsing failed - use default 5 seconds
                            delay = Duration.ofSeconds(5);
                            log.warn("⚠️ Rate limit detected for model {} (HTTP 429). Waiting 5 seconds before trying next model...", category);
                        }
                    } else {
                        log.warn("Model {} failed with error: {}. Waiting 3 seconds before trying next available model...", category, error.getMessage());
                    }
                    
                    // Try next model in the list after delay
                    return Mono.delay(delay)
                            .then(tryRelationshipExtractionWithModels(entities, prompt, documentId, teamId, categories, startIndex + 1));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // No model found for this category, try next
                    log.debug("No model found for category {}. Trying next...", category);
                    return tryRelationshipExtractionWithModels(entities, prompt, documentId, teamId, categories, startIndex + 1);
                }));
    }
    
    /**
     * Execute relationship extraction with a specific model
     */
    private Mono<RelationshipExtractionResult> executeRelationshipExtractionWithModel(
            List<Map<String, Object>> entities, String prompt, org.lite.gateway.entity.LinqLlmModel llmModel,
            String documentId, String teamId) {
        
        // Build LinqRequest
        LinqRequest request = new LinqRequest();
        LinqRequest.Link link = new LinqRequest.Link();
        link.setTarget(llmModel.getModelCategory());
        link.setAction("generate");
        request.setLink(link);
        
        LinqRequest.Query query = new LinqRequest.Query();
        query.setIntent("generate");
        
        // Build messages array
        List<Map<String, Object>> messages = new ArrayList<>();
        Map<String, Object> systemMessage = new HashMap<>();
        systemMessage.put("role", "system");
        systemMessage.put("content", "You are an expert relationship extraction assistant. Analyze entities and identify relationships between them. Return a JSON array of relationships. Each relationship should have: fromType, fromId, toType, toId, type (relationship type), and optional properties. Return ONLY valid JSON, no explanations.");
        messages.add(systemMessage);
        
        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);
        
        query.setPayload(messages);
        
        LinqRequest.Query.LlmConfig llmConfig = new LinqRequest.Query.LlmConfig();
        llmConfig.setModel(llmModel.getModelName());
        Map<String, Object> settings = new HashMap<>();
        settings.put("temperature", 0.3);
        
        // Adjust max_tokens based on model category to respect model limits
        // Cohere models have a max output of 4096 tokens
        int maxTokensForModel = 2000;
        if ("cohere-chat".equals(llmModel.getModelCategory())) {
            maxTokensForModel = Math.min(2000, 4096); // Cohere max output limit
        }
        settings.put("max_tokens", maxTokensForModel);
        llmConfig.setSettings(settings);
        query.setLlmConfig(llmConfig);
        
        Map<String, Object> params = new HashMap<>();
        params.put("teamId", teamId);
        query.setParams(params);
        
        request.setQuery(query);
        
        // Execute LLM request
        return linqLlmModelService.executeLlmRequest(request, llmModel)
                .flatMap(response -> {
                    try {
                        // Extract and log token usage and cost
                        TokenUsageResult tokenUsage = extractTokenUsageFromResponse(
                                response, llmModel.getModelCategory(), llmModel.getModelName());
                        if (tokenUsage != null) {
                            log.info("📊 Relationship extraction token usage - prompt: {}, completion: {}, total: {}, cost: ${}",
                                    tokenUsage.promptTokens, tokenUsage.completionTokens,
                                    tokenUsage.totalTokens, String.format("%.6f", tokenUsage.costUsd));
                        } else {
                            log.warn("⚠️ Could not extract token usage from LLM response for relationship extraction");
                        }
                        
                        // Extract text from response
                        String jsonText = extractTextFromResponse(response, llmModel.getModelCategory());
                        log.debug("LLM response text: {}", jsonText);
                        
                        // Parse JSON array of relationships
                        List<Map<String, Object>> relationships = objectMapper.readValue(
                                jsonText, 
                                new TypeReference<List<Map<String, Object>>>() {});
                        
                        log.debug("Parsed {} relationships from LLM response", relationships.size());
                        
                        // Return both relationships and token usage with model information
                        return Mono.just(new RelationshipExtractionResult(
                                relationships, 
                                tokenUsage,
                                llmModel.getModelName(),
                                llmModel.getModelCategory(),
                                llmModel.getProvider()
                        ));
                    } catch (Exception e) {
                        log.error("Error parsing LLM response: {}", e.getMessage(), e);
                        return Mono.error(new RuntimeException("Failed to parse relationship extraction response", e));
                    }
                })
                .doOnError(error -> log.error("Error extracting relationships from entities: {}", error.getMessage(), error));
    }
    
    private String buildRelationshipExtractionPrompt(List<Map<String, Object>> entities) {
        StringBuilder entitiesJson = new StringBuilder("[\n");
        for (int i = 0; i < entities.size(); i++) {
            Map<String, Object> entity = entities.get(i);
            StringBuilder entityJson = new StringBuilder("  {\n");
            entityJson.append("    \"type\": \"").append(entity.get("type")).append("\",\n");
            entityJson.append("    \"id\": \"").append(entity.get("id")).append("\",\n");
            entityJson.append("    \"name\": \"").append(entity.getOrDefault("name", "")).append("\"");
            if (entity.containsKey("description")) {
                entityJson.append(",\n    \"description\": \"").append(entity.get("description")).append("\"");
            }
            entityJson.append("\n  }");
            if (i < entities.size() - 1) {
                entityJson.append(",");
            }
            entityJson.append("\n");
            entitiesJson.append(entityJson);
        }
        entitiesJson.append("]");
        
        return String.format(
                "Analyze the following entities and identify relationships between them.\n" +
                "\n" +
                "Focus on these relationship types:\n" +
                "- **MENTIONS**: Entity A mentions or references Entity B\n" +
                "- **REQUIRES**: Entity A requires Entity B (e.g., Form I-130 requires Form I-485)\n" +
                "- **RELATED_TO**: Generic relationship between related entities\n" +
                "- **PREVIOUS_TO**: Entity A comes before Entity B (e.g., chronological order)\n" +
                "- **LOCATED_IN**: Entity A is located in Entity B\n" +
                "- **PART_OF**: Entity A is part of Entity B\n" +
                "- **DEPENDS_ON**: Entity A depends on Entity B\n" +
                "\n" +
                "For each relationship, provide:\n" +
                "- **fromType**: Type of source entity\n" +
                "- **fromId**: ID of source entity\n" +
                "- **toType**: Type of target entity\n" +
                "- **toId**: ID of target entity\n" +
                "- **type**: Relationship type (one of the above)\n" +
                "- **properties**: Optional properties (e.g., \"strength\", \"context\")\n" +
                "\n" +
                "Return a JSON array of relationships. Example format:\n" +
                "[\n" +
                "  {\"fromType\": \"Form\", \"fromId\": \"I-130\", \"toType\": \"Form\", \"toId\": \"I-485\", \"type\": \"REQUIRES\", \"properties\": {\"context\": \"marriage-based green card\"}},\n" +
                "  {\"fromType\": \"Organization\", \"fromId\": \"USCIS\", \"toType\": \"Form\", \"toId\": \"I-130\", \"type\": \"MENTIONS\"}\n" +
                "]\n" +
                "\n" +
                "Entities to analyze:\n" +
                "%s",
                entitiesJson.toString());
    }
    
    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(LinqResponse response, String modelCategory) {
        // Extract text from the response based on model category
        Object result = response.getResult();
        
        if (result == null) {
            throw new RuntimeException("No result in LLM response");
        }
        
        try {
            // Check for error responses first
            if (result instanceof Map) {
                Map<String, Object> resultMap = (Map<String, Object>) result;
                
                // Check if this is an error response
                if (resultMap.containsKey("error")) {
                    Object errorObj = resultMap.get("error");
                    String errorMessage = "LLM API returned an error";
                    if (errorObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                        if (errorMap.containsKey("message")) {
                            errorMessage = (String) errorMap.get("message");
                        } else if (errorMap.containsKey("type")) {
                            errorMessage = (String) errorMap.get("type");
                        }
                    } else if (errorObj instanceof String) {
                        errorMessage = (String) errorObj;
                    }
                    log.error("LLM API error response: {}", errorMessage);
                    throw new RuntimeException("LLM API error: " + errorMessage);
                }
                
                // OpenAI/Chat format: result is a Map with "choices" array
                // OpenAI format: { "choices": [ { "message": { "content": "..." } } ] }
                if (resultMap.containsKey("choices")) {
                    List<Map<String, Object>> choices = (List<Map<String, Object>>) resultMap.get("choices");
                    if (!choices.isEmpty()) {
                        Map<String, Object> firstChoice = choices.get(0);
                        if (firstChoice.containsKey("message")) {
                            Map<String, Object> message = (Map<String, Object>) firstChoice.get("message");
                            String content = (String) message.get("content");
                            if (content != null) {
                                return cleanJsonText(content);
                            }
                        }
                    }
                }
                
                // Claude format: { "content": [ { "text": "..." } ] } or { "content": "..." } (direct string)
                // Also handles cases where result itself is a message object with content field
                if (resultMap.containsKey("content")) {
                    Object contentObj = resultMap.get("content");
                    if (contentObj == null) {
                        log.warn("Content field exists but is null in response with keys: {}", resultMap.keySet());
                    } else if (contentObj instanceof String) {
                        // Content is a direct string
                        String contentStr = (String) contentObj;
                        if (!contentStr.trim().isEmpty()) {
                            return cleanJsonText(contentStr);
                        }
                    } else if (contentObj instanceof List) {
                        // Content is an array of objects with text field
                        @SuppressWarnings("unchecked")
                        List<?> contentList = (List<?>) contentObj;
                        if (!contentList.isEmpty()) {
                            Object firstItem = contentList.get(0);
                            if (firstItem instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> firstContent = (Map<String, Object>) firstItem;
                                if (firstContent.containsKey("text")) {
                                    String text = (String) firstContent.get("text");
                                    if (text != null && !text.trim().isEmpty()) {
                                        return cleanJsonText(text);
                                    }
                                }
                            } else if (firstItem instanceof String) {
                                // Content is a list of strings
                                String text = (String) firstItem;
                                if (!text.trim().isEmpty()) {
                                    return cleanJsonText(text);
                                }
                            }
                        }
                    } else {
                        log.debug("Content field exists but has unexpected type: {} in response", contentObj.getClass().getName());
                    }
                }
                
                // Gemini format: { "candidates": [ { "content": { "parts": [ { "text": "..." } ] } } ] }
                if (resultMap.containsKey("candidates")) {
                    Object candidatesObj = resultMap.get("candidates");
                    if (candidatesObj instanceof List) {
                        List<Map<String, Object>> candidates = (List<Map<String, Object>>) candidatesObj;
                        if (!candidates.isEmpty()) {
                            Map<String, Object> candidate = candidates.get(0);
                            if (candidate != null && candidate.containsKey("content")) {
                                Object contentObj = candidate.get("content");
                                if (contentObj instanceof Map) {
                                    Map<String, Object> content = (Map<String, Object>) contentObj;
                                    if (content.containsKey("parts")) {
                                        Object partsObj = content.get("parts");
                                        if (partsObj instanceof List) {
                                            List<Map<String, Object>> parts = (List<Map<String, Object>>) partsObj;
                                            if (!parts.isEmpty() && parts.get(0) != null && parts.get(0).containsKey("text")) {
                                                String text = (String) parts.get(0).get("text");
                                                if (text != null) {
                                                    return cleanJsonText(text);
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Fallback: try to find "text" or "content" directly
                if (resultMap.containsKey("text")) {
                    return cleanJsonText((String) resultMap.get("text"));
                }
                if (resultMap.containsKey("content")) {
                    Object content = resultMap.get("content");
                    if (content instanceof String) {
                        return cleanJsonText((String) content);
                    }
                }
            }
            
            // If result is already a string
            if (result instanceof String) {
                return cleanJsonText((String) result);
            }
            
            // If result is a simple map, try to extract text or content
            if (result instanceof Map) {
                Map<String, Object> simpleMap = (Map<String, Object>) result;
                if (!simpleMap.isEmpty()) {
                    // Try common fields for text content
                    if (simpleMap.containsKey("text")) {
                        Object textObj = simpleMap.get("text");
                        if (textObj instanceof String) {
                            return cleanJsonText((String) textObj);
                        }
                    }
                    // Try content field (could be String or List) - Claude/Anthropic format
                    if (simpleMap.containsKey("content")) {
                        Object contentObj = simpleMap.get("content");
                        if (contentObj instanceof String) {
                            return cleanJsonText((String) contentObj);
                        } else if (contentObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<?> contentList = (List<?>) contentObj;
                            if (!contentList.isEmpty() && contentList.get(0) instanceof Map) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> firstContent = (Map<String, Object>) contentList.get(0);
                                if (firstContent.containsKey("text")) {
                                    String text = (String) firstContent.get("text");
                                    if (text != null) {
                                        return cleanJsonText(text);
                                    }
                                }
                            }
                        }
                    }
                    // Log the structure for debugging
                    log.warn("Unexpected response structure: {} with keys: {}", result.getClass().getName(), simpleMap.keySet());
                }
            }
            
            throw new RuntimeException("Unsupported response format: " + result.getClass().getName());
        } catch (Exception e) {
            log.error("Error extracting text from response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract text from LLM response", e);
        }
    }
    
    private String cleanJsonText(String text) {
        text = text.trim();
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }
        return text.trim();
    }
    
    private <T> List<List<T>> partitionList(List<T> list, int partitionSize) {
        List<List<T>> partitions = new ArrayList<>();
        for (int i = 0; i < list.size(); i += partitionSize) {
            partitions.add(list.subList(i, Math.min(i + partitionSize, list.size())));
        }
        return partitions;
    }
    
    /**
     * Extract token usage from raw LLM response and calculate cost (similar to ChatExecutionServiceImpl)
     */
    @SuppressWarnings("unchecked")
    private TokenUsageResult extractTokenUsageFromResponse(
            LinqResponse response, String modelCategory, String modelName) {
        
        if (response.getResult() == null) {
            return null;
        }
        
        Object result = response.getResult();
        if (!(result instanceof Map)) {
            return null;
        }
        
        Map<String, Object> resultMap = (Map<String, Object>) result;
        boolean hasTokenUsage = false;
        long promptTokens = 0;
        long completionTokens = 0;
        long totalTokens = 0;
        String model = modelName;
        
        // Extract token usage based on model category (same logic as ChatExecutionServiceImpl)
        if ("openai-chat".equals(modelCategory) && resultMap.containsKey("usage")) {
            Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
            if (usage != null) {
                promptTokens = usage.containsKey("prompt_tokens") 
                        ? ((Number) usage.get("prompt_tokens")).longValue() : 0;
                completionTokens = usage.containsKey("completion_tokens")
                        ? ((Number) usage.get("completion_tokens")).longValue() : 0;
                totalTokens = usage.containsKey("total_tokens")
                        ? ((Number) usage.get("total_tokens")).longValue() : (promptTokens + completionTokens);
                
                model = resultMap.containsKey("model") 
                        ? (String) resultMap.get("model") : modelName;
                hasTokenUsage = true;
            }
        } else if ("gemini-chat".equals(modelCategory) && resultMap.containsKey("usageMetadata")) {
            Map<?, ?> usageMetadata = (Map<?, ?>) resultMap.get("usageMetadata");
            if (usageMetadata != null) {
                promptTokens = usageMetadata.containsKey("promptTokenCount")
                        ? ((Number) usageMetadata.get("promptTokenCount")).longValue() : 0;
                completionTokens = usageMetadata.containsKey("candidatesTokenCount")
                        ? ((Number) usageMetadata.get("candidatesTokenCount")).longValue() : 0;
                totalTokens = usageMetadata.containsKey("totalTokenCount")
                        ? ((Number) usageMetadata.get("totalTokenCount")).longValue() : (promptTokens + completionTokens);
                
                model = resultMap.containsKey("modelVersion")
                        ? (String) resultMap.get("modelVersion") : modelName;
                hasTokenUsage = true;
            }
        } else if ("claude-chat".equals(modelCategory) && resultMap.containsKey("usage")) {
            Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
            if (usage != null) {
                promptTokens = usage.containsKey("input_tokens")
                        ? ((Number) usage.get("input_tokens")).longValue() : 0;
                completionTokens = usage.containsKey("output_tokens")
                        ? ((Number) usage.get("output_tokens")).longValue() : 0;
                totalTokens = promptTokens + completionTokens;
                
                model = resultMap.containsKey("model")
                        ? (String) resultMap.get("model") : modelName;
                hasTokenUsage = true;
            }
        } else if ("cohere-chat".equals(modelCategory) && resultMap.containsKey("meta")) {
            Map<?, ?> meta = (Map<?, ?>) resultMap.get("meta");
            if (meta != null && meta.containsKey("billed_units")) {
                Map<?, ?> billedUnits = (Map<?, ?>) meta.get("billed_units");
                if (billedUnits != null) {
                    promptTokens = billedUnits.containsKey("input_tokens")
                            ? ((Number) billedUnits.get("input_tokens")).longValue() : 0;
                    completionTokens = billedUnits.containsKey("output_tokens")
                            ? ((Number) billedUnits.get("output_tokens")).longValue() : 0;
                    totalTokens = promptTokens + completionTokens;
                    
                    model = resultMap.containsKey("model")
                            ? (String) resultMap.get("model") : modelName;
                    hasTokenUsage = true;
                }
            }
        }
        
        if (hasTokenUsage) {
            // Calculate cost
            double cost = llmCostService.calculateCost(model, promptTokens, completionTokens);
            return new TokenUsageResult(promptTokens, completionTokens, totalTokens, cost);
        }
        
        return null;
    }
    
    /**
     * Save relationship extraction costs and model information to document metadata
     */
    private Mono<Void> saveRelationshipExtractionCosts(String documentId, String teamId,
                                                        long promptTokens, long completionTokens, 
                                                        long totalTokens, double costUsd,
                                                        String modelName, String modelCategory, String provider,
                                                        long startedAt, long completedAt) {
        return metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                .flatMap(metadata -> {
                    // Initialize customMetadata if null
                    if (metadata.getCustomMetadata() == null) {
                        metadata.setCustomMetadata(new HashMap<>());
                    }
                    
                    // Get or create graphExtraction map
                    @SuppressWarnings("unchecked")
                    Map<String, Object> graphExtraction = metadata.getCustomMetadata().containsKey("graphExtraction")
                            ? (Map<String, Object>) metadata.getCustomMetadata().get("graphExtraction")
                            : new HashMap<>();
                    
                    // Build relationship extraction metadata with model info and timing
                    Map<String, Object> relationshipExtraction = new HashMap<>();
                    relationshipExtraction.put("promptTokens", promptTokens);
                    relationshipExtraction.put("completionTokens", completionTokens);
                    relationshipExtraction.put("totalTokens", totalTokens);
                    relationshipExtraction.put("costUsd", costUsd);
                    relationshipExtraction.put("startedAt", startedAt);
                    relationshipExtraction.put("completedAt", completedAt);
                    relationshipExtraction.put("extractedAt", completedAt); // Keep for backwards compatibility
                    relationshipExtraction.put("durationMs", completedAt - startedAt);
                    if (modelName != null) {
                        relationshipExtraction.put("modelName", modelName);
                    }
                    if (modelCategory != null) {
                        relationshipExtraction.put("modelCategory", modelCategory);
                    }
                    if (provider != null) {
                        relationshipExtraction.put("provider", provider);
                    }
                    
                    // Store relationship extraction costs
                    graphExtraction.put("relationshipExtraction", relationshipExtraction);
                    
                    metadata.getCustomMetadata().put("graphExtraction", graphExtraction);
                    
                    return metadataRepository.save(metadata)
                            .doOnSuccess(m -> log.info("💾 Saved relationship extraction costs to metadata - prompt: {}, completion: {}, total: {}, cost: ${}",
                                    promptTokens, completionTokens, totalTokens, String.format("%.6f", costUsd)))
                            .then();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("⚠️ No metadata found for document: {}, cannot save relationship extraction costs", documentId);
                    return Mono.empty();
                }))
                .onErrorResume(error -> {
                    log.error("Error saving relationship extraction costs to metadata: {}", error.getMessage());
                    return Mono.empty(); // Don't fail the extraction if cost saving fails
                });
    }
    
    /**
     * Internal class to hold extraction result with token usage
     */
    private static class RelationshipExtractionResult {
        final List<Map<String, Object>> relationships;
        final TokenUsageResult tokenUsage;
        final String modelName;
        final String modelCategory;
        final String provider;
        
        RelationshipExtractionResult(List<Map<String, Object>> relationships, TokenUsageResult tokenUsage,
                                    String modelName, String modelCategory, String provider) {
            this.relationships = relationships;
            this.tokenUsage = tokenUsage;
            this.modelName = modelName;
            this.modelCategory = modelCategory;
            this.provider = provider;
        }
    }
    
    /**
     * Internal class to hold token usage and cost
     */
    private static class TokenUsageResult {
        final long promptTokens;
        final long completionTokens;
        final long totalTokens;
        final double costUsd;
        
        TokenUsageResult(long promptTokens, long completionTokens, long totalTokens, double costUsd) {
            this.promptTokens = promptTokens;
            this.completionTokens = completionTokens;
            this.totalTokens = totalTokens;
            this.costUsd = costUsd;
        }
    }
}

