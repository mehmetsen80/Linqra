package org.lite.gateway.repository;

import org.lite.gateway.entity.ResourceUpdateNotification;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import reactor.core.publisher.Flux;

import java.util.Collection;

public interface ResourceUpdateNotificationRepository
        extends ReactiveMongoRepository<ResourceUpdateNotification, String> {
    Flux<ResourceUpdateNotification> findByResourceCategory(String resourceCategory);

    Flux<ResourceUpdateNotification> findBySubscriptionIdOrderByCreatedAtDesc(String subscriptionId);

    Flux<ResourceUpdateNotification> findBySubscriptionIdInOrderByCreatedAtDesc(Collection<String> subscriptionIds);

    Flux<ResourceUpdateNotification> findByResourceCategoryAndResourceIdOrderByCreatedAtDesc(String resourceCategory,
            String resourceId);

    Flux<ResourceUpdateNotification> findBySubscriptionIdAndReadFalse(String subscriptionId);
}
