package org.lite.gateway.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.annotation.AuditLog;
import org.lite.gateway.dto.EmailRequestDTO;
import org.lite.gateway.enums.AuditActionType;
import org.lite.gateway.enums.AuditEventType;
import org.lite.gateway.enums.AuditResourceType;
import org.lite.gateway.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Map;

/**
 * Controller for managing and dispatching emails through the Linqra Gateway.
 */
@RestController
@RequestMapping("/api/mail")
@RequiredArgsConstructor
@Slf4j
public class MailController {

    private final NotificationService notificationService;

    @PostMapping("/send")
    @AuditLog(eventType = AuditEventType.NOTIFICATION_SENT, action = AuditActionType.CREATE, resourceType = AuditResourceType.NOTIFICATION, reason = "External mail dispatch request", logOnSuccessOnly = true)
    public Mono<ResponseEntity<Map<String, Object>>> sendEmail(@RequestBody EmailRequestDTO request) {
        log.info("Received request to send email to: {}", request.getTo());

        return Mono.fromRunnable(() -> notificationService.sendEmail(request))
                .subscribeOn(Schedulers.boundedElastic())
                .then(Mono.just(ResponseEntity.ok(Map.<String, Object>of(
                        "success", true,
                        "message", "Email dispatched successfully",
                        "to", request.getTo()))))
                .onErrorResume(e -> {
                    log.error("Failed to dispatch email: {}", e.getMessage());
                    return Mono.just(ResponseEntity.internalServerError().body(Map.<String, Object>of(
                            "success", false,
                            "message", "Failed to dispatch email: " + e.getMessage())));
                });
    }
}
