package org.lite.gateway.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.dto.LinqResponse;
import org.lite.gateway.entity.KnowledgeHubChunk;
import org.lite.gateway.repository.KnowledgeHubChunkRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentMetaDataRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.repository.LinqLlmModelRepository;
import org.lite.gateway.service.GraphExtractionJobService;
import org.lite.gateway.service.KnowledgeHubGraphEntityExtractionService;
import org.lite.gateway.service.LinqLlmModelService;
import org.lite.gateway.service.LlmCostService;
import org.lite.gateway.service.Neo4jGraphService;
import org.lite.gateway.service.ChunkEncryptionService;
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
import java.util.stream.Collectors;
import java.util.function.Function;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeHubGraphEntityExtractionServiceImpl implements KnowledgeHubGraphEntityExtractionService {

    private final KnowledgeHubDocumentRepository documentRepository;
    private final KnowledgeHubChunkRepository chunkRepository;
    private final KnowledgeHubDocumentMetaDataRepository metadataRepository;
    private final LinqLlmModelRepository linqLlmModelRepository;
    private final LinqLlmModelService linqLlmModelService;
    private final LlmCostService llmCostService;
    private final Neo4jGraphService graphService;
    private final ChunkEncryptionService chunkEncryptionService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    // Optional: for progress reporting during extraction jobs
    // Use @Lazy to break circular dependency with GraphExtractionJobServiceImpl
    @Autowired(required = false)
    @Lazy
    private GraphExtractionJobService graphExtractionJobService;

    // Chat model categories for entity extraction
    // Ordered by cost priority: cheapest first (Gemini, Cohere) to most expensive
    private static final List<String> CHAT_MODEL_CATEGORIES = List.of(
            "gemini-chat", "cohere-chat", "ollama-chat", "openai-chat", "claude-chat");
    // Batches should be limited by token count, not just chunk count
    private static final int MAX_BATCH_TOKENS_ESTIMATE = 80000; // Safe limit (well below 200k context)
    private static final int MAX_CHUNKS_PER_BATCH = 5; // Secondary limit

    // ... existing constants ...

    private List<List<KnowledgeHubChunk>> partitionChunksBySize(List<KnowledgeHubChunk> chunks) {
        List<List<KnowledgeHubChunk>> partitions = new ArrayList<>();
        List<KnowledgeHubChunk> currentBatch = new ArrayList<>();
        long currentBatchTokens = 0;

        for (KnowledgeHubChunk chunk : chunks) {
            String text = chunk.getText();
            if (text == null)
                continue;

            // Estimate tokens:
            // 1. If encrypted (Base64), real size is ~0.75 * length.
            // 2. 1 token approx 4 chars.
            // So tokens ~ length * 0.75 / 4 = length * 0.1875
            // We use length / 4 as a conservative estimate assuming raw text (safer)
            long estimatedTokens = text.length() / 4;

            // If adding this chunk exceeds the limit AND we already have chunks in the
            // batch,
            // close the current batch and start a new one.
            if (!currentBatch.isEmpty() && currentBatchTokens + estimatedTokens > MAX_BATCH_TOKENS_ESTIMATE) {
                log.debug("Batch full ({} tokens), starting new batch", currentBatchTokens);
                partitions.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
                currentBatchTokens = 0;
            }

            currentBatch.add(chunk);
            currentBatchTokens += estimatedTokens;

            // Secondary check: if we hit max chunks count
            if (currentBatch.size() >= MAX_CHUNKS_PER_BATCH) {
                partitions.add(new ArrayList<>(currentBatch));
                currentBatch.clear();
                currentBatchTokens = 0;
            }
        }

        if (!currentBatch.isEmpty()) {
            partitions.add(currentBatch);
        }

        return partitions;
    }

    // Cost optimization settings
    private static final double TEMPERATURE = 0.3; // Lower temperature for consistent extraction (already optimal)
    private static final int MAX_TOKENS = 8000; // Increased from 2000 to handle large entity extraction tasks with rich
                                                // properties

    // Estimate average tokens per request for cost comparison (1000 prompt + 500
    // completion = 1500 total)
    private static final long ESTIMATED_PROMPT_TOKENS = 1000;
    private static final long ESTIMATED_COMPLETION_TOKENS = 500;

    @Override
    public Mono<Integer> extractEntitiesFromDocument(String documentId, String teamId) {
        return extractEntitiesFromDocument(documentId, teamId, false);
    }

    public Mono<Integer> extractEntitiesFromDocument(String documentId, String teamId, boolean force) {
        log.info("Starting entity extraction for document: {}, team: {}, force: {}", documentId, teamId, force);

        // Record start time for duration tracking
        final long startedAt = System.currentTimeMillis();

        // Check if entities were already extracted (unless force=true)
        Mono<Boolean> shouldProceed = force
                ? Mono.just(true)
                : metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                        .flatMap(metadata -> {
                            if (metadata.getCustomMetadata() != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata()
                                        .get("graphExtraction");
                                if (graphExtraction != null) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> entityExtraction = (Map<String, Object>) graphExtraction
                                            .get("entityExtraction");
                                    if (entityExtraction != null) {
                                        // Entities already extracted - check if any exist in Neo4j
                                        return graphService
                                                .findEntities("Form", Map.of("documentId", documentId), teamId)
                                                .mergeWith(graphService.findEntities("Organization",
                                                        Map.of("documentId", documentId), teamId))
                                                .mergeWith(graphService.findEntities("Person",
                                                        Map.of("documentId", documentId), teamId))
                                                .mergeWith(graphService.findEntities("Date",
                                                        Map.of("documentId", documentId), teamId))
                                                .mergeWith(graphService.findEntities("Location",
                                                        Map.of("documentId", documentId), teamId))
                                                .mergeWith(graphService.findEntities("Document",
                                                        Map.of("documentId", documentId), teamId))
                                                .hasElements()
                                                .map(hasEntities -> !hasEntities); // Proceed only if no entities exist
                                    }
                                }
                            }
                            return Mono.just(true); // No previous extraction, proceed
                        })
                        .defaultIfEmpty(true);

        return shouldProceed
                .flatMap(proceed -> {
                    if (!proceed) {
                        log.warn("Entities already extracted for document: {}. Use force=true to re-extract.",
                                documentId);
                        return getPreviousExtractionCost(documentId, teamId)
                                .flatMap(previousCost -> Mono.<Integer>error(new RuntimeException(
                                        String.format(
                                                "Entities already extracted for this document. Previous extraction cost: $%.6f. Use force=true parameter to re-extract (will incur additional costs).",
                                                previousCost))));
                    }

                    return documentRepository.findByDocumentId(documentId)
                            .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                            .filter(document -> document.getTeamId().equals(teamId))
                            .switchIfEmpty(Mono.error(new RuntimeException("Document access denied")))
                            .flatMap(document -> {
                                // Load all chunks for the document
                                return chunkRepository.findByDocumentIdAndTeamId(documentId, teamId)
                                        .collectList()
                                        .flatMap(chunks -> {
                                            if (chunks.isEmpty()) {
                                                log.warn("No chunks found for document: {}", documentId);
                                                return Mono.just(0);
                                            }

                                            log.info("Found {} chunks for document: {}", chunks.size(), documentId);

                                            // Process chunks in dynamic batches based on estimated token size
                                            // This prevents "prompt is too long" errors for large documents while
                                            // maintaining
                                            // efficiency for smaller chunks
                                            List<List<KnowledgeHubChunk>> batches = partitionChunksBySize(chunks);
                                            log.info("Processing {} batches of chunks", batches.size());

                                            // Track total token usage and cost across all batches
                                            AtomicLong totalPromptTokens = new AtomicLong(0);
                                            AtomicLong totalCompletionTokens = new AtomicLong(0);
                                            AtomicLong totalTokens = new AtomicLong(0);
                                            AtomicReference<Double> totalCost = new AtomicReference<>(0.0);
                                            AtomicInteger totalEntitiesExtracted = new AtomicInteger(0);

                                            // Track model information (use first model selected, or most used model)
                                            AtomicReference<String> modelName = new AtomicReference<>(null);
                                            AtomicReference<String> modelCategory = new AtomicReference<>(null);
                                            AtomicReference<String> provider = new AtomicReference<>(null);

                                            // Extract jobId from thread-local or context if available
                                            // For now, we'll look it up from documentId if graphExtractionJobService is
                                            // available
                                            Mono<String> jobIdMono = graphExtractionJobService != null
                                                    ? graphExtractionJobService.getJobsForDocument(documentId, teamId)
                                                            .filter(job -> "QUEUED".equals(job.getStatus())
                                                                    || "RUNNING".equals(job.getStatus()))
                                                            .filter(job -> "entities".equals(job.getExtractionType())
                                                                    || "all".equals(job.getExtractionType()))
                                                            .next()
                                                            .map(job -> job.getJobId())
                                                            .defaultIfEmpty("")
                                                    : Mono.just("");

                                            return jobIdMono
                                                    .flatMap(jobId -> Flux.fromIterable(batches)
                                                            .index()
                                                            .concatMap(tuple -> {
                                                                long batchIndex = tuple.getT1();
                                                                List<KnowledgeHubChunk> batch = tuple.getT2();

                                                                // Combine batch chunks into a single text (decrypting
                                                                // if necessary)
                                                                return Flux.fromIterable(batch)
                                                                        .flatMapSequential(chunk -> {
                                                                            if (chunk.getText() == null) {
                                                                                return Mono.empty();
                                                                            }
                                                                            if (chunk.getEncryptionKeyVersion() != null
                                                                                    && !chunk.getEncryptionKeyVersion()
                                                                                            .isEmpty()) {
                                                                                log.info(
                                                                                        "Decrypting chunk {} with key version: {}",
                                                                                        chunk.getId(),
                                                                                        chunk.getEncryptionKeyVersion());
                                                                                return chunkEncryptionService
                                                                                        .decryptChunkText(
                                                                                                chunk.getText(),
                                                                                                chunk.getTeamId(),
                                                                                                chunk.getEncryptionKeyVersion())
                                                                                        .onErrorResume(e -> {
                                                                                            log.error(
                                                                                                    "Failed to decrypt chunk {} (version: {}): {}",
                                                                                                    chunk.getId(),
                                                                                                    chunk.getEncryptionKeyVersion(),
                                                                                                    e.getMessage());
                                                                                            return Mono.error(e);
                                                                                        });
                                                                            }
                                                                            return Mono.just(chunk.getText());
                                                                        })
                                                                        .collect(Collectors.joining("\n\n---\n\n"))
                                                                        .flatMap(batchText -> {
                                                                            log.info(
                                                                                    "Processing batch {}/{} with {} chunks ({} chars)",
                                                                                    batchIndex + 1, batches.size(),
                                                                                    batch.size(),
                                                                                    batchText.length());

                                                                            // Add delay between batches to respect rate
                                                                            // limits
                                                                            return batchIndex > 0
                                                                                    ? Mono.delay(Duration.ofSeconds(10))
                                                                                            .then(extractEntitiesFromTextWithCostTracking(
                                                                                                    batchText,
                                                                                                    documentId, teamId))
                                                                                    : extractEntitiesFromTextWithCostTracking(
                                                                                            batchText, documentId,
                                                                                            teamId);
                                                                        })
                                                                        .flatMap(result -> {
                                                                            // Accumulate token usage and cost from this
                                                                            // batch
                                                                            if (result.tokenUsage != null) {
                                                                                totalPromptTokens.addAndGet(
                                                                                        result.tokenUsage.promptTokens);
                                                                                totalCompletionTokens.addAndGet(
                                                                                        result.tokenUsage.completionTokens);
                                                                                totalTokens.addAndGet(
                                                                                        result.tokenUsage.totalTokens);
                                                                                totalCost.updateAndGet(cost -> cost
                                                                                        + result.tokenUsage.costUsd);
                                                                            }

                                                                            // Track model information (use first model
                                                                            // encountered)
                                                                            if (result.modelName != null
                                                                                    && modelName.get() == null) {
                                                                                modelName.set(result.modelName);
                                                                                modelCategory.set(result.modelCategory);
                                                                                provider.set(result.provider);
                                                                            }

                                                                            log.info(
                                                                                    "Extracted {} entities from batch {}/{}",
                                                                                    result.entities.size(),
                                                                                    batchIndex + 1, batches.size());

                                                                            // Store entities in Neo4j
                                                                            return Flux.fromIterable(result.entities)
                                                                                    .flatMap(entity -> {
                                                                                        String entityType = (String) entity
                                                                                                .get("type");
                                                                                        String entityId = generateEntityId(
                                                                                                entityType, entity);
                                                                                        Map<String, Object> properties = new HashMap<>(
                                                                                                entity);
                                                                                        properties.remove("type"); // Remove
                                                                                                                   // type
                                                                                                                   // from
                                                                                                                   // properties
                                                                                                                   // (it's
                                                                                                                   // a
                                                                                                                   // label)
                                                                                        properties.put("documentId",
                                                                                                documentId);
                                                                                        properties.put("extractedAt",
                                                                                                System.currentTimeMillis());

                                                                                        // Log all entity types being
                                                                                        // extracted
                                                                                        log.debug(
                                                                                                "🔍 Extracting {} entity: {} with properties: {}",
                                                                                                entityType, entityId,
                                                                                                properties.keySet());

                                                                                        return graphService
                                                                                                .upsertEntity(
                                                                                                        entityType,
                                                                                                        entityId,
                                                                                                        properties,
                                                                                                        teamId)
                                                                                                .doOnSuccess(id -> {
                                                                                                    log.info(
                                                                                                            "✅ Successfully upserted {} entity {}:{} from document {}",
                                                                                                            entityType,
                                                                                                            entityType,
                                                                                                            id,
                                                                                                            documentId);
                                                                                                })
                                                                                                .onErrorContinue((error,
                                                                                                        obj) -> log
                                                                                                                .error("Error upserting entity {}:{}: {}",
                                                                                                                        entityType,
                                                                                                                        entityId,
                                                                                                                        error.getMessage()));
                                                                                    })
                                                                                    .then(Mono.just(
                                                                                            result.entities.size()));
                                                                        })
                                                                        .doOnNext(batchEntityCount -> {
                                                                            // Update total entities extracted
                                                                            totalEntitiesExtracted
                                                                                    .addAndGet(batchEntityCount);

                                                                            // Report progress after storing entities
                                                                            if (graphExtractionJobService != null
                                                                                    && !jobId.isEmpty()) {
                                                                                graphExtractionJobService
                                                                                        .updateJobProgress(
                                                                                                jobId,
                                                                                                (int) (batchIndex + 1),
                                                                                                batches.size(),
                                                                                                totalEntitiesExtracted
                                                                                                        .get(),
                                                                                                null,
                                                                                                totalCost.get())
                                                                                        .subscribe(
                                                                                                null,
                                                                                                error -> log.warn(
                                                                                                        "Failed to update job progress: {}",
                                                                                                        error.getMessage()));
                                                                            }
                                                                        });
                                                            })
                                                            .collectList()
                                                            .map(sizes -> sizes.stream().mapToInt(Integer::intValue)
                                                                    .sum())
                                                            .flatMap(entityCount -> {
                                                                // Save token usage, cost, model info, and timing to
                                                                // document metadata
                                                                final long completedAt = System.currentTimeMillis();
                                                                return saveEntityExtractionCosts(documentId, teamId,
                                                                        totalPromptTokens.get(),
                                                                        totalCompletionTokens.get(),
                                                                        totalTokens.get(), totalCost.get(),
                                                                        modelName.get(), modelCategory.get(),
                                                                        provider.get(),
                                                                        startedAt, completedAt)
                                                                        .thenReturn(entityCount);
                                                            })
                                                            .doOnSuccess(total -> log.info(
                                                                    "Extracted {} total entities from document: {}",
                                                                    total, documentId)));
                                        });
                            });
                })
                .doOnError(error -> log.error("Error extracting entities from document {}: {}", documentId,
                        error.getMessage(), error));
    }

    /**
     * Get previous extraction cost for a document (for warning messages)
     */
    private Mono<Double> getPreviousExtractionCost(String documentId, String teamId) {
        return metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                .map(metadata -> {
                    if (metadata.getCustomMetadata() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata()
                                .get("graphExtraction");
                        if (graphExtraction != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> entityExtraction = (Map<String, Object>) graphExtraction
                                    .get("entityExtraction");
                            if (entityExtraction != null && entityExtraction.containsKey("costUsd")) {
                                Object costObj = entityExtraction.get("costUsd");
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
    public Mono<List<Map<String, Object>>> extractEntitiesFromText(String text, String documentId, String teamId) {
        log.debug("Extracting entities from text ({} chars) for document: {}", text.length(), documentId);

        // Find cheapest available model for entity extraction
        return linqLlmModelService
                .findCheapestAvailableModel(CHAT_MODEL_CATEGORIES, teamId, ESTIMATED_PROMPT_TOKENS,
                        ESTIMATED_COMPLETION_TOKENS)
                .flatMap(llmModel -> {
                    // Log which model is being used for cost tracking
                    log.info("🔍 Entity extraction using model: {} / {} (category: {}, provider: {})",
                            llmModel.getModelName(),
                            llmModel.getModelCategory(),
                            llmModel.getProvider());

                    // Build extraction prompt
                    String prompt = buildEntityExtractionPrompt(text);

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
                    systemMessage.put("content",
                            "You are an expert entity extraction assistant. Extract entities from the provided text and return a JSON array of entities. Each entity should have: type (e.g., Form, Person, Organization, Date, Location), id (unique identifier), name (display name), and any other relevant properties. Return ONLY valid JSON, no explanations.");
                    messages.add(systemMessage);

                    Map<String, Object> userMessage = new HashMap<>();
                    userMessage.put("role", "user");
                    userMessage.put("content", prompt);
                    messages.add(userMessage);

                    query.setPayload(messages);

                    LinqRequest.Query.LlmConfig llmConfig = new LinqRequest.Query.LlmConfig();
                    llmConfig.setModel(llmModel.getModelName());
                    Map<String, Object> settings = new HashMap<>();
                    settings.put("temperature", TEMPERATURE); // Lower temperature for consistent extraction

                    // Adjust max_tokens based on model category to respect model limits
                    // Cohere models have a max output of 4096 tokens
                    int maxTokensForModel = MAX_TOKENS;
                    if ("cohere-chat".equals(llmModel.getModelCategory())) {
                        maxTokensForModel = Math.min(MAX_TOKENS, 4096); // Cohere max output limit
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
                            .retryWhen(reactor.util.retry.Retry.backoff(3, Duration.ofSeconds(5))
                                    .filter(throwable -> {
                                        // Retry on 429 (Rate Limit) or 5xx (Server Error)
                                        String msg = throwable.getMessage();
                                        return msg != null && (msg.contains("429") || msg.contains("rate limit") ||
                                                msg.contains("500") || msg.contains("502") || msg.contains("503"));
                                    })
                                    .doBeforeRetry(retrySignal -> log.warn(
                                            "⚠️ Retrying entity extraction due to error: {} (attempt {}/3)",
                                            retrySignal.failure().getMessage(), retrySignal.totalRetries() + 1)))
                            .flatMap(response -> {
                                try {
                                    // Extract and log token usage and cost
                                    TokenUsageResult tokenUsage = extractTokenUsageFromResponse(
                                            response, llmModel.getModelCategory(), llmModel.getModelName());
                                    if (tokenUsage != null) {
                                        log.info(
                                                "📊 Entity extraction token usage - prompt: {}, completion: {}, total: {}, cost: ${}",
                                                tokenUsage.promptTokens, tokenUsage.completionTokens,
                                                tokenUsage.totalTokens, String.format("%.6f", tokenUsage.costUsd));
                                    } else {
                                        log.warn(
                                                "⚠️ Could not extract token usage from LLM response for entity extraction");
                                    }

                                    // Extract text from response (OpenAI format: result -> choices[0] -> message ->
                                    // content)
                                    String jsonText = extractTextFromResponse(response, llmModel.getModelCategory());
                                    log.debug("LLM response text: {}", jsonText);

                                    // Parse JSON array of entities
                                    List<Map<String, Object>> entities = objectMapper.readValue(
                                            jsonText,
                                            new TypeReference<List<Map<String, Object>>>() {
                                            });

                                    log.debug("Parsed {} entities from LLM response", entities.size());

                                    // Log entity type counts for debugging
                                    Map<String, Long> entityTypeCounts = entities.stream()
                                            .map(e -> (String) e.get("type"))
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.groupingBy(
                                                    Function.identity(),
                                                    Collectors.counting()));
                                    if (!entityTypeCounts.isEmpty()) {
                                        log.info("📋 Extracted entity type counts: {}", entityTypeCounts);
                                    }
                                    if (entityTypeCounts.containsKey("Document")) {
                                        log.info("✅ Found {} Document entities in LLM response",
                                                entityTypeCounts.get("Document"));
                                    } else {
                                        log.debug("ℹ️ No Document entities found in LLM response (extracted types: {})",
                                                entityTypeCounts.keySet());
                                    }

                                    // Store token usage in a thread-local or pass via context for tracking
                                    // For now, just return entities (costs are tracked separately)
                                    return Mono.just(entities);
                                } catch (Exception e) {
                                    // Check if response was truncated due to max_tokens
                                    String jsonText = null;
                                    try {
                                        jsonText = extractTextFromResponse(response, llmModel.getModelCategory());
                                    } catch (Exception ignored) {
                                        // If we can't extract text, continue with generic error
                                    }

                                    // Check for truncation indicators
                                    boolean isTruncated = false;
                                    String errorMsg = "Failed to parse entity extraction response";

                                    if (jsonText != null) {
                                        String trimmed = jsonText.trim();
                                        if (!trimmed.endsWith("]") && trimmed.contains("[")) {
                                            isTruncated = true;
                                            errorMsg = "Response was truncated (max_tokens limit reached). The input text may be too large - consider processing in smaller batches.";
                                        } else if (e.getMessage() != null && (e.getMessage().contains("end-of-input") ||
                                                e.getMessage().contains("Unexpected EOF") ||
                                                e.getMessage().contains("JsonEOFException"))) {
                                            isTruncated = true;
                                            errorMsg = "Response was truncated (max_tokens limit reached). The input text may be too large - consider processing in smaller batches.";
                                        }
                                    }

                                    if (isTruncated) {
                                        log.error(
                                                "⚠️ Entity extraction response was truncated. Input was too large for max_tokens={}. Consider reducing batch size.",
                                                MAX_TOKENS);
                                        log.error("Truncated response preview (first 500 chars): {}",
                                                jsonText != null && jsonText.length() > 500 ? jsonText.substring(0, 500)
                                                        : jsonText);
                                    } else {
                                        log.error("Error parsing LLM response: {}", e.getMessage(), e);
                                        if (jsonText != null && jsonText.length() > 500) {
                                            log.error("Response text preview (first 500 chars): {}",
                                                    jsonText.substring(0, 500));
                                        }
                                    }

                                    return Mono.error(new RuntimeException(errorMsg, e));
                                }
                            });
                })
                .doOnError(error -> log.error("Error extracting entities from text: {}", error.getMessage(), error));
    }

    private String buildEntityExtractionPrompt(String text) {
        return String.format(
                "Extract all entities from the following text. Focus on:\n" +
                        "- **Forms**: USCIS forms (e.g., I-130, I-485, I-864), government forms, application forms\n" +
                        "- **Organizations**: Government agencies (e.g., USCIS, DHS, State Department), companies, institutions\n"
                        +
                        "- **People**: Names of individuals mentioned\n" +
                        "- **Dates**: Important dates, deadlines, timeframes\n" +
                        "- **Locations**: Addresses, cities, countries, offices\n" +
                        "- **Documents**: Document types, certificates, IDs\n" +
                        "\n" +
                        "For each entity, provide:\n" +
                        "- **type**: One of: Form, Organization, Person, Date, Location, Document\n" +
                        "- **id**: A unique identifier (e.g., form number like 'I-130', organization acronym like 'USCIS')\n"
                        +
                        "- **name**: Display name (e.g., 'Form I-130', 'US Citizenship and Immigration Services')\n" +
                        "- **description**: Brief description if relevant\n" +
                        "\n" +
                        "Additionally, extract rich properties based on entity type:\n" +
                        "\n" +
                        "**Organizations** - Extract when available:\n" +
                        "- **address**: Full or partial address\n" +
                        "- **phone**: Phone number(s)\n" +
                        "- **email**: Email address(es)\n" +
                        "- **website**: Website URL\n" +
                        "- **jurisdiction**: Legal jurisdiction, region, or area of authority\n" +
                        "- **acronym**: Official acronym or abbreviation\n" +
                        "\n" +
                        "**People** - Extract when available:\n" +
                        "- **title**: Job title, position, or role\n" +
                        "- **role**: Specific role or function\n" +
                        "- **affiliation**: Organization, company, or institution they belong to\n" +
                        "- **contactInfo**: Phone, email, or other contact information\n" +
                        "\n" +
                        "**Locations** - Extract when available:\n" +
                        "- **street**: Street number and name\n" +
                        "- **city**: City name\n" +
                        "- **state**: State or province\n" +
                        "- **zipCode**: ZIP or postal code\n" +
                        "- **country**: Country name\n" +
                        "- **coordinates**: Latitude and longitude if mentioned\n" +
                        "- **address**: Full formatted address\n" +
                        "\n" +
                        "**Dates** - Extract when available:\n" +
                        "- **dateValue**: Specific date in ISO format (YYYY-MM-DD) if available\n" +
                        "- **dateRange**: Date range if applicable (start and end dates)\n" +
                        "- **eventContext**: What event or deadline this date relates to\n" +
                        "- **deadlineType**: Type of deadline (filing, response, expiration, etc.)\n" +
                        "\n" +
                        "**Forms** - Extract when available:\n" +
                        "- **formNumber**: Official form number (e.g., 'I-130')\n" +
                        "- **formNumberVariants**: Alternative form number formats mentioned\n" +
                        "- **requiredFields**: Key required fields or information needed\n" +
                        "- **filingInstructions**: How or where to file the form\n" +
                        "- **purpose**: Purpose or use case of the form\n" +
                        "\n" +
                        "**IMPORTANT for Forms**: The **name** field must be the complete, descriptive form name as it appears in the document.\n"
                        +
                        "- For form applications: Use the full form name (e.g., 'Form I-485, Application to Register Permanent Residence or Adjust Status')\n"
                        +
                        "- For form instructions: Include 'Instructions' in the name (e.g., 'Form I-485 Instructions', 'Form I-864 Instructions')\n"
                        +
                        "- Always include the form number prefix (e.g., 'Form I-130', 'Form G-1145', 'Form DS-1884')\n"
                        +
                        "- Use the exact title or heading from the document when available\n" +
                        "\n" +
                        "**Documents** - Extract when available:\n" +
                        "- **documentType**: Type of document (passport, birth certificate, etc.)\n" +
                        "- **issuingAuthority**: Who issued the document\n" +
                        "- **expirationDate**: Expiration date if mentioned\n" +
                        "- **issueDate**: Issue date if mentioned\n" +
                        "- **documentNumber**: Document identification number\n" +
                        "\n" +
                        "Return a JSON array of entities with all available properties. Example format:\n" +
                        "[\n" +
                        "  {\n" +
                        "    \"type\": \"Form\",\n" +
                        "    \"id\": \"I-130\",\n" +
                        "    \"name\": \"Form I-130, Petition for Alien Relative\",\n" +
                        "    \"description\": \"Petition for Alien Relative\",\n" +
                        "    \"formNumber\": \"I-130\",\n" +
                        "    \"purpose\": \"To establish the relationship between a U.S. citizen or lawful permanent resident and their foreign relative\"\n"
                        +
                        "  },\n" +
                        "  {\n" +
                        "    \"type\": \"Form\",\n" +
                        "    \"id\": \"I-485Instructions\",\n" +
                        "    \"name\": \"Form I-485, Instructions for Application to Register Permanent Residence or Adjust Status\",\n"
                        +
                        "    \"description\": \"Instructions for Form I-485\",\n" +
                        "    \"formNumber\": \"I-485\",\n" +
                        "    \"purpose\": \"Instructions for applying to register permanent residence or adjust status\"\n"
                        +
                        "  },\n" +
                        "  {\n" +
                        "    \"type\": \"Organization\",\n" +
                        "    \"id\": \"USCIS\",\n" +
                        "    \"name\": \"US Citizenship and Immigration Services\",\n" +
                        "    \"description\": \"Government agency\",\n" +
                        "    \"acronym\": \"USCIS\",\n" +
                        "    \"jurisdiction\": \"United States\"\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"type\": \"Location\",\n" +
                        "    \"id\": \"USCISFieldOfficeChicago\",\n" +
                        "    \"name\": \"USCIS Chicago Field Office\",\n" +
                        "    \"street\": \"101 W. Congress Parkway\",\n" +
                        "    \"city\": \"Chicago\",\n" +
                        "    \"state\": \"Illinois\",\n" +
                        "    \"zipCode\": \"60605\",\n" +
                        "    \"country\": \"United States\"\n" +
                        "  },\n" +
                        "  {\n" +
                        "    \"type\": \"Date\",\n" +
                        "    \"id\": \"FilingDeadline\",\n" +
                        "    \"name\": \"Filing Deadline\",\n" +
                        "    \"description\": \"Application must be filed by this date\",\n" +
                        "    \"dateValue\": \"2024-12-31\",\n" +
                        "    \"deadlineType\": \"filing\"\n" +
                        "  }\n" +
                        "]\n" +
                        "\n" +
                        "Text to analyze:\n" +
                        "%s",
                text);
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

                // Check if this is an error response (structured error from
                // LinqLlmModelServiceImpl)
                if (resultMap.containsKey("error")) {
                    Object errorObj = resultMap.get("error");
                    String errorMessage = "LLM API returned an error";
                    if (errorObj instanceof Map) {
                        errorMessage = ((Map<String, Object>) errorObj).getOrDefault("message", errorMessage)
                                .toString();
                    } else if (errorObj instanceof String) {
                        errorMessage = (String) errorObj;
                    }
                    throw new RuntimeException("LLM API error: " + errorMessage);
                }

                // Check for HTTP status code in the result map (structured error from
                // LinqLlmModelServiceImpl)
                if (resultMap.containsKey("statusCode") && resultMap.containsKey("errorMessage")) {
                    int statusCode = (int) resultMap.get("statusCode");
                    String errorMessage = (String) resultMap.get("errorMessage");
                    if (statusCode >= 400) {
                        throw new RuntimeException("LLM API error (HTTP " + statusCode + "): " + errorMessage);
                    }
                }
            }

            // Delegate parsing to recursive helper
            return extractTextFromObject(result);

        } catch (RuntimeException e) {
            // Re-throw API errors directly without wrapping
            if (e.getMessage() != null && e.getMessage().startsWith("LLM API error:")) {
                throw e;
            }
            // Re-throw if it's already a clear error
            if (e.getMessage() != null && (e.getMessage().contains("HTTP") || e.getMessage().contains("rate limit"))) {
                throw e;
            }
            // Handle refusals gracefully - log as warning and rethrow
            if (e.getMessage() != null && (e.getMessage().contains("refusal") || e.getMessage().contains("refused"))) {
                log.warn("LLM refused response: {}", e.getMessage());
                throw e;
            }
            log.error("Error extracting text from response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract text from LLM response", e);
        } catch (Exception e) {
            log.error("Error extracting text from response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract text from LLM response", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromObject(Object result) {
        // If result is already a string
        if (result instanceof String) {
            return cleanJsonText((String) result);
        }

        // If result is a simple map (like LinkedHashMap from Anthropic), try to extract
        // text or content
        if (result instanceof Map) {
            Map<String, Object> map = (Map<String, Object>) result;
            if (!map.isEmpty()) {
                // Check for refusal/stop reasons first
                if (map.containsKey("stop_reason")) {
                    Object stopReason = map.get("stop_reason");
                    if ("refusal".equals(stopReason) || "content_filter".equals(stopReason)) {
                        throw new RuntimeException("LLM refused the request: stop_reason=" + stopReason);
                    }
                }

                // OpenAI/Chat format: result is a Map with "choices" array
                if (map.containsKey("choices")) {
                    List<?> choices = (List<?>) map.get("choices");
                    if (!choices.isEmpty()) {
                        Object firstChoice = choices.get(0);
                        if (firstChoice instanceof Map) {
                            Map<?, ?> choiceMap = (Map<?, ?>) firstChoice;
                            if (choiceMap.containsKey("message")) {
                                Object message = choiceMap.get("message");
                                if (message instanceof Map) {
                                    return extractTextFromObject(message);
                                }
                            }
                        }
                    }
                }

                // Gemini format: { "candidates": [ ... ] }
                if (map.containsKey("candidates")) {
                    Object candidatesObj = map.get("candidates");
                    if (candidatesObj instanceof List) {
                        List<?> candidates = (List<?>) candidatesObj;
                        if (!candidates.isEmpty()) {
                            Object firstCandidate = candidates.get(0);
                            if (firstCandidate instanceof Map) {
                                // Recursively process the candidate
                                return extractTextFromObject(firstCandidate);
                            }
                        }
                    }
                }

                // Gemini "parts" format inside content
                if (map.containsKey("parts")) {
                    Object partsObj = map.get("parts");
                    if (partsObj instanceof List) {
                        List<?> parts = (List<?>) partsObj;
                        if (!parts.isEmpty()) {
                            Object firstPart = parts.get(0);
                            if (firstPart instanceof Map) {
                                Map<?, ?> partMap = (Map<?, ?>) firstPart;
                                if (partMap.containsKey("text")) {
                                    return cleanJsonText(partMap.get("text").toString());
                                }
                            }
                        }
                    }
                }

                // 1. Try "text" field (common in simple responses)
                if (map.containsKey("text")) {
                    Object textObj = map.get("text");
                    if (textObj instanceof String) {
                        return cleanJsonText((String) textObj);
                    }
                }

                // 2. Try "content" field (standard in Anthropic/OpenAI)
                if (map.containsKey("content")) {
                    Object contentObj = map.get("content");

                    // Case A: content is a String
                    if (contentObj instanceof String) {
                        return cleanJsonText((String) contentObj);
                    }

                    // Case B: content is a List of blocks (standard)
                    else if (contentObj instanceof List) {
                        List<?> list = (List<?>) contentObj;
                        StringBuilder extractedText = new StringBuilder();
                        boolean foundText = false;

                        for (Object item : list) {
                            if (item instanceof Map) {
                                Map<?, ?> itemMap = (Map<?, ?>) item;
                                // Look for "text" field in the block
                                if (itemMap.containsKey("text")) {
                                    Object textVal = itemMap.get("text");
                                    if (textVal instanceof String) {
                                        extractedText.append((String) textVal);
                                        foundText = true;
                                    }
                                }
                            }
                        }

                        if (foundText) {
                            return cleanJsonText(extractedText.toString());
                        }
                    }
                    // Case C: Recursively try content object (Gemini style sometimes)
                    else if (contentObj instanceof Map) {
                        return extractTextFromObject(contentObj);
                    }
                }

                // 3. Try "message" -> "content" (nested structure)
                if (map.containsKey("message")) {
                    Object messageObj = map.get("message");
                    if (messageObj instanceof Map) {
                        return extractTextFromObject(messageObj);
                    }
                }

                // Log the structure for debugging but don't crash if it's just a partial
                // unknown format
                log.warn("Could not extract text from Map response. Keys: {}", map.keySet());
            }
        }

        throw new RuntimeException("Unsupported response format: " + result.getClass().getName() + " - " + result);
    }

    private String cleanJsonText(String text) {
        if (text == null) {
            return "";
        }
        text = text.trim();

        // Try to find JSON array structure
        int firstBracket = text.indexOf('[');
        int lastBracket = text.lastIndexOf(']');

        if (firstBracket >= 0 && lastBracket >= 0 && lastBracket > firstBracket) {
            return text.substring(firstBracket, lastBracket + 1);
        }

        // Fallback: cleanup markdown code blocks if the substring approach failed (e.g.
        // valid array not found)
        if (text.startsWith("```json")) {
            text = text.substring(7);
        } else if (text.startsWith("```")) {
            text = text.substring(3);
        }
        if (text.endsWith("```")) {
            text = text.substring(0, text.length() - 3);
        }

        text = text.trim();
        // Strict fallback: if it doesn't look like a JSON array, return empty to
        // prevent parsing error
        if (!text.startsWith("[")) {
            return "[]";
        }
        return text;
    }

    private String generateEntityId(String entityType, Map<String, Object> entity) {
        // Try to use the provided ID, otherwise generate from name or type
        Object id = entity.get("id");
        if (id != null && !id.toString().isEmpty()) {
            return id.toString();
        }

        Object name = entity.get("name");
        if (name != null && !name.toString().isEmpty()) {
            // Generate ID from name (sanitize)
            return entityType + "_" + name.toString()
                    .replaceAll("[^a-zA-Z0-9_]", "_")
                    .toLowerCase();
        }

        // Fallback to UUID-based ID
        return entityType + "_" + UUID.randomUUID().toString();
    }

    /**
     * Extract token usage from raw LLM response and calculate cost (similar to
     * ChatExecutionServiceImpl)
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

        // Extract token usage based on model category (same logic as
        // ChatExecutionServiceImpl)
        if ("openai-chat".equals(modelCategory) && resultMap.containsKey("usage")) {
            Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
            if (usage != null) {
                promptTokens = usage.containsKey("prompt_tokens")
                        ? ((Number) usage.get("prompt_tokens")).longValue()
                        : 0;
                completionTokens = usage.containsKey("completion_tokens")
                        ? ((Number) usage.get("completion_tokens")).longValue()
                        : 0;
                totalTokens = usage.containsKey("total_tokens")
                        ? ((Number) usage.get("total_tokens")).longValue()
                        : (promptTokens + completionTokens);

                model = resultMap.containsKey("model")
                        ? (String) resultMap.get("model")
                        : modelName;
                hasTokenUsage = true;
            }
        } else if ("gemini-chat".equals(modelCategory) && resultMap.containsKey("usageMetadata")) {
            Map<?, ?> usageMetadata = (Map<?, ?>) resultMap.get("usageMetadata");
            if (usageMetadata != null) {
                promptTokens = usageMetadata.containsKey("promptTokenCount")
                        ? ((Number) usageMetadata.get("promptTokenCount")).longValue()
                        : 0;
                completionTokens = usageMetadata.containsKey("candidatesTokenCount")
                        ? ((Number) usageMetadata.get("candidatesTokenCount")).longValue()
                        : 0;
                totalTokens = usageMetadata.containsKey("totalTokenCount")
                        ? ((Number) usageMetadata.get("totalTokenCount")).longValue()
                        : (promptTokens + completionTokens);

                model = resultMap.containsKey("modelVersion")
                        ? (String) resultMap.get("modelVersion")
                        : modelName;
                hasTokenUsage = true;
            }
        } else if ("claude-chat".equals(modelCategory) && resultMap.containsKey("usage")) {
            Map<?, ?> usage = (Map<?, ?>) resultMap.get("usage");
            if (usage != null) {
                promptTokens = usage.containsKey("input_tokens")
                        ? ((Number) usage.get("input_tokens")).longValue()
                        : 0;
                completionTokens = usage.containsKey("output_tokens")
                        ? ((Number) usage.get("output_tokens")).longValue()
                        : 0;
                totalTokens = promptTokens + completionTokens;

                model = resultMap.containsKey("model")
                        ? (String) resultMap.get("model")
                        : modelName;
                hasTokenUsage = true;
            }
        } else if ("cohere-chat".equals(modelCategory) && resultMap.containsKey("meta")) {
            Map<?, ?> meta = (Map<?, ?>) resultMap.get("meta");
            if (meta != null && meta.containsKey("billed_units")) {
                Map<?, ?> billedUnits = (Map<?, ?>) meta.get("billed_units");
                if (billedUnits != null) {
                    promptTokens = billedUnits.containsKey("input_tokens")
                            ? ((Number) billedUnits.get("input_tokens")).longValue()
                            : 0;
                    completionTokens = billedUnits.containsKey("output_tokens")
                            ? ((Number) billedUnits.get("output_tokens")).longValue()
                            : 0;
                    totalTokens = promptTokens + completionTokens;

                    model = resultMap.containsKey("model")
                            ? (String) resultMap.get("model")
                            : modelName;
                    hasTokenUsage = true;
                }
            }
        } else if ("ollama-chat".equals(modelCategory) &&
                (resultMap.containsKey("prompt_eval_count") || resultMap.containsKey("eval_count"))) {
            // Ollama native format
            promptTokens = resultMap.containsKey("prompt_eval_count")
                    ? ((Number) resultMap.get("prompt_eval_count")).longValue()
                    : 0;
            completionTokens = resultMap.containsKey("eval_count")
                    ? ((Number) resultMap.get("eval_count")).longValue()
                    : 0;
            totalTokens = promptTokens + completionTokens;

            model = resultMap.containsKey("model")
                    ? (String) resultMap.get("model")
                    : modelName;
            hasTokenUsage = true;
        }

        if (hasTokenUsage) {
            // Calculate cost
            double cost = llmCostService.calculateCost(model, promptTokens, completionTokens);
            return new TokenUsageResult(promptTokens, completionTokens, totalTokens, cost);
        }

        return null;
    }

    /**
     * Extract entities from text with cost tracking (internal method that returns
     * both entities and token usage)
     * Tries models in order with automatic fallback on any error
     */
    private Mono<EntityExtractionResult> extractEntitiesFromTextWithCostTracking(
            String text, String documentId, String teamId) {
        log.debug("Extracting entities from text ({} chars) for document: {}", text.length(), documentId);

        // Build extraction prompt once (same for all models)
        String prompt = buildEntityExtractionPrompt(text);
        log.debug("Generated entity extraction prompt (length: {}). Preview: {}", prompt.length(),
                prompt.substring(0, Math.min(prompt.length(), 200)));

        // Get all available models for the team, sort by priority, and try them in
        // order
        return linqLlmModelRepository.findByTeamId(teamId)
                .filter(model -> CHAT_MODEL_CATEGORIES.contains(model.getModelCategory()))
                .sort(Comparator.comparingInt(m -> m.getPriority() != null ? m.getPriority() : 999))
                .collectList()
                .flatMap(models -> {
                    if (models.isEmpty()) {
                        return Mono.error(new RuntimeException("No chat models configured for team " + teamId));
                    }
                    log.info("Found {} available chat models for team {}. Trying in priority order.", models.size(),
                            teamId);
                    return tryExtractionWithLlmModels(text, prompt, documentId, teamId, models, 0);
                });
    }

    /**
     * Try extraction with a specific list of models, falling back to next model on
     * error
     */
    private Mono<EntityExtractionResult> tryExtractionWithLlmModels(
            String text, String prompt, String documentId, String teamId,
            List<org.lite.gateway.entity.LinqLlmModel> models, int startIndex) {

        if (startIndex >= models.size()) {
            return Mono.error(new RuntimeException(
                    "All available chat models failed. Please check your model configurations or try again later."));
        }

        org.lite.gateway.entity.LinqLlmModel currentModel = models.get(startIndex);
        log.debug("Trying entity extraction with model: {} (Priority: {})",
                currentModel.getModelName(), currentModel.getPriority());

        return executeEntityExtractionWithModel(text, prompt, currentModel, documentId, teamId)
                .onErrorResume(error -> {
                    // Try next model on ANY error
                    boolean isRateLimit = false;
                    if (error instanceof org.springframework.web.reactive.function.client.WebClientResponseException webClientEx) {
                        if (webClientEx.getStatusCode() != null && webClientEx.getStatusCode().value() == 429) {
                            isRateLimit = true;
                        }
                    } else if (error.getMessage() != null &&
                            (error.getMessage().contains("Too Many Requests") ||
                                    error.getMessage().contains("allocations") ||
                                    error.getMessage().contains("quota") ||
                                    error.getMessage().contains("rate limit"))) {
                        isRateLimit = true;
                    }

                    long delay = isRateLimit ? 2000 : 0; // 2s delay for rate limits

                    log.warn("Model {} failed (RateLimit: {}): {}. Falling back to next priority model...",
                            currentModel.getModelName(), isRateLimit, error.getMessage());

                    return Mono.delay(Duration.ofMillis(delay))
                            .then(tryExtractionWithLlmModels(text, prompt, documentId, teamId, models, startIndex + 1));
                });
    }

    /**
     * Execute entity extraction with a specific model
     */
    private Mono<EntityExtractionResult> executeEntityExtractionWithModel(
            String text, String prompt, org.lite.gateway.entity.LinqLlmModel llmModel,
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
        systemMessage.put("content",
                "You are an expert entity extraction assistant. Extract entities from the provided text and return a JSON array of entities. Each entity should have: type (e.g., Form, Person, Organization, Date, Location), id (unique identifier), name (display name), and any other relevant properties. Return ONLY valid JSON, no explanations. Example: [{\"type\": \"Person\", \"id\": \"p1\", \"name\": \"John Doe\"}]");
        messages.add(systemMessage);

        Map<String, Object> userMessage = new HashMap<>();
        userMessage.put("role", "user");
        userMessage.put("content", prompt);
        messages.add(userMessage);

        query.setPayload(messages);

        LinqRequest.Query.LlmConfig llmConfig = new LinqRequest.Query.LlmConfig();
        llmConfig.setModel(llmModel.getModelName());
        Map<String, Object> settings = new HashMap<>();
        settings.put("temperature", TEMPERATURE); // Lower temperature for consistent extraction

        // Adjust max_tokens based on model category to respect model limits
        // Cohere models have a max output of 4096 tokens
        int maxTokensForModel = MAX_TOKENS;
        if ("cohere-chat".equals(llmModel.getModelCategory())) {
            maxTokensForModel = Math.min(MAX_TOKENS, 4096); // Cohere max output limit
        }
        settings.put("max_tokens", maxTokensForModel);

        // Enable JSON mode for Ollama to force valid JSON output
        if ("ollama-chat".equals(llmModel.getModelCategory())) {
            settings.put("format", "json");
        }

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
                            log.info(
                                    "📊 Entity extraction token usage - prompt: {}, completion: {}, total: {}, cost: ${}",
                                    tokenUsage.promptTokens, tokenUsage.completionTokens,
                                    tokenUsage.totalTokens, String.format("%.6f", tokenUsage.costUsd));
                        } else {
                            log.warn("⚠️ Could not extract token usage from LLM response for entity extraction");
                        }

                        // Extract text from response (OpenAI format: result -> choices[0] -> message ->
                        // content)
                        String rawText = extractTextFromResponse(response, llmModel.getModelCategory());
                        // Clean up the text (remove markdown, extract JSON array)
                        String jsonText = cleanJsonText(rawText);
                        log.debug("LLM response text (cleaned): {}", jsonText);

                        // Parse JSON array of entities
                        List<Map<String, Object>> entities = objectMapper.readValue(
                                jsonText,
                                new TypeReference<List<Map<String, Object>>>() {
                                });

                        log.debug("Parsed {} entities from LLM response", entities.size());

                        // Return both entities and token usage with model information
                        return Mono.just(new EntityExtractionResult(
                                entities,
                                tokenUsage,
                                llmModel.getModelName(),
                                llmModel.getModelCategory(),
                                llmModel.getProvider()));
                    } catch (Exception e) {
                        // Check if response was truncated due to max_tokens
                        String jsonText = null;
                        try {
                            jsonText = extractTextFromResponse(response, llmModel.getModelCategory());
                        } catch (Exception ignored) {
                            // If we can't extract text, continue with generic error
                        }

                        // Check for truncation indicators
                        boolean isTruncated = false;
                        String errorMsg = "Failed to parse entity extraction response";

                        if (jsonText != null) {
                            // Check if JSON is incomplete (common truncation patterns)
                            String trimmed = jsonText.trim();
                            if (!trimmed.endsWith("]") && trimmed.contains("[")) {
                                isTruncated = true;
                                errorMsg = "Response was truncated (max_tokens limit reached). The input text may be too large - consider processing in smaller batches.";
                            } else if (e.getMessage() != null && (e.getMessage().contains("end-of-input") ||
                                    e.getMessage().contains("Unexpected EOF") ||
                                    e.getMessage().contains("JsonEOFException"))) {
                                isTruncated = true;
                                errorMsg = "Response was truncated (max_tokens limit reached). The input text may be too large - consider processing in smaller batches.";
                            }
                        }

                        // Log detailed error information
                        if (isTruncated) {
                            log.error(
                                    "⚠️ Entity extraction response was truncated. Input was too large for max_tokens={}. Consider reducing batch size.",
                                    MAX_TOKENS);
                            log.error("Truncated response preview (first 500 chars): {}",
                                    jsonText != null && jsonText.length() > 500 ? jsonText.substring(0, 500)
                                            : jsonText);
                        } else {
                            log.error("Error parsing LLM response: {}", e.getMessage(), e);
                            if (jsonText != null && jsonText.length() > 500) {
                                log.error("Response text preview (first 500 chars): {}", jsonText.substring(0, 500));
                            }
                        }

                        return Mono.error(new RuntimeException(errorMsg, e));
                    }
                })
                .doOnError(error -> log.error("Error extracting entities from text: {}", error.getMessage(), error));
    }

    /**
     * Save entity extraction costs, model information, and timing to document
     * metadata
     */
    private Mono<Void> saveEntityExtractionCosts(String documentId, String teamId,
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

                    // Build entity extraction metadata with model info and timing
                    Map<String, Object> entityExtraction = new HashMap<>();
                    entityExtraction.put("promptTokens", promptTokens);
                    entityExtraction.put("completionTokens", completionTokens);
                    entityExtraction.put("totalTokens", totalTokens);
                    entityExtraction.put("costUsd", costUsd);
                    entityExtraction.put("startedAt", startedAt);
                    entityExtraction.put("completedAt", completedAt);
                    entityExtraction.put("extractedAt", completedAt); // Keep for backwards compatibility
                    entityExtraction.put("durationMs", completedAt - startedAt);
                    if (modelName != null) {
                        entityExtraction.put("modelName", modelName);
                    }
                    if (modelCategory != null) {
                        entityExtraction.put("modelCategory", modelCategory);
                    }
                    if (provider != null) {
                        entityExtraction.put("provider", provider);
                    }

                    // Store graph extraction costs in customMetadata
                    Map<String, Object> graphExtraction = new HashMap<>();
                    graphExtraction.put("entityExtraction", entityExtraction);

                    // Merge with existing graphExtraction if present
                    if (metadata.getCustomMetadata().containsKey("graphExtraction")) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> existing = (Map<String, Object>) metadata.getCustomMetadata()
                                .get("graphExtraction");
                        existing.putAll(graphExtraction);
                        graphExtraction = existing;
                    }

                    metadata.getCustomMetadata().put("graphExtraction", graphExtraction);

                    return metadataRepository.save(metadata)
                            .doOnSuccess(m -> log.info(
                                    "💾 Saved entity extraction costs to metadata - prompt: {}, completion: {}, total: {}, cost: ${}",
                                    promptTokens, completionTokens, totalTokens, String.format("%.6f", costUsd)))
                            .then();
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("⚠️ No metadata found for document: {}, cannot save entity extraction costs", documentId);
                    return Mono.empty();
                }))
                .onErrorResume(error -> {
                    log.error("Error saving entity extraction costs to metadata: {}", error.getMessage());
                    return Mono.empty(); // Don't fail the extraction if cost saving fails
                });
    }

    /**
     * Internal class to hold extraction result with token usage
     */
    private static class EntityExtractionResult {
        final List<Map<String, Object>> entities;
        final TokenUsageResult tokenUsage;
        final String modelName;
        final String modelCategory;
        final String provider;

        EntityExtractionResult(List<Map<String, Object>> entities, TokenUsageResult tokenUsage,
                String modelName, String modelCategory, String provider) {
            this.entities = entities;
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
