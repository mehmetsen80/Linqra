package org.lite.gateway.service;

import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class NotificationService {
    private final JavaMailSender mailSender;
    private final WebClient.Builder webClientBuilder;

    @Value("${notifications.email.to:admin@localhost}")
    private String emailTo;

    @Value("${notifications.slack.webhook-url:dummy}")
    private String slackWebhookUrl;

    @Value("${notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Value("${notifications.email.from:system@linqra.com}")
    private String fromEmail;

    @Value("${notifications.slack.enabled:false}")
    private boolean slackEnabled;

    public NotificationService(JavaMailSender mailSender, WebClient.Builder webClientBuilder) {
        this.mailSender = mailSender;
        this.webClientBuilder = webClientBuilder;
    }

    public void sendEmailAlert(String subject, String message) {
        sendEmail(emailTo, subject, message, true);
    }

    public Mono<Void> sendWelcomeEmail(String to, String username) {
        if (!emailEnabled) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
            String subject = "Welcome to Linqra!";
            String message = String.format("""
                    <html>
                        <body>
                            <h2>Welcome to Linqra!</h2>
                            <p>Hello %s,</p>
                            <p>Thank you for registering. We are excited to have you on board!</p>
                            <br/>
                            <p>Best regards,</p>
                            <p>The Linqra Team</p>
                        </body>
                    </html>
                    """, username);
            sendEmail(to, subject, message, true);
        })
                .subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic())
                .then();
    }

    private void sendEmail(String to, String subject, String message, boolean isHtml) {
        if (!emailEnabled || mailSender == null) {
            log.debug("Email notifications are disabled");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true);

            if (fromEmail != null && !fromEmail.isEmpty()) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(message, isHtml);

            mailSender.send(mimeMessage);
            log.info("Email sent successfully to: {}", to);
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", to, e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    public void sendSlackNotification(String color, String subject, String message) {
        if (!slackEnabled) {
            log.debug("Slack notifications are disabled");
            return;
        }

        String payload = String.format("""
                {
                    "attachments": [{
                        "color": "%s",
                        "title": "%s",
                        "text": "%s",
                        "footer": "API Gateway Health Monitor"
                    }]
                }""", color, subject, message);

        webClientBuilder.build()
                .post()
                .uri(slackWebhookUrl)
                .bodyValue(payload)
                .retrieve()
                .bodyToMono(String.class)
                .doOnSuccess(response -> log.info("Slack notification sent successfully"))
                .doOnError(e -> log.error("Failed to send Slack notification: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty())
                .subscribe();
    }
}