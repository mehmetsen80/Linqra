package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.ResourceNotificationDispatchDTO;
import org.lite.gateway.entity.ResourceUpdateNotification;
import org.lite.gateway.service.ResourceNotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Slf4j
public class ResourceNotificationController {

    private final ResourceNotificationService notificationService;

    @GetMapping("/{subscriptionId}")
    public Flux<ResourceUpdateNotification> getNotifications(@PathVariable String subscriptionId) {
        return notificationService.getNotificationsForSubscription(subscriptionId);
    }

    @PostMapping("/{notificationId}/read")
    public Mono<ResponseEntity<Void>> markAsRead(@PathVariable String notificationId) {
        return notificationService.markAsRead(notificationId)
                .then(Mono.just(ResponseEntity.ok().build()));
    }

    @PostMapping("/dispatch")
    public Mono<ResponseEntity<Void>> dispatchNotification(@RequestBody ResourceNotificationDispatchDTO dto) {
        log.info("Production dispatch requested for resource category {} and ID {}",
                dto.getResourceCategory(), dto.getResourceId());

        ResourceUpdateNotification notification = ResourceUpdateNotification.builder()
                .resourceCategory(dto.getResourceCategory())
                .resourceId(dto.getResourceId())
                .type(dto.getType())
                .severity(dto.getSeverity())
                .summary(dto.getSummary())
                .details(dto.getDetails())
                .directEmail(dto.getDirectEmail())
                .reportUrl(dto.getReportUrl())
                .delta(dto.getDelta())
                .build();

        return notificationService.dispatchNotification(notification)
                .then(Mono.just(ResponseEntity.ok().build()));
    }
}
