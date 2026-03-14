package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.UrlIngressionRequest;
import org.lite.gateway.entity.KnowledgeHubDocument;
import org.lite.gateway.service.KnowledgeHubIngressionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/ingression")
@RequiredArgsConstructor
@Slf4j
public class KnowledgeHubIngressionController {

        private final KnowledgeHubIngressionService ingressionService;

        @PostMapping("/url")
        public Mono<ResponseEntity<KnowledgeHubDocument>> ingressFromUrl(
                        @RequestBody UrlIngressionRequest request) {
                log.info("Ingression request for URL: {} (File: {}, Collection: {})",
                                request.getUrl(), request.getFileName(), request.getCollectionId());

                return ingressionService.ingressFromUrl(
                                request.getUrl(),
                                request.getFileName(),
                                request.getCollectionId(),
                                request.getTeamId(),
                                request.getContentType())
                                .map(ResponseEntity::ok);
        }
}
