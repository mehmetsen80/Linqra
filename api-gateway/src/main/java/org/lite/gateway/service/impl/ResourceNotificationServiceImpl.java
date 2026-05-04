package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.config.GatewayProperties;
import org.lite.gateway.entity.ResourceSubscription;
import org.lite.gateway.entity.ResourceUpdateNotification;
import org.lite.gateway.repository.ResourceUpdateNotificationRepository;
import org.lite.gateway.service.NotificationService;
import org.lite.gateway.service.ResourceNotificationService;
import org.lite.gateway.service.ResourceSubscriptionService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResourceNotificationServiceImpl implements ResourceNotificationService {

    private final ResourceSubscriptionService subscriptionService;
    private final ResourceUpdateNotificationRepository notificationRepository;
    private final NotificationService notificationService;
    private final WebClient.Builder webClientBuilder;
    private final GatewayProperties gatewayProperties;

    @Override
    public Mono<Void> dispatchNotification(ResourceUpdateNotification notification) {
        if (notification.getCreatedAt() == null) {
            notification.setCreatedAt(LocalDateTime.now());
        }

        // If directEmail is provided, bypass subscription lookup (mock/direct mode)
        if (notification.getDirectEmail() != null && !notification.getDirectEmail().isBlank()) {
            log.info("Direct/Mock notification dispatch for directEmail: {}", notification.getDirectEmail());
            ResourceSubscription dummySub = ResourceSubscription.builder()
                    .delivery(ResourceSubscription.DeliveryConfig.builder()
                            .emailEnabled(true)
                            .email(notification.getDirectEmail())
                            .build())
                    .build();
            return deliverNotification(notification, dummySub);
        }

        // Fetch exact domain + category + resource matches
        return subscriptionService
                .getActiveSubscriptionsForResource(notification.getDomain(), notification.getCategory(),
                        notification.getResourceId())
                .flatMap(sub -> {
                    ResourceUpdateNotification personalUpdate = ResourceUpdateNotification.builder()
                            .domain(notification.getDomain())
                            .category(notification.getCategory())
                            .resourceId(notification.getResourceId())
                            .appName(sub.getAppName())
                            .subscriptionId(sub.getId())
                            .type(notification.getType())
                            .severity(notification.getSeverity())
                            .summary(notification.getSummary())
                            .details(notification.getDetails())
                            .delta(notification.getDelta())
                            .createdAt(notification.getCreatedAt())
                            .read(false)
                            .build();

                    return notificationRepository.save(personalUpdate)
                            .flatMap(saved -> deliverNotification(saved, sub));
                })
                .then();
    }

    private Mono<Void> deliverNotification(ResourceUpdateNotification notification, ResourceSubscription sub) {
        ResourceSubscription.DeliveryConfig delivery = sub.getDelivery();
        if (delivery == null)
            return Mono.empty();

        Mono<Void> webHookMono = Mono.empty();
        if (delivery.isWebhookEnabled()) {
            String url = delivery.getWebhookUrl();
            if ((url == null || url.isBlank()) && sub.getAppName() != null) {
                // Linqra Pattern: Always route through the gateway using the app's route
                // identifier
                url = gatewayProperties.getInternalBaseUrl() + "/r/" + sub.getAppName() + "/webhook";
                log.info("Auto-discovered webhook URL for {}: {}", sub.getAppName(), url);
            }

            if (url != null && !url.isBlank()) {
                webHookMono = webClientBuilder.build()
                        .post()
                        .uri(url)
                        .bodyValue(notification)
                        .retrieve()
                        .bodyToMono(Void.class)
                        .doOnError(
                                e -> log.error("Webhook delivery failed for sub {}: {}", sub.getId(), e.getMessage()))
                        .onErrorResume(e -> Mono.empty());
            }
        }

        Mono<Void> emailMono = Mono.empty();
        if (delivery.isEmailEnabled()) {
            if (delivery.getEmail() != null && !delivery.getEmail().isBlank()) {
                emailMono = Mono.fromRunnable(() -> {
                    notificationService.sendEmail(
                            delivery.getEmail(),
                            "Linqra Alert: " + notification.getSummary(),
                            notification.getSummary(),
                            "A resource you are monitoring has been updated.",
                            notification.getDetails(),
                            notification.getDelta(),
                            notification.getReportUrl());
                }).subscribeOn(Schedulers.boundedElastic()).then();
            } else {
                log.warn("Email delivery enabled for sub {} but no target email provided", sub.getId());
            }
        }

        return Mono.when(webHookMono, emailMono);
    }

    @Override
    public Mono<Void> sendDirectNotification(ResourceUpdateNotification notification, String deliveryChannel,
            String target) {
        return deliverNotification(notification, ResourceSubscription.builder()
                .delivery(ResourceSubscription.DeliveryConfig.builder()
                        .webhookUrl("webhook".equalsIgnoreCase(deliveryChannel) ? target : null)
                        .webhookEnabled("webhook".equalsIgnoreCase(deliveryChannel))
                        .emailEnabled("email".equalsIgnoreCase(deliveryChannel))
                        .email("email".equalsIgnoreCase(deliveryChannel) ? target : null)
                        .build())
                .build());
    }

    @Override
    public Flux<ResourceUpdateNotification> getNotificationsForSubscription(String subscriptionId) {
        return notificationRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId);
    }

    @Override
    public Flux<ResourceUpdateNotification> getNotificationsForUser(String userId) {
        return subscriptionService.getSubscriptionsForUser(userId)
                .map(ResourceSubscription::getId)
                .collectList()
                .flatMapMany(notificationRepository::findBySubscriptionIdInOrderByCreatedAtDesc);
    }

    @Override
    public Mono<Void> markAsRead(String notificationId) {
        return notificationRepository.findById(notificationId)
                .flatMap(n -> {
                    n.setRead(true);
                    return notificationRepository.save(n);
                }).then();
    }

    @Override
    public Mono<Long> countUnreadNotificationsForUser(String userId) {
        return subscriptionService.getSubscriptionsForUser(userId)
                .map(ResourceSubscription::getId)
                .collectList()
                .flatMap(notificationRepository::countBySubscriptionIdInAndReadFalse);
    }
}
