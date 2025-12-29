package org.lite.gateway.service;

import org.lite.gateway.entity.DocReviewAssistant;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface DocReviewAssistantService {
    Mono<DocReviewAssistant> createReview(DocReviewAssistant review);

    Mono<DocReviewAssistant> getReview(String id);

    Mono<DocReviewAssistant> updateReview(String id, DocReviewAssistant updates);

    Flux<DocReviewAssistant> getReviewsByTeam(String teamId);
}
