package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.S3Properties;
import org.lite.gateway.dto.ProcessedDocumentDto;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.event.KnowledgeHubDocumentMetaDataEvent;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.repository.KnowledgeHubChunkRepository;
import org.lite.gateway.service.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferFactory;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Service for processing documents: parsing, chunking, and storing
 */
@Service
@Slf4j
public class KnowledgeHubDocumentProcessingServiceImpl implements KnowledgeHubDocumentProcessingService {
    
    private final KnowledgeHubDocumentRepository documentRepository;
    private final KnowledgeHubChunkRepository chunkRepository;
    private final S3Service s3Service;
    private final TikaDocumentParser tikaDocumentParser;
    private final ChunkingService chunkingService;
    private final S3Properties s3Properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ApplicationEventPublisher eventPublisher;
    
    private final MessageChannel executionMessageChannel;
    
    // Constructor with @Qualifier for MessageChannel
    public KnowledgeHubDocumentProcessingServiceImpl(
            KnowledgeHubDocumentRepository documentRepository,
            KnowledgeHubChunkRepository chunkRepository,
            S3Service s3Service,
            TikaDocumentParser tikaDocumentParser,
            ChunkingService chunkingService,
            S3Properties s3Properties,
            ApplicationEventPublisher eventPublisher,
            @Qualifier("executionMessageChannel") MessageChannel executionMessageChannel) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.s3Service = s3Service;
        this.tikaDocumentParser = tikaDocumentParser;
        this.chunkingService = chunkingService;
        this.s3Properties = s3Properties;
        this.eventPublisher = eventPublisher;
        this.executionMessageChannel = executionMessageChannel;
    }
    
    @Override
    public Mono<Void> processDocument(String documentId, String teamId) {
        log.info("Starting document processing for document: {}", documentId);
        
        return documentRepository.findByDocumentId(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                .filter(document -> document.getTeamId().equals(teamId))
                .switchIfEmpty(Mono.error(new RuntimeException("Document access denied")))
                .flatMap(document -> {
                    // Check if chunks already exist - validate they match current chunking strategy
                    return chunkRepository.countByDocumentId(documentId)
                            .flatMap(chunkCount -> {
                                if (chunkCount != null && chunkCount > 0) {
                                    // Chunks exist - validate they match current chunking strategy
                                    String currentStrategy = document.getChunkStrategy() != null 
                                            ? document.getChunkStrategy() 
                                            : "sentence";
                                    
                                    // Sample first chunk to check strategy (efficient - only fetch one)
                                    // Note: We only validate chunk strategy, not chunk size, since KnowledgeHubChunk
                                    // doesn't store chunkSize/overlapTokens (only stores chunkStrategy)
                                    return chunkRepository.findByDocumentId(documentId)
                                            .next()
                                            .flatMap(sampleChunk -> {
                                                String existingStrategy = sampleChunk.getChunkStrategy();
                                                
                                                // Check if chunking strategy matches
                                                if (existingStrategy != null && existingStrategy.equals(currentStrategy)) {
                                                    log.info("Document {} already has {} chunks with matching strategy '{}'. Skipping reprocessing.", 
                                                            documentId, chunkCount, currentStrategy);
                                                    // Ensure status is PROCESSED if chunks exist
                                                    if (!"PROCESSED".equals(document.getStatus())) {
                                                        document.setStatus("PROCESSED");
                                                        return documentRepository.save(document)
                                                                .doOnSuccess(doc -> publishDocumentStatusUpdate(doc))
                                                                .then(Mono.<KnowledgeHubDocument>empty());
                                                    }
                                                    // Return empty to skip processing
                                                    return Mono.<KnowledgeHubDocument>empty();
                                                } else {
                                                    // Strategy mismatch - existing chunks use different strategy
                                                    log.warn("Document {} has {} chunks with strategy '{}', but document settings require '{}'. " +
                                                            "Consider deleting chunks and reprocessing. Skipping for now. " +
                                                            "(Future: UI option to force reprocessing will be available.)", 
                                                            documentId, chunkCount, existingStrategy, currentStrategy);
                                                    // For now, skip processing to avoid duplicate chunks
                                                    // TODO: Add force reprocess option in UI/API to delete old chunks and reprocess
                                                    if (!"PROCESSED".equals(document.getStatus())) {
                                                        document.setStatus("PROCESSED");
                                                        return documentRepository.save(document)
                                                                .doOnSuccess(doc -> publishDocumentStatusUpdate(doc))
                                                                .then(Mono.<KnowledgeHubDocument>empty());
                                                    }
                                                    return Mono.<KnowledgeHubDocument>empty();
                                                }
                                            })
                                            .switchIfEmpty(Mono.defer(() -> {
                                                // No chunks found (shouldn't happen if count > 0, but handle gracefully)
                                                log.warn("Document {} chunk count is {} but no chunks found. Proceeding with processing.", 
                                                        documentId, chunkCount);
                                                document.setStatus("PARSING");
                                                return documentRepository.save(document)
                                                        .doOnSuccess(doc -> publishDocumentStatusUpdate(doc));
                                            }));
                                }
                                
                                // No chunks exist, proceed with processing
                                log.debug("No existing chunks found for document {}. Proceeding with processing.", documentId);
                                document.setStatus("PARSING");
                                return documentRepository.save(document)
                                        .doOnSuccess(doc -> publishDocumentStatusUpdate(doc));
                            });
                })
                .switchIfEmpty(Mono.defer(() -> {
                    // If we got here because chunks exist, return empty Void
                    log.debug("Skipping processing for document {} - chunks already exist", documentId);
                    return Mono.<KnowledgeHubDocument>empty();
                }))
                .flatMap(document -> {
                    long startTime = System.currentTimeMillis();
                    
                    // Step 1: Download file from S3
                    return s3Service.downloadFileContent(document.getS3Key())
                            .doOnSuccess(bytes -> log.info("Downloaded {} bytes for document: {}", bytes.length, documentId))
                            .flatMap(bytes -> {
                                // Step 2: Parse document with Tika
                                log.info("Parsing document with Tika: {}", documentId);
                                TikaDocumentParser.ParseResult parseResult = 
                                        tikaDocumentParser.parse(bytes, document.getContentType());
                                
                                log.info("Extracted {} characters from document, {} pages", 
                                        parseResult.getText().length(), parseResult.getPageCount());
                                
                                // Step 3: Chunk the text
                                return chunkText(document, parseResult.getText(), parseResult.getPageCount())
                                        .flatMap(chunks -> {
                                            // Step 4: Build processed document DTO
                                            ProcessedDocumentDto processedDoc = buildProcessedDocument(
                                                    document, parseResult, chunks, startTime);
                                            
                                            // Step 5: Save processed JSON to S3
                                            return saveProcessedJsonToS3(document, processedDoc)
                                                    .flatMap(s3Key -> {
                                                        // Step 6: Save chunks to MongoDB
                                                        int chunkCount = chunks.size();
                                                        int totalTokens = chunks.stream().mapToInt(ChunkingService.ChunkResult::getTokenCount).sum();
                                                        return saveChunksToMongo(document, chunks)
                                                                .then(updateDocumentStatus(document, s3Key, chunkCount, totalTokens))
                                                                .doOnSuccess(v -> {
                                                                    long processingTime = System.currentTimeMillis() - startTime;
                                                                    log.info("Successfully processed document: {} in {} ms", 
                                                                            documentId, processingTime);
                                                                });
                                                    });
                                        });
                            });
                })
                .onErrorResume(error -> {
                    log.error("Error processing document: {}", documentId, error);
                    return documentRepository.findByDocumentId(documentId)
                            .flatMap(document -> {
                                document.setStatus("FAILED");
                                document.setErrorMessage(error.getMessage());
                                return documentRepository.save(document)
                                        .doOnSuccess(doc -> publishDocumentStatusUpdate(doc));
                            })
                            .then(Mono.error(error));
                })
                .then();
    }
    
    /**
     * Chunk the extracted text based on the document's chunking strategy
     */
    private Mono<List<ChunkingService.ChunkResult>> chunkText(
            KnowledgeHubDocument document, String text, int pageCount) {
        String strategy = document.getChunkStrategy() != null ? document.getChunkStrategy() : "sentence";
        int chunkSize = document.getChunkSize() != null ? document.getChunkSize() : 400;
        int overlap = document.getOverlapTokens() != null ? document.getOverlapTokens() : 50;
        
        List<ChunkingService.ChunkResult> chunks = switch (strategy.toLowerCase()) {
            case "paragraph" -> chunkingService.chunkByParagraphs(text, chunkSize);
            case "token" -> chunkingService.chunkByTokens(text, chunkSize, overlap);
            default -> chunkingService.chunkBySentences(text, chunkSize);
        };

        // Calculate page numbers for each chunk (simple heuristic)
        chunks = assignPageNumbers(chunks, pageCount, text.length());
        
        return Mono.just(chunks);
    }
    
    /**
     * Assign page numbers to chunks based on their position in the document
     */
    private List<ChunkingService.ChunkResult> assignPageNumbers(
            List<ChunkingService.ChunkResult> chunks, int pageCount, int textLength) {
        List<ChunkingService.ChunkResult> result = new ArrayList<>();
        
        for (ChunkingService.ChunkResult chunk : chunks) {
            // Estimate page number based on position
            double positionRatio = (double) chunk.getStartPosition() / textLength;
            int estimatedPage = Math.max(1, Math.min(pageCount, (int) Math.ceil(positionRatio * pageCount)));
            
            List<Integer> pageNumbers = List.of(estimatedPage);
            
            result.add(ChunkingService.ChunkResult.builder()
                    .text(chunk.getText())
                    .tokenCount(chunk.getTokenCount())
                    .startPosition(chunk.getStartPosition())
                    .endPosition(chunk.getEndPosition())
                    .chunkIndex(chunk.getChunkIndex())
                    .pageNumbers(pageNumbers)
                    .build());
        }
        
        return result;
    }
    
    /**
     * Build the processed document DTO
     */
    private ProcessedDocumentDto buildProcessedDocument(
            KnowledgeHubDocument document, 
            TikaDocumentParser.ParseResult parseResult,
            List<ChunkingService.ChunkResult> chunks,
            long startTime) {
        
        long processingTime = System.currentTimeMillis() - startTime;
        
        return ProcessedDocumentDto.builder()
                .processingMetadata(ProcessedDocumentDto.ProcessingMetadata.builder()
                        .documentId(document.getDocumentId())
                        .processedAt(Instant.now().toString())
                        .processingVersion("1.0.0")
                        .ingestServiceVersion("linqra-ingest-v1.0")
                        .processingTimeMs(processingTime)
                        .status("SUCCESS")
                        .build())
                .sourceDocument(ProcessedDocumentDto.SourceDocument.builder()
                        .fileName(document.getFileName())
                        .s3Key(document.getS3Key())
                        .fileSize(document.getFileSize())
                        .contentType(document.getContentType())
                        .uploadedAt(document.getUploadedAt().toString())
                        .build())
                .extractedMetadata(buildExtractedMetadata(parseResult))
                .chunkingStrategy(ProcessedDocumentDto.ChunkingStrategy.builder()
                        .method(document.getChunkStrategy())
                        .maxTokens(document.getChunkSize())
                        .overlapTokens(document.getOverlapTokens())
                        .tokenizer("simple") // TODO: Use proper tokenizer
                        .build())
                .chunks(chunks.stream().map(chunk -> ProcessedDocumentDto.ChunkDto.builder()
                        .chunkId(UUID.randomUUID().toString())
                        .chunkIndex(chunk.getChunkIndex())
                        .text(chunk.getText())
                        .tokenCount(chunk.getTokenCount())
                        .startPosition(chunk.getStartPosition())
                        .endPosition(chunk.getEndPosition())
                        .pageNumbers(chunk.getPageNumbers())
                        .containsTable(false) // TODO: Detect tables
                        .language("en")
                        .qualityScore(calculateQualityScore(chunk))
                        .build()).toList())
                .statistics(buildStatistics(parseResult, chunks))
                .qualityChecks(ProcessedDocumentDto.QualityChecks.builder()
                        .allChunksValid(true)
                        .warnings(new ArrayList<>())
                        .errors(new ArrayList<>())
                        .build())
                .build();
    }
    
    /**
     * Build extracted metadata from Tika parse result
     */
    private ProcessedDocumentDto.ExtractedMetadata buildExtractedMetadata(TikaDocumentParser.ParseResult parseResult) {
        org.apache.tika.metadata.Metadata tikaMetadata = parseResult.getMetadata();
        
        ProcessedDocumentDto.ExtractedMetadata.ExtractedMetadataBuilder builder = ProcessedDocumentDto.ExtractedMetadata.builder()
                .pageCount(parseResult.getPageCount());
        
        // Extract Tika metadata fields (check multiple possible keys for each field)
        // PDFs may use different metadata keys depending on how they were created
        if (tikaMetadata != null) {
            // Title: check multiple possible keys (PDFs may use different metadata keys)
            String title = getFirstAvailable(tikaMetadata, 
                    "title", "dc:title", "xmp:Title", "Title");
            builder.title(title);
            
            // Author: check multiple possible keys
            String author = getFirstAvailable(tikaMetadata,
                    "Author", "author", "dc:creator", "creator", "xmp:Creator");
            builder.author(author);
            
            // Subject: check multiple possible keys
            String subject = getFirstAvailable(tikaMetadata,
                    "subject", "Subject", "dc:subject", "xmp:Subject");
            builder.subject(subject);
            
            // Keywords: check multiple possible keys
            String keywords = getFirstAvailable(tikaMetadata,
                    "Keywords", "keywords", "xmp:Keywords", "meta:keyword");
            builder.keywords(keywords);
            
            // Creator and Producer (PDF-specific metadata)
            builder.creator(getFirstAvailable(tikaMetadata, "creator", "Creator", "xmp:CreatorTool"))
                    .producer(getFirstAvailable(tikaMetadata, "producer", "Producer", "xmp:Producer"))
                    .creationDate(getFirstAvailable(tikaMetadata, "Creation-Date", "creation-date", "xmp:CreateDate", "dcterms:created"))
                    .modificationDate(getFirstAvailable(tikaMetadata, "Last-Modified", "last-modified", "xmp:ModifyDate", "dcterms:modified"));
            
            // Detect language from metadata or default to "en"
            String language = tikaMetadata.get("language");
            if (language == null || language.isEmpty()) {
                language = "en"; // Default to English
            }
            builder.language(language);
            
            // Log what metadata was found (for debugging)
            if (log.isDebugEnabled()) {
                log.debug("Extracted metadata - Title: {}, Author: {}, Subject: {}, Keywords: {}", 
                        title, author, subject, keywords);
            }
        } else {
            builder.language("en"); // Default if metadata is null
        }
        
        return builder.build();
    }
    
    /**
     * Helper method to get the first available value from metadata using multiple possible keys
     */
    private String getFirstAvailable(org.apache.tika.metadata.Metadata metadata, String... keys) {
        for (String key : keys) {
            String value = metadata.get(key);
            if (value != null && !value.trim().isEmpty()) {
                return value;
            }
        }
        return null;
    }
    
    /**
     * Build statistics from parse result and chunks
     */
    private ProcessedDocumentDto.Statistics buildStatistics(
            TikaDocumentParser.ParseResult parseResult, 
            List<ChunkingService.ChunkResult> chunks) {
        
        String text = parseResult.getText();
        int wordCount = text != null && !text.isEmpty() 
                ? text.trim().split("\\s+").length 
                : 0;
        int characterCount = text != null ? text.length() : 0;
        
        org.apache.tika.metadata.Metadata tikaMetadata = parseResult.getMetadata();
        String language = tikaMetadata != null 
                ? tikaMetadata.get("language") 
                : null;
        if (language == null || language.isEmpty()) {
            language = "en"; // Default to English
        }
        
        return ProcessedDocumentDto.Statistics.builder()
                .totalChunks(chunks.size())
                .totalTokens(chunks.stream().mapToInt(ChunkingService.ChunkResult::getTokenCount).sum())
                .avgTokensPerChunk(chunks.isEmpty() ? 0 : 
                        chunks.stream().mapToInt(ChunkingService.ChunkResult::getTokenCount).sum() / chunks.size())
                .avgQualityScore(chunks.isEmpty() ? 0.0 : 
                        chunks.stream().mapToDouble(chunk -> calculateQualityScore(chunk)).average().orElse(0.0))
                .chunksWithLessThan50Tokens((int) chunks.stream()
                        .filter(chunk -> chunk.getTokenCount() < 50)
                        .count())
                .chunksWithTables(0) // TODO: Detect tables
                .pageCount(parseResult.getPageCount())
                .wordCount(wordCount)
                .characterCount(characterCount)
                .language(language)
                .build();
    }
    
    /**
     * Calculate quality score for a chunk
     */
    private double calculateQualityScore(ChunkingService.ChunkResult chunk) {
        // Simple heuristic: quality based on token count
        int tokens = chunk.getTokenCount();
        if (tokens < 50) return 0.7;
        if (tokens < 100) return 0.85;
        if (tokens > 500) return 0.9;
        return 0.95;
    }
    
    /**
     * Save processed JSON to S3
     */
    private Mono<String> saveProcessedJsonToS3(
            KnowledgeHubDocument document, ProcessedDocumentDto processedDoc) {
        try {
            String jsonContent = objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(processedDoc);
            byte[] jsonBytes = jsonContent.getBytes();
            
            // Build S3 key for processed document
            String s3Key = s3Properties.buildProcessedKey(
                    document.getTeamId(), 
                    document.getCollectionId(), 
                    document.getDocumentId()
            );
            
            DataBufferFactory factory = org.springframework.core.io.buffer.DefaultDataBufferFactory.sharedInstance;
            Flux<DataBuffer> flux = Flux.just(factory.wrap(jsonBytes));
            
            return s3Service.uploadFile(s3Key, flux, "application/json", jsonBytes.length)
                    .thenReturn(s3Key);
                    
        } catch (Exception e) {
            log.error("Error serializing processed document to JSON", e);
            return Mono.error(new RuntimeException("Failed to save processed document to S3", e));
        }
    }
    
    /**
     * Save chunks to MongoDB
     */
    private Mono<Void> saveChunksToMongo(
            KnowledgeHubDocument document, 
            List<ChunkingService.ChunkResult> chunkResults) {
        
        // Convert ChunkResult to KnowledgeHubChunk entities
        List<org.lite.gateway.entity.KnowledgeHubChunk> chunks = chunkResults.stream()
                .map(chunk -> org.lite.gateway.entity.KnowledgeHubChunk.builder()
                        .chunkId(UUID.randomUUID().toString())
                        .documentId(document.getDocumentId())
                        .teamId(document.getTeamId())
                        .chunkIndex(chunk.getChunkIndex())
                        .text(chunk.getText())
                        .tokenCount(chunk.getTokenCount())
                        .startPosition(chunk.getStartPosition())
                        .endPosition(chunk.getEndPosition())
                        .pageNumbers(chunk.getPageNumbers())
                        .containsTable(false)
                        .language("en")
                        .qualityScore(calculateQualityScore(chunk))
                        .createdAt(System.currentTimeMillis())
                        .chunkStrategy(document.getChunkStrategy())
                        .build())
                .toList();
        
        // Bulk save all chunks
        return chunkRepository.saveAll(chunks)
                .doOnComplete(() -> log.info("Saved {} chunks to MongoDB for document: {}", 
                        chunks.size(), document.getDocumentId()))
                .then();
    }
    
    /**
     * Update document status to PROCESSED
     * Status flow: PENDING_UPLOAD -> UPLOADED -> PARSING -> PROCESSED -> METADATA_EXTRACTION -> EMBEDDING -> AI_READY
     */
    private Mono<KnowledgeHubDocument> updateDocumentStatus(
            KnowledgeHubDocument document, String processedS3Key, int chunkCount, int totalTokens) {
        document.setStatus("PROCESSED");
        document.setProcessedAt(LocalDateTime.now());
        document.setProcessedS3Key(processedS3Key);
        document.setTotalChunks(chunkCount);
        document.setTotalTokens(totalTokens > 0 ? (long) totalTokens : null);
        
        return documentRepository.save(document)
                .doOnSuccess(savedDoc -> {
                    publishDocumentStatusUpdate(savedDoc);
                    
                    // Publish metadata extraction event after document processing is complete
                    KnowledgeHubDocumentMetaDataEvent event = KnowledgeHubDocumentMetaDataEvent.builder()
                            .documentId(savedDoc.getDocumentId())
                            .teamId(savedDoc.getTeamId())
                            .collectionId(savedDoc.getCollectionId())
                            .processedS3Key(savedDoc.getProcessedS3Key())
                            .fileName(savedDoc.getFileName())
                            .contentType(savedDoc.getContentType())
                            .build();
                    
                    eventPublisher.publishEvent(event);
                    log.info("Published document metadata extraction event for: {}", savedDoc.getDocumentId());
                });
    }
    
    /**
     * Publish document status update via WebSocket
     */
    private void publishDocumentStatusUpdate(KnowledgeHubDocument document) {
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
            statusUpdate.put("processedS3Key", document.getProcessedS3Key());
            statusUpdate.put("errorMessage", document.getErrorMessage());
            
            if (executionMessageChannel != null) {
                boolean sent = executionMessageChannel.send(MessageBuilder.withPayload(statusUpdate).build());
                if (sent) {
                    log.debug("Published document status update via WebSocket for document: {} with status: {}", 
                            document.getDocumentId(), document.getStatus());
                } else {
                    log.warn("Failed to publish document status update via WebSocket for document: {}", 
                            document.getDocumentId());
                }
            }
        } catch (Exception e) {
            log.error("Error publishing document status update via WebSocket for document: {}", 
                    document.getDocumentId(), e);
        }
    }
}

