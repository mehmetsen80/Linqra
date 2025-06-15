package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.MilvusCreateCollectionRequest;
import org.lite.gateway.dto.MilvusQueryRequest;
import org.lite.gateway.dto.MilvusStoreRecordRequest;
import org.lite.gateway.dto.MilvusCollectionInfo;
import org.lite.gateway.service.LinqMilvusStoreService;
import org.lite.gateway.service.TeamService;
import org.lite.gateway.service.UserContextService;
import org.lite.gateway.service.UserService;
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
                teamService.hasRole(request.getTeamId(), user.getId(), "ADMIN")
                    .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                    .switchIfEmpty(Mono.error(new AccessDeniedException(
                        "Admin access required for team " + request.getTeamId())))
                    .then(linqMilvusStoreService.createCollection(
                        request.getCollectionName(),
                        request.getSchemaFields(),
                        request.getDescription(),
                        request.getTeamId()
                    ))
            )
            .map(ResponseEntity::ok);
    }

    @PostMapping("/collections/{collectionName}/records")
    public Mono<ResponseEntity<Map<String, String>>> storeRecord(
            @PathVariable String collectionName,
            @RequestBody MilvusStoreRecordRequest request,
            ServerWebExchange exchange) {
        log.info("Storing record in collection: {}", collectionName);
        return userContextService.getCurrentUsername(exchange)
            .flatMap(userService::findByUsername)
            .flatMap(user -> 
                teamService.hasRole(request.getTeamId(), user.getId(), "ADMIN")
                    .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                    .switchIfEmpty(Mono.error(new AccessDeniedException(
                        "Admin access required for team " + request.getTeamId())))
                    .then(linqMilvusStoreService.storeRecord(
                        collectionName,
                        request.getRecord(),
                        request.getTargetTool(),
                        request.getModelType(),
                        request.getTextField(),
                        request.getTeamId()
                    ))
            )
            .map(ResponseEntity::ok);
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
                request.getTeamId()
        ).map(ResponseEntity::ok);
    }

    @DeleteMapping("/collections/{collectionName}")
    public Mono<ResponseEntity<Map<String, String>>> deleteCollection(
            @PathVariable String collectionName,
            @RequestParam String teamId,
            ServerWebExchange exchange) {
        log.info("Deleting collection: {}", collectionName);
        return userContextService.getCurrentUsername(exchange)
            .flatMap(userService::findByUsername)
            .flatMap(user -> 
                teamService.hasRole(teamId, user.getId(), "ADMIN")
                    .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                    .switchIfEmpty(Mono.error(new AccessDeniedException(
                        "Admin access required for team " + teamId)))
                    .then(linqMilvusStoreService.deleteCollection(collectionName, teamId))
            )
            .map(ResponseEntity::ok);
    }

    @GetMapping("/collections/{teamId}")
    public Mono<ResponseEntity<List<MilvusCollectionInfo>>> listCollections(
            @PathVariable String teamId,
            ServerWebExchange exchange) {
        log.info("Listing collections for team: {}", teamId);
        return userContextService.getCurrentUsername(exchange)
            .flatMap(userService::findByUsername)
            .flatMap(user -> 
                teamService.hasRole(teamId, user.getId(), "ADMIN")
                    .filter(hasRole -> hasRole || user.getRoles().contains("SUPER_ADMIN"))
                    .switchIfEmpty(Mono.error(new AccessDeniedException(
                        "Admin access required for team " + teamId)))
                    .then(linqMilvusStoreService.listCollections(teamId))
            )
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
} 