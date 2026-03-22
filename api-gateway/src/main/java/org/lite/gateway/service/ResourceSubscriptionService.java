package org.lite.gateway.service;

import org.lite.gateway.entity.ResourceSubscription;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ResourceSubscriptionService {
        Mono<ResourceSubscription> subscribeUser(String userId, String resourceCategory, String resourceId,
                        String appName,
                        ResourceSubscription.DeliveryConfig delivery);

        Mono<ResourceSubscription> subscribeTeam(String teamId, String resourceCategory, String resourceId,
                        String appName,
                        ResourceSubscription.DeliveryConfig delivery);

        Mono<Void> unsubscribe(String subscriptionId);

        Flux<ResourceSubscription> getSubscriptionsForUser(String userId);

        Flux<ResourceSubscription> getSubscriptionsForTeam(String teamId);

        Flux<ResourceSubscription> getActiveSubscriptionsForResource(String resourceCategory, String resourceId);

        Mono<ResourceSubscription> updateSubscription(String subscriptionId,
                        ResourceSubscription.DeliveryConfig delivery,
                        boolean enabled);
}
