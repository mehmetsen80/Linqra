package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.lite.gateway.dto.ProcessedDocumentDto;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.KnowledgeHubDocumentMetaData;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.enums.DocumentStatus;
import org.lite.gateway.enums.AuditResultType;
import org.lite.gateway.repository.KnowledgeHubDocumentMetaDataRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.KnowledgeHubDocumentMetaDataService;
import org.lite.gateway.service.KnowledgeHubDocumentEmbeddingService;
import org.lite.gateway.service.S3Service;
import org.lite.gateway.util.AuditLogHelper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.util.Pair;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import reactor.core.publisher.Flux;

@Service
@Slf4j
public class KnowledgeHubDocumentMetaDataServiceImpl implements KnowledgeHubDocumentMetaDataService {

    // Constants for metadata extraction tracking
    private static final String EXTRACTION_MODEL = "ProcessedJSONParser"; // Extraction method: parsing processed JSON
    private static final String EXTRACTION_VERSION = "1.0"; // Version of extraction logic

    private final KnowledgeHubDocumentMetaDataRepository metadataRepository;
    private final KnowledgeHubDocumentRepository documentRepository;
    private final S3Service s3Service;
    private final ObjectMapper objectMapper;
    private final MessageChannel executionMessageChannel;
    private final KnowledgeHubDocumentEmbeddingService embeddingService;
    private final ChunkEncryptionService chunkEncryptionService;
    private final AuditLogHelper auditLogHelper;

    public KnowledgeHubDocumentMetaDataServiceImpl(
            KnowledgeHubDocumentMetaDataRepository metadataRepository,
            KnowledgeHubDocumentRepository documentRepository,
            S3Service s3Service,
            ObjectMapper objectMapper,
            @Qualifier("executionMessageChannel") MessageChannel executionMessageChannel,
            KnowledgeHubDocumentEmbeddingService embeddingService,
            ChunkEncryptionService chunkEncryptionService,
            AuditLogHelper auditLogHelper) {
        this.metadataRepository = metadataRepository;
        this.documentRepository = documentRepository;
        this.s3Service = s3Service;
        this.objectMapper = objectMapper;
        this.executionMessageChannel = executionMessageChannel;
        this.embeddingService = embeddingService;
        this.chunkEncryptionService = chunkEncryptionService;
        this.auditLogHelper = auditLogHelper;
    }

    @Override
    public Mono<KnowledgeHubDocumentMetaData> extractMetadata(String documentId, String teamId) {
        log.info("Extracting metadata for document: {}", documentId);

        LocalDateTime startTime = LocalDateTime.now();

        return documentRepository.findByDocumentId(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                .filter(document -> document.getTeamId().equals(teamId))
                .switchIfEmpty(Mono.error(new RuntimeException("Document access denied")))
                .filter(document -> document.getProcessedS3Key() != null && !document.getProcessedS3Key().isEmpty())
                .switchIfEmpty(Mono.error(
                        new RuntimeException("Document has no processed JSON. Please process the document first.")))
                .flatMap(document -> {
                    // Capture document details for audit
                    String fileName = document.getFileName();
                    String collectionId = document.getCollectionId();
                    String contentType = document.getContentType();

                    // Update document status to METADATA_EXTRACTION first (before extraction
                    // starts)
                    document.setStatus(DocumentStatus.METADATA_EXTRACTION);
                    return documentRepository.save(document)
                            .doOnSuccess(savedDoc -> {
                                log.info("Updated document status to METADATA_EXTRACTION for document: {}", documentId);
                                publishDocumentStatusUpdate(savedDoc);
                            })
                            .flatMap(savedDoc -> {
                                // Check if metadata already exists to determine if this is an update
                                return metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                                        .flatMap(existing -> {
                                            log.info("Metadata already exists for document: {}, updating...",
                                                    documentId);
                                            return updateMetadataFromProcessedJson(existing, savedDoc)
                                                    .map(metadata -> Pair.of(metadata, true)); // true = isUpdate
                                        })
                                        .switchIfEmpty(
                                                // Create new metadata extract
                                                createMetadataFromProcessedJson(savedDoc)
                                                        .map(metadata -> Pair.of(metadata, false)) // false = isNew
                                )
                                        .flatMap(metadataPair -> {
                                            KnowledgeHubDocumentMetaData metadata = metadataPair.getFirst();
                                            boolean isUpdate = metadataPair.getSecond();

                                            // Build audit context with extracted metadata details
                                            long durationMs = java.time.Duration.between(startTime, LocalDateTime.now())
                                                    .toMillis();

                                            Map<String, Object> auditContext = new HashMap<>();
                                            auditContext.put("fileName", fileName);
                                            auditContext.put("teamId", teamId);
                                            auditContext.put("collectionId", collectionId);
                                            auditContext.put("contentType", contentType);
                                            auditContext.put("extractionModel", EXTRACTION_MODEL);
                                            auditContext.put("extractionVersion", EXTRACTION_VERSION);
                                            auditContext.put("isUpdate", isUpdate);
                                            auditContext.put("durationMs", durationMs);
                                            auditContext.put("extractionTimestamp", LocalDateTime.now().toString());

                                            // Capture extracted metadata fields
                                            if (metadata.getTitle() != null) {
                                                auditContext.put("title", metadata.getTitle());
                                            }
                                            if (metadata.getAuthor() != null) {
                                                auditContext.put("author", metadata.getAuthor());
                                            }
                                            if (metadata.getSubject() != null) {
                                                auditContext.put("subject", metadata.getSubject());
                                            }
                                            if (metadata.getKeywords() != null) {
                                                auditContext.put("keywords", metadata.getKeywords());
                                            }
                                            if (metadata.getPageCount() != null) {
                                                auditContext.put("pageCount", metadata.getPageCount());
                                            }
                                            if (metadata.getWordCount() != null) {
                                                auditContext.put("wordCount", metadata.getWordCount());
                                            }
                                            if (metadata.getCharacterCount() != null) {
                                                auditContext.put("characterCount", metadata.getCharacterCount());
                                            }
                                            if (metadata.getDocumentType() != null) {
                                                auditContext.put("documentType", metadata.getDocumentType());
                                            }
                                            if (metadata.getMimeType() != null) {
                                                auditContext.put("mimeType", metadata.getMimeType());
                                            }

                                            String auditReason = String.format(
                                                    "%s metadata for document '%s' (type: %s, model: %s)",
                                                    isUpdate ? "Updated" : "Extracted",
                                                    fileName,
                                                    metadata.getDocumentType() != null ? metadata.getDocumentType()
                                                            : contentType,
                                                    EXTRACTION_MODEL);

                                            // Log successful metadata extraction
                                            return auditLogHelper.logDetailedEvent(
                                                    AuditEventType.METADATA_EXTRACTED,
                                                    isUpdate ? AuditActionType.UPDATE : AuditActionType.CREATE,
                                                    AuditResourceType.METADATA,
                                                    documentId, // resourceId - the document ID
                                                    auditReason,
                                                    auditContext,
                                                    documentId, // documentId
                                                    collectionId // collectionId
                                            ).then(triggerEmbedding(savedDoc)
                                                    .onErrorResume(error -> {
                                                        log.error(
                                                                "Failed to trigger embedding after metadata extraction for document {}: {}",
                                                                documentId, error.getMessage());
                                                        return Mono.empty();
                                                    })
                                                    .thenReturn(metadata));
                                        });
                            });
                })
                .doOnSuccess(metadata -> log.info("Successfully extracted metadata for document: {}", documentId))
                .onErrorResume(error -> {
                    log.error("Error extracting metadata for document: {}", documentId, error);

                    // Log failed metadata extraction attempt and update document status
                    return documentRepository.findByDocumentId(documentId)
                            .flatMap(document -> {
                                long durationMs = java.time.Duration.between(startTime, LocalDateTime.now()).toMillis();

                                Map<String, Object> errorContext = new HashMap<>();
                                errorContext.put("fileName", document.getFileName());
                                errorContext.put("teamId", document.getTeamId());
                                errorContext.put("collectionId", document.getCollectionId());
                                errorContext.put("contentType", document.getContentType());
                                errorContext.put("extractionModel", EXTRACTION_MODEL);
                                errorContext.put("extractionVersion", EXTRACTION_VERSION);
                                errorContext.put("error", error.getMessage());
                                errorContext.put("durationMs", durationMs);
                                errorContext.put("failureTimestamp", LocalDateTime.now().toString());

                                // Update document status to FAILED if extraction fails
                                document.setStatus(DocumentStatus.FAILED);
                                document.setErrorMessage("Metadata extraction failed: " + error.getMessage());

                                // Chain audit logging before saving and returning error
                                return auditLogHelper.logDetailedEvent(
                                        AuditEventType.METADATA_EXTRACTED,
                                        AuditActionType.CREATE,
                                        AuditResourceType.METADATA,
                                        documentId,
                                        String.format("Metadata extraction failed for document '%s': %s",
                                                document.getFileName(), error.getMessage()),
                                        errorContext,
                                        documentId,
                                        document.getCollectionId(),
                                        AuditResultType.FAILED // Result: FAILED
                                )
                                        .doOnError(auditError -> log.error(
                                                "Failed to log audit event (metadata extraction failed): {}",
                                                auditError.getMessage(), auditError))
                                        .onErrorResume(auditError -> Mono.empty()) // Don't fail if audit logging fails
                                        .then(documentRepository.save(document))
                                        .doOnSuccess(this::publishDocumentStatusUpdate)
                                        .then(Mono.<KnowledgeHubDocumentMetaData>error(error)); // Return the original
                                                                                                // error after logging
                                                                                                // and updating
                            })
                            .switchIfEmpty(Mono.<KnowledgeHubDocumentMetaData>error(error)); // If document not found,
                                                                                             // return original error
                });
    }

    private Mono<Void> triggerEmbedding(KnowledgeHubDocument document) {
        if (embeddingService == null) {
            log.warn("Embedding service not available; cannot trigger embedding for document {}",
                    document.getDocumentId());
            return Mono.empty();
        }

        return documentRepository.findByDocumentId(document.getDocumentId())
                .defaultIfEmpty(document)
                .flatMap(doc -> {
                    if (doc.getStatus() == DocumentStatus.AI_READY) {
                        log.info("Document {} already AI_READY; skipping embedding trigger.", doc.getDocumentId());
                        return Mono.empty();
                    }

                    doc.setStatus(DocumentStatus.EMBEDDING);
                    doc.setErrorMessage(null);
                    return documentRepository.save(doc)
                            .doOnSuccess(this::publishDocumentStatusUpdate)
                            .flatMap(saved -> embeddingService.embedDocument(saved.getDocumentId(), saved.getTeamId()))
                            .then();
                });
    }

    private Mono<KnowledgeHubDocumentMetaData> createMetadataFromProcessedJson(KnowledgeHubDocument document) {
        String documentId = document.getDocumentId();
        String teamId = document.getTeamId();
        String collectionId = document.getCollectionId();

        return metadataRepository.deleteByDocumentIdAndTeamIdAndCollectionId(documentId, teamId, collectionId)
                .then(s3Service.downloadFileContent(document.getProcessedS3Key()))
                .flatMap(jsonBytes -> {
                    try {
                        // Parse as ProcessedDocumentDto to decrypt sensitive fields
                        ProcessedDocumentDto processedDoc = objectMapper.readValue(new String(jsonBytes),
                                ProcessedDocumentDto.class);

                        // Decrypt sensitive fields in processed document
                        return decryptProcessedDocumentDto(processedDoc, teamId)
                                .then(Mono.defer(() -> {
                                    try {
                                        // Convert decrypted DTO back to JsonNode for backward compatibility with
                                        // extraction logic
                                        String decryptedJsonString = objectMapper.writerWithDefaultPrettyPrinter()
                                                .writeValueAsString(processedDoc);
                                        JsonNode processedJson = objectMapper.readTree(decryptedJsonString);

                                        KnowledgeHubDocumentMetaData metadata = KnowledgeHubDocumentMetaData.builder()
                                                .documentId(documentId)
                                                .teamId(teamId)
                                                .collectionId(collectionId)
                                                .extractedAt(LocalDateTime.now())
                                                .status("EXTRACTED")
                                                .extractionModel(EXTRACTION_MODEL)
                                                .extractionVersion(EXTRACTION_VERSION)
                                                .build();

                                        // Extract metadata from processed JSON (now decrypted)
                                        extractMetadataFromJson(metadata, processedJson, document);

                                        // Encrypt sensitive metadata fields before saving
                                        return chunkEncryptionService.getCurrentKeyVersion(teamId)
                                                .flatMap(encryptionKeyVersion -> {
                                                    metadata.setEncryptionKeyVersion(encryptionKeyVersion);
                                                    return encryptMetadataFields(metadata, teamId, encryptionKeyVersion)
                                                            .then(metadataRepository.save(metadata))
                                                            .onErrorResume(DuplicateKeyException.class, ex -> {
                                                                log.warn(
                                                                        "Duplicate metadata detected for document {} (team {}, collection {}). Falling back to update existing record.",
                                                                        documentId, teamId, collectionId);
                                                                return metadataRepository
                                                                        .findTopByDocumentIdAndTeamIdAndCollectionIdOrderByExtractedAtDesc(
                                                                                documentId, teamId, collectionId)
                                                                        .switchIfEmpty(Mono.error(ex))
                                                                        .flatMap(existing -> {
                                                                            applyMetadataUpdates(existing, metadata);
                                                                            return metadataRepository.save(existing);
                                                                        });
                                                            });
                                                });
                                    } catch (Exception e) {
                                        return Mono.error(e);
                                    }
                                }));
                    } catch (Exception e) {
                        log.error("Error parsing processed JSON for document: {}", documentId, e);
                        return Mono.error(new RuntimeException("Failed to parse processed JSON: " + e.getMessage(), e));
                    }
                });
    }

    private Mono<KnowledgeHubDocumentMetaData> updateMetadataFromProcessedJson(
            KnowledgeHubDocumentMetaData existing, KnowledgeHubDocument document) {
        return s3Service.downloadFileContent(document.getProcessedS3Key())
                .flatMap(jsonBytes -> {
                    try {
                        // Parse as ProcessedDocumentDto to decrypt sensitive fields
                        ProcessedDocumentDto processedDoc = objectMapper.readValue(new String(jsonBytes),
                                ProcessedDocumentDto.class);

                        // Decrypt sensitive fields in processed document
                        return decryptProcessedDocumentDto(processedDoc, document.getTeamId())
                                .then(Mono.defer(() -> {
                                    try {
                                        // Convert decrypted DTO back to JsonNode for backward compatibility with
                                        // extraction logic
                                        String decryptedJsonString = objectMapper.writerWithDefaultPrettyPrinter()
                                                .writeValueAsString(processedDoc);
                                        JsonNode processedJson = objectMapper.readTree(decryptedJsonString);

                                        // Update existing metadata (from decrypted JSON)
                                        extractMetadataFromJson(existing, processedJson, document);
                                        existing.setExtractedAt(LocalDateTime.now());
                                        existing.setStatus("EXTRACTED");

                                        // Encrypt sensitive metadata fields before saving
                                        return chunkEncryptionService.getCurrentKeyVersion(document.getTeamId())
                                                .flatMap(encryptionKeyVersion -> {
                                                    existing.setEncryptionKeyVersion(encryptionKeyVersion);
                                                    return encryptMetadataFields(existing, document.getTeamId(),
                                                            encryptionKeyVersion)
                                                            .then(metadataRepository.save(existing));
                                                });
                                    } catch (Exception e) {
                                        return Mono.error(e);
                                    }
                                }));
                    } catch (Exception e) {
                        log.error("Error parsing processed JSON for document: {}", document.getDocumentId(), e);
                        return Mono.error(new RuntimeException("Failed to parse processed JSON: " + e.getMessage(), e));
                    }
                });
    }

    private void extractMetadataFromJson(KnowledgeHubDocumentMetaData metadata, JsonNode processedJson,
            KnowledgeHubDocument document) {
        // Extract document-level metadata
        // Log the structure of processed JSON for debugging
        java.util.Iterator<String> topLevelKeys = processedJson.fieldNames();
        java.util.List<String> topLevelKeyList = new java.util.ArrayList<>();
        topLevelKeys.forEachRemaining(topLevelKeyList::add);
        log.info("Processing JSON structure for document: {} - Available top-level keys: {}",
                document.getDocumentId(),
                topLevelKeyList.isEmpty() ? "none" : String.join(", ", topLevelKeyList));

        // Extract file information
        metadata.setDocumentType(determineDocumentType(document.getContentType()));
        metadata.setMimeType(document.getContentType());

        // Extract statistics from processed JSON
        if (processedJson.has("statistics")) {
            JsonNode stats = processedJson.get("statistics");
            log.info("Extracting statistics from processed JSON for document: {}", document.getDocumentId());
            java.util.Iterator<String> statsKeys = stats.fieldNames();
            java.util.List<String> statsKeyList = new java.util.ArrayList<>();
            statsKeys.forEachRemaining(statsKeyList::add);
            log.info("Statistics section keys: {}", statsKeyList.isEmpty() ? "none" : String.join(", ", statsKeyList));

            if (stats.has("pageCount")) {
                metadata.setPageCount(stats.get("pageCount").asInt());
                log.info("  - pageCount: {}", metadata.getPageCount());
            } else {
                log.info("  - pageCount: not found in statistics");
            }

            if (stats.has("wordCount")) {
                metadata.setWordCount(stats.get("wordCount").asInt());
                log.info("  - wordCount: {}", metadata.getWordCount());
            } else {
                log.info("  - wordCount: not found in statistics (document may need reprocessing)");
            }

            if (stats.has("characterCount")) {
                metadata.setCharacterCount(stats.get("characterCount").asInt());
                log.info("  - characterCount: {}", metadata.getCharacterCount());
            } else {
                log.info("  - characterCount: not found in statistics (document may need reprocessing)");
            }

            if (stats.has("language")) {
                metadata.setLanguage(stats.get("language").asText());
                log.info("  - language: {}", metadata.getLanguage());
            } else {
                log.info("  - language: not found in statistics");
            }
        } else {
            log.warn("No 'statistics' section found in processed JSON for document: {}", document.getDocumentId());
        }

        // Extract metadata from extractedMetadata section (not "metadata")
        if (processedJson.has("extractedMetadata")) {
            JsonNode extractedMetadataNode = processedJson.get("extractedMetadata");
            log.info("Extracting metadata from extractedMetadata section for document: {}", document.getDocumentId());
            java.util.Iterator<String> extractedKeys = extractedMetadataNode.fieldNames();
            java.util.List<String> extractedKeyList = new java.util.ArrayList<>();
            extractedKeys.forEachRemaining(extractedKeyList::add);
            log.info("ExtractedMetadata section keys: {}",
                    extractedKeyList.isEmpty() ? "none" : String.join(", ", extractedKeyList));

            if (extractedMetadataNode.has("title")) {
                String title = extractedMetadataNode.get("title").asText();
                if (!title.isEmpty()) {
                    metadata.setTitle(title);
                    log.info("  - title: {}", title);
                } else {
                    log.info("  - title: empty string");
                }
            } else {
                log.info("  - title: not found in extractedMetadata (document may need reprocessing)");
            }

            if (extractedMetadataNode.has("author")) {
                String author = extractedMetadataNode.get("author").asText();
                if (!author.isEmpty()) {
                    metadata.setAuthor(author);
                    log.info("  - author: {}", author);
                } else {
                    log.info("  - author: empty string");
                }
            } else {
                log.info("  - author: not found in extractedMetadata (document may need reprocessing)");
            }
            if (extractedMetadataNode.has("subject")) {
                String subject = extractedMetadataNode.get("subject").asText();
                if (!subject.isEmpty()) {
                    metadata.setSubject(subject);
                    log.info("  - subject: {}", subject);
                }
            }
            if (extractedMetadataNode.has("keywords")) {
                String keywords = extractedMetadataNode.get("keywords").asText();
                if (!keywords.isEmpty()) {
                    metadata.setKeywords(keywords);
                    log.info("  - keywords: {}", keywords);
                }
            }
            if (extractedMetadataNode.has("creator")) {
                String creator = extractedMetadataNode.get("creator").asText();
                if (!creator.isEmpty()) {
                    metadata.setCreator(creator);
                    log.info("  - creator: {}", creator);
                }
            }
            if (extractedMetadataNode.has("producer")) {
                String producer = extractedMetadataNode.get("producer").asText();
                if (!producer.isEmpty()) {
                    metadata.setProducer(producer);
                    log.info("  - producer: {}", producer);
                }
            }
            if (extractedMetadataNode.has("creationDate")) {
                try {
                    String dateStr = extractedMetadataNode.get("creationDate").asText();
                    if (!dateStr.isEmpty()) {
                        // TODO: Parse and set creationDate if needed
                        log.debug("Creation date found: {}", dateStr);
                    }
                } catch (Exception e) {
                    log.warn("Could not parse creation date: {}", e.getMessage());
                }
            }
            if (extractedMetadataNode.has("modificationDate")) {
                try {
                    String dateStr = extractedMetadataNode.get("modificationDate").asText();
                    if (!dateStr.isEmpty()) {
                        // TODO: Parse and set modificationDate if needed
                        log.debug("Modification date found: {}", dateStr);
                    }
                } catch (Exception e) {
                    log.warn("Could not parse modification date: {}", e.getMessage());
                }
            }
            // Also check pageCount and language in extractedMetadata (fallback)
            if (metadata.getPageCount() == null && extractedMetadataNode.has("pageCount")) {
                metadata.setPageCount(extractedMetadataNode.get("pageCount").asInt());
                log.info("  - pageCount (fallback from extractedMetadata): {}", metadata.getPageCount());
            }
            if (metadata.getLanguage() == null && extractedMetadataNode.has("language")) {
                metadata.setLanguage(extractedMetadataNode.get("language").asText());
                log.info("  - language (fallback from extractedMetadata): {}", metadata.getLanguage());
            }
        } else {
            log.warn("No 'extractedMetadata' section found in processed JSON for document: {}",
                    document.getDocumentId());
        }

        // Extract custom metadata from processed JSON
        Map<String, Object> customMetadata = new HashMap<>();
        if (processedJson.has("customMetadata")) {
            JsonNode customNode = processedJson.get("customMetadata");
            customNode.fields().forEachRemaining(entry -> {
                customMetadata.put(entry.getKey(), entry.getValue().asText());
            });
        }

        // Add document-level info as custom metadata
        if (document.getTotalChunks() != null) {
            customMetadata.put("totalChunks", document.getTotalChunks());
        }
        if (document.getTotalTokens() != null) {
            customMetadata.put("totalTokens", document.getTotalTokens());
        }
        if (document.getChunkStrategy() != null) {
            customMetadata.put("chunkStrategy", document.getChunkStrategy());
        }
        if (document.getChunkSize() != null) {
            customMetadata.put("chunkSize", document.getChunkSize());
        }

        metadata.setCustomMetadata(customMetadata);
    }

    private void applyMetadataUpdates(KnowledgeHubDocumentMetaData target, KnowledgeHubDocumentMetaData source) {
        target.setTitle(source.getTitle());
        target.setAuthor(source.getAuthor());
        target.setSubject(source.getSubject());
        target.setKeywords(source.getKeywords());
        target.setLanguage(source.getLanguage());
        target.setCreator(source.getCreator());
        target.setProducer(source.getProducer());
        target.setCreationDate(source.getCreationDate());
        target.setModificationDate(source.getModificationDate());
        target.setPageCount(source.getPageCount());
        target.setWordCount(source.getWordCount());
        target.setCharacterCount(source.getCharacterCount());
        target.setDocumentType(source.getDocumentType());
        target.setMimeType(source.getMimeType());
        target.setCustomMetadata(source.getCustomMetadata());
        target.setExtractionModel(source.getExtractionModel());
        target.setExtractionVersion(source.getExtractionVersion());
        target.setStatus(source.getStatus());
        target.setErrorMessage(source.getErrorMessage());
        target.setExtractedAt(source.getExtractedAt());
    }

    private String determineDocumentType(String contentType) {
        if (contentType == null) {
            return "UNKNOWN";
        }
        if (contentType.contains("pdf")) {
            return "PDF";
        } else if (contentType.contains("wordprocessingml") || contentType.contains("msword")) {
            return "DOCX";
        } else if (contentType.contains("text")) {
            return "TXT";
        } else if (contentType.contains("html")) {
            return "HTML";
        } else if (contentType.contains("json")) {
            return "JSON";
        } else {
            return "UNKNOWN";
        }
    }

    @Override
    public Mono<KnowledgeHubDocumentMetaData> getMetadataExtract(String documentId, String teamId) {
        return metadataRepository.findTopByDocumentIdAndTeamIdOrderByExtractedAtDesc(documentId, teamId)
                .flatMap(metadata -> {
                    // Decrypt sensitive metadata fields before returning
                    log.info("Retrieved metadata for document {} from MongoDB, decrypting fields...", documentId);
                    log.info("Metadata before decryption - Title: {}, Author: {}, encryptionKeyVersion: {}",
                            metadata.getTitle() != null
                                    ? metadata.getTitle().substring(0, Math.min(50, metadata.getTitle().length()))
                                    : "null",
                            metadata.getAuthor() != null
                                    ? metadata.getAuthor().substring(0, Math.min(50, metadata.getAuthor().length()))
                                    : "null",
                            metadata.getEncryptionKeyVersion());
                    return decryptMetadataFields(metadata, teamId)
                            .then(Mono.defer(() -> {
                                log.info("Metadata after decryption - Title: {}, Author: {}",
                                        metadata.getTitle() != null
                                                ? metadata.getTitle().substring(0,
                                                        Math.min(50, metadata.getTitle().length()))
                                                : "null",
                                        metadata.getAuthor() != null
                                                ? metadata.getAuthor().substring(0,
                                                        Math.min(50, metadata.getAuthor().length()))
                                                : "null");
                                return Mono.just(metadata);
                            }));
                })
                .switchIfEmpty(documentRepository.findByDocumentId(documentId)
                        .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                        .filter(doc -> Objects.equals(doc.getTeamId(), teamId))
                        .switchIfEmpty(Mono.error(new RuntimeException("Document access denied")))
                        .flatMap(doc -> {
                            log.info("Metadata not found for document {} — triggering extraction on demand.",
                                    documentId);
                            return extractMetadata(documentId, teamId)
                                    .flatMap(meta -> metadataRepository
                                            .findTopByDocumentIdAndTeamIdAndCollectionIdOrderByExtractedAtDesc(
                                                    documentId, teamId, doc.getCollectionId()))
                                    .flatMap(metadata -> {
                                        // Decrypt sensitive metadata fields before returning
                                        return decryptMetadataFields(metadata, teamId).thenReturn(metadata);
                                    })
                                    .switchIfEmpty(Mono.error(new RuntimeException(
                                            "Metadata extract not found for document: " + documentId)))
                                    .onErrorResume(err -> {
                                        log.error("On-demand metadata extraction failed for document {}: {}",
                                                documentId, err.getMessage());
                                        return Mono.error(new RuntimeException(
                                                "Metadata extract not found for document: " + documentId));
                                    });
                        }));
    }

    @Override
    public Mono<Void> deleteMetadataExtract(String documentId) {
        return metadataRepository.deleteByDocumentId(documentId)
                .doOnSuccess(v -> log.info("Deleted metadata extract for document: {}", documentId))
                .doOnError(error -> log.error("Error deleting metadata extract for document: {}", documentId, error))
                .then();
    }

    /**
     * Publish document status update via WebSocket
     */
    private void publishDocumentStatusUpdate(KnowledgeHubDocument document) {
        try {
            Map<String, Object> statusUpdate = new HashMap<>();
            statusUpdate.put("type", "DOCUMENT_STATUS_UPDATE");
            statusUpdate.put("documentId", document.getDocumentId());
            statusUpdate.put("status", document.getStatus() != null ? document.getStatus().name() : null);
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

    /**
     * Encrypt sensitive metadata fields before storing in MongoDB.
     * 
     * @param metadata             The metadata entity to encrypt
     * @param teamId               The team ID for key derivation
     * @param encryptionKeyVersion The encryption key version to use
     */
    /**
     * Encrypt sensitive metadata fields before storing in MongoDB.
     *
     * @param metadata             The metadata entity to encrypt
     * @param teamId               The team ID for key derivation
     * @param encryptionKeyVersion The encryption key version to use
     * @return Mono<Void> when encryption is complete
     */
    private Mono<Void> encryptMetadataFields(KnowledgeHubDocumentMetaData metadata, String teamId,
            String encryptionKeyVersion) {
        List<Mono<Void>> encryptionTasks = new ArrayList<>();

        // Encrypt sensitive string fields
        if (metadata.getTitle() != null && !metadata.getTitle().isEmpty()) {
            encryptionTasks
                    .add(chunkEncryptionService.encryptChunkText(metadata.getTitle(), teamId, encryptionKeyVersion)
                            .doOnNext(metadata::setTitle)
                            .then());
        }

        if (metadata.getAuthor() != null && !metadata.getAuthor().isEmpty()) {
            encryptionTasks
                    .add(chunkEncryptionService.encryptChunkText(metadata.getAuthor(), teamId, encryptionKeyVersion)
                            .doOnNext(metadata::setAuthor)
                            .then());
        }

        if (metadata.getSubject() != null && !metadata.getSubject().isEmpty()) {
            encryptionTasks
                    .add(chunkEncryptionService.encryptChunkText(metadata.getSubject(), teamId, encryptionKeyVersion)
                            .doOnNext(metadata::setSubject)
                            .then());
        }

        if (metadata.getKeywords() != null && !metadata.getKeywords().isEmpty()) {
            encryptionTasks
                    .add(chunkEncryptionService.encryptChunkText(metadata.getKeywords(), teamId, encryptionKeyVersion)
                            .doOnNext(metadata::setKeywords)
                            .then());
        }

        if (metadata.getCreator() != null && !metadata.getCreator().isEmpty()) {
            encryptionTasks
                    .add(chunkEncryptionService.encryptChunkText(metadata.getCreator(), teamId, encryptionKeyVersion)
                            .doOnNext(metadata::setCreator)
                            .then());
        }

        if (metadata.getProducer() != null && !metadata.getProducer().isEmpty()) {
            encryptionTasks
                    .add(chunkEncryptionService.encryptChunkText(metadata.getProducer(), teamId, encryptionKeyVersion)
                            .doOnNext(metadata::setProducer)
                            .then());
        }

        // Encrypt string values in customMetadata Map
        if (metadata.getCustomMetadata() != null && !metadata.getCustomMetadata().isEmpty()) {
            Map<String, Object> encryptedCustomMetadata = new HashMap<>(metadata.getCustomMetadata());
            for (Map.Entry<String, Object> entry : metadata.getCustomMetadata().entrySet()) {
                if (entry.getValue() instanceof String && !((String) entry.getValue()).isEmpty()) {
                    encryptionTasks.add(chunkEncryptionService
                            .encryptChunkText((String) entry.getValue(), teamId, encryptionKeyVersion)
                            .doOnNext(encrypted -> encryptedCustomMetadata.put(entry.getKey(), encrypted))
                            .then());
                }
            }
            metadata.setCustomMetadata(encryptedCustomMetadata);
        }

        return Flux.merge(encryptionTasks)
                .then()
                .onErrorResume(e -> {
                    log.warn(
                            "Failed to encrypt some metadata fields for team {}: {}. Some fields may remain unencrypted.",
                            teamId, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Decrypt sensitive metadata fields when retrieving from MongoDB.
     * 
     * @param metadata The metadata entity to decrypt
     * @param teamId   The team ID for key derivation
     */
    /**
     * Decrypt sensitive metadata fields when retrieving from MongoDB.
     *
     * @param metadata The metadata entity to decrypt
     * @param teamId   The team ID for key derivation
     * @return Mono<Void> when decryption is complete
     */
    private Mono<Void> decryptMetadataFields(KnowledgeHubDocumentMetaData metadata, String teamId) {
        if (metadata == null) {
            return Mono.empty();
        }

        String keyVersion = metadata.getEncryptionKeyVersion() != null && !metadata.getEncryptionKeyVersion().isEmpty()
                ? metadata.getEncryptionKeyVersion()
                : "v1";

        log.info("Decrypting metadata fields for document {} (team {}, key version {})",
                metadata.getDocumentId(), teamId, keyVersion);

        List<Mono<Void>> decryptionTasks = new ArrayList<>();

        if (metadata.getTitle() != null && !metadata.getTitle().isEmpty()) {
            decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getTitle(), teamId, keyVersion)
                    .doOnNext(metadata::setTitle)
                    .onErrorResume(e -> {
                        log.warn("Failed to decrypt title: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .then());
        }

        if (metadata.getAuthor() != null && !metadata.getAuthor().isEmpty()) {
            decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getAuthor(), teamId, keyVersion)
                    .doOnNext(metadata::setAuthor)
                    .onErrorResume(e -> {
                        log.warn("Failed to decrypt author: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .then());
        }

        if (metadata.getSubject() != null && !metadata.getSubject().isEmpty()) {
            decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getSubject(), teamId, keyVersion)
                    .doOnNext(metadata::setSubject)
                    .onErrorResume(e -> {
                        log.warn("Failed to decrypt subject: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .then());
        }

        if (metadata.getKeywords() != null && !metadata.getKeywords().isEmpty()) {
            decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getKeywords(), teamId, keyVersion)
                    .doOnNext(metadata::setKeywords)
                    .onErrorResume(e -> {
                        log.warn("Failed to decrypt keywords: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .then());
        }

        if (metadata.getCreator() != null && !metadata.getCreator().isEmpty()) {
            decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getCreator(), teamId, keyVersion)
                    .doOnNext(metadata::setCreator)
                    .onErrorResume(e -> {
                        log.warn("Failed to decrypt creator: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .then());
        }

        if (metadata.getProducer() != null && !metadata.getProducer().isEmpty()) {
            decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getProducer(), teamId, keyVersion)
                    .doOnNext(metadata::setProducer)
                    .onErrorResume(e -> {
                        log.warn("Failed to decrypt producer: {}", e.getMessage());
                        return Mono.empty();
                    })
                    .then());
        }

        // Decrypt string values in customMetadata Map
        if (metadata.getCustomMetadata() != null && !metadata.getCustomMetadata().isEmpty()) {
            Map<String, Object> decryptedCustomMetadata = new HashMap<>(metadata.getCustomMetadata());
            for (Map.Entry<String, Object> entry : metadata.getCustomMetadata().entrySet()) {
                if (entry.getValue() instanceof String && !((String) entry.getValue()).isEmpty()) {
                    decryptionTasks.add(chunkEncryptionService
                            .decryptChunkText((String) entry.getValue(), teamId, keyVersion)
                            .doOnNext(decrypted -> decryptedCustomMetadata.put(entry.getKey(), decrypted))
                            .onErrorResume(e -> {
                                log.debug("Failed to decrypt custom metadata field '{}': {}", entry.getKey(),
                                        e.getMessage());
                                return Mono.empty();
                            })
                            .then());
                }
            }
            metadata.setCustomMetadata(decryptedCustomMetadata);
        }

        return Flux.merge(decryptionTasks)
                .then()
                .onErrorResume(e -> {
                    log.warn(
                            "Failed to decrypt some metadata fields for team {} with key version {}: {}. Some fields may remain encrypted.",
                            teamId, keyVersion, e.getMessage());
                    return Mono.empty();
                });
    }

    /**
     * Decrypt sensitive fields in ProcessedDocumentDto after reading from S3.
     * Decrypts chunk text and metadata fields.
     *
     * @param processedDoc The processed document DTO to decrypt
     * @param teamId       The team ID for key derivation
     * @return Mono<Void> when decryption is complete
     */
    private Mono<Void> decryptProcessedDocumentDto(ProcessedDocumentDto processedDoc, String teamId) {
        if (processedDoc == null) {
            return Mono.empty();
        }

        String keyVersion = processedDoc.getEncryptionKeyVersion() != null
                && !processedDoc.getEncryptionKeyVersion().isEmpty()
                        ? processedDoc.getEncryptionKeyVersion()
                        : "v1";

        List<Mono<Void>> decryptionTasks = new ArrayList<>();

        // Decrypt chunk text
        if (processedDoc.getChunks() != null && !processedDoc.getChunks().isEmpty()) {
            for (ProcessedDocumentDto.ChunkDto chunk : processedDoc.getChunks()) {
                if (chunk.getText() != null && !chunk.getText().isEmpty()) {
                    decryptionTasks.add(chunkEncryptionService.decryptChunkText(chunk.getText(), teamId, keyVersion)
                            .doOnNext(chunk::setText)
                            .onErrorResume(e -> {
                                log.debug(
                                        "Failed to decrypt chunk text for team {} with key version {}: {}. Keeping encrypted value.",
                                        teamId, keyVersion, e.getMessage());
                                return Mono.empty();
                            })
                            .then());
                }
            }
        }

        // Decrypt sensitive metadata fields
        if (processedDoc.getExtractedMetadata() != null) {
            ProcessedDocumentDto.ExtractedMetadata metadata = processedDoc.getExtractedMetadata();

            if (metadata.getTitle() != null && !metadata.getTitle().isEmpty()) {
                decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getTitle(), teamId, keyVersion)
                        .doOnNext(metadata::setTitle)
                        .onErrorResume(e -> Mono.empty())
                        .then());
            }
            if (metadata.getAuthor() != null && !metadata.getAuthor().isEmpty()) {
                decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getAuthor(), teamId, keyVersion)
                        .doOnNext(metadata::setAuthor)
                        .onErrorResume(e -> Mono.empty())
                        .then());
            }
            if (metadata.getSubject() != null && !metadata.getSubject().isEmpty()) {
                decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getSubject(), teamId, keyVersion)
                        .doOnNext(metadata::setSubject)
                        .onErrorResume(e -> Mono.empty())
                        .then());
            }
            if (metadata.getKeywords() != null && !metadata.getKeywords().isEmpty()) {
                decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getKeywords(), teamId, keyVersion)
                        .doOnNext(metadata::setKeywords)
                        .onErrorResume(e -> Mono.empty())
                        .then());
            }
            if (metadata.getCreator() != null && !metadata.getCreator().isEmpty()) {
                decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getCreator(), teamId, keyVersion)
                        .doOnNext(metadata::setCreator)
                        .onErrorResume(e -> Mono.empty())
                        .then());
            }
            if (metadata.getProducer() != null && !metadata.getProducer().isEmpty()) {
                decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getProducer(), teamId, keyVersion)
                        .doOnNext(metadata::setProducer)
                        .onErrorResume(e -> Mono.empty())
                        .then());
            }
            if (metadata.getFormTitle() != null && !metadata.getFormTitle().isEmpty()) {
                decryptionTasks.add(chunkEncryptionService.decryptChunkText(metadata.getFormTitle(), teamId, keyVersion)
                        .doOnNext(metadata::setFormTitle)
                        .onErrorResume(e -> Mono.empty())
                        .then());
            }
            if (metadata.getFormNumber() != null && !metadata.getFormNumber().isEmpty()) {
                decryptionTasks
                        .add(chunkEncryptionService.decryptChunkText(metadata.getFormNumber(), teamId, keyVersion)
                                .doOnNext(metadata::setFormNumber)
                                .onErrorResume(e -> Mono.empty())
                                .then());
            }
        }

        // Decrypt form field values
        if (processedDoc.getFormFields() != null && !processedDoc.getFormFields().isEmpty()) {
            for (ProcessedDocumentDto.FormField field : processedDoc.getFormFields()) {
                if (field.getValue() != null && !field.getValue().isEmpty()) {
                    decryptionTasks.add(chunkEncryptionService.decryptChunkText(field.getValue(), teamId, keyVersion)
                            .doOnNext(field::setValue)
                            .onErrorResume(e -> {
                                log.debug(
                                        "Failed to decrypt form field value for team {}: {}. Keeping encrypted value.",
                                        teamId, e.getMessage());
                                return Mono.empty();
                            })
                            .then());
                }
            }
        }

        return Flux.merge(decryptionTasks)
                .then()
                .onErrorResume(e -> {
                    log.warn(
                            "Failed to decrypt some fields for team {} with key version {}: {}. Some fields may remain encrypted.",
                            teamId, keyVersion, e.getMessage());
                    return Mono.empty();
                });
    }
}
