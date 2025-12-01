package org.lite.gateway.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.MilvusCollectionSchemaInfo;
import org.lite.gateway.dto.ProcessedDocumentDto;
import org.lite.gateway.entity.KnowledgeHubCollection;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.repository.KnowledgeHubCollectionRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.repository.LlmModelRepository;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.KnowledgeHubDocumentEmbeddingService;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.lite.gateway.service.S3Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.data.util.Pair;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubDocumentEmbeddingServiceImpl implements KnowledgeHubDocumentEmbeddingService {

    private static final Duration EMBEDDING_CACHE_TTL = Duration.ofHours(6);
    private static final String EMBEDDING_CACHE_PREFIX = "embedding:doc:";
    private static final String MILVUS_TEXT_FIELD = "text";
    private static final int DEFAULT_CONTEXT_WINDOW_TOKENS = 4096;
    private static final Set<String> ALLOWED_EMBEDDING_STATUSES = Set.of(
            "METADATA_EXTRACTION",
            "EMBEDDING",
            "AI_READY",
            "FAILED"
    );

    private final KnowledgeHubDocumentRepository documentRepository;
    private final KnowledgeHubCollectionRepository collectionRepository;
    private final S3Service s3Service;
    private final LinqMilvusStoreService milvusStoreService;
    private final ObjectMapper objectMapper;
    private final RedisTemplate<String, String> redisTemplate;
    private final LlmModelRepository llmModelRepository;
    private final ChunkEncryptionService chunkEncryptionService;
    @Qualifier("executionMessageChannel")
    private final MessageChannel executionMessageChannel;

    @Override
    public Mono<Void> embedDocument(String documentId, String teamId) {
        log.info("Starting embedding workflow for document {} (team {})", documentId, teamId);

        return documentRepository.findByDocumentId(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                .flatMap(document -> validateDocument(document, teamId))
                .flatMap(document -> collectionRepository.findById(document.getCollectionId())
                        .switchIfEmpty(Mono.error(new RuntimeException("Collection not found for document: " + document.getCollectionId())))
                        .map(collection -> Pair.of(document, collection)))
                .flatMap(pair -> {
                    KnowledgeHubDocument document = pair.getFirst();
                    KnowledgeHubCollection collection = pair.getSecond();

                    validateCollectionEmbeddingConfig(collection);

                    return prepareDocumentForEmbedding(document, collection)
                            .then(fetchProcessedDocument(document))
                            .flatMap(dto -> processChunks(document, collection, dto))
                            .flatMap(embeddedCount -> finalizeEmbedding(document, collection, embeddedCount))
                            .doOnSuccess(v -> publishStatusUpdate(document))
                            .doOnError(error -> log.error("Embedding failed for document {}: {}", document.getDocumentId(), error.getMessage()))
                            .onErrorResume(error -> handleEmbeddingFailure(document, error));
                });
    }

    private String enforceTokenLimit(String text, int maxTokens, String contextLabel) {
        if (!StringUtils.hasText(text) || maxTokens <= 0) {
            return text;
        }

        int softLimit = Math.max(512, (int) Math.floor(maxTokens * 0.75));
        int approxTokens = approximateTokenCount(text);
        if (approxTokens <= softLimit) {
            return text;
        }

        int maxChars = Math.max(1, softLimit * 3);
        String trimmed = text.length() > maxChars ? text.substring(0, maxChars) : text;

        while (approximateTokenCount(trimmed) > softLimit && trimmed.length() > 50) {
            trimmed = trimmed.substring(0, trimmed.length() - 50);
        }

        while (approximateTokenCount(trimmed) > softLimit && trimmed.length() > 0) {
            trimmed = trimmed.substring(0, Math.max(0, trimmed.length() - 10));
        }

        int trimmedTokens = approximateTokenCount(trimmed);
        if (trimmedTokens > softLimit) {
            log.warn("Text for {} still above safe limit after trimming ({} tokens). Final truncation applied.", contextLabel, trimmedTokens);
            trimmed = trimmed.substring(0, Math.min(trimmed.length(), softLimit * 2));
            trimmedTokens = approximateTokenCount(trimmed);
        }

        log.warn("Text for {} exceeded token limit (approx {} > {}). Trimmed to {} characters (~{} tokens).",
                contextLabel, approxTokens, softLimit, trimmed.length(), trimmedTokens);

        return trimmed;
    }

    private int approximateTokenCount(String text) {
        if (!StringUtils.hasText(text)) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 4.0);
    }

    /**
     * Enforces the text field length limit, accounting for encryption overhead.
     * 
     * Encryption adds overhead (IV + GCM tag + Base64 encoding), so we need to
     * truncate the plaintext to a smaller size to ensure the encrypted text fits
     * within the Milvus field limit.
     * 
     * Calculation for encryption overhead:
     * - Plaintext bytes → AES-GCM encryption adds 16 bytes (GCM tag)
     * - IV (12 bytes) is prepended before Base64 encoding
     * - Total overhead: 28 bytes before Base64 encoding
     * - Base64 encoding: (plaintext_bytes + 28) * 4/3 characters
     * 
     * For ASCII text (1 byte/char): plaintext_length + 28 bytes → Base64 ≈ (plaintext_length + 28) * 4/3
     * For UTF-8 text (avg 1-2 bytes/char): more conservative limit needed
     * 
     * We use a conservative approach: reserve space for multi-byte UTF-8 characters
     * and encryption overhead. For maxLength=5000, we truncate to ~3000 chars to be safe.
     */
    private String enforceTextFieldLimit(String text,
                                         MilvusCollectionSchemaInfo schemaInfo,
                                         String textFieldName,
                                         String documentId,
                                         String chunkId) {
        if (!StringUtils.hasText(text) || schemaInfo == null || schemaInfo.getTextFieldMaxLength() == null) {
            return text;
        }
        int maxLength = schemaInfo.getTextFieldMaxLength();
        if (maxLength <= 0) {
            return text;
        }
        
        // Calculate maximum plaintext length accounting for encryption overhead
        // Formula: (maxLength * 3/4) - 28 bytes for overhead, then divide by bytes per char
        // For safety with multi-byte UTF-8, assume ~1.5 bytes per char on average
        // Conservative calculation: use ~60% of maxLength for plaintext
        int maxPlaintextLength = (int) Math.floor(maxLength * 0.60);
        
        // Ensure minimum reasonable size (at least 100 chars)
        if (maxPlaintextLength < 100) {
            maxPlaintextLength = Math.max(100, (int) Math.floor(maxLength * 0.50));
        }
        
        if (text.length() <= maxPlaintextLength) {
            return text;
        }
        
        String truncated = text.substring(0, maxPlaintextLength);
        log.warn("Truncated text for document {} chunk {} to {} characters (from {} chars) to satisfy Milvus field '{}' max_length {} after encryption overhead",
                documentId,
                StringUtils.hasText(chunkId) ? chunkId : "unknown",
                maxPlaintextLength,
                text.length(),
                textFieldName,
                maxLength);
        return truncated;
    }
    
    private String resolveMimeType(KnowledgeHubDocument document, ProcessedDocumentDto processedDocumentDto) {
        if (processedDocumentDto != null && processedDocumentDto.getSourceDocument() != null) {
            String dtoContentType = processedDocumentDto.getSourceDocument().getContentType();
            if (StringUtils.hasText(dtoContentType)) {
                return dtoContentType;
            }
        }
        return StringUtils.hasText(document.getContentType()) ? document.getContentType() : null;
    }

    private String determineDocumentType(String mimeType) {
        if (!StringUtils.hasText(mimeType)) {
            return "UNKNOWN";
        }
        String normalized = mimeType.toLowerCase(Locale.ROOT);
        if (normalized.contains("pdf")) {
            return "PDF";
        }
        if (normalized.contains("wordprocessingml") || normalized.contains("msword")) {
            return "DOCX";
        }
        if (normalized.contains("text")) {
            return "TXT";
        }
        if (normalized.contains("html")) {
            return "HTML";
        }
        if (normalized.contains("json")) {
            return "JSON";
        }
        return "UNKNOWN";
    }

    private Mono<KnowledgeHubDocument> validateDocument(KnowledgeHubDocument document, String teamId) {
        if (!Objects.equals(document.getTeamId(), teamId)) {
            return Mono.error(new RuntimeException("Document not found or access denied: " + document.getDocumentId()));
        }

        if (!StringUtils.hasText(document.getProcessedS3Key())) {
            return Mono.error(new RuntimeException("Document has no processed JSON. Please process the document before embedding."));
        }

        if (!StringUtils.hasText(document.getStatus()) ||
                ALLOWED_EMBEDDING_STATUSES.stream().noneMatch(status -> status.equalsIgnoreCase(document.getStatus()))) {
            return Mono.error(new RuntimeException("Document must be in METADATA_EXTRACTION, EMBEDDING, AI_READY or FAILED status to start embedding"));
        }

        return Mono.just(document);
    }

    private void validateCollectionEmbeddingConfig(KnowledgeHubCollection collection) {
        if (!StringUtils.hasText(collection.getMilvusCollectionName())) {
            throw new RuntimeException("Knowledge Hub collection has no Milvus collection assigned. Please assign one before embedding.");
        }
        if (!StringUtils.hasText(collection.getEmbeddingModel()) || !StringUtils.hasText(collection.getEmbeddingModelName())) {
            throw new RuntimeException("Embedding model configuration missing for collection. Please configure embedding model before embedding.");
        }
        if (collection.getEmbeddingDimension() == null || collection.getEmbeddingDimension() <= 0) {
            throw new RuntimeException("Embedding dimension missing or invalid for collection.");
        }
    }

    private Mono<Void> prepareDocumentForEmbedding(KnowledgeHubDocument document, KnowledgeHubCollection collection) {
        if ("AI_READY".equalsIgnoreCase(document.getStatus())) {
            log.info("Document {} already AI_READY, re-embedding will overwrite existing embeddings", document.getDocumentId());
        }

        return removeExistingEmbeddings(document, collection)
                .doOnNext(count -> {
                    if (count > 0) {
                        log.info("Removed {} existing embeddings for document {} from collection {}", count, document.getDocumentId(), collection.getMilvusCollectionName());
                    }
                })
                .onErrorResume(error -> {
                    log.warn("Failed to remove existing embeddings for document {}: {}", document.getDocumentId(), error.getMessage());
                    return Mono.just(0L);
                })
                .then(Mono.defer(() -> {
                    document.setStatus("EMBEDDING");
                    document.setProcessingModel(collection.getEmbeddingModelName());
                    document.setTotalEmbeddings(0);
                    document.setErrorMessage(null);

                    return documentRepository.save(document)
                            .doOnSuccess(this::publishStatusUpdate)
                            .then();
                }));
    }

    private Mono<Long> removeExistingEmbeddings(KnowledgeHubDocument document, KnowledgeHubCollection collection) {
        if (!StringUtils.hasText(collection.getMilvusCollectionName())) {
            return Mono.just(0L);
        }

        return milvusStoreService.deleteDocumentEmbeddings(
                collection.getMilvusCollectionName(),
                document.getDocumentId(),
                document.getTeamId()
        );
    }

    private Mono<ProcessedDocumentDto> fetchProcessedDocument(KnowledgeHubDocument document) {
        return s3Service.downloadFileContent(document.getProcessedS3Key())
                .map(bytes -> {
                    try {
                        ProcessedDocumentDto processedDoc = objectMapper.readValue(new String(bytes, StandardCharsets.UTF_8), ProcessedDocumentDto.class);
                        // Decrypt sensitive fields in processed document after reading from S3
                        decryptProcessedDocumentDto(processedDoc, document.getTeamId());
                        return processedDoc;
                    } catch (Exception e) {
                        throw new RuntimeException("Failed to parse processed document JSON: " + e.getMessage(), e);
                    }
                });
    }

    private Mono<Integer> processChunks(KnowledgeHubDocument document,
                                        KnowledgeHubCollection collection,
                                        ProcessedDocumentDto processedDocumentDto) {

        List<ProcessedDocumentDto.ChunkDto> chunks = Optional.ofNullable(processedDocumentDto.getChunks()).orElse(List.of());
        if (chunks.isEmpty()) {
            return Mono.error(new RuntimeException("Processed document contains no chunks to embed."));
        }

        String milvusCollectionName = collection.getMilvusCollectionName();
        if (!StringUtils.hasText(milvusCollectionName)) {
            return Mono.error(new RuntimeException("Knowledge Hub collection is not linked to a Milvus collection."));
        }

        return milvusStoreService.getCollectionSchema(milvusCollectionName)
                .flatMap(schemaInfo -> resolveContextWindowTokens(collection)
                        .map(limit -> limit != null && limit > 0 ? limit : DEFAULT_CONTEXT_WINDOW_TOKENS)
                        .flatMap(contextWindowTokens -> {
                            if (collection.isLateChunkingEnabled()) {
                                log.info("Late chunking enabled for collection {}. Using pooled window embeddings.", collection.getId());
                                return processChunksWithLateChunking(document, collection, processedDocumentDto, chunks, contextWindowTokens, schemaInfo);
                            }
                            return processChunksStandard(document, collection, processedDocumentDto, chunks, contextWindowTokens, schemaInfo);
                        }));
    }

    private Mono<Integer> processChunksStandard(KnowledgeHubDocument document,
                                                KnowledgeHubCollection collection,
                                                ProcessedDocumentDto processedDocumentDto,
                                                List<ProcessedDocumentDto.ChunkDto> chunks,
                                                int contextWindowTokens,
                                                MilvusCollectionSchemaInfo schemaInfo) {
        AtomicInteger embeddedCount = new AtomicInteger();

        return Flux.fromIterable(chunks)
                .concatMap(chunk -> embedChunk(document, collection, processedDocumentDto, chunk, contextWindowTokens, schemaInfo)
                        .doOnSuccess(v -> embeddedCount.incrementAndGet()))
                .then(Mono.fromCallable(embeddedCount::get));
    }

    private Mono<Integer> resolveContextWindowTokens(KnowledgeHubCollection collection) {
        String modelName = collection.getEmbeddingModelName();
        Integer collectionDimension = collection.getEmbeddingDimension();

        if (!StringUtils.hasText(modelName)) {
            int derived = deriveContextWindowFromDimension(collectionDimension);
            log.debug("Late chunking: No model name configured, using derived context window {} (dimension={}).", derived, collectionDimension);
            return Mono.just(derived);
        }

        return llmModelRepository.findByModelName(modelName)
                .map(llmModel -> {
                    Integer configured = llmModel.getContextWindowTokens();
                    if (configured != null && configured > 0) {
                        return configured;
                    }

                    Integer modelDimension = llmModel.getDimensions();
                    int derived = deriveContextWindowFromDimension(modelDimension != null ? modelDimension : collectionDimension);
                    log.debug("Late chunking: Model {} missing explicit context window, derived {} (dimensions model={}, collection={}).",
                            modelName, derived, modelDimension, collectionDimension);
                    return derived;
                })
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    int derived = deriveContextWindowFromDimension(collectionDimension);
                    log.warn("Late chunking: Model {} not found in repository. Falling back to derived context window {} (dimension={}).",
                            modelName, derived, collectionDimension);
                    return derived;
                }))
                .map(tokens -> tokens > 0 ? tokens : DEFAULT_CONTEXT_WINDOW_TOKENS)
                .doOnNext(tokens -> log.debug("Late chunking: resolved context window {} tokens for model {}", tokens, modelName));
    }

    private int deriveContextWindowFromDimension(Integer dimension) {
        if (dimension == null || dimension <= 0) {
            return DEFAULT_CONTEXT_WINDOW_TOKENS;
        }
        if (dimension >= 2000) {
            return 8192;
        }
        if (dimension >= 1000) {
            return 4096;
        }
        if (dimension >= 512) {
            return 3072;
        }
        return 2048;
    }

    private Mono<Integer> processChunksWithLateChunking(KnowledgeHubDocument document,
                                                        KnowledgeHubCollection collection,
                                                        ProcessedDocumentDto processedDocumentDto,
                                                        List<ProcessedDocumentDto.ChunkDto> chunks,
                                                        int contextWindowTokens,
                                                        MilvusCollectionSchemaInfo schemaInfo) {

        int strideTokens = Math.max(contextWindowTokens / 2, 256);

        List<ChunkWindow> windows = buildLateChunkWindows(chunks, contextWindowTokens, strideTokens);
        if (windows.isEmpty()) {
            log.warn("Late chunking windows were empty for document {}. Falling back to standard chunk embeddings.", document.getDocumentId());
            return processChunksStandard(document, collection, processedDocumentDto, chunks, contextWindowTokens, schemaInfo);
        }

        int totalWindowTokens = 0;
        for (ChunkWindow window : windows) {
            totalWindowTokens += window.getTokenCount();
        }

        log.info("Late chunking: generated {} pooled windows (context={}, stride={}, tokens≈{}) for {} chunks.",
                windows.size(), contextWindowTokens, strideTokens, totalWindowTokens, chunks.size());

        return Flux.fromIterable(windows)
                .concatMap(window -> {
                    String windowCacheKey = buildWindowCacheKey(document.getDocumentId(), collection.getEmbeddingModelName(), window);
                    String windowText = enforceTokenLimit(window.getText(), contextWindowTokens,
                            String.format("window %d-%d of document %s", window.getStartIndex(), window.getEndIndex(), document.getDocumentId()));
                    if (!StringUtils.hasText(windowText)) {
                        log.warn("Late chunking: window {}-{} produced empty text after enforcing token limit. Skipping.",
                                window.getStartIndex(), window.getEndIndex());
                        return Mono.empty();
                    }
                    return getEmbeddingWithCache(windowCacheKey, windowText, collection.getEmbeddingModel(), collection.getEmbeddingModelName(), document.getTeamId())
                            .map(embedding -> Pair.of(window, embedding))
                            .doOnError(error -> log.error("Failed to embed window {}-{} for document {}: {}",
                                    window.getStartIndex(), window.getEndIndex(), document.getDocumentId(), error.getMessage()));
                })
                .collectList()
                .flatMap(windowEmbeddings -> {
                    if (windowEmbeddings.isEmpty()) {
                        log.warn("Late chunking: no window embeddings were generated for document {} despite {} windows; falling back to direct chunk embeddings.",
                                document.getDocumentId(), windows.size());
                    }
                    Map<Integer, List<List<Float>>> chunkEmbeddingPool = mapChunkEmbeddingsToWindows(windowEmbeddings, chunks.size());
                    log.info("Late chunking: mapped {} window embeddings to {} chunk indices for document {}",
                            windowEmbeddings.size(), chunkEmbeddingPool.size(), document.getDocumentId());

                    return Flux.range(0, chunks.size())
                            .concatMap(chunkIndex -> {
                                ProcessedDocumentDto.ChunkDto chunk = chunks.get(chunkIndex);
                                if (!StringUtils.hasText(chunk.getText())) {
                                    log.warn("Late chunking: skipping empty chunk {} for document {}", chunk.getChunkId(), document.getDocumentId());
                                    return Mono.empty();
                                }

                                List<List<Float>> pooledVectors = chunkEmbeddingPool.get(chunkIndex);
                                Mono<List<Float>> embeddingMono;
                                if (pooledVectors != null && !pooledVectors.isEmpty()) {
                                    log.info("Late chunking: chunk {} received {} pooled vectors", chunk.getChunkId(), pooledVectors.size());
                                    embeddingMono = Mono.fromCallable(() -> averageEmbeddings(pooledVectors))
                                            .flatMap(embedding -> {
                                                if (embedding == null || embedding.isEmpty()) {
                                                    log.info("Late chunking: pooled vectors empty for chunk {} – falling back to direct embedding.", chunk.getChunkId());
                                                    String cacheKey = buildEmbeddingCacheKey(document.getDocumentId(), collection.getEmbeddingModelName(), chunk);
                                                    String chunkText = enforceTokenLimit(chunk.getText(), contextWindowTokens,
                                                            String.format("chunk %s of document %s", chunk.getChunkId(), document.getDocumentId()));
                                                    if (!StringUtils.hasText(chunkText)) {
                                                        return Mono.empty();
                                                    }
                                                    return getEmbeddingWithCache(cacheKey, chunkText, collection.getEmbeddingModel(), collection.getEmbeddingModelName(), document.getTeamId());
                                                }
                                                return Mono.just(embedding);
                                            });
                                } else {
                                    log.info("Late chunking: no pooled vectors for chunk {} – embedding chunk text directly.", chunk.getChunkId());
                                    String cacheKey = buildEmbeddingCacheKey(document.getDocumentId(), collection.getEmbeddingModelName(), chunk);
                                    String chunkText = enforceTokenLimit(chunk.getText(), contextWindowTokens,
                                            String.format("chunk %s of document %s", chunk.getChunkId(), document.getDocumentId()));
                                    if (!StringUtils.hasText(chunkText)) {
                                        return Mono.empty();
                                    }
                                    embeddingMono = getEmbeddingWithCache(cacheKey, chunkText, collection.getEmbeddingModel(), collection.getEmbeddingModelName(), document.getTeamId());
                                }

                                return embeddingMono
                                        .flatMap(embedding -> storeChunkRecord(collection, document, processedDocumentDto, chunk, embedding, chunkIndex, schemaInfo))
                                        .doOnSuccess(v -> log.info("Late chunking: stored embedding for chunk {} (index {}) in document {}",
                                                chunk.getChunkId(), chunkIndex, document.getDocumentId()))
                                        .doOnError(error -> log.error("Late chunking: failed to store embedding for chunk {} (index {}) in document {}: {}",
                                                chunk.getChunkId(), chunkIndex, document.getDocumentId(), error.getMessage()))
                                        .thenReturn(1);
                            })
                            .reduce(0, Integer::sum)
                            .doOnSuccess(total -> log.info("Late chunking: stored embeddings for {} chunks of document {}", total, document.getDocumentId()));
                });
    }

    private Mono<Void> embedChunk(KnowledgeHubDocument document,
                                  KnowledgeHubCollection collection,
                                  ProcessedDocumentDto processedDocumentDto,
                                  ProcessedDocumentDto.ChunkDto chunk,
                                  int contextWindowTokens,
                                  MilvusCollectionSchemaInfo schemaInfo) {

        if (!StringUtils.hasText(chunk.getText())) {
            log.warn("Skipping chunk {} for document {} due to empty text", chunk.getChunkId(), document.getDocumentId());
            return Mono.empty();
        }

        String cacheKey = buildEmbeddingCacheKey(document.getDocumentId(), collection.getEmbeddingModelName(), chunk);
        String embeddingText = enforceTokenLimit(chunk.getText(), contextWindowTokens,
                String.format("chunk %s of document %s", chunk.getChunkId(), document.getDocumentId()));
        if (!StringUtils.hasText(embeddingText)) {
            log.warn("Chunk {} for document {} became empty after enforcing token limit; skipping embedding.",
                    chunk.getChunkId(), document.getDocumentId());
            return Mono.empty();
        }

        return getEmbeddingWithCache(cacheKey, embeddingText, collection.getEmbeddingModel(), collection.getEmbeddingModelName(), document.getTeamId())
                .flatMap(embedding -> storeChunkRecord(
                        collection,
                        document,
                        processedDocumentDto,
                        chunk,
                        embedding,
                        resolveChunkIndex(processedDocumentDto, chunk),
                        schemaInfo))
                .then();
    }

    private int resolveChunkIndex(ProcessedDocumentDto processedDocumentDto, ProcessedDocumentDto.ChunkDto chunk) {
        if (chunk.getChunkIndex() != null) {
            return chunk.getChunkIndex();
        }
        if (processedDocumentDto.getChunks() != null) {
            int idx = processedDocumentDto.getChunks().indexOf(chunk);
            if (idx >= 0) {
                return idx;
            }
        }
        if (StringUtils.hasText(chunk.getChunkId())) {
            String digits = chunk.getChunkId().replaceAll("\\D+", "");
            if (StringUtils.hasText(digits)) {
                try {
                    return Integer.parseInt(digits);
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    private Mono<Void> storeChunkRecord(KnowledgeHubCollection collection,
                                        KnowledgeHubDocument document,
                                        ProcessedDocumentDto processedDocumentDto,
                                        ProcessedDocumentDto.ChunkDto chunk,
                                        List<Float> embedding,
                                        int chunkIndex,
                                        MilvusCollectionSchemaInfo schemaInfo) {

        Set<String> allowedFields = schemaInfo != null ? schemaInfo.getFieldNames() : Set.of();

        String textFieldName = schemaInfo != null && StringUtils.hasText(schemaInfo.getTextFieldName())
                ? schemaInfo.getTextFieldName()
                : MILVUS_TEXT_FIELD;

        String mimeType = resolveMimeType(document, processedDocumentDto);
        String documentType = determineDocumentType(mimeType);
        String collectionTypeValue = schemaInfo != null && StringUtils.hasText(schemaInfo.getCollectionType())
                ? schemaInfo.getCollectionType()
                : "KNOWLEDGE_HUB";

        Map<String, Object> record = new HashMap<>();
        
        // Encrypt chunk text before storing in Milvus
        String plaintext = enforceTextFieldLimit(chunk.getText(), schemaInfo, textFieldName, document.getDocumentId(), chunk.getChunkId());
        String currentKeyVersion = chunkEncryptionService.getCurrentKeyVersion();
        String encryptedText = chunkEncryptionService.encryptChunkText(plaintext, document.getTeamId(), currentKeyVersion);
        
        record.put(textFieldName, encryptedText);
        
        // Store encryption key version for decryption later
        putIfAllowed(record, allowedFields, "encryptionKeyVersion", currentKeyVersion);
        putIfAllowed(record, allowedFields, "chunkId", Optional.ofNullable(chunk.getChunkId()).orElse(UUID.randomUUID().toString()));
        Integer effectiveChunkIndex = chunk.getChunkIndex();
        if (effectiveChunkIndex == null || effectiveChunkIndex < 0) {
            effectiveChunkIndex = chunkIndex >= 0 ? chunkIndex : null;
        }
        putIfAllowed(record, allowedFields, "chunkIndex", effectiveChunkIndex);
        putIfAllowed(record, allowedFields, "documentId", document.getDocumentId());
        putIfAllowed(record, allowedFields, "collectionId", document.getCollectionId());
        putIfAllowed(record, allowedFields, "fileName", document.getFileName());
        putIfAllowed(record, allowedFields, "pageNumbers", Optional.ofNullable(chunk.getPageNumbers())
                .map(list -> list.stream().map(String::valueOf).collect(Collectors.joining(",")))
                .orElse(null));
        putIfAllowed(record, allowedFields, "tokenCount", chunk.getTokenCount());
        putIfAllowed(record, allowedFields, "language", Optional.ofNullable(chunk.getLanguage()).orElse(processedDocumentDto.getStatistics() != null ? processedDocumentDto.getStatistics().getLanguage() : null));
        putIfAllowed(record, allowedFields, "createdAt", System.currentTimeMillis());
        putIfAllowed(record, allowedFields, "teamId", document.getTeamId());
        putIfAllowed(record, allowedFields, "qualityScore", chunk.getQualityScore());
        putIfAllowed(record, allowedFields, "startPosition", chunk.getStartPosition());
        putIfAllowed(record, allowedFields, "endPosition", chunk.getEndPosition());
        putIfAllowed(record, allowedFields, "metadataOnly", chunk.getMetadataOnly());

        String categoryValue = processedDocumentDto.getExtractedMetadata() != null
                ? processedDocumentDto.getExtractedMetadata().getCategory()
                : null;
        if (!StringUtils.hasText(categoryValue) && collection.getCategories() != null && !collection.getCategories().isEmpty()) {
            categoryValue = collection.getCategories().stream()
                    .filter(Objects::nonNull)
                    .map(Enum::name)
                    .collect(Collectors.joining(","));
        }

        if (schemaInfo != null && "KNOWLEDGE_HUB".equalsIgnoreCase(schemaInfo.getCollectionType())) {
            putIfAllowed(record, allowedFields, "title", processedDocumentDto.getExtractedMetadata() != null ? processedDocumentDto.getExtractedMetadata().getTitle() : null);
            putIfAllowed(record, allowedFields, "author", processedDocumentDto.getExtractedMetadata() != null ? processedDocumentDto.getExtractedMetadata().getAuthor() : null);
            putIfAllowed(record, allowedFields, "subject", processedDocumentDto.getExtractedMetadata() != null ? processedDocumentDto.getExtractedMetadata().getSubject() : null);
            putIfAllowed(record, allowedFields, "category", categoryValue);
        }
        putIfAllowed(record, allowedFields, "documentType", documentType);
        putIfAllowed(record, allowedFields, "mimeType", mimeType);
        putIfAllowed(record, allowedFields, "collectionType", collectionTypeValue);

        if (processedDocumentDto.getExtractedMetadata() != null) {
            Map<String, Object> extractedMetadataMap = objectMapper.convertValue(
                    processedDocumentDto.getExtractedMetadata(),
                    new TypeReference<Map<String, Object>>() {});
            extractedMetadataMap.forEach((key, value) -> putIfAllowed(record, allowedFields, key, value));
        }

        if (log.isDebugEnabled()) {
            Map<String, Object> metadataSnapshot = new LinkedHashMap<>(record);
            metadataSnapshot.remove("text");
            metadataSnapshot.put("embeddingSize", embedding != null ? embedding.size() : null);
            log.debug("Milvus chunk metadata for document {} chunk {} -> {}",
                    document.getDocumentId(),
                    record.get("chunkId"),
                    metadataSnapshot);
        }

        return milvusStoreService.storeRecord(
                collection.getMilvusCollectionName(),
                record,
                collection.getEmbeddingModel(),
                collection.getEmbeddingModelName(),
                textFieldName,
                document.getTeamId(),
                embedding)
                .then();
    }

    private void putIfAllowed(Map<String, Object> record,
                              Set<String> allowedFields,
                              String fieldName,
                              Object value) {
        if (record == null || allowedFields == null || fieldName == null) {
            return;
        }
        if (!allowedFields.contains(fieldName)) {
            return;
        }
        if (value == null) {
            return;
        }
        record.put(fieldName, value);
    }

    private Map<Integer, List<List<Float>>> mapChunkEmbeddingsToWindows(List<Pair<ChunkWindow, List<Float>>> windowEmbeddings,
                                                                        int chunkCount) {
        Map<Integer, List<List<Float>>> chunkEmbeddingPool = new HashMap<>(chunkCount);

        for (Pair<ChunkWindow, List<Float>> pair : windowEmbeddings) {
            ChunkWindow window = pair.getFirst();
            List<Float> embedding = pair.getSecond();
            if (window == null || embedding == null || embedding.isEmpty()) {
                continue;
            }

            for (int idx = window.getStartIndex(); idx <= window.getEndIndex(); idx++) {
                chunkEmbeddingPool.computeIfAbsent(idx, key -> new ArrayList<>()).add(embedding);
            }
        }

        return chunkEmbeddingPool;
    }

    private List<Float> averageEmbeddings(List<List<Float>> vectors) {
        if (vectors == null || vectors.isEmpty()) {
            return List.of();
        }

        int dimension = vectors.get(0).size();
        if (dimension == 0) {
            return List.of();
        }

        float[] sums = new float[dimension];
        int count = 0;

        for (List<Float> vector : vectors) {
            if (vector == null || vector.size() != dimension) {
                continue;
            }
            for (int i = 0; i < dimension; i++) {
                sums[i] += vector.get(i);
            }
            count++;
        }

        if (count == 0) {
            return List.of();
        }

        List<Float> averaged = new ArrayList<>(dimension);
        for (int i = 0; i < dimension; i++) {
            averaged.add(sums[i] / count);
        }
        return averaged;
    }

    private List<ChunkWindow> buildLateChunkWindows(List<ProcessedDocumentDto.ChunkDto> chunks,
                                                    int maxTokens,
                                                    int strideTokens) {
        if (maxTokens <= 0) {
            maxTokens = 2048;
        }
        if (strideTokens <= 0) {
            strideTokens = Math.max(maxTokens / 2, 256);
        }

        int chunkCount = chunks.size();
        List<ChunkWindow> windows = new ArrayList<>();
        if (chunkCount == 0) {
            return windows;
        }

        int[] tokenCounts = new int[chunkCount];
        for (int i = 0; i < chunkCount; i++) {
            tokenCounts[i] = Math.max(1, estimateTokens(chunks.get(i)));
        }

        int[] prefixTokens = new int[chunkCount + 1];
        for (int i = 0; i < chunkCount; i++) {
            prefixTokens[i + 1] = prefixTokens[i] + tokenCounts[i];
        }

        int startIndex = 0;
        while (startIndex < chunkCount) {
            int startTokenOffset = prefixTokens[startIndex];
            int endIndex = startIndex;
            int tokensInWindow = 0;
            StringBuilder textBuilder = new StringBuilder();

            while (endIndex < chunkCount) {
                int chunkTokens = tokenCounts[endIndex];
                ProcessedDocumentDto.ChunkDto chunk = chunks.get(endIndex);

                if (tokensInWindow > 0 && tokensInWindow + chunkTokens > maxTokens && textBuilder.length() > 0) {
                    break;
                }

                if (StringUtils.hasText(chunk.getText())) {
                    if (textBuilder.length() > 0) {
                        textBuilder.append("\n\n");
                    }
                    textBuilder.append(chunk.getText());
                    tokensInWindow += chunkTokens;
                }

                endIndex++;
                if (tokensInWindow >= maxTokens) {
                    break;
                }
            }

            int actualEndIndex = Math.max(startIndex, endIndex - 1);
            if (textBuilder.length() > 0) {
                windows.add(new ChunkWindow(startIndex, actualEndIndex, tokensInWindow, textBuilder.toString()));
            }

            if (endIndex >= chunkCount) {
                break;
            }

            int targetTokenOffset = startTokenOffset + strideTokens;
            int nextStartIndex = startIndex + 1;
            while (nextStartIndex < chunkCount && prefixTokens[nextStartIndex] < targetTokenOffset) {
                nextStartIndex++;
            }

            if (nextStartIndex <= startIndex) {
                nextStartIndex = startIndex + 1;
            }

            if (nextStartIndex >= chunkCount) {
                startIndex = endIndex;
            } else {
                startIndex = nextStartIndex;
            }
        }

        if (windows.isEmpty()) {
            StringBuilder fallbackText = new StringBuilder();
            for (ProcessedDocumentDto.ChunkDto chunk : chunks) {
                if (StringUtils.hasText(chunk.getText())) {
                    if (fallbackText.length() > 0) {
                        fallbackText.append("\n\n");
                    }
                    fallbackText.append(chunk.getText());
                }
            }
            if (fallbackText.length() > 0) {
                windows.add(new ChunkWindow(0, chunkCount - 1, prefixTokens[chunkCount], fallbackText.toString()));
            }
        }

        return windows;
    }

    private int estimateTokens(ProcessedDocumentDto.ChunkDto chunk) {
        if (chunk == null) {
            return 1;
        }
        if (chunk.getTokenCount() != null && chunk.getTokenCount() > 0) {
            return chunk.getTokenCount();
        }
        if (!StringUtils.hasText(chunk.getText())) {
            return 1;
        }
        String text = chunk.getText();
        int estimated = Math.max(1, text.length() / 4);
        return Math.min(estimated, 8192);
    }

    private String buildWindowCacheKey(String documentId, String modelName, ChunkWindow window) {
        return EMBEDDING_CACHE_PREFIX + documentId + ":" + (modelName != null ? modelName : "default") +
                ":window:" + window.getStartIndex() + "-" + window.getEndIndex();
    }

    private static class ChunkWindow {
        private final int startIndex;
        private final int endIndex;
        private final int tokenCount;
        private final String text;

        ChunkWindow(int startIndex, int endIndex, int tokenCount, String text) {
            this.startIndex = startIndex;
            this.endIndex = endIndex;
            this.tokenCount = tokenCount;
            this.text = text;
        }

        int getStartIndex() {
            return startIndex;
        }

        int getEndIndex() {
            return endIndex;
        }

        int getTokenCount() {
            return tokenCount;
        }

        String getText() {
            return text;
        }
    }

    private Mono<List<Float>> getEmbeddingWithCache(String cacheKey,
                                                    String text,
                                                    String modelCategory,
                                                    String modelName,
                                                    String teamId) {
        return Mono.fromCallable(() -> Optional.ofNullable(redisTemplate.opsForValue().get(cacheKey)))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(optionalCached -> optionalCached.map(cached -> {
                            try {
                                List<Double> cachedValues = objectMapper.readValue(cached, objectMapper.getTypeFactory().constructCollectionType(List.class, Double.class));
                                if (!cachedValues.isEmpty()) {
                                    List<Float> embedding = cachedValues.stream().map(Double::floatValue).collect(Collectors.toList());
                                    return Mono.just(embedding);
                                }
                            } catch (Exception e) {
                                log.warn("Failed to deserialize cached embedding for key {}: {}", cacheKey, e.getMessage());
                            }
                            return Mono.<List<Float>>empty();
                        })
                        .orElseGet(() -> Mono.empty()))
                .switchIfEmpty(milvusStoreService.getEmbedding(text, modelCategory, modelName, teamId)
                        .flatMap(embedding -> Mono.fromRunnable(() -> {
                                    try {
                                        List<Double> toCache = embedding.stream().map(Float::doubleValue).toList();
                                        redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(toCache), EMBEDDING_CACHE_TTL);
                                    } catch (Exception e) {
                                        log.warn("Failed to cache embedding for key {}: {}", cacheKey, e.getMessage());
                                    }
                                })
                                .subscribeOn(Schedulers.boundedElastic())
                                .thenReturn(embedding)));
    }

    private Mono<Void> finalizeEmbedding(KnowledgeHubDocument document,
                                         KnowledgeHubCollection collection,
                                         int embeddedCount) {
        document.setStatus("AI_READY");
        document.setTotalEmbeddings(embeddedCount);
        document.setProcessingModel(collection.getEmbeddingModelName());
        document.setErrorMessage(null);

        return documentRepository.save(document)
                .doOnSuccess(this::publishStatusUpdate)
                .then();
    }

    private Mono<Void> handleEmbeddingFailure(KnowledgeHubDocument document, Throwable error) {
        document.setStatus("FAILED");
        document.setErrorMessage("Embedding failed: " + error.getMessage());

        return documentRepository.save(document)
                .doOnSuccess(this::publishStatusUpdate)
                .then(Mono.error(error));
    }

    private void publishStatusUpdate(KnowledgeHubDocument document) {
        try {
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("type", "DOCUMENT_STATUS_UPDATE");
            statusUpdate.put("documentId", document.getDocumentId());
            statusUpdate.put("status", document.getStatus());
            statusUpdate.put("teamId", document.getTeamId());
            statusUpdate.put("collectionId", document.getCollectionId());
            statusUpdate.put("fileName", document.getFileName());
            statusUpdate.put("processedAt", document.getProcessedAt());
            statusUpdate.put("totalChunks", document.getTotalChunks());
            statusUpdate.put("totalTokens", document.getTotalTokens());
            statusUpdate.put("totalEmbeddings", document.getTotalEmbeddings());
            statusUpdate.put("processedS3Key", document.getProcessedS3Key());
            statusUpdate.put("errorMessage", document.getErrorMessage());

            boolean sent = executionMessageChannel.send(MessageBuilder.withPayload(statusUpdate).build());
            if (!sent) {
                log.warn("Failed to publish document embedding status update for document {}", document.getDocumentId());
            }
        } catch (Exception e) {
            log.error("Error publishing status update for document {}", document.getDocumentId(), e);
        }
    }

    private String buildEmbeddingCacheKey(String documentId, String modelName, ProcessedDocumentDto.ChunkDto chunk) {
        String suffix;
        if (chunk.getChunkIndex() != null) {
            suffix = chunk.getChunkIndex().toString();
        } else if (StringUtils.hasText(chunk.getChunkId())) {
            suffix = chunk.getChunkId();
        } else {
            suffix = String.valueOf(Math.abs(Objects.hash(chunk.getText())));
        }
        return EMBEDDING_CACHE_PREFIX + documentId + ":" + modelName + ":" + suffix;
    }
    
    /**
     * Decrypt sensitive fields in ProcessedDocumentDto after reading from S3.
     * Decrypts chunk text and metadata fields.
     * 
     * @param processedDoc The processed document DTO to decrypt
     * @param teamId The team ID for key derivation
     */
    private void decryptProcessedDocumentDto(ProcessedDocumentDto processedDoc, String teamId) {
        if (processedDoc == null) {
            return;
        }
        
        String keyVersion = processedDoc.getEncryptionKeyVersion();
        if (keyVersion == null || keyVersion.isEmpty()) {
            keyVersion = "v1"; // Default to v1 for legacy data
        }
        
        try {
            // Decrypt chunk text
            if (processedDoc.getChunks() != null && !processedDoc.getChunks().isEmpty()) {
                for (ProcessedDocumentDto.ChunkDto chunk : processedDoc.getChunks()) {
                    if (chunk.getText() != null && !chunk.getText().isEmpty()) {
                        try {
                            String decryptedText = chunkEncryptionService.decryptChunkText(
                                chunk.getText(), 
                                teamId, 
                                keyVersion
                            );
                            chunk.setText(decryptedText);
                        } catch (Exception e) {
                            log.warn("Failed to decrypt chunk text for team {} with key version {}: {}. Keeping encrypted value.", 
                                teamId, keyVersion, e.getMessage());
                            // Keep encrypted if decryption fails (might be legacy unencrypted data)
                        }
                    }
                }
            }
            
            // Decrypt sensitive metadata fields
            if (processedDoc.getExtractedMetadata() != null) {
                ProcessedDocumentDto.ExtractedMetadata metadata = processedDoc.getExtractedMetadata();
                
                try {
                    if (metadata.getTitle() != null && !metadata.getTitle().isEmpty()) {
                        metadata.setTitle(chunkEncryptionService.decryptChunkText(metadata.getTitle(), teamId, keyVersion));
                    }
                    if (metadata.getAuthor() != null && !metadata.getAuthor().isEmpty()) {
                        metadata.setAuthor(chunkEncryptionService.decryptChunkText(metadata.getAuthor(), teamId, keyVersion));
                    }
                    if (metadata.getSubject() != null && !metadata.getSubject().isEmpty()) {
                        metadata.setSubject(chunkEncryptionService.decryptChunkText(metadata.getSubject(), teamId, keyVersion));
                    }
                    if (metadata.getKeywords() != null && !metadata.getKeywords().isEmpty()) {
                        metadata.setKeywords(chunkEncryptionService.decryptChunkText(metadata.getKeywords(), teamId, keyVersion));
                    }
                    if (metadata.getCreator() != null && !metadata.getCreator().isEmpty()) {
                        metadata.setCreator(chunkEncryptionService.decryptChunkText(metadata.getCreator(), teamId, keyVersion));
                    }
                    if (metadata.getProducer() != null && !metadata.getProducer().isEmpty()) {
                        metadata.setProducer(chunkEncryptionService.decryptChunkText(metadata.getProducer(), teamId, keyVersion));
                    }
                    if (metadata.getFormTitle() != null && !metadata.getFormTitle().isEmpty()) {
                        metadata.setFormTitle(chunkEncryptionService.decryptChunkText(metadata.getFormTitle(), teamId, keyVersion));
                    }
                    if (metadata.getFormNumber() != null && !metadata.getFormNumber().isEmpty()) {
                        metadata.setFormNumber(chunkEncryptionService.decryptChunkText(metadata.getFormNumber(), teamId, keyVersion));
                    }
                } catch (Exception e) {
                    log.warn("Failed to decrypt metadata fields for team {} with key version {}: {}. Some fields may remain encrypted.", 
                        teamId, keyVersion, e.getMessage());
                }
            }
            
            // Decrypt form field values
            if (processedDoc.getFormFields() != null && !processedDoc.getFormFields().isEmpty()) {
                for (ProcessedDocumentDto.FormField field : processedDoc.getFormFields()) {
                    if (field.getValue() != null && !field.getValue().isEmpty()) {
                        try {
                            field.setValue(chunkEncryptionService.decryptChunkText(field.getValue(), teamId, keyVersion));
                        } catch (Exception e) {
                            log.debug("Failed to decrypt form field value for team {}: {}. Keeping encrypted value.", 
                                teamId, e.getMessage());
                        }
                    }
                }
            }
            
            log.debug("Decrypted processed document DTO for team {} with key version {}", teamId, keyVersion);
            
        } catch (Exception e) {
            log.warn("Failed to decrypt some fields in processed document DTO for team {}: {}. Some fields may remain encrypted.", 
                teamId, e.getMessage());
        }
    }
}

