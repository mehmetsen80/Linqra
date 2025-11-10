package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.service.KnowledgeHubDocumentEmbeddingService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubDocumentEmbeddingController {

    private final TeamContextService teamContextService;
    private final KnowledgeHubDocumentEmbeddingService embeddingService;

    /**
     * Trigger embedding workflow for a document
     */
    @PostMapping("/{documentId}/embed")
    public Mono<ResponseEntity<Void>> embedDocument(@PathVariable String documentId,
                                                    ServerWebExchange exchange) {
        log.info("API request to trigger embedding for document {}", documentId);

        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> embeddingService.embedDocument(documentId, teamId)
                        .thenReturn(ResponseEntity.accepted().<Void>build()))
                .onErrorResume(error -> {
                    log.error("Error triggering embedding for document {}: {}", documentId, error.getMessage(), error);
                    return Mono.just(ResponseEntity.status(HttpStatus.BAD_REQUEST).build());
                });
    }
}

