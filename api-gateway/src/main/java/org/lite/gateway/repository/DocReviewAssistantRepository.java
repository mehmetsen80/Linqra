package org.lite.gateway.repository;

import org.lite.gateway.entity.DocReviewAssistant;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

@Repository
public interface DocReviewAssistantRepository extends ReactiveMongoRepository<DocReviewAssistant, String> {
    Flux<DocReviewAssistant> findByTeamId(String teamId);

    Flux<DocReviewAssistant> findByUserId(String userId);
}
