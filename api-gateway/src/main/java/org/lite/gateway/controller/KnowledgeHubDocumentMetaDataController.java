package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.KnowledgeHubDocumentMetaData;
import org.lite.gateway.service.KnowledgeHubDocumentMetaDataService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/documents/metadata")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubDocumentMetaDataController {
    
    private final KnowledgeHubDocumentMetaDataService metadataService;
    private final TeamContextService teamContextService;
    
    /**
     * Extract metadata from a processed document
     */
    @PostMapping("/extract/{documentId}")
    public Mono<ResponseEntity<KnowledgeHubDocumentMetaData>> extractMetadata(
            @PathVariable String documentId,
            ServerWebExchange exchange) {
        log.info("Extracting metadata for document: {}", documentId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> metadataService.extractMetadata(documentId, teamId)
                        .map(ResponseEntity::ok))
                .onErrorResume(error -> {
                    log.error("Error extracting metadata for document: {}", documentId, error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }
    
    /**
     * Get metadata extract for a document
     */
    @GetMapping("/{documentId}")
    public Mono<ResponseEntity<KnowledgeHubDocumentMetaData>> getMetadata(
            @PathVariable String documentId,
            ServerWebExchange exchange) {
        log.info("Getting metadata for document: {}", documentId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> metadataService.getMetadataExtract(documentId, teamId)
                        .map(ResponseEntity::ok))
                .onErrorResume(error -> {
                    log.error("Error getting metadata for document: {}", documentId, error);
                    return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .build());
                });
    }
    
    /**
     * Delete metadata extract for a document
     */
    @DeleteMapping("/{documentId}")
    public Mono<ResponseEntity<Void>> deleteMetadata(
            @PathVariable String documentId,
            ServerWebExchange exchange) {
        log.info("Deleting metadata for document: {}", documentId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> {
                    // Verify document belongs to team before deleting metadata
                    return metadataService.getMetadataExtract(documentId, teamId)
                            .flatMap(metadata -> metadataService.deleteMetadataExtract(documentId)
                                    .then(Mono.just(ResponseEntity.ok().<Void>build())));
                })
                .onErrorResume(error -> {
                    log.error("Error deleting metadata for document: {}", documentId, error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }
}

