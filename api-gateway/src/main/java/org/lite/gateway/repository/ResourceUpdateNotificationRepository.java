package org.lite.gateway.repository;

import org.lite.gateway.entity.ResourceUpdateNotification;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Collection;

public interface ResourceUpdateNotificationRepository
                extends ReactiveMongoRepository<ResourceUpdateNotification, String> {
        Flux<ResourceUpdateNotification> findByDomain(String domain);

        Flux<ResourceUpdateNotification> findBySubscriptionIdOrderByCreatedAtDesc(String subscriptionId);

        Flux<ResourceUpdateNotification> findBySubscriptionIdInOrderByCreatedAtDesc(Collection<String> subscriptionIds);

        Flux<ResourceUpdateNotification> findByDomainAndCategoryAndResourceIdOrderByCreatedAtDesc(String domain,
                        String category, String resourceId);

        Mono<Boolean> existsByDomainAndCategoryAndResourceId(String domain, String category, String resourceId);

        Flux<ResourceUpdateNotification> findBySubscriptionIdAndReadFalse(String subscriptionId);

        Mono<Long> countBySubscriptionIdInAndReadFalse(Collection<String> subscriptionIds);
}
