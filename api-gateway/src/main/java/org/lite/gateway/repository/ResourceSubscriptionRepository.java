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

        Mono<ResourceSubscription> findByUserIdAndResourceCategoryAndResourceIdAndAppName(String userId,
                        String resourceCategory,
                        String resourceId, String appName);

        Mono<ResourceSubscription> findByTeamIdAndResourceCategoryAndResourceIdAndAppName(String teamId,
                        String resourceCategory,
                        String resourceId, String appName);
}
