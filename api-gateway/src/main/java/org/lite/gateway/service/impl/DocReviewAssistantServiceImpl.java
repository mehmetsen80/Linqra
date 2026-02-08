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
import java.util.List;

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

    @Override
    public Mono<DocReviewAssistant> analyzeDocument(String reviewId, String documentId,
            String assistantId, String teamId, String username) {
        log.info("Analyzing document {} for review {} with assistant {}", documentId, reviewId, assistantId);

        return docReviewAssistantRepository.findById(reviewId)
                .flatMap(review -> {
                    // TODO: In production, this would:
                    // 1. Fetch the document content from Knowledge Hub
                    // 2. Send to AI Assistant with a system prompt for document review
                    // 3. Parse the AI response into ReviewPoint objects
                    // 4. Save and return the updated review

                    // For demo purposes, return mock review points
                    log.info("Document analysis initiated for review: {}", reviewId);

                    // Create mock review points for demo
                    List<DocReviewAssistant.ReviewPoint> mockPoints = List.of(
                            DocReviewAssistant.ReviewPoint.builder()
                                    .id("rp-1")
                                    .originalText("Section 1.1 - Definitions")
                                    .verdict("WARNING")
                                    .reasoning(
                                            "The definitions section may benefit from additional clarity on key terms.")
                                    .suggestion("Consider adding definitions for 'Party A' and 'Party B' explicitly.")
                                    .userAccepted(null)
                                    .build(),
                            DocReviewAssistant.ReviewPoint.builder()
                                    .id("rp-2")
                                    .originalText("Section 3.2 - Payment Terms")
                                    .verdict("REJECT")
                                    .reasoning(
                                            "The payment terms specify NET-60 which may impact cash flow significantly.")
                                    .suggestion("Negotiate for NET-30 payment terms to improve cash flow.")
                                    .userAccepted(null)
                                    .build(),
                            DocReviewAssistant.ReviewPoint.builder()
                                    .id("rp-3")
                                    .originalText("Section 5.1 - Liability Clause")
                                    .verdict("ACCEPT")
                                    .reasoning("The liability cap is reasonable and aligns with industry standards.")
                                    .suggestion("No changes recommended.")
                                    .userAccepted(null)
                                    .build());

                    review.setReviewPoints(mockPoints);
                    review.setUpdatedAt(LocalDateTime.now());
                    return docReviewAssistantRepository.save(review);
                });
    }
}
