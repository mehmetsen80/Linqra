package org.lite.gateway.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.lite.gateway.service.GraphExtractionJobService;
import org.lite.gateway.service.Neo4jGraphService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/knowledge-graph")
@RequiredArgsConstructor
@Tag(name = "Knowledge Graph", description = "Knowledge Graph operations using Neo4j")
public class KnowledgeHubGraphController {

    private final Neo4jGraphService graphService;
    private final TeamContextService teamContextService;

    private final GraphExtractionJobService graphExtractionJobService;

    @PostMapping("/entities/{entityType}")
    @Operation(summary = "Create or update an entity", description = "Creates or updates an entity node in the knowledge graph")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Map<String, Object>>> upsertEntity(
            @PathVariable String entityType,
            @RequestParam String entityId,
            @RequestBody Map<String, Object> properties,
            ServerWebExchange exchange) {
        log.info("Upserting entity {}:{}", entityType, entityId);
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> graphService.upsertEntity(entityType, entityId, properties, teamId)
                        .map(id -> {
                            Map<String, Object> response = Map.of(
                                    "entityType", entityType,
                                    "entityId", id,
                                    "success", true
                            );
                            return ResponseEntity.ok(response);
                        }))
                .doOnError(error -> log.error("Error upserting entity {}:{}: {}", entityType, entityId, error.getMessage()));
    }

    @PostMapping("/relationships")
    @Operation(summary = "Create or update a relationship", description = "Creates or updates a relationship between two entities")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<ResponseEntity<Map<String, Object>>> upsertRelationship(
            @RequestParam String fromEntityType,
            @RequestParam String fromEntityId,
            @RequestParam String relationshipType,
            @RequestParam String toEntityType,
            @RequestParam String toEntityId,
            @RequestBody(required = false) Map<String, Object> properties,
            ServerWebExchange exchange) {
        log.info("Upserting relationship {} from {}:{} to {}:{}", 
                relationshipType, fromEntityType, fromEntityId, toEntityType, toEntityId);
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> graphService.upsertRelationship(
                                fromEntityType, fromEntityId,
                                relationshipType,
                                toEntityType, toEntityId,
                                properties != null ? properties : Map.of(),
                                teamId)
                        .map(success -> {
                            Map<String, Object> response = Map.of(
                                    "success", success,
                                    "relationshipType", relationshipType,
                                    "fromEntity", Map.of("type", fromEntityType, "id", fromEntityId),
                                    "toEntity", Map.of("type", toEntityType, "id", toEntityId)
                            );
                            return ResponseEntity.ok(response);
                        }))
                .doOnError(error -> log.error("Error upserting relationship {}: {}", relationshipType, error.getMessage()));
    }

    @GetMapping("/entities/{entityType}")
    @Operation(summary = "Find entities", description = "Finds entities by type with optional filters")
    public Flux<Map<String, Object>> findEntities(
            @PathVariable String entityType,
            @RequestParam(required = false) Map<String, Object> filters,
            ServerWebExchange exchange) {
        log.info("Finding entities of type {} with filters: {}", entityType, filters);
        return teamContextService.getTeamFromContext(exchange)
                .flatMapMany(teamId -> graphService.findEntities(entityType, filters != null ? filters : Map.of(), teamId))
                .doOnError(error -> log.error("Error finding entities {}: {}", entityType, error.getMessage()));
    }

    @GetMapping("/entities/{entityType}/{entityId}/related")
    @Operation(summary = "Find related entities", description = "Finds entities related to a given entity")
    public Flux<Map<String, Object>> findRelatedEntities(
            @PathVariable String entityType,
            @PathVariable String entityId,
            @RequestParam(required = false) String relationshipType,
            @RequestParam(defaultValue = "1") int maxDepth,
            ServerWebExchange exchange) {
        log.info("Finding related entities for {}:{} with relationship type: {}, max depth: {}", 
                entityType, entityId, relationshipType, maxDepth);
        return teamContextService.getTeamFromContext(exchange)
                .flatMapMany(teamId -> graphService.findRelatedEntities(
                        entityType, entityId, relationshipType, maxDepth, teamId))
                .doOnError(error -> log.error("Error finding related entities for {}:{}: {}", 
                        entityType, entityId, error.getMessage()));
    }

    @PostMapping("/query")
    @Operation(summary = "Execute custom Cypher query", description = "Executes a custom Cypher query (teamId is automatically added)")
    public Flux<Map<String, Object>> executeQuery(
            @RequestBody Map<String, Object> request,
            ServerWebExchange exchange) {
        String cypherQuery = (String) request.get("query");
        @SuppressWarnings("unchecked")
        Map<String, Object> parameters = (Map<String, Object>) request.getOrDefault("parameters", Map.of());
        
        log.info("Executing custom Cypher query");
        return teamContextService.getTeamFromContext(exchange)
                .flatMapMany(teamId -> {
                    // Add teamId to parameters for multi-tenant isolation
                    Map<String, Object> paramsWithTeam = new java.util.HashMap<>(parameters);
                    paramsWithTeam.put("teamId", teamId);
                    
                    // If query doesn't already filter by teamId, add it as a comment recommendation
                    // Note: For security, we rely on the service layer to enforce teamId in production
                    return graphService.executeQuery(cypherQuery, paramsWithTeam);
                })
                .doOnError(error -> log.error("Error executing Cypher query: {}", error.getMessage()));
    }

    @DeleteMapping("/entities/{entityType}/{entityId}")
    @Operation(summary = "Delete an entity", description = "Deletes an entity and all its relationships")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<ResponseEntity<Void>> deleteEntity(
            @PathVariable String entityType,
            @PathVariable String entityId,
            ServerWebExchange exchange) {
        log.info("Deleting entity {}:{}", entityType, entityId);
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> graphService.deleteEntity(entityType, entityId, teamId)
                        .then(Mono.just(ResponseEntity.noContent().<Void>build())))
                .doOnError(error -> log.error("Error deleting entity {}:{}: {}", entityType, entityId, error.getMessage()));
    }

    @GetMapping("/statistics")
    @Operation(summary = "Get graph statistics", description = "Gets statistics about the knowledge graph for the current team")
    public Mono<ResponseEntity<Map<String, Object>>> getGraphStatistics(ServerWebExchange exchange) {
        log.info("Getting graph statistics");
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(graphService::getGraphStatistics)
                .map(ResponseEntity::ok)
                .doOnError(error -> log.error("Error getting graph statistics: {}", error.getMessage()));
    }

    @PostMapping("/documents/{documentId}/extract-entities")
    @Operation(summary = "Queue entity extraction job", description = "Queues an entity extraction job for a Knowledge Hub document")
    public Mono<ResponseEntity<Map<String, Object>>> extractEntitiesFromDocument(
            @PathVariable String documentId,
            @RequestParam(required = false, defaultValue = "false") boolean force,
            ServerWebExchange exchange) {
        log.info("Queueing entity extraction job for document: {}, force: {}", documentId, force);
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> graphExtractionJobService.queueExtraction(documentId, teamId, "entities", force)
                        .map(job -> {
                            Map<String, Object> response = Map.of(
                                    "jobId", job.getJobId(),
                                    "documentId", documentId,
                                    "status", job.getStatus(),
                                    "extractionType", "entities",
                                    "success", true
                            );
                            return ResponseEntity.accepted().body(response);
                        })
                        .onErrorResume(error -> {
                            log.error("Error queueing entity extraction for document {}: {}", documentId, error.getMessage());
                            Map<String, Object> errorResponse = Map.of(
                                    "success", false,
                                    "error", error.getMessage() != null ? error.getMessage() : "Failed to queue entity extraction"
                            );
                            return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                        }));
    }

    @PostMapping("/documents/{documentId}/extract-relationships")
    @Operation(summary = "Queue relationship extraction job", description = "Queues a relationship extraction job for a Knowledge Hub document")
    public Mono<ResponseEntity<Map<String, Object>>> extractRelationshipsFromDocument(
            @PathVariable String documentId,
            @RequestParam(required = false, defaultValue = "false") boolean force,
            ServerWebExchange exchange) {
        log.info("Queueing relationship extraction job for document: {}, force: {}", documentId, force);
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> graphExtractionJobService.queueExtraction(documentId, teamId, "relationships", force)
                        .map(job -> {
                            Map<String, Object> response = Map.of(
                                    "jobId", job.getJobId(),
                                    "documentId", documentId,
                                    "status", job.getStatus(),
                                    "extractionType", "relationships",
                                    "success", true
                            );
                            return ResponseEntity.accepted().body(response);
                        })
                        .onErrorResume(error -> {
                            log.error("Error queueing relationship extraction for document {}: {}", documentId, error.getMessage());
                            Map<String, Object> errorResponse = Map.of(
                                    "success", false,
                                    "error", error.getMessage() != null ? error.getMessage() : "Failed to queue relationship extraction"
                            );
                            return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                        }));
    }

    @PostMapping("/documents/{documentId}/extract-all")
    @Operation(summary = "Queue full extraction job", description = "Queues both entity and relationship extraction jobs for a Knowledge Hub document")
    public Mono<ResponseEntity<Map<String, Object>>> extractAllFromDocument(
            @PathVariable String documentId,
            @RequestParam(required = false, defaultValue = "false") boolean force,
            ServerWebExchange exchange) {
        log.info("Queueing full extraction job for document: {}, force: {}", documentId, force);
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> graphExtractionJobService.queueExtraction(documentId, teamId, "all", force)
                        .map(job -> {
                            Map<String, Object> response = Map.of(
                                    "jobId", job.getJobId(),
                                    "documentId", documentId,
                                    "status", job.getStatus(),
                                    "extractionType", "all",
                                    "success", true
                            );
                            return ResponseEntity.accepted().body(response);
                        })
                        .onErrorResume(error -> {
                            log.error("Error queueing full extraction for document {}: {}", documentId, error.getMessage());
                            Map<String, Object> errorResponse = Map.of(
                                    "success", false,
                                    "error", error.getMessage() != null ? error.getMessage() : "Failed to queue full extraction"
                            );
                            return Mono.just(ResponseEntity.badRequest().body(errorResponse));
                        }));
    }
    
    @GetMapping("/jobs/{jobId}")
    @Operation(summary = "Get extraction job status", description = "Gets the status of a graph extraction job")
    public Mono<ResponseEntity<Map<String, Object>>> getJobStatus(
            @PathVariable String jobId,
            ServerWebExchange exchange) {
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> graphExtractionJobService.getJobStatus(jobId)
                        .filter(job -> job.getTeamId().equals(teamId))
                        .switchIfEmpty(Mono.error(new RuntimeException("Job not found or access denied")))
                        .map(job -> {
                            Map<String, Object> response = new java.util.HashMap<>();
                            response.put("jobId", job.getJobId());
                            response.put("documentId", job.getDocumentId());
                            response.put("status", job.getStatus());
                            response.put("extractionType", job.getExtractionType());
                            response.put("queuedAt", job.getQueuedAt());
                            response.put("startedAt", job.getStartedAt());
                            response.put("completedAt", job.getCompletedAt());
                            response.put("totalBatches", job.getTotalBatches());
                            response.put("processedBatches", job.getProcessedBatches());
                            response.put("totalEntities", job.getTotalEntities());
                            response.put("totalRelationships", job.getTotalRelationships());
                            response.put("totalCostUsd", job.getTotalCostUsd());
                            response.put("errorMessage", job.getErrorMessage());
                            return ResponseEntity.ok(response);
                        }))
                .doOnError(error -> log.error("Error getting job status {}: {}", jobId, error.getMessage()));
    }
    
    @GetMapping("/documents/{documentId}/jobs")
    @Operation(summary = "Get all extraction jobs for a document", description = "Gets all graph extraction jobs for a document")
    public Flux<Map<String, Object>> getJobsForDocument(
            @PathVariable String documentId,
            ServerWebExchange exchange) {
        return teamContextService.getTeamFromContext(exchange)
                .flatMapMany(teamId -> graphExtractionJobService.getJobsForDocument(documentId, teamId)
                        .map(job -> {
                            Map<String, Object> response = new java.util.HashMap<>();
                            response.put("jobId", job.getJobId());
                            response.put("status", job.getStatus());
                            response.put("extractionType", job.getExtractionType());
                            response.put("queuedAt", job.getQueuedAt());
                            response.put("startedAt", job.getStartedAt());
                            response.put("completedAt", job.getCompletedAt());
                            response.put("totalEntities", job.getTotalEntities());
                            response.put("totalRelationships", job.getTotalRelationships());
                            response.put("totalCostUsd", job.getTotalCostUsd());
                            return response;
                        }))
                .doOnError(error -> log.error("Error getting jobs for document {}: {}", documentId, error.getMessage()));
    }
    
    @PostMapping("/jobs/{jobId}/cancel")
    @Operation(summary = "Cancel extraction job", description = "Cancels a running or queued graph extraction job")
    public Mono<ResponseEntity<Map<String, Object>>> cancelJob(
            @PathVariable String jobId,
            ServerWebExchange exchange) {
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> graphExtractionJobService.cancelJob(jobId, teamId)
                        .map(cancelled -> {
                            Map<String, Object> response = Map.of(
                                    "jobId", jobId,
                                    "cancelled", cancelled,
                                    "success", true
                            );
                            return ResponseEntity.ok(response);
                        }))
                .doOnError(error -> log.error("Error cancelling job {}: {}", jobId, error.getMessage()));
    }
}

