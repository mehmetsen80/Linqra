package org.lite.gateway.service.impl;

import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.lite.gateway.dto.EmailRequestDTO;
import org.lite.gateway.service.NotificationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

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

    @Value("${linqra.base-url:https://linqra.com}")
    private String baseUrl;

    @Override
    public void sendEmailAlert(String subject, String message) {
        sendEmail(emailTo, subject, message, true);
    }

    @Override
    public Mono<Void> sendWelcomeEmail(String to, String username) {
        if (!emailEnabled) {
            return Mono.empty();
        }
        return Mono.fromRunnable(() -> {
            try {
                String template = loadTemplate("templates/welcome-email.html");
                String body = template.replace("[USERNAME]", username);
                sendEmail(to, "Welcome to Linqra!", body, true);
            } catch (Exception e) {
                log.error("Failed to send welcome email to {}: {}", to, e.getMessage());
            }
        })
                .subscribeOn(Schedulers.boundedElastic())
                .then();
    }

    @Override
    public void sendEmail(String to, String subject, String title, String summary, String details,
            Map<String, Object> delta, String reportUrl) {
        if (!emailEnabled) {
            return;
        }

        try {
            String template = loadTemplate("templates/alert-email.html");

            String changesHtml = (delta != null && !delta.isEmpty()) ? String.format(
                    "<div class='details-box' style='border-left-color: #ff4081;'><div class='details-title'>Detected Changes</div><div class='details-content'>%s</div></div>",
                    formatDeltaToHtml(delta)) : "";

            String body = template
                    .replace("[TITLE]", title)
                    .replace("[SUMMARY]", formatMarkdownToHtml(summary))
                    .replace("[DETAILS]", formatMarkdownToHtml(details))
                    .replace("[CHANGES]", changesHtml)
                    .replace("[REPORT_URL]", (reportUrl != null && !reportUrl.isEmpty()) ? reportUrl : baseUrl);

            sendEmail(to, subject, body, true);
        } catch (Exception e) {
            log.error("Failed to load or send alert email to {}: {}", to, e.getMessage());
        }
    }

    private String loadTemplate(String path) throws java.io.IOException {
        try (var is = new ClassPathResource(path).getInputStream()) {
            return StreamUtils.copyToString(is, StandardCharsets.UTF_8);
        }
    }

    @Override
    public void sendEmail(EmailRequestDTO request) {
        if (!emailEnabled || mailSender == null) {
            log.debug("Email notifications are disabled");
            return;
        }

        try {
            MimeMessage mimeMessage = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            String from = (request.getFrom() != null && !request.getFrom().isEmpty())
                    ? request.getFrom()
                    : fromEmail;

            helper.setFrom(from);
            helper.setTo(request.getTo());
            helper.setSubject(request.getSubject());
            helper.setText(request.getBody(), request.isHtml());

            if (request.getCc() != null && !request.getCc().isEmpty()) {
                helper.setCc(request.getCc().toArray(new String[0]));
            }
            if (request.getBcc() != null && !request.getBcc().isEmpty()) {
                helper.setBcc(request.getBcc().toArray(new String[0]));
            }

            mailSender.send(mimeMessage);
            log.info("Email sent successfully to: {}", request.getTo());
        } catch (Exception e) {
            log.error("Failed to send email to {}: {}", request.getTo(), e.getMessage());
            throw new RuntimeException("Failed to send email", e);
        }
    }

    @Override
    public void sendEmail(String to, String subject, String message, boolean isHtml) {
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

    @Override
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

    private String formatMarkdownToHtml(String text) {
        if (text == null || text.isBlank())
            return "";

        // First handle bold **text** -> <b>text</b>
        String processed = text.replaceAll("\\*\\*(.*?)\\*\\*", "<b>$1</b>");

        // Iterate line by line to handle headers and lists
        String[] lines = processed.split("\n");
        StringBuilder sb = new StringBuilder();
        boolean inList = false;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                if (inList) {
                    sb.append("</ul>");
                    inList = false;
                }
                sb.append("<br/>");
                continue;
            }

            if (line.startsWith("### ")) {
                if (inList) {
                    sb.append("</ul>");
                    inList = false;
                }
                sb.append("<h3 style='color: #ffffff; margin-top: 20px; margin-bottom: 10px;'>")
                        .append(line.substring(4)).append("</h3>");
            } else if (line.startsWith("- ")) {
                if (!inList) {
                    sb.append("<ul style='padding-left: 20px; margin: 10px 0;'>");
                    inList = true;
                }
                sb.append("<li style='margin-bottom: 5px;'>").append(line.substring(2)).append("</li>");
            } else {
                if (inList) {
                    sb.append("</ul>");
                    inList = false;
                }
                sb.append(line);
                if (i < lines.length - 1) {
                    sb.append("<br/>");
                }
            }
        }

        if (inList) {
            sb.append("</ul>");
        }

        return sb.toString();
    }

    private String formatDeltaToHtml(Map<String, Object> delta) {
        if (delta == null || delta.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<table style='width: 100%; border-collapse: collapse; font-size: 14px;'>");
        for (Map.Entry<String, Object> entry : delta.entrySet()) {
            sb.append("<tr style='border-bottom: 1px solid rgba(255, 255, 255, 0.05);'>");
            sb.append("<td style='padding: 8px 0; color: #8b949e; width: 30%; vertical-align: top;'>")
                    .append(entry.getKey()).append("</td>");
            sb.append("<td style='padding: 8px 0; color: #c9d1d9; font-family: monospace;'>");

            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) entry.getValue();
                sb.append("<div style='background: rgba(255,255,255,0.02); padding: 5px; border-radius: 4px;'>");
                for (Map.Entry<String, Object> subEntry : subMap.entrySet()) {
                    sb.append("<div><span style='color: #6e7681;'>").append(subEntry.getKey()).append(":</span> ")
                            .append(subEntry.getValue()).append("</div>");
                }
                sb.append("</div>");
            } else {
                sb.append(entry.getValue());
            }

            sb.append("</td></tr>");
        }
        sb.append("</table>");
        return sb.toString();
    }
}
