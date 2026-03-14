package org.lite.gateway.service.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.entity.ResourceSubscription;
import org.lite.gateway.entity.ResourceUpdateNotification;
import org.lite.gateway.repository.ResourceUpdateNotificationRepository;
import org.lite.gateway.service.ResourceNotificationService;
import org.lite.gateway.service.ResourceSubscriptionService;
import org.lite.gateway.service.NotificationService;
import org.lite.gateway.service.UserService;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
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
    private final NotificationService systemNotificationService;
    private final UserService userService;
    private final WebClient.Builder webClientBuilder;

    private final MessageChannel notificationMessageChannel;

    @Override
    public Mono<Void> dispatchNotification(ResourceUpdateNotification notification) {
        if (notification.getCreatedAt() == null) {
            notification.setCreatedAt(LocalDateTime.now());
        }

        // Fetch exact category + resource matches
        return subscriptionService
                .getActiveSubscriptionsForResource(notification.getResourceCategory(), notification.getResourceId())
                .flatMap(sub -> {
                    ResourceUpdateNotification personalUpdate = ResourceUpdateNotification.builder()
                            .resourceCategory(notification.getResourceCategory())
                            .resourceId(notification.getResourceId())
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
        if (delivery.getWebhookUrl() != null && !delivery.getWebhookUrl().isBlank()) {
            webHookMono = webClientBuilder.build()
                    .post()
                    .uri(delivery.getWebhookUrl())
                    .bodyValue(notification)
                    .retrieve()
                    .bodyToMono(Void.class)
                    .doOnError(e -> log.error("Webhook delivery failed for sub {}: {}", sub.getId(), e.getMessage()))
                    .onErrorResume(e -> Mono.empty());
        }

        Mono<Void> wsMono = Mono.fromRunnable(() -> {
            if (delivery.isPushEnabled()) {
                notificationMessageChannel.send(MessageBuilder.withPayload(notification).build());
            }
        });

        Mono<Void> emailMono = Mono.empty();
        if (delivery.isEmailEnabled()) {
            if (delivery.getOverrideEmail() != null && !delivery.getOverrideEmail().isBlank()) {
                emailMono = Mono.fromRunnable(() -> {
                    systemNotificationService.sendPremiumEmail(
                            delivery.getOverrideEmail(),
                            "Linqra Alert: " + notification.getSummary(),
                            notification.getSummary(),
                            "A resource you are monitoring has been updated.",
                            notification.getDetails(),
                            notification.getDelta());
                }).subscribeOn(Schedulers.boundedElastic()).then();
            } else if (sub.getUserId() != null) {
                emailMono = userService.findById(sub.getUserId())
                        .flatMap(user -> {
                            if (user.getEmail() != null && !user.getEmail().isBlank()) {
                                return Mono.fromRunnable(() -> {
                                    systemNotificationService.sendPremiumEmail(
                                            user.getEmail(),
                                            "Linqra Alert: " + notification.getSummary(),
                                            notification.getSummary(),
                                            "A resource you are monitoring has been updated.",
                                            notification.getDetails(),
                                            notification.getDelta());
                                }).subscribeOn(Schedulers.boundedElastic());
                            }
                            log.warn("Subscriber {} has no email configured", sub.getUserId());
                            return Mono.empty();
                        }).then();
            } else {
                // Fallback for team or system-wide alerts
                emailMono = Mono.fromRunnable(() -> {
                    systemNotificationService.sendEmailAlert(
                            "Linqra Alert: " + notification.getSummary(),
                            notification.getDetails());
                }).subscribeOn(Schedulers.boundedElastic()).then();
            }
        }

        return Mono.when(webHookMono, wsMono, emailMono);
    }

    @Override
    public Mono<Void> sendDirectNotification(ResourceUpdateNotification notification, String deliveryChannel,
            String target) {
        return deliverNotification(notification, ResourceSubscription.builder()
                .delivery(ResourceSubscription.DeliveryConfig.builder()
                        .webhookUrl("webhook".equalsIgnoreCase(deliveryChannel) ? target : null)
                        .pushEnabled("websocket".equalsIgnoreCase(deliveryChannel))
                        .build())
                .build());
    }

    @Override
    public Flux<ResourceUpdateNotification> getNotificationsForSubscription(String subscriptionId) {
        return notificationRepository.findBySubscriptionIdOrderByCreatedAtDesc(subscriptionId);
    }

    @Override
    public Mono<Void> markAsRead(String notificationId) {
        return notificationRepository.findById(notificationId)
                .flatMap(n -> {
                    n.setRead(true);
                    return notificationRepository.save(n);
                }).then();
    }
}
