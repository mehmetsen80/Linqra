package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ResourceDeltaAnalysisRequest;
import org.lite.gateway.dto.ResourceDeltaContentResponse;
import org.lite.gateway.repository.KnowledgeHubDocumentRepository;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.lite.gateway.service.TeamContextService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/kh/sync")
@Slf4j
@RequiredArgsConstructor
public class KnowledgeHubSyncController {

    private final KnowledgeHubDocumentRepository documentRepository;
    private final KnowledgeHubDocumentService documentService;
    private final TeamContextService teamContextService;

    @GetMapping("/documents/{id}/exists")
    public Mono<Boolean> checkDocumentExists(@PathVariable String id) {
        log.info("🔍 Received existence check for documentId: {}", id);
        return documentRepository.findByDocumentId(id)
                .map(doc -> true)
                .defaultIfEmpty(false);
    }

    @PostMapping("/delta-content")
    public Mono<ResourceDeltaContentResponse> fetchDeltaContent(
            @RequestBody ResourceDeltaAnalysisRequest request,
            ServerWebExchange exchange) {
        log.info("🔍 Received Delta Content request for resource: {}", request.getResourceId());

        return teamContextService.getTeamFromContext(exchange)
                .flatMap(teamId -> documentService.fetchDeltaContent(
                        request.getOldDocumentId(),
                        request.getNewDocumentId(),
                        teamId,
                        request.getResourceId(),
                        request.getResourceCategory(),
                        request.getCategories()))
                .onErrorResume(error -> {
                    log.error("❌ Delta Content fetch failed: {}", error.getMessage());
                    return Mono.error(new RuntimeException("Fetch failed: " + error.getMessage()));
                });
    }
}
