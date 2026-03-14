package org.lite.gateway.service;

import org.lite.gateway.entity.ResourceUpdateNotification;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface ResourceNotificationService {
    /**
     * Dispatches a notification to all active subscribers of a specific resource.
     */
    Mono<Void> dispatchNotification(ResourceUpdateNotification notification);

    /**
     * Sends a direct notification via a specific delivery configuration.
     */
    Mono<Void> sendDirectNotification(ResourceUpdateNotification notification, String deliveryChannel, String target);

    Flux<ResourceUpdateNotification> getNotificationsForSubscription(String subscriptionId);

    Mono<Void> markAsRead(String notificationId);
}
