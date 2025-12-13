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
    private final ObjectMapper objectMapper = new ObjectMapper();
    
    // Optional: for progress reporting during extraction jobs
    // Use @Lazy to break circular dependency with GraphExtractionJobServiceImpl
    @Autowired(required = false)
    @Lazy
    private GraphExtractionJobService graphExtractionJobService;
    
    // Chat model categories for entity extraction
    // Ordered by cost priority: cheapest first (Gemini, Cohere) to most expensive
    private static final List<String> CHAT_MODEL_CATEGORIES = List.of(
            "gemini-chat", "cohere-chat", "openai-chat", "claude-chat", "mistral-chat"
    );
    private static final int MAX_CHUNKS_PER_BATCH = 5; // Process chunks in batches to avoid token limits
    
    // Cost optimization settings
    private static final double TEMPERATURE = 0.3; // Lower temperature for consistent extraction (already optimal)
    private static final int MAX_TOKENS = 8000; // Increased from 2000 to handle large entity extraction tasks with rich properties
    
    // Estimate average tokens per request for cost comparison (1000 prompt + 500 completion = 1500 total)
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
                                Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata().get("graphExtraction");
                                if (graphExtraction != null) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> entityExtraction = (Map<String, Object>) graphExtraction.get("entityExtraction");
                                    if (entityExtraction != null) {
                                        // Entities already extracted - check if any exist in Neo4j
                                        return graphService.findEntities("Form", Map.of("documentId", documentId), teamId)
                                                .mergeWith(graphService.findEntities("Organization", Map.of("documentId", documentId), teamId))
                                                .mergeWith(graphService.findEntities("Person", Map.of("documentId", documentId), teamId))
                                                .mergeWith(graphService.findEntities("Date", Map.of("documentId", documentId), teamId))
                                                .mergeWith(graphService.findEntities("Location", Map.of("documentId", documentId), teamId))
                                                .mergeWith(graphService.findEntities("Document", Map.of("documentId", documentId), teamId))
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
                        log.warn("Entities already extracted for document: {}. Use force=true to re-extract.", documentId);
                        return getPreviousExtractionCost(documentId, teamId)
                                .flatMap(previousCost -> Mono.<Integer>error(new RuntimeException(
                                        String.format("Entities already extracted for this document. Previous extraction cost: $%.6f. Use force=true parameter to re-extract (will incur additional costs).", previousCost))));
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
                                            
                                            // Process chunks in batches
                                            List<List<KnowledgeHubChunk>> batches = partitionList(chunks, MAX_CHUNKS_PER_BATCH);
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
                                            // For now, we'll look it up from documentId if graphExtractionJobService is available
                                            Mono<String> jobIdMono = graphExtractionJobService != null
                                                    ? graphExtractionJobService.getJobsForDocument(documentId, teamId)
                                                            .filter(job -> "QUEUED".equals(job.getStatus()) || "RUNNING".equals(job.getStatus()))
                                                            .filter(job -> "entities".equals(job.getExtractionType()) || "all".equals(job.getExtractionType()))
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
                                                                
                                                                // Combine batch chunks into a single text
                                                                String batchText = batch.stream()
                                                                        .map(KnowledgeHubChunk::getText)
                                                                        .filter(Objects::nonNull)
                                                                        .collect(Collectors.joining("\n\n---\n\n"));
                                                                
                                                                log.info("Processing batch {}/{} with {} chunks ({} chars)",
                                                                        batchIndex + 1, batches.size(), batch.size(), batchText.length());
                                                                
                                                                // Add delay between batches to respect rate limits and prevent hitting API rate limits
                                                                // Use longer delay to prevent rate limit errors (3 seconds between batches)
                                                                // This ensures we don't overwhelm the LLM API with rapid requests
                                                                Mono<EntityExtractionResult> extractionMono = batchIndex > 0 
                                                                        ? Mono.delay(Duration.ofSeconds(3))
                                                                                .then(extractEntitiesFromTextWithCostTracking(batchText, documentId, teamId))
                                                                        : extractEntitiesFromTextWithCostTracking(batchText, documentId, teamId);
                                                                
                                                                // Extract entities from this batch
                                                                // Note: Model fallback (handled in extractEntitiesFromTextWithCostTracking) will automatically
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
                                                                            
                                                                            log.info("Extracted {} entities from batch {}/{}",
                                                                                    result.entities.size(), batchIndex + 1, batches.size());
                                                                            
                                                                            // Store entities in Neo4j
                                                                            return Flux.fromIterable(result.entities)
                                                                                    .flatMap(entity -> {
                                                                                        String entityType = (String) entity.get("type");
                                                                                        String entityId = generateEntityId(entityType, entity);
                                                                                        Map<String, Object> properties = new HashMap<>(entity);
                                                                                        properties.remove("type"); // Remove type from properties (it's a label)
                                                                                        properties.put("documentId", documentId);
                                                                                        properties.put("extractedAt", System.currentTimeMillis());
                                                                                        
                                                                                        // Log all entity types being extracted
                                                                                        log.debug("🔍 Extracting {} entity: {} with properties: {}", entityType, entityId, properties.keySet());
                                                                                        
                                                                                        return graphService.upsertEntity(entityType, entityId, properties, teamId)
                                                                                                .doOnSuccess(id -> {
                                                                                                    log.info("✅ Successfully upserted {} entity {}:{} from document {}", entityType, entityType, id, documentId);
                                                                                                })
                                                                                                .onErrorContinue((error, obj) -> 
                                                                                                        log.error("Error upserting entity {}:{}: {}", 
                                                                                                                entityType, entityId, error.getMessage()));
                                                                                    })
                                                                                    .then(Mono.just(result.entities.size()));
                                                                        })
                                                                        .doOnNext(batchEntityCount -> {
                                                                            // Update total entities extracted
                                                                            totalEntitiesExtracted.addAndGet(batchEntityCount);
                                                                            
                                                                            // Report progress after storing entities
                                                                            if (graphExtractionJobService != null && !jobId.isEmpty()) {
                                                                                graphExtractionJobService.updateJobProgress(
                                                                                        jobId, 
                                                                                        (int)(batchIndex + 1), 
                                                                                        batches.size(), 
                                                                                        totalEntitiesExtracted.get(), 
                                                                                        null, 
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
                                                            .flatMap(entityCount -> {
                                                                // Save token usage, cost, model info, and timing to document metadata
                                                                final long completedAt = System.currentTimeMillis();
                                                                return saveEntityExtractionCosts(documentId, teamId, 
                                                                        totalPromptTokens.get(), totalCompletionTokens.get(), 
                                                                        totalTokens.get(), totalCost.get(),
                                                                        modelName.get(), modelCategory.get(), provider.get(),
                                                                        startedAt, completedAt)
                                                                        .thenReturn(entityCount);
                                                            })
                                                            .doOnSuccess(total -> log.info("Extracted {} total entities from document: {}", total, documentId)));
                                        });
                                    });
                            })
                .doOnError(error -> log.error("Error extracting entities from document {}: {}", documentId, error.getMessage(), error));
    }
    
    /**
     * Get previous extraction cost for a document (for warning messages)
     */
    private Mono<Double> getPreviousExtractionCost(String documentId, String teamId) {
        return metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                .map(metadata -> {
                    if (metadata.getCustomMetadata() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata().get("graphExtraction");
                        if (graphExtraction != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> entityExtraction = (Map<String, Object>) graphExtraction.get("entityExtraction");
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
        return linqLlmModelService.findCheapestAvailableModel(CHAT_MODEL_CATEGORIES, teamId, ESTIMATED_PROMPT_TOKENS, ESTIMATED_COMPLETION_TOKENS)
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
                    systemMessage.put("content", "You are an expert entity extraction assistant. Extract entities from the provided text and return a JSON array of entities. Each entity should have: type (e.g., Form, Person, Organization, Date, Location), id (unique identifier), name (display name), and any other relevant properties. Return ONLY valid JSON, no explanations.");
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
                            .flatMap(response -> {
                                try {
                                    // Extract and log token usage and cost
                                    TokenUsageResult tokenUsage = extractTokenUsageFromResponse(
                                            response, llmModel.getModelCategory(), llmModel.getModelName());
                                    if (tokenUsage != null) {
                                        log.info("📊 Entity extraction token usage - prompt: {}, completion: {}, total: {}, cost: ${}",
                                                tokenUsage.promptTokens, tokenUsage.completionTokens,
                                                tokenUsage.totalTokens, String.format("%.6f", tokenUsage.costUsd));
                                    } else {
                                        log.warn("⚠️ Could not extract token usage from LLM response for entity extraction");
                                    }
                                    
                                    // Extract text from response (OpenAI format: result -> choices[0] -> message -> content)
                                    String jsonText = extractTextFromResponse(response, llmModel.getModelCategory());
                                    log.debug("LLM response text: {}", jsonText);
                                    
                                    // Parse JSON array of entities
                                    List<Map<String, Object>> entities = objectMapper.readValue(
                                            jsonText, 
                                            new TypeReference<List<Map<String, Object>>>() {});
                                    
                                    log.debug("Parsed {} entities from LLM response", entities.size());
                                    
                                    // Log entity type counts for debugging
                                    Map<String, Long> entityTypeCounts = entities.stream()
                                            .map(e -> (String) e.get("type"))
                                            .filter(Objects::nonNull)
                                            .collect(Collectors.groupingBy(
                                                    Function.identity(), 
                                                    Collectors.counting()
                                            ));
                                    if (!entityTypeCounts.isEmpty()) {
                                        log.info("📋 Extracted entity type counts: {}", entityTypeCounts);
                                    }
                                    if (entityTypeCounts.containsKey("Document")) {
                                        log.info("✅ Found {} Document entities in LLM response", entityTypeCounts.get("Document"));
                                    } else {
                                        log.debug("ℹ️ No Document entities found in LLM response (extracted types: {})", entityTypeCounts.keySet());
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
                                        log.error("⚠️ Entity extraction response was truncated. Input was too large for max_tokens={}. Consider reducing batch size.", MAX_TOKENS);
                                        log.error("Truncated response preview (first 500 chars): {}", 
                                                jsonText != null && jsonText.length() > 500 ? jsonText.substring(0, 500) : jsonText);
                                    } else {
                                        log.error("Error parsing LLM response: {}", e.getMessage(), e);
                                        if (jsonText != null && jsonText.length() > 500) {
                                            log.error("Response text preview (first 500 chars): {}", jsonText.substring(0, 500));
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
                "- **Organizations**: Government agencies (e.g., USCIS, DHS, State Department), companies, institutions\n" +
                "- **People**: Names of individuals mentioned\n" +
                "- **Dates**: Important dates, deadlines, timeframes\n" +
                "- **Locations**: Addresses, cities, countries, offices\n" +
                "- **Documents**: Document types, certificates, IDs\n" +
                "\n" +
                "For each entity, provide:\n" +
                "- **type**: One of: Form, Organization, Person, Date, Location, Document\n" +
                "- **id**: A unique identifier (e.g., form number like 'I-130', organization acronym like 'USCIS')\n" +
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
                "**IMPORTANT for Forms**: The **name** field must be the complete, descriptive form name as it appears in the document.\n" +
                "- For form applications: Use the full form name (e.g., 'Form I-485, Application to Register Permanent Residence or Adjust Status')\n" +
                "- For form instructions: Include 'Instructions' in the name (e.g., 'Form I-485 Instructions', 'Form I-864 Instructions')\n" +
                "- Always include the form number prefix (e.g., 'Form I-130', 'Form G-1145', 'Form DS-1884')\n" +
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
                "    \"purpose\": \"To establish the relationship between a U.S. citizen or lawful permanent resident and their foreign relative\"\n" +
                "  },\n" +
                "  {\n" +
                "    \"type\": \"Form\",\n" +
                "    \"id\": \"I-485Instructions\",\n" +
                "    \"name\": \"Form I-485, Instructions for Application to Register Permanent Residence or Adjust Status\",\n" +
                "    \"description\": \"Instructions for Form I-485\",\n" +
                "    \"formNumber\": \"I-485\",\n" +
                "    \"purpose\": \"Instructions for applying to register permanent residence or adjust status\"\n" +
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
                
                // Check if this is an error response (structured error from LinqLlmModelServiceImpl)
                if (resultMap.containsKey("error")) {
                    Object errorObj = resultMap.get("error");
                    String errorMessage = "LLM API returned an error";
                    boolean isRateLimit = false;
                    
                    // Check for HTTP status code in the result map (structured error from LinqLlmModelServiceImpl)
                    if (resultMap.containsKey("httpStatus")) {
                        Integer httpStatus = (Integer) resultMap.get("httpStatus");
                        if (httpStatus != null && httpStatus == 429) {
                            isRateLimit = true;
                        }
                    }
                    
                    // Extract error message from structured error object
                    if (errorObj instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> errorMap = (Map<String, Object>) errorObj;
                        if (errorMap.containsKey("message")) {
                            errorMessage = (String) errorMap.get("message");
                        } else if (errorMap.containsKey("type")) {
                            errorMessage = (String) errorMap.get("type");
                        }
                        // Check for rate limit indicators in error message
                        if (!isRateLimit && errorMessage != null) {
                            String errorLower = errorMessage.toLowerCase();
                            isRateLimit = errorLower.contains("rate limit") || 
                                         errorLower.contains("too many requests");
                        }
                    } else if (errorObj instanceof String) {
                        // Fallback: error is a plain string
                        errorMessage = (String) errorObj;
                        String errorLower = errorMessage.toLowerCase();
                        isRateLimit = errorLower.contains("rate limit") || 
                                     errorLower.contains("too many requests") ||
                                     errorLower.contains("429");
                    }
                    
                    // Throw appropriate error - the fallback mechanism in tryExtractionWithModels
                    // will automatically try the next available model on ANY error
                    if (isRateLimit) {
                        log.warn("⚠️ Rate limit detected for LLM service. Will fallback to alternative model. Error: {}", errorMessage);
                        throw new RuntimeException("Rate limit exceeded: " + errorMessage);
                    } else {
                        log.error("LLM API error response: {}. Will fallback to alternative model.", errorMessage);
                        throw new RuntimeException("LLM API error: " + errorMessage);
                    }
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
            
            // If result is a simple map, try to extract text or content (already checked above, but catch edge cases)
            if (result instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> simpleMap = (Map<String, Object>) result;
                if (!simpleMap.isEmpty()) {
                    // Try common fields for text content
                    if (simpleMap.containsKey("text")) {
                        Object textObj = simpleMap.get("text");
                        if (textObj instanceof String) {
                            return cleanJsonText((String) textObj);
                        }
                    }
                    // Try content field (could be String or List)
                    if (simpleMap.containsKey("content")) {
                        Object contentObj = simpleMap.get("content");
                        if (contentObj instanceof String) {
                            return cleanJsonText((String) contentObj);
                        } else if (contentObj instanceof List) {
                            @SuppressWarnings("unchecked")
                            List<Map<String, Object>> contentList = (List<Map<String, Object>>) contentObj;
                            if (!contentList.isEmpty() && contentList.get(0) != null && contentList.get(0).containsKey("text")) {
                                String text = (String) contentList.get(0).get("text");
                                if (text != null) {
                                    return cleanJsonText(text);
                                }
                            }
                        }
                    }
                    // Log the structure for debugging
                    log.warn("Unexpected response structure: {} with keys: {}", result.getClass().getName(), simpleMap.keySet());
                }
            }
            
            throw new RuntimeException("Unsupported response format: " + result.getClass().getName());
        } catch (RuntimeException e) {
            // Re-throw API errors directly without wrapping
            if (e.getMessage() != null && e.getMessage().startsWith("LLM API error:")) {
                throw e;
            }
            // Re-throw if it's already a clear error
            if (e.getMessage() != null && (e.getMessage().contains("HTTP") || e.getMessage().contains("rate limit"))) {
                throw e;
            }
            log.error("Error extracting text from response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract text from LLM response", e);
        } catch (Exception e) {
            log.error("Error extracting text from response: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to extract text from LLM response", e);
        }
    }
    
    private String cleanJsonText(String text) {
        // Clean up the response - remove markdown code blocks if present
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
     * Extract entities from text with cost tracking (internal method that returns both entities and token usage)
     * Tries models in order with automatic fallback on any error
     */
    private Mono<EntityExtractionResult> extractEntitiesFromTextWithCostTracking(
            String text, String documentId, String teamId) {
        log.debug("Extracting entities from text ({} chars) for document: {}", text.length(), documentId);
        
        // Build extraction prompt once (same for all models)
        String prompt = buildEntityExtractionPrompt(text);
        
        // Find cheapest available model first
        return linqLlmModelService.findCheapestAvailableModel(CHAT_MODEL_CATEGORIES, teamId, ESTIMATED_PROMPT_TOKENS, ESTIMATED_COMPLETION_TOKENS)
                .flatMap(cheapestModel -> {
                    // Start with cheapest model, but fallback to others if it fails
                    return tryExtractionWithModelsStartingWith(text, prompt, documentId, teamId, cheapestModel);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // Fallback to original logic if no cheapest model found
                    log.warn("Could not find cheapest model, falling back to original model selection logic");
                    return tryExtractionWithModels(text, prompt, documentId, teamId, CHAT_MODEL_CATEGORIES, 0);
                }));
    }
    
    /**
     * Try extraction starting with a specific model, falling back to others on error
     */
    private Mono<EntityExtractionResult> tryExtractionWithModelsStartingWith(
            String text, String prompt, String documentId, String teamId,
            org.lite.gateway.entity.LinqLlmModel preferredModel) {
        
        // Find the index of the preferred model's category
        final int preferredIndex = CHAT_MODEL_CATEGORIES.indexOf(preferredModel.getModelCategory());
        final int startIndex = preferredIndex == -1 ? 0 : preferredIndex + 1; // Start after preferred model
        
        // Try the preferred model first
        return executeEntityExtractionWithModel(text, prompt, preferredModel, documentId, teamId)
                .onErrorResume(error -> {
                    log.warn("Preferred model {} failed with error: {}. Trying other available models...", 
                            preferredModel.getModelName(), error.getMessage());
                    // Try other models in order (starting after the preferred model's category)
                    return tryExtractionWithModels(text, prompt, documentId, teamId, CHAT_MODEL_CATEGORIES, startIndex);
                });
    }
    
    /**
     * Try extraction with models, falling back to next model on any error
     */
    private Mono<EntityExtractionResult> tryExtractionWithModels(
            String text, String prompt, String documentId, String teamId,
            List<String> categories, int startIndex) {
        
        if (startIndex >= categories.size()) {
            return Mono.error(new RuntimeException(
                    "All available chat models failed. Please check your model configurations or try again later."));
        }
        
        String category = categories.get(startIndex);
        log.debug("Trying entity extraction with model category: {}", category);
        
        return linqLlmModelRepository.findByModelCategoryAndTeamId(category, teamId)
                .next()
                .flatMap(llmModel -> {
                    log.info("Using model {}:{} for entity extraction", llmModel.getModelCategory(), llmModel.getModelName());
                    return executeEntityExtractionWithModel(text, prompt, llmModel, documentId, teamId);
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
                    // Check if this is an HTTP 400 error due to invalid max_tokens configuration
                    boolean isMaxTokensError = false;
                    String errorMsg = error.getMessage();
                    if (errorMsg != null && errorMsg.contains("HTTP 400")) {
                        // Check if it's a max_tokens error
                        if (errorMsg.contains("too many tokens") || 
                            errorMsg.contains("max tokens must be less than") ||
                            errorMsg.contains("maximum output length")) {
                            isMaxTokensError = true;
                            log.warn("⚠️ Invalid max_tokens configuration for model {} (HTTP 400). Error: {}. Skipping this model and trying next...", 
                                    category, errorMsg);
                        }
                    }
                    
                    if (isRateLimit) {
                        if (errorMsg == null) {
                            errorMsg = error.getMessage();
                        }
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
                            .then(tryExtractionWithModels(text, prompt, documentId, teamId, categories, startIndex + 1));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // No model found for this category, try next
                    log.debug("No model found for category {}. Trying next...", category);
                    return tryExtractionWithModels(text, prompt, documentId, teamId, categories, startIndex + 1);
                }));
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
        systemMessage.put("content", "You are an expert entity extraction assistant. Extract entities from the provided text and return a JSON array of entities. Each entity should have: type (e.g., Form, Person, Organization, Date, Location), id (unique identifier), name (display name), and any other relevant properties. Return ONLY valid JSON, no explanations.");
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
                .flatMap(response -> {
                    try {
                        // Extract and log token usage and cost
                        TokenUsageResult tokenUsage = extractTokenUsageFromResponse(
                                response, llmModel.getModelCategory(), llmModel.getModelName());
                        if (tokenUsage != null) {
                            log.info("📊 Entity extraction token usage - prompt: {}, completion: {}, total: {}, cost: ${}",
                                    tokenUsage.promptTokens, tokenUsage.completionTokens,
                                    tokenUsage.totalTokens, String.format("%.6f", tokenUsage.costUsd));
                        } else {
                            log.warn("⚠️ Could not extract token usage from LLM response for entity extraction");
                        }
                        
                        // Extract text from response (OpenAI format: result -> choices[0] -> message -> content)
                        String jsonText = extractTextFromResponse(response, llmModel.getModelCategory());
                        log.debug("LLM response text: {}", jsonText);
                        
                        // Parse JSON array of entities
                        List<Map<String, Object>> entities = objectMapper.readValue(
                                jsonText, 
                                new TypeReference<List<Map<String, Object>>>() {});
                        
                        log.debug("Parsed {} entities from LLM response", entities.size());
                        
                        // Return both entities and token usage with model information
                        return Mono.just(new EntityExtractionResult(
                                entities, 
                                tokenUsage,
                                llmModel.getModelName(),
                                llmModel.getModelCategory(),
                                llmModel.getProvider()
                        ));
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
                            log.error("⚠️ Entity extraction response was truncated. Input was too large for max_tokens={}. Consider reducing batch size.", MAX_TOKENS);
                            log.error("Truncated response preview (first 500 chars): {}", 
                                    jsonText != null && jsonText.length() > 500 ? jsonText.substring(0, 500) : jsonText);
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
     * Save entity extraction costs, model information, and timing to document metadata
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
                        Map<String, Object> existing = (Map<String, Object>) metadata.getCustomMetadata().get("graphExtraction");
                        existing.putAll(graphExtraction);
                        graphExtraction = existing;
                    }
                    
                    metadata.getCustomMetadata().put("graphExtraction", graphExtraction);
                    
                    return metadataRepository.save(metadata)
                            .doOnSuccess(m -> log.info("💾 Saved entity extraction costs to metadata - prompt: {}, completion: {}, total: {}, cost: ${}",
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

