package org.lite.gateway.repository;

import org.lite.gateway.entity.ResourceSubscription;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ResourceSubscriptionRepository extends ReactiveMongoRepository<ResourceSubscription, String> {
        Flux<ResourceSubscription> findByUserId(String userId);

        Flux<ResourceSubscription> findByTeamId(String teamId);

        Flux<ResourceSubscription> findByDomainAndCategoryAndResourceIdAndEnabledTrue(String domain, String category,
                        String resourceId);

        Mono<ResourceSubscription> findByUserIdAndDomainAndCategoryAndResourceIdAndAppName(String userId,
                        String domain, String category,
                        String resourceId, String appName);

        Mono<ResourceSubscription> findByTeamIdAndDomainAndCategoryAndResourceIdAndAppName(String teamId,
                        String domain, String category,
                        String resourceId, String appName);
}
