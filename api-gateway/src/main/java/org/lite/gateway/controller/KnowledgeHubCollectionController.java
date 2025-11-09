package org.lite.gateway.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.AssignMilvusCollectionRequest;
import org.lite.gateway.dto.CreateKnowledgeCollectionRequest;
import org.lite.gateway.dto.KnowledgeCollectionResponse;
import org.lite.gateway.dto.KnowledgeCollectionSearchRequest;
import org.lite.gateway.dto.UpdateKnowledgeCollectionRequest;
import org.lite.gateway.entity.KnowledgeHubCollection;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.KnowledgeHubCollectionService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/knowledge/collections")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubCollectionController {
    
    private final KnowledgeHubCollectionService collectionService;
    private final TeamContextService teamContextService;
    private final UserContextService userContextService;
    private final KnowledgeHubDocumentRepository documentRepository;
    
    /**
     * Create a new knowledge collection
     */
    @PostMapping
    public Mono<ResponseEntity<KnowledgeCollectionResponse>> createCollection(
            @Valid @RequestBody CreateKnowledgeCollectionRequest request,
            org.springframework.web.server.ServerWebExchange exchange) {
        
        log.info("Creating knowledge collection: {}", request.getName());
        
        return Mono.zip(
                    teamContextService.getTeamFromContext(exchange),
                    userContextService.getCurrentUsername(exchange)
                )
                .flatMap(tuple -> {
                    String teamId = tuple.getT1();
                    String createdBy = tuple.getT2();
                    return collectionService.createCollection(
                            request.getName(),
                            request.getDescription(),
                            request.getCategories(),
                            teamId,
                            createdBy);
                })
                .map(collection -> toResponse(collection, 0L))
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error creating collection", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(KnowledgeCollectionResponse.builder()
                                    .build()));
                });
    }
    
    /**
     * Update an existing knowledge collection
     */
    @PutMapping("/{id}")
    public Mono<ResponseEntity<KnowledgeCollectionResponse>> updateCollection(
            @PathVariable String id,
            @Valid @RequestBody UpdateKnowledgeCollectionRequest request,
            org.springframework.web.server.ServerWebExchange exchange) {
        
        log.info("Updating knowledge collection: {}", id);
        
        return Mono.zip(
                    teamContextService.getTeamFromContext(exchange),
                    userContextService.getCurrentUsername(exchange)
                )
                .flatMap(tuple -> {
                    String teamId = tuple.getT1();
                    String updatedBy = tuple.getT2();
                    return collectionService.updateCollection(
                            id,
                            request.getName(),
                            request.getDescription(),
                            request.getCategories(),
                            teamId,
                            updatedBy);
                })
                .flatMap(collection -> {
                    // Count documents for this collection
                    return documentRepository.findByCollectionId(collection.getId())
                            .count()
                            .map(documentCount -> toResponse(collection, documentCount));
                })
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error updating collection", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(KnowledgeCollectionResponse.builder()
                                    .build()));
                });
    }
    
    /**
     * Delete a knowledge collection
     */
    @DeleteMapping("/{id}")
    public Mono<ResponseEntity<Void>> deleteCollection(
            @PathVariable String id,
            org.springframework.web.server.ServerWebExchange exchange) {
        log.info("Deleting knowledge collection: {}", id);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> collectionService.deleteCollection(id, teamId))
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(error -> {
                    log.error("Error deleting collection", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build());
                });
    }
    
    /**
     * Get all knowledge collections for the current team
     */
    @GetMapping
    public Mono<ResponseEntity<List<KnowledgeCollectionResponse>>> getCollections(
            org.springframework.web.server.ServerWebExchange exchange) {
        log.info("Getting knowledge collections");
        
        return teamContextService.getTeamFromContext(exchange)
                .doOnNext(teamId -> log.info("Getting collections for teamId: {}", teamId))
                .flatMap(teamId -> {
                    return collectionService.getCollectionsByTeam(teamId)
                            .flatMap(collection -> {
                                // Count documents for each collection
                                return documentRepository.findByCollectionId(collection.getId())
                                        .count()
                                        .map(documentCount -> toResponse(collection, documentCount));
                            })
                            .collectList()
                            .map(ResponseEntity::ok);
                });
    }
    
    /**
     * Get a specific knowledge collection by ID
     */
    @GetMapping("/{id}")
    public Mono<ResponseEntity<KnowledgeCollectionResponse>> getCollection(
            @PathVariable String id,
            org.springframework.web.server.ServerWebExchange exchange) {
        log.info("Getting knowledge collection: {}", id);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> collectionService.getCollectionById(id, teamId))
                .flatMap(collection -> {
                    // Count documents for this collection
                    return documentRepository.findByCollectionId(collection.getId())
                            .count()
                            .map(documentCount -> toResponse(collection, documentCount));
                })
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error getting collection", error);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
    
    /**
     * Get collection count for the current team
     */
    @GetMapping("/count")
    public Mono<ResponseEntity<Object>> getCollectionCount(
            org.springframework.web.server.ServerWebExchange exchange) {
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> collectionService.getCollectionCountByTeam(teamId)
                        .map(ResponseEntity::ok));
    }
    
    /**
     * Assign a Milvus collection and embedding configuration to a Knowledge Hub collection
     */
    @PutMapping("/{id}/milvus")
    public Mono<ResponseEntity<KnowledgeCollectionResponse>> assignMilvusCollection(
            @PathVariable String id,
            @Valid @RequestBody AssignMilvusCollectionRequest request,
            org.springframework.web.server.ServerWebExchange exchange) {
        log.info("Assigning Milvus collection {} to Knowledge Hub collection {}", request.getMilvusCollectionName(), id);

        return Mono.zip(
                    teamContextService.getTeamFromContext(exchange),
                    userContextService.getCurrentUsername(exchange)
                )
                .flatMap(tuple -> collectionService.assignMilvusCollection(
                        id,
                        tuple.getT1(),
                        tuple.getT2(),
                        request
                ))
                .flatMap(collection -> documentRepository.findByCollectionId(collection.getId())
                        .count()
                        .map(documentCount -> toResponse(collection, documentCount)))
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error assigning Milvus collection", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(KnowledgeCollectionResponse.builder().build()));
                });
    }

    /**
     * Remove Milvus collection assignment from a Knowledge Hub collection
     */
    @DeleteMapping("/{id}/milvus")
    public Mono<ResponseEntity<KnowledgeCollectionResponse>> removeMilvusCollection(
            @PathVariable String id,
            org.springframework.web.server.ServerWebExchange exchange) {
        log.info("Removing Milvus collection assignment for Knowledge Hub collection {}", id);

        return Mono.zip(
                    teamContextService.getTeamFromContext(exchange),
                    userContextService.getCurrentUsername(exchange)
                )
                .flatMap(tuple -> collectionService.removeMilvusCollection(
                        id,
                        tuple.getT1(),
                        tuple.getT2()
                ))
                .flatMap(collection -> documentRepository.findByCollectionId(collection.getId())
                        .count()
                        .map(documentCount -> toResponse(collection, documentCount)))
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error removing Milvus assignment", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(KnowledgeCollectionResponse.builder().build()));
                });
    }

    @PostMapping("/{id}/search")
    public Mono<ResponseEntity<Map<String, Object>>> searchCollection(
            @PathVariable String id,
            @Valid @RequestBody KnowledgeCollectionSearchRequest request,
            org.springframework.web.server.ServerWebExchange exchange) {
        log.info("Performing semantic search for collection {}", id);

        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> collectionService.searchCollection(
                        id,
                        teamId,
                        request.getQuery(),
                        request.getTopK(),
                        request.getMetadataFilters()))
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error searching collection {}: {}", id, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                            .body(Map.of(
                                    "error", error.getMessage() != null ? error.getMessage() : "Failed to search collection"
                            )));
                });
    }
    
    private KnowledgeCollectionResponse toResponse(KnowledgeHubCollection collection, Long documentCount) {
        return KnowledgeCollectionResponse.builder()
                .id(collection.getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .teamId(collection.getTeamId())
                .createdBy(collection.getCreatedBy())
                .updatedBy(collection.getUpdatedBy())
                .categories(collection.getCategories())
                .milvusCollectionName(collection.getMilvusCollectionName())
                .embeddingModel(collection.getEmbeddingModel())
                .embeddingModelName(collection.getEmbeddingModelName())
                .embeddingDimension(collection.getEmbeddingDimension())
                .lateChunkingEnabled(collection.isLateChunkingEnabled())
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .documentCount(documentCount)
                .build();
    }
}

