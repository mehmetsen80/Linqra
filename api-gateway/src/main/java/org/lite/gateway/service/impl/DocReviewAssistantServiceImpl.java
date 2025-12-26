package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.DocReviewAssistant;
import org.lite.gateway.repository.DocReviewAssistantRepository;
import org.lite.gateway.service.DocReviewAssistantService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class DocReviewAssistantServiceImpl implements DocReviewAssistantService {

    private final DocReviewAssistantRepository docReviewAssistantRepository;

    @Override
    public Mono<DocReviewAssistant> createReview(DocReviewAssistant review) {
        log.info("Creating contract review for user {} and document {}", review.getUserId(), review.getDocumentId());
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        if (review.getStatus() == null) {
            review.setStatus(DocReviewAssistant.ReviewStatus.IN_PROGRESS);
        }
        return docReviewAssistantRepository.save(review);
    }

    @Override
    public Mono<DocReviewAssistant> getReview(String id) {
        return docReviewAssistantRepository.findById(id);
    }

    @Override
    public Mono<DocReviewAssistant> updateReview(String id, DocReviewAssistant updates) {
        return docReviewAssistantRepository.findById(id)
                .flatMap(existing -> {
                    if (updates.getStatus() != null)
                        existing.setStatus(updates.getStatus());
                    if (updates.getReviewPoints() != null)
                        existing.setReviewPoints(updates.getReviewPoints());
                    if (updates.getConversationId() != null)
                        existing.setConversationId(updates.getConversationId());

                    existing.setUpdatedAt(LocalDateTime.now());
                    return docReviewAssistantRepository.save(existing);
                });
    }

    @Override
    public Flux<DocReviewAssistant> getReviewsByTeam(String teamId) {
        return docReviewAssistantRepository.findByTeamId(teamId);
    }
}
