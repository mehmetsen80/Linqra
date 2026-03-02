package org.lite.gateway.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.LinqRequest;
import org.lite.gateway.entity.AIAssistant;
import org.lite.gateway.entity.DocReviewAssistant;
import org.lite.gateway.repository.DocReviewAssistantRepository;
import org.lite.gateway.service.AIAssistantService;
import org.lite.gateway.service.ChatExecutionService;
import org.lite.gateway.service.DocReviewAssistantService;
import org.lite.gateway.service.KnowledgeHubDocumentService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Slf4j
public class DocReviewAssistantServiceImpl implements DocReviewAssistantService {

    private final DocReviewAssistantRepository docReviewAssistantRepository;
    private final KnowledgeHubDocumentService knowledgeHubDocumentService;
    private final ChatExecutionService chatExecutionService;
    private final AIAssistantService aiAssistantService;
    private final ObjectMapper objectMapper;

    public DocReviewAssistantServiceImpl(
            DocReviewAssistantRepository docReviewAssistantRepository,
            KnowledgeHubDocumentService knowledgeHubDocumentService,
            @Qualifier("docReviewChatExecutionService") ChatExecutionService chatExecutionService,
            AIAssistantService aiAssistantService,
            ObjectMapper objectMapper) {
        this.docReviewAssistantRepository = docReviewAssistantRepository;
        this.knowledgeHubDocumentService = knowledgeHubDocumentService;
        this.chatExecutionService = chatExecutionService;
        this.aiAssistantService = aiAssistantService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<DocReviewAssistant> createReview(DocReviewAssistant review) {
        log.info("Creating contract review for user {} and document {}", review.getUserId(), review.getDocumentId());
        review.setCreatedAt(LocalDateTime.now());
        review.setUpdatedAt(LocalDateTime.now());
        if (review.getStatus() == null) {
            review.setStatus(DocReviewAssistant.ReviewStatus.IN_PROGRESS);
        }

        // Initialize document version from the current document record
        return knowledgeHubDocumentService.getDocumentById(review.getDocumentId(), review.getTeamId())
                .map(doc -> {
                    review.setDocumentVersion(doc.getCurrentVersion());
                    return review;
                })
                .defaultIfEmpty(review)
                .flatMap(docReviewAssistantRepository::save);
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
                    if (updates.getDocumentVersion() != null)
                        existing.setDocumentVersion(updates.getDocumentVersion());

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
            String assistantId, String teamId, String username, String content) {
        log.info("Analyzing document {} for review {} with assistant {}", documentId, reviewId, assistantId);

        return Mono.zip(
                docReviewAssistantRepository.findById(reviewId),
                aiAssistantService.getAssistantById(assistantId))
                .flatMap(tuple -> {
                    DocReviewAssistant review = tuple.getT1();
                    AIAssistant assistant = tuple.getT2();

                    // determine content source
                    Mono<String> contentMono = (content != null && !content.isEmpty())
                            ? Mono.just(content)
                            : knowledgeHubDocumentService.getDocumentText(documentId, teamId);

                    return contentMono.flatMap(documentText -> {
                        if (!StringUtils.hasText(documentText)) {
                            log.warn("Document text is empty for document: {}", documentId);
                            review.setReviewPoints(Collections.emptyList());
                            review.setStatus(DocReviewAssistant.ReviewStatus.COMPLETED);
                            review.setUpdatedAt(LocalDateTime.now());
                            return docReviewAssistantRepository.save(review);
                        }

                        log.info("Document text length: {}", documentText.length());

                        // Build LinqRequest using standard assistant chat pattern
                        LinqRequest request = new LinqRequest();
                        request.setExecutedBy(username);

                        LinqRequest.Link link = new LinqRequest.Link();
                        link.setTarget("assistant");
                        link.setAction("chat");
                        request.setLink(link);

                        LinqRequest.Query query = new LinqRequest.Query();
                        query.setIntent("chat");

                        Map<String, Object> params = new HashMap<>();
                        params.put("teamId", teamId);
                        params.put("username", username);
                        query.setParams(params);

                        LinqRequest.Query.ChatConversation chat = new LinqRequest.Query.ChatConversation();
                        chat.setAssistantId(assistantId);
                        // The user message triggers the analysis.
                        // The structured JSON requirement should be in the assistant's own system
                        // prompt.
                        chat.setMessage("Please analyze this document and identify legal risks or suggestions.");

                        // Pass document context needed by DocReviewChatExecutionServiceImpl
                        Map<String, Object> context = new HashMap<>();
                        context.put("documentId", documentId);
                        context.put("documentContent", documentText);
                        context.put("teamId", teamId);
                        context.put("contractReviewId", reviewId);
                        chat.setContext(context);

                        query.setChat(chat);
                        request.setQuery(query);

                        // Execute via the specialized DocReview chat service
                        // This service will handle Agent Tasks (if any) and use the assistant's default
                        // prompt/model
                        return chatExecutionService.executeChat(request)
                                .flatMap(response -> {
                                    if (response.getChatResult() != null
                                            && response.getChatResult().getMessage() != null) {
                                        String responseText = response.getChatResult().getMessage();
                                        log.info("AI Analysis Response received (length: {})", responseText.length());

                                        // Robust JSON Extraction
                                        String jsonOnly = "";
                                        int startIndex = responseText.indexOf("[");
                                        int endIndex = responseText.lastIndexOf("]");

                                        if (startIndex != -1 && endIndex != -1 && endIndex > startIndex) {
                                            jsonOnly = responseText.substring(startIndex, endIndex + 1);
                                        } else {
                                            log.error("Could not find JSON array in response. First 100 chars: {}",
                                                    responseText.substring(0, Math.min(responseText.length(), 100)));
                                            review.setStatus(DocReviewAssistant.ReviewStatus.FAILED);
                                            review.setUpdatedAt(LocalDateTime.now());
                                            return docReviewAssistantRepository.save(review);
                                        }

                                        try {
                                            List<ReviewPointDto> dtos = objectMapper.readValue(jsonOnly,
                                                    new TypeReference<List<ReviewPointDto>>() {
                                                    });

                                            log.info("Successfully parsed {} review points", dtos.size());

                                            List<DocReviewAssistant.ReviewPoint> points = dtos.stream()
                                                    .map(dto -> DocReviewAssistant.ReviewPoint.builder()
                                                            .id(UUID.randomUUID().toString())
                                                            .originalText(dto.getOriginalText())
                                                            .verdict(dto.getVerdict())
                                                            .reasoning(dto.getReasoning())
                                                            .suggestion(dto.getSuggestion())
                                                            .suggestedReplacement(dto.getSuggestedReplacement())
                                                            .userAccepted(null)
                                                            .build())
                                                    .toList();

                                            review.setReviewPoints(points);
                                            review.setUpdatedAt(LocalDateTime.now());
                                            review.setStatus(DocReviewAssistant.ReviewStatus.COMPLETED);
                                            return docReviewAssistantRepository.save(review);

                                        } catch (Exception e) {
                                            log.error("Failed to parse AI JSON: {}", jsonOnly, e);
                                            review.setStatus(DocReviewAssistant.ReviewStatus.FAILED);
                                            review.setUpdatedAt(LocalDateTime.now());
                                            return docReviewAssistantRepository.save(review);
                                        }
                                    }
                                    return Mono.error(new RuntimeException("Empty response from AI analysis"));
                                });
                    });
                });
    }

    // DTO for JSON parsing
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    private static class ReviewPointDto {
        private String originalText;
        private String verdict;
        private String reasoning;
        private String suggestion;
        private String suggestedReplacement;
    }
}
