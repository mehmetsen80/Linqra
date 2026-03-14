package org.lite.gateway.repository;

import org.lite.gateway.entity.ResourceSubscription;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ResourceSubscriptionRepository extends ReactiveMongoRepository<ResourceSubscription, String> {
    Flux<ResourceSubscription> findByUserId(String userId);

    Flux<ResourceSubscription> findByTeamId(String teamId);

    Flux<ResourceSubscription> findByResourceCategoryAndResourceIdAndEnabledTrue(String resourceCategory,
            String resourceId);

    Mono<ResourceSubscription> findByUserIdAndResourceCategoryAndResourceId(String userId, String resourceCategory,
            String resourceId);

    Mono<ResourceSubscription> findByTeamIdAndResourceCategoryAndResourceId(String teamId, String resourceCategory,
            String resourceId);
}
