package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ResourceSubscription;
import org.lite.gateway.repository.ResourceSubscriptionRepository;
import org.lite.gateway.service.ResourceSubscriptionService;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceSubscriptionServiceImpl implements ResourceSubscriptionService {

    private final ResourceSubscriptionRepository subscriptionRepository;

    @Override
    public Mono<ResourceSubscription> subscribeUser(String userId, String resourceCategory, String resourceId,
            ResourceSubscription.DeliveryConfig delivery) {
        return subscriptionRepository.findByUserIdAndResourceCategoryAndResourceId(userId, resourceCategory, resourceId)
                .flatMap(existing -> {
                    existing.setDelivery(delivery);
                    existing.setEnabled(true);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return subscriptionRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    ResourceSubscription sub = ResourceSubscription.builder()
                            .userId(userId)
                            .resourceCategory(resourceCategory)
                            .resourceId(resourceId)
                            .delivery(delivery)
                            .enabled(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return subscriptionRepository.save(sub);
                }));
    }

    @Override
    public Mono<ResourceSubscription> subscribeTeam(String teamId, String resourceCategory, String resourceId,
            ResourceSubscription.DeliveryConfig delivery) {
        return subscriptionRepository.findByTeamIdAndResourceCategoryAndResourceId(teamId, resourceCategory, resourceId)
                .flatMap(existing -> {
                    existing.setDelivery(delivery);
                    existing.setEnabled(true);
                    existing.setUpdatedAt(LocalDateTime.now());
                    return subscriptionRepository.save(existing);
                })
                .switchIfEmpty(Mono.defer(() -> {
                    ResourceSubscription sub = ResourceSubscription.builder()
                            .teamId(teamId)
                            .resourceCategory(resourceCategory)
                            .resourceId(resourceId)
                            .delivery(delivery)
                            .enabled(true)
                            .createdAt(LocalDateTime.now())
                            .updatedAt(LocalDateTime.now())
                            .build();
                    return subscriptionRepository.save(sub);
                }));
    }

    @Override
    public Mono<Void> unsubscribe(String subscriptionId) {
        return subscriptionRepository.deleteById(subscriptionId);
    }

    @Override
    public Flux<ResourceSubscription> getSubscriptionsForUser(String userId) {
        return subscriptionRepository.findByUserId(userId);
    }

    @Override
    public Flux<ResourceSubscription> getSubscriptionsForTeam(String teamId) {
        return subscriptionRepository.findByTeamId(teamId);
    }

    @Override
    public Flux<ResourceSubscription> getActiveSubscriptionsForResource(String resourceCategory, String resourceId) {
        return subscriptionRepository.findByResourceCategoryAndResourceIdAndEnabledTrue(resourceCategory, resourceId);
    }

    @Override
    public Mono<ResourceSubscription> updateSubscription(String subscriptionId,
            ResourceSubscription.DeliveryConfig delivery,
            boolean enabled) {
        return subscriptionRepository.findById(subscriptionId)
                .flatMap(sub -> {
                    sub.setDelivery(delivery);
                    sub.setEnabled(enabled);
                    sub.setUpdatedAt(LocalDateTime.now());
                    return subscriptionRepository.save(sub);
                });
    }
}
