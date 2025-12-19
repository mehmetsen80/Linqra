package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.MilvusCreateCollectionRequest;
import org.lite.gateway.dto.MilvusQueryRequest;
import org.lite.gateway.dto.MilvusStoreRecordRequest;
import org.lite.gateway.dto.MilvusVerifyRequest;
import org.lite.gateway.dto.MilvusSearchRequest;
import org.lite.gateway.dto.MilvusCollectionInfo;
import org.lite.gateway.dto.MilvusCollectionVerificationResponse;
import org.lite.gateway.dto.MilvusUpdateCollectionMetadataRequest;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/milvus")
@RequiredArgsConstructor
public class MilvusController {

    private final LinqMilvusStoreService linqMilvusStoreService;
    private final UserContextService userContextService;
    private final UserService userService;
    private final TeamService teamService;

    @PostMapping("/collections")
    public Mono<ResponseEntity<Map<String, String>>> createCollection(
            @RequestBody MilvusCreateCollectionRequest request,
            ServerWebExchange exchange) {
        log.info("Creating collection: {}", request.getCollectionName());
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user ->
                // First check if the team exists
                teamService.getTeamById(request.getTeamId())
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(
                                "Team with ID " + request.getTeamId() + " does not exist")))
                        .then(
                                // Then check if user has admin role for this team
                                teamService.hasRole(request.getTeamId(), user.getId(), "ADMIN")
                                        .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                                "Admin access required for team " + request.getTeamId())))
                                        .then(linqMilvusStoreService.createCollection(
                                                request.getCollectionName(),
                                                request.getSchemaFields(),
                                                request.getDescription(),
                                                request.getTeamId(),
                                                request.getCollectionType(),
                                                request.getProperties()))))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/collections/{collectionName}/records")
    public Mono<ResponseEntity<Map<String, String>>> storeRecord(
            @PathVariable String collectionName,
            @RequestBody MilvusStoreRecordRequest request,
            ServerWebExchange exchange) {
        log.info("Storing record in collection: {}", collectionName);

        // Check if this is a workflow step call with executedBy header
        String executedBy = exchange.getRequest().getHeaders().getFirst("X-Executed-By");
        if (executedBy != null) {
            log.info("Using executedBy header for workflow step: {}", executedBy);
            // If scheduler/system path, bypass user lookup and role checks, trust teamId
            // from request
            if ("scheduler".equalsIgnoreCase(executedBy)) {
                log.warn("Bypassing user/role checks for scheduler execution. Team: {}, Collection: {}",
                        request.getTeamId(), collectionName);
                return linqMilvusStoreService.storeRecord(
                        collectionName,
                        request.getRecord(),
                        request.getModelCategory(),
                        request.getModelName(),
                        request.getTextField(),
                        request.getTeamId(),
                        request.getEmbedding() // ← Pass embedding
                )
                        .doOnSuccess(v -> log.info(
                                "linqMilvusStoreService.storeRecord completed for collection {} (scheduler path)",
                                collectionName))
                        .map(v -> ResponseEntity.ok(Map.of(
                                "status", "stored",
                                "collection", collectionName)));
            }
            return executeStoreRecordWithUser(executedBy, collectionName, request);
        }

        // Regular user authentication flow
        return userContextService.getCurrentUsername(exchange)
                .flatMap(username -> executeStoreRecordWithUser(username, collectionName, request));
    }

    /**
     * Helper method to execute store record with user authentication and
     * authorization
     */
    private Mono<ResponseEntity<Map<String, String>>> executeStoreRecordWithUser(
            String username,
            String collectionName,
            MilvusStoreRecordRequest request) {
        Mono<Map<String, String>> storeMono = userService.findByUsername(username)
                .doOnNext(user -> log.info(
                        "Milvus storeRecord requested by user: {} (id={}), teamId={}, collection={}, textField={}, modelCategory={}, modelName={}",
                        user.getUsername(), user.getId(), request.getTeamId(), collectionName, request.getTextField(),
                        request.getModelCategory(), request.getModelName()))
                .doOnNext(u -> log.info("Milvus storeRecord payload: {}", request.getRecord()))
                .flatMap(user -> teamService.hasRole(request.getTeamId(), user.getId(), "ADMIN")
                        .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Admin access required for team " + request.getTeamId())))
                        .then(Mono.defer(() -> {
                            log.info("Invoking linqMilvusStoreService.storeRecord for collection {} (user path)",
                                    collectionName);
                            return linqMilvusStoreService.storeRecord(
                                    collectionName,
                                    request.getRecord(),
                                    request.getModelCategory(),
                                    request.getModelName(),
                                    request.getTextField(),
                                    request.getTeamId(),
                                    request.getEmbedding() // ← Pass embedding
                            );
                        })))
                .switchIfEmpty(Mono.defer(() -> {
                    // Fallback for workflow/system users not present in DB (e.g., 'scheduler')
                    log.warn("No user found for '{}', treating as internal workflow call. Proceeding with team {}.",
                            username, request.getTeamId());
                    return teamService.getTeamById(request.getTeamId())
                            .switchIfEmpty(
                                    Mono.error(new AccessDeniedException("Team not found: " + request.getTeamId())))
                            .then(Mono.defer(() -> {
                                log.info(
                                        "Invoking linqMilvusStoreService.storeRecord for collection {} (internal path)",
                                        collectionName);
                                return linqMilvusStoreService.storeRecord(
                                        collectionName,
                                        request.getRecord(),
                                        request.getModelCategory(),
                                        request.getModelName(),
                                        request.getTextField(),
                                        request.getTeamId(),
                                        request.getEmbedding() // ← Pass embedding
                                );
                            }));
                }))
                .doOnSuccess(v -> log.info("linqMilvusStoreService.storeRecord completed for collection {}",
                        collectionName));

        return storeMono.thenReturn(ResponseEntity.ok(Map.of(
                "status", "stored",
                "collection", collectionName)));
    }

    @PostMapping("/collections/{collectionName}/query")
    public Mono<ResponseEntity<Map<String, Object>>> queryRecords(
            @PathVariable String collectionName,
            @RequestBody MilvusQueryRequest request) {
        log.info("Querying collection: {}", collectionName);
        return linqMilvusStoreService.queryRecords(
                collectionName,
                request.getEmbedding(),
                request.getNResults(),
                request.getOutputFields(),
                request.getTeamId()).map(ResponseEntity::ok);
    }

    @DeleteMapping("/collections/{collectionName}")
    public Mono<ResponseEntity<Map<String, String>>> deleteCollection(
            @PathVariable String collectionName,
            @RequestParam String teamId,
            ServerWebExchange exchange) {
        log.info("Deleting collection: {}", collectionName);
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> teamService.hasRole(teamId, user.getId(), "ADMIN")
                        .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Admin access required for team " + teamId)))
                        .then(linqMilvusStoreService.deleteCollection(collectionName, teamId)))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/collections/{teamId}")
    public Mono<ResponseEntity<List<MilvusCollectionInfo>>> listCollections(
            @PathVariable String teamId,
            @RequestParam(value = "type", required = false) String collectionType,
            ServerWebExchange exchange) {
        log.info("Listing collections for team: {}", teamId);
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> teamService.hasRole(teamId, user.getId(), "ADMIN")
                        .filter(hasAccess -> Boolean.TRUE.equals(hasAccess) || user.getRoles().contains("SUPER_ADMIN"))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Access denied for team " + teamId)))
                        .then(linqMilvusStoreService.listCollections(teamId, collectionType)))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/collections/{teamId}/{collectionName}/verify")
    public Mono<ResponseEntity<MilvusCollectionVerificationResponse>> verifyCollection(
            @PathVariable String teamId,
            @PathVariable String collectionName,
            ServerWebExchange exchange) {
        log.info("Verifying Milvus collection {} for team {}", collectionName, teamId);
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> teamService.hasRole(teamId, user.getId(), "ADMIN")
                        .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Admin access required for team " + teamId)))
                        .then(linqMilvusStoreService.verifyCollection(collectionName, teamId)))
                .map(ResponseEntity::ok)
                .onErrorResume(error -> {
                    log.error("Error verifying Milvus collection {}: {}", collectionName, error.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }

    @PutMapping("/collections/{collectionName}/metadata")
    public Mono<ResponseEntity<Map<String, String>>> updateCollectionMetadata(
            @PathVariable String collectionName,
            @RequestBody MilvusUpdateCollectionMetadataRequest request,
            ServerWebExchange exchange) {
        log.info("Updating metadata for collection {} (team {})", collectionName, request.getTeamId());

        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> teamService.hasRole(request.getTeamId(), user.getId(), "ADMIN")
                        .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Admin access required for team " + request.getTeamId())))
                        .then(linqMilvusStoreService.updateCollectionMetadata(
                                collectionName,
                                request.getTeamId(),
                                request.getMetadata())))
                .map(ResponseEntity::ok);
    }

    @GetMapping("/collections")
    public Mono<ResponseEntity<List<MilvusCollectionInfo>>> listAllCollections(ServerWebExchange exchange) {
        log.info("Listing all collections");
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .filter(user -> user.getRoles().contains("SUPER_ADMIN"))
                .switchIfEmpty(Mono.error(new AccessDeniedException("Super admin access required")))
                .then(linqMilvusStoreService.listAllCollections())
                .map(ResponseEntity::ok);
    }

    @GetMapping("/collections/details")
    public Mono<ResponseEntity<Map<String, Object>>> getCollectionDetails(ServerWebExchange exchange) {
        log.info("Getting detailed collection information");
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .filter(user -> user.getRoles().contains("SUPER_ADMIN"))
                .switchIfEmpty(Mono.error(new AccessDeniedException("Super admin access required")))
                .then(linqMilvusStoreService.getCollectionDetails())
                .map(ResponseEntity::ok);
    }

    @PostMapping("/collections/{collectionName}/verify")
    public Mono<ResponseEntity<Map<String, Object>>> verifyRecord(
            @PathVariable String collectionName,
            @RequestBody MilvusVerifyRequest request,
            ServerWebExchange exchange) {
        log.info("Verifying record in collection: {} for team: {}", collectionName, request.getTeamId());
        return userContextService.getCurrentUsername(exchange)
                .flatMap(userService::findByUsername)
                .flatMap(user -> teamService.hasRole(request.getTeamId(), user.getId(), "ADMIN")
                        .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Admin access required for team " + request.getTeamId())))
                        .then(linqMilvusStoreService.verifyRecord(
                                collectionName,
                                request.getTextField(),
                                request.getText(),
                                request.getTeamId(),
                                request.getModelCategory() != null ? request.getModelCategory() : "openai-embed",
                                request.getModelName() != null ? request.getModelName() : "text-embedding-3-small")))
                .map(ResponseEntity::ok);
    }

    @PostMapping("/collections/{collectionName}/search")
    public Mono<ResponseEntity<Map<String, Object>>> searchRecord(
            @PathVariable String collectionName,
            @RequestBody MilvusSearchRequest request,
            ServerWebExchange exchange) {
        log.info("Searching records in collection: {} for team: {}", collectionName, request.getTeamId());

        // Check if this is a workflow step call with executedBy header
        String executedBy = exchange.getRequest().getHeaders().getFirst("X-Executed-By");
        if (executedBy != null) {
            log.info("Using executedBy header for workflow step: {}", executedBy);
            return executeSearchRecordWithUser(executedBy, collectionName, request);
        }

        // Regular user authentication flow
        return userContextService.getCurrentUsername(exchange)
                .flatMap(username -> executeSearchRecordWithUser(username, collectionName, request));
    }

    /**
     * Helper method to execute search record with user authentication and
     * authorization
     */
    private Mono<ResponseEntity<Map<String, Object>>> executeSearchRecordWithUser(
            String username,
            String collectionName,
            MilvusSearchRequest request) {
        Mono<Map<String, Object>> searchMono = userService.findByUsername(username)
                .flatMap(user -> teamService.hasRole(request.getTeamId(), user.getId(), "ADMIN")
                        .filter(hasAccess -> Boolean.TRUE.equals(hasAccess) || user.getRoles().contains("SUPER_ADMIN"))
                        .switchIfEmpty(Mono.error(new AccessDeniedException(
                                "Access denied for team " + request.getTeamId())))
                        .then(linqMilvusStoreService.searchRecord(
                                collectionName,
                                request.getTextField(),
                                request.getText(),
                                request.getTeamId(),
                                request.getModelCategory() != null ? request.getModelCategory() : "openai-embed",
                                request.getModelName() != null ? request.getModelName() : "text-embedding-3-small",
                                request.getNResults() != null ? request.getNResults() : 10,
                                request.getMetadataFilters())))
                .switchIfEmpty(Mono.defer(() -> {
                    // Fallback for workflow/system users not present in DB (e.g., 'scheduler')
                    log.warn("No user found for '{}', treating as internal workflow search. Proceeding with team {}.",
                            username, request.getTeamId());
                    return teamService.getTeamById(request.getTeamId())
                            .switchIfEmpty(
                                    Mono.error(new AccessDeniedException("Team not found: " + request.getTeamId())))
                            .then(linqMilvusStoreService.searchRecord(
                                    collectionName,
                                    request.getTextField(),
                                    request.getText(),
                                    request.getTeamId(),
                                    request.getModelCategory() != null ? request.getModelCategory() : "openai-embed",
                                    request.getModelName() != null ? request.getModelName() : "text-embedding-3-small",
                                    request.getNResults() != null ? request.getNResults() : 10,
                                    request.getMetadataFilters()));
                }));

        return searchMono
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.ok(Map.of(
                        "results", java.util.List.of(),
                        "count", 0)));
    }
}