package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.KnowledgeHubDocumentMetaData;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.repository.KnowledgeHubDocumentMetaDataRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.KnowledgeHubDocumentMetaDataService;
import org.lite.gateway.service.S3Service;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
    
    public KnowledgeHubDocumentMetaDataServiceImpl(
            KnowledgeHubDocumentMetaDataRepository metadataRepository,
            KnowledgeHubDocumentRepository documentRepository,
            S3Service s3Service,
            ObjectMapper objectMapper,
            @Qualifier("executionMessageChannel") MessageChannel executionMessageChannel) {
        this.metadataRepository = metadataRepository;
        this.documentRepository = documentRepository;
        this.s3Service = s3Service;
        this.objectMapper = objectMapper;
        this.executionMessageChannel = executionMessageChannel;
    }
    
    @Override
    public Mono<KnowledgeHubDocumentMetaData> extractMetadata(String documentId, String teamId) {
        log.info("Extracting metadata for document: {}", documentId);
        
        return documentRepository.findByDocumentId(documentId)
                .switchIfEmpty(Mono.error(new RuntimeException("Document not found: " + documentId)))
                .filter(document -> document.getTeamId().equals(teamId))
                .switchIfEmpty(Mono.error(new RuntimeException("Document access denied")))
                .filter(document -> document.getProcessedS3Key() != null && !document.getProcessedS3Key().isEmpty())
                .switchIfEmpty(Mono.error(new RuntimeException("Document has no processed JSON. Please process the document first.")))
                .flatMap(document -> {
                    // Update document status to METADATA_EXTRACTION first (before extraction starts)
                    document.setStatus("METADATA_EXTRACTION");
                    return documentRepository.save(document)
                            .doOnSuccess(savedDoc -> {
                                log.info("Updated document status to METADATA_EXTRACTION for document: {}", documentId);
                                publishDocumentStatusUpdate(savedDoc);
                            })
                            .flatMap(savedDoc -> {
                                // Check if metadata already exists
                                return metadataRepository.findByDocumentId(documentId)
                                        .flatMap(existing -> {
                                            log.info("Metadata already exists for document: {}, updating...", documentId);
                                            return updateMetadataFromProcessedJson(existing, savedDoc);
                                        })
                                        .switchIfEmpty(
                                                // Create new metadata extract
                                                createMetadataFromProcessedJson(savedDoc)
                                        )
                                        .flatMap(metadata -> {
                                            // After successful extraction, publish status update again
                                            // (status remains METADATA_EXTRACTION, but metadata is now available)
                                            publishDocumentStatusUpdate(savedDoc);
                                            return Mono.just(metadata);
                                        });
                            });
                })
                .doOnSuccess(metadata -> log.info("Successfully extracted metadata for document: {}", documentId))
                .doOnError(error -> {
                    log.error("Error extracting metadata for document: {}", documentId, error);
                    // Update document status to FAILED if extraction fails
                    documentRepository.findByDocumentId(documentId)
                            .flatMap(document -> {
                                document.setStatus("FAILED");
                                document.setErrorMessage("Metadata extraction failed: " + error.getMessage());
                                return documentRepository.save(document)
                                        .doOnSuccess(savedDoc -> publishDocumentStatusUpdate(savedDoc));
                            })
                            .subscribe();
                });
    }
    
    private Mono<KnowledgeHubDocumentMetaData> createMetadataFromProcessedJson(KnowledgeHubDocument document) {
        return s3Service.downloadFileContent(document.getProcessedS3Key())
                .flatMap(jsonBytes -> {
                    try {
                        String jsonString = new String(jsonBytes);
                        JsonNode processedJson = objectMapper.readTree(jsonString);
                        
                        KnowledgeHubDocumentMetaData metadata = KnowledgeHubDocumentMetaData.builder()
                                .documentId(document.getDocumentId())
                                .teamId(document.getTeamId())
                                .collectionId(document.getCollectionId())
                                .extractedAt(LocalDateTime.now())
                                .status("EXTRACTED")
                                .extractionModel(EXTRACTION_MODEL)
                                .extractionVersion(EXTRACTION_VERSION)
                                .build();
                        
                        // Extract metadata from processed JSON
                        extractMetadataFromJson(metadata, processedJson, document);
                        
                        return metadataRepository.save(metadata);
                    } catch (Exception e) {
                        log.error("Error parsing processed JSON for document: {}", document.getDocumentId(), e);
                        return Mono.error(new RuntimeException("Failed to parse processed JSON: " + e.getMessage(), e));
                    }
                });
    }
    
    private Mono<KnowledgeHubDocumentMetaData> updateMetadataFromProcessedJson(
            KnowledgeHubDocumentMetaData existing, KnowledgeHubDocument document) {
        return s3Service.downloadFileContent(document.getProcessedS3Key())
                .flatMap(jsonBytes -> {
                    try {
                        String jsonString = new String(jsonBytes);
                        JsonNode processedJson = objectMapper.readTree(jsonString);
                        
                        // Update existing metadata
                        extractMetadataFromJson(existing, processedJson, document);
                        existing.setExtractedAt(LocalDateTime.now());
                        existing.setStatus("EXTRACTED");
                        
                        return metadataRepository.save(existing);
                    } catch (Exception e) {
                        log.error("Error parsing processed JSON for document: {}", document.getDocumentId(), e);
                        return Mono.error(new RuntimeException("Failed to parse processed JSON: " + e.getMessage(), e));
                    }
                });
    }
    
    private void extractMetadataFromJson(KnowledgeHubDocumentMetaData metadata, JsonNode processedJson, KnowledgeHubDocument document) {
        // Extract document-level metadata
        if (processedJson.has("documentId")) {
            // Already set from document
        }
        
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
            log.info("ExtractedMetadata section keys: {}", extractedKeyList.isEmpty() ? "none" : String.join(", ", extractedKeyList));
            
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
            log.warn("No 'extractedMetadata' section found in processed JSON for document: {}", document.getDocumentId());
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
        return metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                .switchIfEmpty(Mono.error(new RuntimeException("Metadata extract not found for document: " + documentId)));
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

