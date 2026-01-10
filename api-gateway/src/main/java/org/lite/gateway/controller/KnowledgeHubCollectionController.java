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
import org.lite.gateway.service.TeamService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;

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
        private final TeamService teamService;
        private final UserService userService;

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
                                userContextService.getCurrentUsername(exchange))
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
                                userContextService.getCurrentUsername(exchange))
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
                                        return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                                                        .build());
                                });
        }

        /**
         * Get all knowledge collections for the current team
         */
        @GetMapping
        public Mono<ResponseEntity<List<KnowledgeCollectionResponse>>> getCollections(
                        @RequestParam(required = false) String teamId,
                        org.springframework.web.server.ServerWebExchange exchange) {
                log.info("Getting knowledge collections");

                return resolveTeamId(exchange, teamId, "ADMIN")
                                .doOnNext(resolvedTeamId -> log.info("Getting collections for teamId: {}",
                                                resolvedTeamId))
                                .flatMap(resolvedTeamId -> {
                                        return collectionService.getCollectionsByTeam(resolvedTeamId)
                                                        .flatMap(collection -> {
                                                                // Count documents for each collection
                                                                return documentRepository
                                                                                .findByCollectionId(collection.getId())
                                                                                .count()
                                                                                .map(documentCount -> toResponse(
                                                                                                collection,
                                                                                                documentCount));
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
         * Assign a Milvus collection and embedding configuration to a Knowledge Hub
         * collection
         */
        @PutMapping("/{id}/milvus")
        public Mono<ResponseEntity<KnowledgeCollectionResponse>> assignMilvusCollection(
                        @PathVariable String id,
                        @RequestParam(required = false) String teamId,
                        @Valid @RequestBody AssignMilvusCollectionRequest request,
                        org.springframework.web.server.ServerWebExchange exchange) {
                log.info("Assigning Milvus collection {} to Knowledge Hub collection {}",
                                request.getMilvusCollectionName(), id);

                return resolveTeamId(exchange, teamId, "ADMIN")
                                .flatMap(resolvedTeamId -> userContextService.getCurrentUsername(exchange)
                                                .flatMap(username -> collectionService.assignMilvusCollection(
                                                                id,
                                                                resolvedTeamId,
                                                                username,
                                                                request)))
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
                                userContextService.getCurrentUsername(exchange))
                                .flatMap(tuple -> collectionService.removeMilvusCollection(
                                                id,
                                                tuple.getT1(),
                                                tuple.getT2()))
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

                return Mono.zip(
                                teamContextService.getTeamFromContext(exchange),
                                userContextService.getCurrentUsername(exchange).defaultIfEmpty("Unknown User"))
                                .flatMap(tuple -> {
                                        String teamId = tuple.getT1();
                                        String username = tuple.getT2();
                                        return collectionService.searchCollection(
                                                        id,
                                                        teamId,
                                                        request.getQuery(),
                                                        request.getTopK(),
                                                        request.getMetadataFilters(),
                                                        username);
                                })
                                .map(ResponseEntity::ok)
                                .onErrorResume(error -> {
                                        log.error("Error searching collection {}: {}", id, error.getMessage());
                                        return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST)
                                                        .body(Map.of(
                                                                        "error",
                                                                        error.getMessage() != null ? error.getMessage()
                                                                                        : "Failed to search collection")));
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

        private Mono<String> resolveTeamId(ServerWebExchange exchange,
                        String teamIdParam, String requiredRole) {
                if (teamIdParam != null && !teamIdParam.trim().isEmpty()) {
                        return userContextService.getCurrentUsername(exchange)
                                        .flatMap(userService::findByUsername)
                                        .flatMap(user -> teamService.hasRole(teamIdParam, user.getId(), requiredRole)
                                                        .flatMap(hasRole -> {
                                                                if (Boolean.TRUE.equals(hasRole)) {
                                                                        return Mono.just(true);
                                                                }
                                                                // Fallback: check if user is admin (handles OWNER case
                                                                // or hierarchical ADMIN case)
                                                                return teamService.isUserTeamAdmin(teamIdParam,
                                                                                user.getId());
                                                        })
                                                        .flatMap(hasAccess -> {
                                                                if (Boolean.TRUE.equals(hasAccess)) {
                                                                        return Mono.just(teamIdParam);
                                                                }
                                                                return Mono.error(new RuntimeException(
                                                                                "Access denied to team: "
                                                                                                + teamIdParam));
                                                        }));
                }
                return teamContextService.getTeamFromContext(exchange);
        }
}
