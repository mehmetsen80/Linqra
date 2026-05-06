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

            // Add High-Fidelity headers to disable Mailjet tracking
            mimeMessage.addHeader("X-Mailjet-TrackOpen", "0");
            mimeMessage.addHeader("X-Mailjet-TrackClick", "0");

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
            MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

            if (fromEmail != null && !fromEmail.isEmpty()) {
                helper.setFrom(fromEmail);
            }
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(message, isHtml);

            // Add High-Fidelity headers to disable Mailjet tracking
            mimeMessage.addHeader("X-Mailjet-TrackOpen", "0");
            mimeMessage.addHeader("X-Mailjet-TrackClick", "0");

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
        sb.append("<div style='font-family: -apple-system, BlinkMacSystemFont, \"Segoe UI\", Helvetica, Arial, sans-serif;'>");
        
        for (Map.Entry<String, Object> entry : delta.entrySet()) {
            // Only show keys that are not generic top-level wrappers if we have a single key
            boolean showKey = delta.size() > 1 || !entry.getKey().equalsIgnoreCase("alerts");
            
            if (showKey) {
                sb.append("<h3 style='margin-top: 24px; margin-bottom: 12px; color: #c9d1d9; font-size: 16px; text-transform: capitalize; border-bottom: 1px solid rgba(255,255,255,0.1); padding-bottom: 8px;'>")
                  .append(entry.getKey()).append("</h3>");
            }

            if (entry.getValue() instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> subMap = (Map<String, Object>) entry.getValue();
                sb.append("<div style='background: rgba(255,255,255,0.02); padding: 16px; border-radius: 6px; border: 1px solid rgba(255,255,255,0.05);'>");
                for (Map.Entry<String, Object> subEntry : subMap.entrySet()) {
                    sb.append("<div style='margin-bottom: 8px;'><strong style='color: #8b949e; text-transform: capitalize;'>")
                      .append(subEntry.getKey()).append(":</strong> <span style='color: #c9d1d9;'>")
                      .append(subEntry.getValue()).append("</span></div>");
                }
                sb.append("</div>");
            } else if (entry.getValue() instanceof java.util.List) {
                @SuppressWarnings("unchecked")
                java.util.List<?> list = (java.util.List<?>) entry.getValue();
                sb.append("<div style='display: flex; flex-direction: column; gap: 16px;'>");
                
                for (Object item : list) {
                    if (item instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> subMap = (Map<String, Object>) item;
                        sb.append("<div style='background: rgba(255,255,255,0.03); padding: 20px; border-radius: 8px; border: 1px solid rgba(255,255,255,0.08);'>");
                        
                        // Extract common fields for better layout
                        Object title = subMap.get("title");
                        Object date = subMap.get("date");
                        Object url = subMap.getOrDefault("url", subMap.get("link"));
                        
                        // Header row (Title + Date)
                        if (title != null) {
                            sb.append("<div style='display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px;'>");
                            sb.append("<h4 style='margin: 0; color: #58a6ff; font-size: 16px; line-height: 1.4;'>").append(title).append("</h4>");
                            if (date != null) {
                                sb.append("<span style='color: #8b949e; font-size: 12px; white-space: nowrap; margin-left: 16px; padding: 2px 8px; background: rgba(255,255,255,0.05); border-radius: 12px;'>")
                                  .append(date).append("</span>");
                            }
                            sb.append("</div>");
                        }
                        
                        // Body (Summary and other fields)
                        for (Map.Entry<String, Object> subEntry : subMap.entrySet()) {
                            String k = subEntry.getKey().toLowerCase();
                            if (k.equals("title") || k.equals("date") || k.equals("url") || k.equals("link")) continue;
                            
                            if (k.equals("summary") || k.equals("description")) {
                                sb.append("<div style='color: #c9d1d9; font-size: 14px; line-height: 1.6; margin-bottom: 16px;'>")
                                  .append(subEntry.getValue()).append("</div>");
                            } else {
                                sb.append("<div style='margin-bottom: 8px; font-size: 13px;'><strong style='color: #8b949e; text-transform: capitalize;'>")
                                  .append(subEntry.getKey()).append(":</strong> <span style='color: #c9d1d9;'>")
                                  .append(subEntry.getValue()).append("</span></div>");
                            }
                        }
                        
                        // Footer (URL Action)
                        if (url != null) {
                            sb.append("<div style='margin-top: 16px; padding-top: 16px; border-top: 1px solid rgba(255,255,255,0.05);'>");
                            sb.append("<a href='").append(url)
                              .append("' style='display: inline-block; color: #ed7534; text-decoration: none; font-size: 13px; font-weight: 600;'>View Announcement &rarr;</a>");
                            sb.append("</div>");
                        }
                        
                        sb.append("</div>");
                    } else {
                        sb.append("<div style='background: rgba(255,255,255,0.02); padding: 12px; border-radius: 4px; color: #c9d1d9;'>")
                          .append(item).append("</div>");
                    }
                }
                sb.append("</div>");
            } else {
                sb.append("<div style='color: #c9d1d9; font-size: 14px; line-height: 1.6;'>")
                  .append(entry.getValue()).append("</div>");
            }
        }
        sb.append("</div>");
        return sb.toString();
    }
}
