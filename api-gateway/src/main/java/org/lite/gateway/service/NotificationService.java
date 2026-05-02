package org.lite.gateway.service;

import org.lite.gateway.dto.EmailRequestDTO;
import reactor.core.publisher.Mono;
import java.util.Map;

public interface NotificationService {
    void sendEmailAlert(String subject, String message);
    Mono<Void> sendWelcomeEmail(String to, String username);
    void sendEmail(String to, String subject, String title, String summary, String details,
                   Map<String, Object> delta, String reportUrl);
    void sendEmail(EmailRequestDTO request);
    void sendEmail(String to, String subject, String message, boolean isHtml);
    void sendSlackNotification(String color, String subject, String message);
}