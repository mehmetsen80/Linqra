package org.lite.gateway.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.GraphExtractionProgressUpdate;
import org.lite.gateway.entity.GraphExtractionJob;
import org.lite.gateway.model.GraphExtractionTask;
import org.lite.gateway.repository.GraphExtractionJobRepository;
import org.lite.gateway.repository.KnowledgeHubDocumentMetaDataRepository;
import org.lite.gateway.service.GraphExtractionJobService;
import org.lite.gateway.service.KnowledgeHubGraphEntityExtractionService;
import org.lite.gateway.service.KnowledgeHubGraphRelationshipExtractionService;
import org.lite.gateway.service.Neo4jGraphService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.lite.gateway.service.CacheService;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class GraphExtractionJobServiceImpl implements GraphExtractionJobService {

    private static final String QUEUE_KEY = "graph:extraction:queue";

    private final GraphExtractionJobRepository jobRepository;
    private final KnowledgeHubGraphEntityExtractionService entityExtractionService;
    private final KnowledgeHubGraphRelationshipExtractionService relationshipExtractionService;
    private final CacheService cacheService;
    private final ObjectMapper objectMapper;
    private final KnowledgeHubDocumentMetaDataRepository metadataRepository;
    private final Neo4jGraphService graphService;

    @Qualifier("graphExtractionMessageChannel")
    private final MessageChannel graphExtractionMessageChannel;

    // Track cancellation flags for active jobs
    private final ConcurrentHashMap<String, AtomicBoolean> cancellationFlags = new ConcurrentHashMap<>();

    @Override
    public Mono<GraphExtractionJob> queueExtraction(String documentId, String teamId, String extractionType,
            boolean force) {
        // Check idempotency BEFORE queueing if force=false
        if (!force) {
            Mono<String> idempotencyCheck;

            if ("entities".equals(extractionType)) {
                // Check if entities are already extracted
                idempotencyCheck = checkEntitiesAlreadyExtracted(documentId, teamId)
                        .flatMap(alreadyExtracted -> {
                            if (alreadyExtracted) {
                                return getPreviousEntityExtractionCost(documentId, teamId)
                                        .map(previousCost -> String.format(
                                                "Entities already extracted for this document. Previous extraction cost: $%.6f. Use force=true parameter to re-extract (will incur additional costs).",
                                                previousCost));
                            }
                            return Mono.empty();
                        });
            } else if ("relationships".equals(extractionType)) {
                // Check if relationships are already extracted
                idempotencyCheck = checkRelationshipsAlreadyExtracted(documentId, teamId)
                        .flatMap(alreadyExtracted -> {
                            if (alreadyExtracted) {
                                return getPreviousRelationshipExtractionCost(documentId, teamId)
                                        .map(previousCost -> String.format(
                                                "Relationships already extracted for this document. Previous extraction cost: $%.6f. Use force=true parameter to re-extract (will incur additional costs).",
                                                previousCost));
                            }
                            return Mono.empty();
                        });
            } else if ("all".equals(extractionType)) {
                // For "all", check entities first (since entities are extracted before
                // relationships)
                idempotencyCheck = checkEntitiesAlreadyExtracted(documentId, teamId)
                        .flatMap(entitiesExtracted -> {
                            if (entitiesExtracted) {
                                return getPreviousEntityExtractionCost(documentId, teamId)
                                        .map(previousCost -> String.format(
                                                "Entities already extracted for this document. Previous extraction cost: $%.6f. Use force=true parameter to re-extract (will incur additional costs).",
                                                previousCost));
                            }
                            // If entities are not extracted, check relationships
                            return checkRelationshipsAlreadyExtracted(documentId, teamId)
                                    .flatMap(relationshipsExtracted -> {
                                        if (relationshipsExtracted) {
                                            return getPreviousRelationshipExtractionCost(documentId, teamId)
                                                    .map(previousCost -> String.format(
                                                            "Relationships already extracted for this document. Previous extraction cost: $%.6f. Use force=true parameter to re-extract (will incur additional costs).",
                                                            previousCost));
                                        }
                                        return Mono.empty();
                                    });
                        });
            } else {
                idempotencyCheck = Mono.empty();
            }

            // If idempotency check returns an error message, return error Mono
            return idempotencyCheck.hasElement()
                    .flatMap(hasError -> {
                        if (hasError) {
                            return idempotencyCheck
                                    .flatMap(msg -> Mono.<GraphExtractionJob>error(new RuntimeException(msg)));
                        }
                        // Proceed with queueing
                        return createAndQueueJob(documentId, teamId, extractionType, force);
                    });
        }

        // If force=true, proceed with queueing
        return createAndQueueJob(documentId, teamId, extractionType, force);
    }

    private Mono<GraphExtractionJob> createAndQueueJob(String documentId, String teamId, String extractionType,
            boolean force) {
        String jobId = UUID.randomUUID().toString();

        GraphExtractionJob job = GraphExtractionJob.builder()
                .jobId(jobId)
                .documentId(documentId)
                .teamId(teamId)
                .extractionType(extractionType)
                .status("QUEUED")
                .queuedAt(LocalDateTime.now())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Save job to MongoDB
        return jobRepository.save(job)
                .doOnSuccess(savedJob -> {
                    log.info("Created graph extraction job {} for document {} (type: {})",
                            jobId, documentId, extractionType);
                    // Publish QUEUED status via WebSocket
                    publishProgressUpdate(savedJob);
                })
                .flatMap(savedJob -> {
                    // Create task for queue
                    GraphExtractionTask task = new GraphExtractionTask(
                            jobId,
                            documentId,
                            teamId,
                            extractionType,
                            force);

                    if (!redisEnabled) {
                        log.warn("Redis disabled. Job {} created but NOT queued.", jobId);
                        return Mono.just(savedJob);
                    }

                    try {
                        String taskJson = objectMapper.writeValueAsString(task);
                        // Add to Redis queue
                        return cacheService.rightPush(QUEUE_KEY, taskJson)
                                .doOnSuccess(count -> log.info("Queued graph extraction job {} (queue size: {})", jobId,
                                        count))
                                .thenReturn(savedJob);
                    } catch (Exception e) {
                        log.error("Failed to serialize task for job {}: {}", jobId, e.getMessage(), e);
                        // Update job status to FAILED
                        savedJob.setStatus("FAILED");
                        savedJob.setErrorMessage("Failed to queue job: " + e.getMessage());
                        return jobRepository.save(savedJob);
                    }
                });
    }

    private Mono<Boolean> checkEntitiesAlreadyExtracted(String documentId, String teamId) {
        return metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
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
                                return graphService.findEntities("Form", Map.of("documentId", documentId), teamId)
                                        .mergeWith(graphService.findEntities("Organization",
                                                Map.of("documentId", documentId), teamId))
                                        .mergeWith(graphService.findEntities("Person", Map.of("documentId", documentId),
                                                teamId))
                                        .mergeWith(graphService.findEntities("Date", Map.of("documentId", documentId),
                                                teamId))
                                        .mergeWith(graphService.findEntities("Location",
                                                Map.of("documentId", documentId), teamId))
                                        .mergeWith(graphService.findEntities("Document",
                                                Map.of("documentId", documentId), teamId))
                                        .hasElements()
                                        .map(hasEntities -> hasEntities); // Return true if entities exist
                            }
                        }
                    }
                    return Mono.just(false); // No previous extraction
                })
                .defaultIfEmpty(false);
    }

    private Mono<Boolean> checkRelationshipsAlreadyExtracted(String documentId, String teamId) {
        return metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                .flatMap(metadata -> {
                    if (metadata.getCustomMetadata() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata()
                                .get("graphExtraction");
                        if (graphExtraction != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> relationshipExtraction = (Map<String, Object>) graphExtraction
                                    .get("relationshipExtraction");
                            if (relationshipExtraction != null) {
                                // Relationships already extracted - check if any exist in Neo4j
                                String cypherQuery = "MATCH ()-[r]-() WHERE r.documentId = $documentId AND r.teamId = $teamId RETURN count(r) as count";
                                Map<String, Object> params = Map.of("documentId", documentId, "teamId", teamId);
                                return graphService.executeQuery(cypherQuery, params)
                                        .next()
                                        .map(result -> {
                                            Long count = result.containsKey("count")
                                                    ? ((Number) result.get("count")).longValue()
                                                    : 0L;
                                            return count > 0; // Return true if relationships exist
                                        })
                                        .defaultIfEmpty(false);
                            }
                        }
                    }
                    return Mono.just(false); // No previous extraction
                })
                .defaultIfEmpty(false);
    }

    private Mono<Double> getPreviousEntityExtractionCost(String documentId, String teamId) {
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

    private Mono<Double> getPreviousRelationshipExtractionCost(String documentId, String teamId) {
        return metadataRepository.findByDocumentIdAndTeamId(documentId, teamId)
                .map(metadata -> {
                    if (metadata.getCustomMetadata() != null) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> graphExtraction = (Map<String, Object>) metadata.getCustomMetadata()
                                .get("graphExtraction");
                        if (graphExtraction != null) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> relationshipExtraction = (Map<String, Object>) graphExtraction
                                    .get("relationshipExtraction");
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
    public Mono<GraphExtractionJob> getJobStatus(String jobId) {
        return jobRepository.findByJobId(jobId)
                .switchIfEmpty(Mono.error(new RuntimeException("Job not found: " + jobId)));
    }

    @Override
    public Flux<GraphExtractionJob> getJobsForDocument(String documentId, String teamId) {
        return jobRepository.findByDocumentIdAndTeamId(documentId, teamId);
    }

    @Override
    public Mono<Boolean> cancelJob(String jobId, String teamId) {
        return jobRepository.findByJobId(jobId)
                .filter(job -> job.getTeamId().equals(teamId))
                .flatMap(job -> {
                    if ("QUEUED".equals(job.getStatus())) {
                        // Remove from queue and mark as cancelled
                        job.setStatus("CANCELLED");
                        job.setUpdatedAt(LocalDateTime.now());
                        return jobRepository.save(job)
                                .then(Mono.just(true));
                    } else if ("RUNNING".equals(job.getStatus())) {
                        // Set cancellation flag
                        cancellationFlags.put(jobId, new AtomicBoolean(true));
                        job.setStatus("CANCELLED");
                        job.setUpdatedAt(LocalDateTime.now());
                        return jobRepository.save(job)
                                .then(Mono.just(true));
                    } else {
                        log.warn("Cannot cancel job {} with status: {}", jobId, job.getStatus());
                        return Mono.just(false);
                    }
                })
                .switchIfEmpty(Mono.just(false));
    }

    @Value("${app.redis.listener.enabled:true}")
    private boolean redisEnabled;

    @Scheduled(fixedDelay = 5000) // Poll every 5 seconds
    public void processQueue() {
        if (!redisEnabled) {
            // log.trace("Redis queue is disabled, skipping graph extraction poll");
            return;
        }

        cacheService.leftPop(QUEUE_KEY)
                .doOnSubscribe(s -> log.debug("Checking graph extraction queue..."))
                .doOnNext(message -> log.info("Found graph extraction job in queue: {}", message))
                .flatMap(message -> {
                    try {
                        GraphExtractionTask task = objectMapper.readValue(message, GraphExtractionTask.class);
                        log.info("Processing graph extraction job {} for document {} (type: {})",
                                task.getJobId(), task.getDocumentId(), task.getExtractionType());
                        return processExtractionJob(task);
                    } catch (Exception e) {
                        log.error("Failed to process queued task: {}", e.getMessage(), e);
                        return Mono.error(e);
                    }
                })
                .doOnError(error -> log.error("Error processing graph extraction queue: {}", error.getMessage(), error))
                .subscribe(
                        null,
                        error -> log.error("Error in graph extraction queue subscription: {}", error.getMessage(),
                                error));
    }

    private Mono<Void> processExtractionJob(GraphExtractionTask task) {
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
                                // Publish RUNNING status via WebSocket
                                publishProgressUpdate(savedJob);
                            })
                            .flatMap(savedJob -> {
                                // Execute extraction based on type
                                Mono<Integer> extractionMono;

                                if ("entities".equals(task.getExtractionType())) {
                                    // Use reflection to call the method with force parameter
                                    extractionMono = ((KnowledgeHubGraphEntityExtractionServiceImpl) entityExtractionService)
                                            .extractEntitiesFromDocument(task.getDocumentId(), task.getTeamId(),
                                                    task.isForce())
                                            .cast(Integer.class);
                                } else if ("relationships".equals(task.getExtractionType())) {
                                    extractionMono = relationshipExtractionService.extractRelationshipsFromDocument(
                                            task.getDocumentId(), task.getTeamId(), task.isForce())
                                            .cast(Integer.class);
                                } else if ("all".equals(task.getExtractionType())) {
                                    // Extract entities first, then relationships
                                    extractionMono = ((KnowledgeHubGraphEntityExtractionServiceImpl) entityExtractionService)
                                            .extractEntitiesFromDocument(task.getDocumentId(), task.getTeamId(),
                                                    task.isForce())
                                            .flatMap(entityCount -> relationshipExtractionService
                                                    .extractRelationshipsFromDocument(
                                                            task.getDocumentId(), task.getTeamId(), task.isForce())
                                                    .map(relCount -> entityCount + relCount));
                                } else {
                                    return Mono.error(new RuntimeException(
                                            "Unknown extraction type: " + task.getExtractionType()));
                                }

                                return extractionMono
                                        .flatMap(count -> {
                                            // Check if cancelled
                                            AtomicBoolean cancelled = cancellationFlags.get(jobId);
                                            if (cancelled != null && cancelled.get()) {
                                                log.info("Job {} was cancelled", jobId);
                                                return updateJobStatus(jobId, "CANCELLED", null, null);
                                            }

                                            // Update job to COMPLETED
                                            return updateJobStatus(jobId, "COMPLETED", count, null);
                                        })
                                        .onErrorResume(error -> {
                                            log.error("Error processing extraction job {}: {}", jobId,
                                                    error.getMessage(), error);
                                            return updateJobStatus(jobId, "FAILED", null, error.getMessage());
                                        });
                            });
                })
                .doFinally(signalType -> {
                    // Clean up cancellation flag
                    cancellationFlags.remove(jobId);
                })
                .then();
    }

    private Mono<GraphExtractionJob> updateJobStatus(String jobId, String status, Integer count, String errorMessage) {
        return jobRepository.findByJobId(jobId)
                .flatMap(job -> {
                    job.setStatus(status);
                    job.setUpdatedAt(LocalDateTime.now());

                    if ("COMPLETED".equals(status)) {
                        job.setCompletedAt(LocalDateTime.now());
                        if ("entities".equals(job.getExtractionType()) || "all".equals(job.getExtractionType())) {
                            job.setTotalEntities(count);
                        }
                        if ("relationships".equals(job.getExtractionType()) || "all".equals(job.getExtractionType())) {
                            job.setTotalRelationships(count);
                        }
                    } else if ("FAILED".equals(status)) {
                        job.setErrorMessage(errorMessage);
                    }

                    return jobRepository.save(job)
                            .doOnSuccess(savedJob -> {
                                log.info("Updated job {} status to {}", jobId, status);
                                // Publish status update via WebSocket
                                publishProgressUpdate(savedJob);
                            });
                });
    }

    @Override
    public Mono<Void> updateJobProgress(String jobId, Integer processedBatches, Integer totalBatches,
            Integer totalEntities, Integer totalRelationships, Double totalCostUsd) {
        return jobRepository.findByJobId(jobId)
                .flatMap(job -> {
                    job.setProcessedBatches(processedBatches);
                    job.setTotalBatches(totalBatches);
                    if (totalEntities != null) {
                        job.setTotalEntities(totalEntities);
                    }
                    if (totalRelationships != null) {
                        job.setTotalRelationships(totalRelationships);
                    }
                    if (totalCostUsd != null) {
                        job.setTotalCostUsd(totalCostUsd);
                    }
                    job.setUpdatedAt(LocalDateTime.now());

                    return jobRepository.save(job)
                            .doOnSuccess(savedJob -> {
                                log.debug("Updated job {} progress: batch {}/{}, entities: {}, relationships: {}",
                                        jobId, processedBatches, totalBatches, totalEntities, totalRelationships);
                                // Publish progress update via WebSocket
                                publishProgressUpdate(savedJob);
                            })
                            .then();
                })
                .onErrorResume(error -> {
                    log.error("Error updating job progress for job {}: {}", jobId, error.getMessage(), error);
                    return Mono.empty();
                });
    }

    /**
     * Publish job progress update via WebSocket
     */
    private void publishProgressUpdate(GraphExtractionJob job) {
        try {
            if (graphExtractionMessageChannel == null) {
                log.debug("Graph extraction message channel not available, skipping update for job: {}",
                        job.getJobId());
                return;
            }

            GraphExtractionProgressUpdate update = GraphExtractionProgressUpdate.builder()
                    .jobId(job.getJobId())
                    .documentId(job.getDocumentId())
                    .teamId(job.getTeamId())
                    .extractionType(job.getExtractionType())
                    .status(job.getStatus())
                    .totalBatches(job.getTotalBatches())
                    .processedBatches(job.getProcessedBatches())
                    .totalEntities(job.getTotalEntities())
                    .totalRelationships(job.getTotalRelationships())
                    .totalCostUsd(job.getTotalCostUsd())
                    .errorMessage(job.getErrorMessage())
                    .queuedAt(job.getQueuedAt())
                    .startedAt(job.getStartedAt())
                    .completedAt(job.getCompletedAt())
                    .timestamp(LocalDateTime.now())
                    .build();

            boolean sent = graphExtractionMessageChannel.send(MessageBuilder.withPayload(update).build());
            if (sent) {
                log.debug("📊 Published graph extraction progress update via WebSocket for job: {}", job.getJobId());
            } else {
                log.warn("📊 Failed to publish graph extraction progress update via WebSocket for job: {}",
                        job.getJobId());
            }
        } catch (Exception e) {
            log.error("📊 Error publishing graph extraction progress update via WebSocket for job: {} - {}",
                    job.getJobId(), e.getMessage(), e);
        }
    }

    public boolean isCancelled(String jobId) {
        AtomicBoolean cancelled = cancellationFlags.get(jobId);
        return cancelled != null && cancelled.get();
    }
}
