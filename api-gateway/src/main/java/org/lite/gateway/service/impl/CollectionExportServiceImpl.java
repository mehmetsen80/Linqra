package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.CollectionExportProgressUpdate;
import org.lite.gateway.dto.ProcessedDocumentDto;
import org.lite.gateway.entity.CollectionExportJob;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.model.CollectionExportTask;
import org.lite.gateway.repository.CollectionExportJobRepository;
import org.lite.gateway.repository.KnowledgeHubCollectionRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.ChunkEncryptionService;
import org.lite.gateway.service.CollectionExportService;
import org.lite.gateway.service.Neo4jGraphService;
import org.lite.gateway.service.S3Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Slf4j
@Service
@RequiredArgsConstructor
public class CollectionExportServiceImpl implements CollectionExportService {
    
    private static final String QUEUE_KEY = "collection:export:queue";
    private static final int EXPORT_EXPIRY_HOURS = 24; // ZIP files expire after 24 hours
    
    private final CollectionExportJobRepository jobRepository;
    private final KnowledgeHubCollectionRepository collectionRepository;
    private final KnowledgeHubDocumentRepository documentRepository;
    private final S3Service s3Service;
    private final ChunkEncryptionService chunkEncryptionService;
    private final Neo4jGraphService neo4jGraphService;
    private final ReactiveRedisTemplate<String, String> asyncStepQueueRedisTemplate;
    private final ObjectMapper objectMapper;
    
    @Autowired(required = false)
    @Qualifier("collectionExportMessageChannel")
    private MessageChannel collectionExportMessageChannel;
    
    // Track cancellation flags for active jobs
    private final ConcurrentHashMap<String, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();
    
    @Override
    public Mono<CollectionExportJob> queueExport(List<String> collectionIds, String teamId, String exportedBy) {
        // Validate collections belong to team
        return Flux.fromIterable(collectionIds)
                .flatMap(collectionId -> collectionRepository.findByIdAndTeamId(collectionId, teamId)
                        .switchIfEmpty(Mono.error(new RuntimeException("Collection " + collectionId + " not found or not accessible for team " + teamId))))
                .collectList()
                .flatMap(collections -> {
                    if (collections.isEmpty()) {
                        return Mono.error(new RuntimeException("No valid collections found for export"));
                    }
                    
                    String jobId = UUID.randomUUID().toString();
                    
                    CollectionExportJob job = CollectionExportJob.builder()
                            .jobId(jobId)
                            .teamId(teamId)
                            .exportedBy(exportedBy)
                            .collectionIds(collectionIds)
                            .status("QUEUED")
                            .queuedAt(LocalDateTime.now())
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .format("zip")
                            .includeVectors(false)
                            .build();
                    
                    // Save job to MongoDB
                    return jobRepository.save(job)
                            .doOnSuccess(savedJob -> {
                                log.info("Created collection export job {} for team {} (collections: {})", 
                                        jobId, teamId, collectionIds);
                                // Publish QUEUED status via WebSocket
                                publishProgressUpdate(savedJob);
                            })
                            .flatMap(savedJob -> {
                                // Create task for queue
                                CollectionExportTask task = new CollectionExportTask(
                                        jobId,
                                        collectionIds,
                                        teamId,
                                        exportedBy
                                );
                                
                                try {
                                    String taskJson = objectMapper.writeValueAsString(task);
                                    // Add to Redis queue
                                    return asyncStepQueueRedisTemplate.opsForList()
                                            .rightPush(QUEUE_KEY, taskJson)
                                            .doOnSuccess(count -> log.info("Queued collection export job {} (queue size: {})", jobId, count))
                                            .thenReturn(savedJob);
                                } catch (Exception e) {
                                    log.error("Failed to serialize task for job {}: {}", jobId, e.getMessage(), e);
                                    // Update job status to FAILED
                                    savedJob.setStatus("FAILED");
                                    savedJob.setErrorMessage("Failed to queue job: " + e.getMessage());
                                    return jobRepository.save(savedJob);
                                }
                            });
                });
    }
    
    @Override
    public Mono<CollectionExportJob> getExportJobStatus(String jobId, String teamId) {
        return jobRepository.findByJobId(jobId)
                .filter(job -> job.getTeamId().equals(teamId))
                .switchIfEmpty(Mono.error(new RuntimeException("Export job not found or access denied")));
    }
    
    @Override
    public Flux<CollectionExportJob> getExportJobsForTeam(String teamId) {
        return jobRepository.findByTeamIdOrderByCreatedAtDesc(teamId);
    }
    
    @Override
    public Mono<Boolean> cancelExportJob(String jobId, String teamId) {
        return jobRepository.findByJobId(jobId)
                .filter(job -> job.getTeamId().equals(teamId))
                .switchIfEmpty(Mono.error(new RuntimeException("Export job not found or access denied")))
                .flatMap(job -> {
                    if ("COMPLETED".equals(job.getStatus()) || "FAILED".equals(job.getStatus()) || "CANCELLED".equals(job.getStatus())) {
                        return Mono.just(false); // Cannot cancel completed/failed/cancelled jobs
                    }
                    
                    // Set cancellation flag
                    cancellationFlags.put(jobId, new AtomicBoolean(true));
                    
                    // Update job status
                    job.setStatus("CANCELLED");
                    job.setUpdatedAt(LocalDateTime.now());
                    return jobRepository.save(job)
                            .doOnSuccess(savedJob -> {
                                log.info("Cancelled export job: {}", jobId);
                                publishProgressUpdate(savedJob);
                            })
                            .thenReturn(true);
                });
    }
    
    @Scheduled(fixedDelay = 5000) // Poll every 5 seconds
    public void processQueue() {
        asyncStepQueueRedisTemplate.opsForList().leftPop(QUEUE_KEY)
                .doOnSubscribe(s -> log.debug("Checking collection export queue..."))
                .doOnNext(message -> log.info("Found collection export job in queue: {}", message))
                .flatMap(message -> {
                    try {
                        CollectionExportTask task = objectMapper.readValue(message, CollectionExportTask.class);
                        log.info("Processing collection export job {} for team {} (collections: {})", 
                                task.getJobId(), task.getTeamId(), task.getCollectionIds());
                        return processExportJob(task);
                    } catch (Exception e) {
                        log.error("Failed to process queued task: {}", e.getMessage(), e);
                        return Mono.error(e);
                    }
                })
                .doOnError(error -> log.error("Error processing collection export queue: {}", error.getMessage(), error))
                .subscribe(
                        null,
                        error -> log.error("Error in collection export queue subscription: {}", error.getMessage(), error)
                );
    }
    
    private Mono<Void> processExportJob(CollectionExportTask task) {
        String jobId = task.getJobId();
        cancellationFlags.put(jobId, new AtomicBoolean(false));
        
        return jobRepository.findByJobId(jobId)
                .switchIfEmpty(Mono.error(new RuntimeException("Job not found: " + jobId)))
                .flatMap(job -> {
                    // Update job status to RUNNING
                    job.setStatus("RUNNING");
                    job.setStartedAt(LocalDateTime.now());
                    job.setUpdatedAt(LocalDateTime.now());
                    
                    return jobRepository.save(job)
                            .doOnSuccess(savedJob -> {
                                log.info("Started processing export job: {}", jobId);
                                publishProgressUpdate(savedJob);
                            })
                            .flatMap(savedJob -> {
                                // Collect documents grouped by collection
                                return collectDocumentsByCollection(task.getCollectionIds(), task.getTeamId())
                                        .flatMap(collectionDocumentsMap -> {
                                            if (collectionDocumentsMap.isEmpty()) {
                                                log.warn("No documents found for export job: {}", jobId);
                                                return updateJobStatusWithResults(jobId, "COMPLETED", new ArrayList<>(), null);
                                            }
                                            
                                            // Calculate totals
                                            int totalDocuments = collectionDocumentsMap.values().stream()
                                                    .mapToInt(List::size)
                                                    .sum();
                                            int totalFiles = totalDocuments * 2; // Each document has raw file + processed JSON
                                            
                                            savedJob.setTotalDocuments(totalDocuments);
                                            savedJob.setTotalFiles(totalFiles);
                                            
                                            return jobRepository.save(savedJob)
                                                    .then(processCollectionExports(jobId, collectionDocumentsMap, task.getCollectionIds(), task.getTeamId(), totalDocuments, totalFiles))
                                                    .flatMap(exportResults -> {
                                                        // Check if cancelled
                                                        AtomicBoolean cancelled = cancellationFlags.get(jobId);
                                                        if (cancelled != null && cancelled.get()) {
                                                            log.info("Export job {} was cancelled", jobId);
                                                            return updateJobStatusWithResults(jobId, "CANCELLED", exportResults, null);
                                                        }
                                                        
                                                        // Update job to COMPLETED
                                                        return updateJobStatusWithResults(jobId, "COMPLETED", exportResults, null);
                                                    })
                                                    .onErrorResume(error -> {
                                                        log.error("Error processing export job {}: {}", jobId, error.getMessage(), error);
                                                        return updateJobStatusWithResults(jobId, "FAILED", new ArrayList<>(), error.getMessage());
                                                    });
                                        })
                                        .then();
                            });
                })
                .doFinally(signalType -> {
                    // Clean up cancellation flag
                    cancellationFlags.remove(jobId);
                })
                .then();
    }
    
    private Mono<Map<String, List<KnowledgeHubDocument>>> collectDocumentsByCollection(List<String> collectionIds, String teamId) {
        return Flux.fromIterable(collectionIds)
                .flatMap(collectionId -> 
                    documentRepository.findByTeamIdAndCollectionId(teamId, collectionId)
                            .collectList()
                            .map(documents -> Map.entry(collectionId, documents))
                )
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .map(collectionDocumentsMap -> {
                    int totalDocs = collectionDocumentsMap.values().stream().mapToInt(List::size).sum();
                    log.info("Collected {} documents from {} collections for export", totalDocs, collectionIds.size());
                    return collectionDocumentsMap;
                });
    }
    
    private Mono<List<CollectionExportJob.CollectionExportResult>> processCollectionExports(
            String jobId, 
            Map<String, List<KnowledgeHubDocument>> collectionDocumentsMap,
            List<String> collectionIds,
            String teamId,
            int totalDocuments,
            int totalFiles) {
        // Use concatMap to process collections sequentially for proper progress tracking
        AtomicInteger processedCollections = new AtomicInteger(0);
        return Flux.fromIterable(collectionIds)
                .concatMap(collectionId -> {
                    List<KnowledgeHubDocument> documents = collectionDocumentsMap.getOrDefault(collectionId, new ArrayList<>());
                    
                    if (documents.isEmpty()) {
                        log.warn("No documents found for collection {} in export job {}", collectionId, jobId);
                        // Still create an empty ZIP or skip? Let's skip empty collections
                        return Mono.empty();
                    }
                    
                    // Get collection name for ZIP file naming
                    return collectionRepository.findByIdAndTeamId(collectionId, teamId)
                            .map(org.lite.gateway.entity.KnowledgeHubCollection::getName)
                            .switchIfEmpty(Mono.just(collectionId))
                            .flatMap(collectionName -> {
                                // Create ZIP for this collection
                                return createExportZip(jobId, collectionId, collectionName, documents, teamId)
                                        .flatMap(zipBytes -> {
                                            // Upload ZIP to S3
                                            String sanitizedCollectionName = sanitizeFileName(collectionName);
                                            String s3Key = String.format("exports/%s/%s/%s_%s.zip", 
                                                    teamId, jobId, collectionId, sanitizedCollectionName);
                                            
                                            return uploadExportZip(s3Key, zipBytes, teamId)
                                                    .flatMap(s3KeyUploaded -> {
                                                        // Generate presigned URL
                                                        return s3Service.generatePresignedDownloadUrl(s3KeyUploaded)
                                                                .map(downloadUrl -> {
                                                                    LocalDateTime expiresAt = LocalDateTime.now().plusHours(EXPORT_EXPIRY_HOURS);
                                                                    
                                                                    // Create export result for this collection
                                                                    return CollectionExportJob.CollectionExportResult.builder()
                                                                            .collectionId(collectionId)
                                                                            .collectionName(collectionName)
                                                                            .s3Key(s3KeyUploaded)
                                                                            .downloadUrl(downloadUrl)
                                                                            .fileSizeBytes((long) zipBytes.length)
                                                                            .documentCount(documents.size())
                                                                            .expiresAt(expiresAt)
                                                                            .build();
                                                                });
                                                    });
                                        })
                                        .doOnSuccess(result -> {
                                            // Update progress after each collection is processed
                                            int collectionsProcessed = processedCollections.incrementAndGet();
                                            // Calculate cumulative progress (rough estimate based on collections)
                                            int estimatedProcessedDocs = (int) ((double) collectionsProcessed / collectionIds.size() * totalDocuments);
                                            int estimatedProcessedFiles = estimatedProcessedDocs * 2;
                                            updateExportProgress(jobId, estimatedProcessedDocs, totalDocuments, estimatedProcessedFiles, totalFiles).subscribe();
                                        })
                                        .onErrorResume(error -> {
                                            log.error("Error exporting collection {} in job {}: {}", collectionId, jobId, error.getMessage(), error);
                                            // Still increment counter even on error
                                            processedCollections.incrementAndGet();
                                            // Return error result but continue with other collections
                                            // Use collectionId as fallback name since we don't have collection object in error handler
                                            return Mono.just(CollectionExportJob.CollectionExportResult.builder()
                                                    .collectionId(collectionId)
                                                    .collectionName(collectionId)
                                                    .documentCount(documents.size())
                                                    .build());
                                        });
                            });
                })
                .collectList()
                .doOnSuccess(results -> {
                    log.info("Completed exporting {} collections for job {}", results.size(), jobId);
                });
    }
    
    private Mono<byte[]> createExportZip(String jobId, String collectionId, String collectionName, 
                                         List<KnowledgeHubDocument> documents, String teamId) {
        return Mono.fromCallable(() -> {
            log.info("Creating export ZIP for collection {} (job {}) with {} documents", collectionName, jobId, documents.size());
            
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ZipOutputStream zos = new ZipOutputStream(baos);
            
            try {
                int processedDocs = 0;
                int processedFiles = 0;
                
                for (KnowledgeHubDocument document : documents) {
                    // Check cancellation
                    AtomicBoolean cancelled = cancellationFlags.get(jobId);
                    if (cancelled != null && cancelled.get()) {
                        log.info("Export job {} cancelled during ZIP creation", jobId);
                        throw new RuntimeException("Export cancelled");
                    }
                    
                    try {
                        // Add raw file to ZIP
                        if (document.getS3Key() != null && !document.getS3Key().isEmpty()) {
                            byte[] fileBytes = downloadAndDecryptFile(document, teamId).block(); // Blocking call in reactive context
                            if (fileBytes != null && fileBytes.length > 0) {
                                String fileName = "documents/" + document.getDocumentId() + "/" + sanitizeFileName(document.getFileName());
                                addFileToZip(zos, fileName, fileBytes);
                                processedFiles++;
                            }
                        }
                        
                        // Add processed JSON to ZIP
                        if (document.getProcessedS3Key() != null && !document.getProcessedS3Key().isEmpty()) {
                            byte[] processedJsonBytes = downloadAndDecryptProcessedJson(document, teamId).block(); // Blocking call in reactive context
                            if (processedJsonBytes != null && processedJsonBytes.length > 0) {
                                String jsonFileName = "documents/" + document.getDocumentId() + "/" + sanitizeFileName(
                                        document.getFileName().replaceAll("\\.[^.]+$", "") + "_processed.json");
                                addFileToZip(zos, jsonFileName, processedJsonBytes);
                                processedFiles++;
                            }
                        }
                        
                        // Add Knowledge Graph entities to ZIP
                        Map<String, Object> graphData = exportDocumentGraphData(document.getDocumentId(), teamId);
                        if (!graphData.isEmpty()) {
                            String graphFileName = "documents/" + document.getDocumentId() + "/knowledge_graph.json";
                            byte[] graphJsonBytes = objectMapper.writerWithDefaultPrettyPrinter()
                                    .writeValueAsBytes(graphData);
                            addFileToZip(zos, graphFileName, graphJsonBytes);
                            processedFiles++;
                        }
                        
                        processedDocs++;
                        
                    } catch (Exception e) {
                        log.error("Error adding document {} to export ZIP: {}", document.getDocumentId(), e.getMessage(), e);
                        // Continue with next document
                    }
                }
                
                zos.finish();
                zos.close();
                
                byte[] zipBytes = baos.toByteArray();
                log.info("Created export ZIP for job {}: {} bytes ({} documents, {} files)", 
                        jobId, zipBytes.length, processedDocs, processedFiles);
                
                return zipBytes;
                
            } catch (IOException e) {
                log.error("Error creating export ZIP for job {}: {}", jobId, e.getMessage(), e);
                try {
                    zos.close();
                } catch (IOException closeError) {
                    log.error("Error closing ZIP stream: {}", closeError.getMessage());
                }
                throw new RuntimeException("Failed to create export ZIP: " + e.getMessage(), e);
            }
        })
        .doOnError(error -> log.error("Error in ZIP creation for job {}: {}", jobId, error.getMessage(), error));
    }
    
    private Mono<byte[]> downloadAndDecryptFile(KnowledgeHubDocument document, String teamId) {
        return s3Service.downloadFileContent(document.getS3Key())
                .map(encryptedBytes -> {
                    try {
                        byte[] fileBytes = encryptedBytes;
                        
                        // Decrypt if file is encrypted
                        if (document.getEncrypted() != null && document.getEncrypted() 
                                && document.getEncryptionKeyVersion() != null && !document.getEncryptionKeyVersion().isEmpty()) {
                            log.debug("Decrypting file for document: {} (key version: {})", 
                                    document.getDocumentId(), document.getEncryptionKeyVersion());
                            fileBytes = chunkEncryptionService.decryptFile(
                                    encryptedBytes,
                                    teamId,
                                    document.getEncryptionKeyVersion()
                            );
                        }
                        
                        return fileBytes;
                    } catch (Exception e) {
                        log.error("Failed to decrypt file for document {}: {}", document.getDocumentId(), e.getMessage(), e);
                        throw new RuntimeException("Failed to decrypt file: " + e.getMessage(), e);
                    }
                });
    }
    
    private Mono<byte[]> downloadAndDecryptProcessedJson(KnowledgeHubDocument document, String teamId) {
        return s3Service.downloadFileContent(document.getProcessedS3Key())
                .map(bytes -> {
                    try {
                        ProcessedDocumentDto processedDoc = objectMapper.readValue(
                                new String(bytes, StandardCharsets.UTF_8), 
                                ProcessedDocumentDto.class
                        );
                        
                        // Decrypt sensitive fields in processed document
                        decryptProcessedDocumentDto(processedDoc, teamId);
                        
                        // Convert back to JSON
                        return objectMapper.writerWithDefaultPrettyPrinter()
                                .writeValueAsString(processedDoc)
                                .getBytes(StandardCharsets.UTF_8);
                    } catch (Exception e) {
                        log.error("Failed to parse and decrypt processed document JSON for document {}: {}", 
                                document.getDocumentId(), e.getMessage(), e);
                        throw new RuntimeException("Failed to parse processed document: " + e.getMessage(), e);
                    }
                });
    }
    
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
                            log.debug("Failed to decrypt chunk text: {}. Keeping encrypted value.", e.getMessage());
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
                    log.debug("Failed to decrypt metadata fields: {}", e.getMessage());
                }
            }
            
            // Decrypt form field values
            if (processedDoc.getFormFields() != null && !processedDoc.getFormFields().isEmpty()) {
                for (ProcessedDocumentDto.FormField field : processedDoc.getFormFields()) {
                    if (field.getValue() != null && !field.getValue().isEmpty()) {
                        try {
                            field.setValue(chunkEncryptionService.decryptChunkText(field.getValue(), teamId, keyVersion));
                        } catch (Exception e) {
                            log.debug("Failed to decrypt form field value: {}", e.getMessage());
                        }
                    }
                }
            }
            
        } catch (Exception e) {
            log.warn("Failed to decrypt some fields in processed document DTO: {}", e.getMessage());
        }
    }
    
    private void addFileToZip(ZipOutputStream zos, String fileName, byte[] fileBytes) throws IOException {
        ZipEntry entry = new ZipEntry(fileName);
        entry.setSize(fileBytes.length);
        zos.putNextEntry(entry);
        zos.write(fileBytes);
        zos.closeEntry();
    }
    
    private String sanitizeFileName(String fileName) {
        // Replace problematic characters in file names
        return fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
    
    /**
     * Export Knowledge Graph entities and relationships for a document
     * This method fetches all entities and relationships, decrypts them, and returns structured data
     */
    private Map<String, Object> exportDocumentGraphData(String documentId, String teamId) {
        Map<String, Object> graphData = new HashMap<>();
        
        try {
            // Fetch all entities for this document (all types)
            List<String> entityTypes = List.of("Form", "Organization", "Person", "Date", "Location", "Document");
            List<Map<String, Object>> allEntities = new ArrayList<>();
            
            for (String entityType : entityTypes) {
                List<Map<String, Object>> entities = neo4jGraphService.findEntities(
                        entityType, 
                        Map.of("documentId", documentId), 
                        teamId
                ).collectList().block(); // Blocking call in reactive context
                
                if (entities != null && !entities.isEmpty()) {
                    // Decrypt properties for each entity
                    for (Map<String, Object> entity : entities) {
                        Map<String, Object> decryptedEntity = neo4jGraphService.decryptProperties(entity, teamId).block();
                        if (decryptedEntity != null) {
                            allEntities.add(decryptedEntity);
                        }
                    }
                }
            }
            
            graphData.put("entities", allEntities);
            
            // Fetch all relationships for this document
            String relationshipQuery = "MATCH (from)-[r]->(to) " +
                    "WHERE r.documentId = $documentId AND from.teamId = $teamId AND to.teamId = $teamId " +
                    "RETURN type(r) as relationshipType, " +
                    "from.id as fromId, labels(from)[0] as fromType, properties(from) as fromProps, " +
                    "to.id as toId, labels(to)[0] as toType, properties(to) as toProps, " +
                    "properties(r) as relProps " +
                    "ORDER BY relationshipType";
            
            Map<String, Object> params = new HashMap<>();
            params.put("documentId", documentId);
            params.put("teamId", teamId);
            
            List<Map<String, Object>> relationships = neo4jGraphService.executeQuery(relationshipQuery, params)
                    .collectList().block(); // Blocking call in reactive context
            
            if (relationships != null && !relationships.isEmpty()) {
                // Decrypt properties for relationships
                List<Map<String, Object>> decryptedRelationships = new ArrayList<>();
                for (Map<String, Object> rel : relationships) {
                    Map<String, Object> decryptedRel = new HashMap<>(rel);
                    
                    // Decrypt from entity properties
                    if (rel.containsKey("fromProps") && rel.get("fromProps") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> fromProps = (Map<String, Object>) rel.get("fromProps");
                        Map<String, Object> decryptedFromProps = neo4jGraphService.decryptProperties(fromProps, teamId).block();
                        if (decryptedFromProps != null) {
                            decryptedRel.put("fromProperties", decryptedFromProps);
                        }
                    }
                    
                    // Decrypt to entity properties
                    if (rel.containsKey("toProps") && rel.get("toProps") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> toProps = (Map<String, Object>) rel.get("toProps");
                        Map<String, Object> decryptedToProps = neo4jGraphService.decryptProperties(toProps, teamId).block();
                        if (decryptedToProps != null) {
                            decryptedRel.put("toProperties", decryptedToProps);
                        }
                    }
                    
                    // Decrypt relationship properties
                    if (rel.containsKey("relProps") && rel.get("relProps") instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> relProps = (Map<String, Object>) rel.get("relProps");
                        Map<String, Object> decryptedRelProps = neo4jGraphService.decryptProperties(relProps, teamId).block();
                        if (decryptedRelProps != null) {
                            decryptedRel.put("relationshipProperties", decryptedRelProps);
                        }
                    }
                    
                    // Remove original encrypted props
                    decryptedRel.remove("fromProps");
                    decryptedRel.remove("toProps");
                    decryptedRel.remove("relProps");
                    
                    decryptedRelationships.add(decryptedRel);
                }
                
                graphData.put("relationships", decryptedRelationships);
            } else {
                graphData.put("relationships", new ArrayList<>());
            }
            
            log.debug("Exported graph data for document {}: {} entities, {} relationships", 
                    documentId, allEntities.size(), 
                    relationships != null ? relationships.size() : 0);
            
        } catch (Exception e) {
            log.error("Error exporting graph data for document {}: {}", documentId, e.getMessage(), e);
            // Return empty structure instead of failing
            graphData.put("entities", new ArrayList<>());
            graphData.put("relationships", new ArrayList<>());
        }
        
        return graphData;
    }
    
    private Mono<String> uploadExportZip(String s3Key, byte[] zipBytes, String teamId) {
        return s3Service.uploadFileBytes(s3Key, zipBytes, "application/zip", null)
                .thenReturn(s3Key)
                .doOnSuccess(key -> log.info("Uploaded export ZIP to S3: {} ({} bytes)", key, zipBytes.length));
    }
    
    private Mono<Void> updateJobStatusWithResults(String jobId, String status, 
                                                   List<CollectionExportJob.CollectionExportResult> exportResults,
                                                   String errorMessage) {
        return jobRepository.findByJobId(jobId)
                .flatMap(job -> {
                    job.setStatus(status);
                    job.setUpdatedAt(LocalDateTime.now());
                    
                    if (exportResults != null && !exportResults.isEmpty()) {
                        // Calculate totals from export results
                        int totalDocs = exportResults.stream()
                                .mapToInt(result -> result.getDocumentCount() != null ? result.getDocumentCount() : 0)
                                .sum();
                        int totalFiles = totalDocs * 2; // Each document has raw file + processed JSON
                        
                        job.setProcessedDocuments(totalDocs);
                        job.setTotalDocuments(totalDocs);
                        job.setProcessedFiles(totalFiles);
                        job.setTotalFiles(totalFiles);
                        job.setExportResults(exportResults);
                    }
                    
                    if ("COMPLETED".equals(status)) {
                        job.setCompletedAt(LocalDateTime.now());
                    } else if ("FAILED".equals(status)) {
                        if (errorMessage != null) {
                            job.setErrorMessage(errorMessage);
                        }
                    } else if ("CANCELLED".equals(status)) {
                        // Cancelled status already set
                    }
                    
                    return jobRepository.save(job)
                            .doOnSuccess(savedJob -> {
                                log.info("Updated export job {} status to {} ({} collections exported)", 
                                        jobId, status, exportResults != null ? exportResults.size() : 0);
                                publishProgressUpdate(savedJob);
                            })
                            .then();
                })
                .onErrorResume(error -> {
                    log.error("Error updating export job status for job {}: {}", jobId, error.getMessage(), error);
                    return Mono.empty();
                });
    }
    
    @Override
    public Mono<Void> updateExportProgress(String jobId, Integer processedDocuments, Integer totalDocuments,
                                           Integer processedFiles, Integer totalFiles) {
        return jobRepository.findByJobId(jobId)
                .flatMap(job -> {
                    if (processedDocuments != null) {
                        job.setProcessedDocuments(processedDocuments);
                    }
                    if (totalDocuments != null) {
                        job.setTotalDocuments(totalDocuments);
                    }
                    if (processedFiles != null) {
                        job.setProcessedFiles(processedFiles);
                    }
                    if (totalFiles != null) {
                        job.setTotalFiles(totalFiles);
                    }
                    job.setUpdatedAt(LocalDateTime.now());
                    
                    return jobRepository.save(job)
                            .doOnSuccess(savedJob -> {
                                log.debug("Updated export job {} progress: documents {}/{}, files {}/{}", 
                                        jobId, processedDocuments, totalDocuments, processedFiles, totalFiles);
                                publishProgressUpdate(savedJob);
                            })
                            .then();
                })
                .onErrorResume(error -> {
                    log.error("Error updating export progress for job {}: {}", jobId, error.getMessage(), error);
                    return Mono.empty();
                });
    }
    
    /**
     * Publish export job progress update via WebSocket
     */
    private void publishProgressUpdate(CollectionExportJob job) {
        try {
            if (collectionExportMessageChannel == null) {
                log.debug("Collection export message channel not available, skipping update for job: {}", job.getJobId());
                return;
            }
            
            // Map export results to DTO
            List<CollectionExportProgressUpdate.ExportResult> exportResultsDto = null;
            if (job.getExportResults() != null && !job.getExportResults().isEmpty()) {
                exportResultsDto = job.getExportResults().stream()
                        .map(result -> CollectionExportProgressUpdate.ExportResult.builder()
                                .collectionId(result.getCollectionId())
                                .collectionName(result.getCollectionName())
                                .downloadUrl(result.getDownloadUrl())
                                .fileSizeBytes(result.getFileSizeBytes())
                                .documentCount(result.getDocumentCount())
                                .expiresAt(result.getExpiresAt())
                                .build())
                        .toList();
            }
            
            CollectionExportProgressUpdate update = CollectionExportProgressUpdate.builder()
                    .jobId(job.getJobId())
                    .teamId(job.getTeamId())
                    .exportedBy(job.getExportedBy())
                    .status(job.getStatus())
                    .totalDocuments(job.getTotalDocuments())
                    .processedDocuments(job.getProcessedDocuments())
                    .totalFiles(job.getTotalFiles())
                    .processedFiles(job.getProcessedFiles())
                    .exportResults(exportResultsDto)
                    .errorMessage(job.getErrorMessage())
                    .queuedAt(job.getQueuedAt())
                    .startedAt(job.getStartedAt())
                    .completedAt(job.getCompletedAt())
                    .timestamp(LocalDateTime.now())
                    .build();
            
            boolean sent = collectionExportMessageChannel.send(MessageBuilder.withPayload(update).build());
            if (sent) {
                log.debug("📦 Published collection export progress update via WebSocket for job: {}", job.getJobId());
            } else {
                log.warn("📦 Failed to publish collection export progress update via WebSocket for job: {}", job.getJobId());
            }
        } catch (Exception e) {
            log.error("📦 Error publishing collection export progress update via WebSocket for job: {} - {}", 
                    job.getJobId(), e.getMessage(), e);
        }
    }
}

