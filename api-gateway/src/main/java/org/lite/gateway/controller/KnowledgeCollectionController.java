package org.lite.gateway.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.*;
import org.lite.gateway.entity.KnowledgeCollection;
import org.lite.gateway.repository.DocumentRepository;
import org.lite.gateway.service.KnowledgeCollectionService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/api/v1/knowledge/collections")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeCollectionController {
    
    private final KnowledgeCollectionService collectionService;
    private final TeamContextService teamContextService;
    private final UserContextService userContextService;
    private final DocumentRepository documentRepository;
    
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
                        .map(count -> ResponseEntity.ok(count)));
    }
    
    private KnowledgeCollectionResponse toResponse(KnowledgeCollection collection, Long documentCount) {
        return KnowledgeCollectionResponse.builder()
                .id(collection.getId())
                .name(collection.getName())
                .description(collection.getDescription())
                .teamId(collection.getTeamId())
                .createdBy(collection.getCreatedBy())
                .updatedBy(collection.getUpdatedBy())
                .categories(collection.getCategories())
                .createdAt(collection.getCreatedAt())
                .updatedAt(collection.getUpdatedAt())
                .documentCount(documentCount)
                .build();
    }
}

