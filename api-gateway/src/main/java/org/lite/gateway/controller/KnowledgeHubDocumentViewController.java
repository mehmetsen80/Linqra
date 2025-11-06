package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.lite.gateway.service.S3Service;
import org.lite.gateway.service.TeamContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubDocumentViewController {
    
    private final KnowledgeHubDocumentService documentService;
    private final S3Service s3Service;
    private final TeamContextService teamContextService;
    
    /**
     * Get document by ID
     */
    @GetMapping("/view/{documentId}")
    public Mono<ResponseEntity<KnowledgeHubDocument>> getDocumentById(
            @PathVariable String documentId,
            ServerWebExchange exchange) {
        log.info("Getting document: {}", documentId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> documentService.getDocumentById(documentId, teamId)
                        .map(ResponseEntity::ok))
                .onErrorResume(error -> {
                    log.error("Error getting document", error);
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .build());
                });
    }
    
    /**
     * Generate presigned URL for downloading
     */
    @GetMapping("/view/{documentId}/download")
    public Mono<ResponseEntity<Object>> generateDownloadUrl(
            @PathVariable String documentId,
            ServerWebExchange exchange) {
        log.info("Generating download URL for document: {}", documentId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> documentService.getDocumentById(documentId, teamId)
                        .flatMap(document -> s3Service.generatePresignedDownloadUrl(document.getS3Key())
                                .map(url -> {
                                    Map<String, String> response = Map.of(
                                            "downloadUrl", url,
                                            "fileName", document.getFileName()
                                    );
                                    return ResponseEntity.<Object>ok(response);
                                })))
                .onErrorResume(error -> {
                    log.error("Error generating download URL", error);
                    return Mono.just(ResponseEntity.notFound().build());
                });
    }
    
    /**
     * Generate presigned URL for downloading processed JSON
     */
    @GetMapping("/view/{documentId}/processed/download")
    public Mono<ResponseEntity<Object>> generateProcessedJsonDownloadUrl(
            @PathVariable String documentId,
            ServerWebExchange exchange) {
        log.info("Generating download URL for processed JSON of document: {}", documentId);
        
        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> documentService.getDocumentById(documentId, teamId)
                        .filter(document -> document.getProcessedS3Key() != null && !document.getProcessedS3Key().isEmpty())
                        .switchIfEmpty(Mono.error(new RuntimeException("Processed JSON not available for this document")))
                        .flatMap(document -> s3Service.generatePresignedDownloadUrl(document.getProcessedS3Key())
                                .map(url -> {
                                    String fileName = document.getFileName();
                                    String jsonFileName = fileName.endsWith(".json") 
                                            ? fileName 
                                            : fileName.substring(0, fileName.lastIndexOf('.')) + "_processed.json";
                                    Map<String, String> response = Map.of(
                                            "downloadUrl", url,
                                            "fileName", jsonFileName
                                    );
                                    return ResponseEntity.<Object>ok(response);
                                })))
                .onErrorResume(error -> {
                    log.error("Error generating processed JSON download URL", error);
                    if (error instanceof RuntimeException && error.getMessage().contains("not available")) {
                        return Mono.just(ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", error.getMessage())));
                    }
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(Map.of("error", "Failed to generate download URL")));
                });
    }
}

