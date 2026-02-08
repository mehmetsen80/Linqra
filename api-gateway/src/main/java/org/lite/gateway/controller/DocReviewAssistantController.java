package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.DocReviewAssistant;
import org.lite.gateway.service.DocReviewAssistantService;
import org.lite.gateway.service.TeamContextService;
import org.lite.gateway.service.UserContextService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/api/doc-reviews")
@RequiredArgsConstructor
@Slf4j
public class DocReviewAssistantController {

    private final DocReviewAssistantService docReviewAssistantService;
    private final UserContextService userContextService;

    private final TeamContextService teamContextService;

    @PostMapping
    public Mono<ResponseEntity<DocReviewAssistant>> createReview(@RequestBody DocReviewAssistant review,
            ServerWebExchange exchange) {
        return userContextService.getCurrentUsername(exchange)
                .flatMap(username -> teamContextService.getTeamFromContext(exchange)
                        .flatMap(teamId -> {
                            review.setUserId(username);
                            review.setTeamId(teamId);
                            return docReviewAssistantService.createReview(review);
                        }))
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.error(new RuntimeException("Context not found")));
    }

    @GetMapping("/{id}")
    public Mono<ResponseEntity<DocReviewAssistant>> getReview(@PathVariable String id) {
        return docReviewAssistantService.getReview(id)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}")
    public Mono<ResponseEntity<DocReviewAssistant>> updateReview(@PathVariable String id,
            @RequestBody DocReviewAssistant updates) {
        return docReviewAssistantService.updateReview(id, updates)
                .map(ResponseEntity::ok)
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    @GetMapping("/team/{teamId}")
    public Flux<DocReviewAssistant> getReviewsByTeam(@PathVariable String teamId) {
        return docReviewAssistantService.getReviewsByTeam(teamId);
    }

    @PostMapping("/{id}/analyze")
    public Mono<ResponseEntity<DocReviewAssistant>> analyzeDocument(
            @PathVariable String id,
            @RequestBody Map<String, String> request,
            ServerWebExchange exchange) {

        String documentId = request.get("documentId");
        String assistantId = request.get("assistantId");

        log.info("Starting document analysis for review {} with document {} using assistant {}",
                id, documentId, assistantId);

        return userContextService.getCurrentUsername(exchange)
                .flatMap(username -> teamContextService.getTeamFromContext(exchange)
                        .flatMap(teamId -> docReviewAssistantService.analyzeDocument(
                                id, documentId, assistantId, teamId, username)))
                .map(ResponseEntity::ok)
                .switchIfEmpty(Mono.just(ResponseEntity.notFound().build()))
                .onErrorResume(error -> {
                    log.error("Error analyzing document: {}", error.getMessage(), error);
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }
}
