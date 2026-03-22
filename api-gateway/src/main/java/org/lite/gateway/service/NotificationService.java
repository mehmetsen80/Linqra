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

    public void sendEmail(String to, String subject, String title, String summary, String details,
            java.util.Map<String, Object> delta, String reportUrl) {
        if (!emailEnabled) {
            return;
        }

        String htmlContent = String.format(
                """
                        <!DOCTYPE html>
                        <html>
                        <head>
                        <style>
                            body {
                                margin: 0;
                                padding: 0;
                                background-color: #0d1117;
                                font-family: 'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
                                color: #c9d1d9;
                            }
                            .container {
                                max-width: 600px;
                                margin: 40px auto;
                                padding: 30px;
                                background: rgba(22, 27, 34, 0.8);
                                border-radius: 16px;
                                border: 1px solid rgba(255, 255, 255, 0.1);
                                backdrop-filter: blur(10px);
                                box-shadow: 0 10px 30px rgba(0, 0, 0, 0.5);
                            }
                            .header {
                                padding-bottom: 20px;
                                border-bottom: 1px solid rgba(255, 255, 255, 0.1);
                                margin-bottom: 25px;
                                text-align: center;
                            }
                            .logo {
                                font-size: 28px;
                                font-weight: 800;
                                background: linear-gradient(135deg, #ed7534 0%%, #ff8c4c 100%%);
                                -webkit-background-clip: text;
                                -webkit-text-fill-color: transparent;
                                letter-spacing: -1px;
                            }
                            .title {
                                font-size: 22px;
                                font-weight: 700;
                                color: #ffffff;
                                margin-bottom: 15px;
                            }
                            .badge {
                                display: inline-block;
                                padding: 4px 12px;
                                border-radius: 20px;
                                font-size: 12px;
                                font-weight: 600;
                                background: rgba(237, 117, 52, 0.2);
                                color: #ff8c4c;
                                margin-bottom: 20px;
                                text-transform: uppercase;
                                letter-spacing: 1px;
                            }
                            .summary {
                                font-size: 16px;
                                line-height: 1.6;
                                color: #8b949e;
                                margin-bottom: 25px;
                            }
                            .details-box {
                                background: rgba(255, 255, 255, 0.03);
                                border-radius: 8px;
                                padding: 20px;
                                border-left: 4px solid #ed7534;
                                margin-bottom: 25px;
                            }
                            .details-title {
                                font-size: 14px;
                                font-weight: 600;
                                color: #ffffff;
                                margin-bottom: 10px;
                                text-transform: uppercase;
                            }
                            .details-content {
                                font-size: 15px;
                                line-height: 1.5;
                                white-space: pre-wrap;
                            }
                            .footer {
                                text-align: center;
                                font-size: 13px;
                                color: #484f58;
                                margin-top: 40px;
                            }
                            .cta-button {
                                display: block;
                                width: 100%%;
                                padding: 14px;
                                text-align: center;
                                background: linear-gradient(135deg, #ed7534 0%%, #d65f1f 100%%);
                                color: #ffffff;
                                text-decoration: none;
                                border-radius: 8px;
                                font-weight: 700;
                                margin-top: 30px;
                            }
                        </style>
                        </head>
                        <body>
                            <div class="container">
                                <div class="header">
                                    <span class="logo">LINQRA</span>
                                </div>
                                <div class="badge">AGENT ALERT</div>
                                <div class="title">%s</div>
                                <div class="summary">%s</div>
                                <div class="details-box">
                                    <div class="details-title">Analysis Details</div>
                                    <div class="details-content">%s</div>
                                </div>
                                %s
                                <a href="%s" class="cta-button">View Full Report</a>
                                <div class="footer">
                                    &copy; 2026 Linqra Inc. &bull; Sovereign Agent Platform
                                </div>
                            </div>
                        </body>
                        </html>
                        """,
                title, formatMarkdownToHtml(summary), formatMarkdownToHtml(details),
                (delta != null && !delta.isEmpty()) ? String.format(
                        "<div class='details-box' style='border-left-color: #ff4081;'><div class='details-title'>Detected Changes</div><div class='details-content'>%s</div></div>",
                        formatDeltaToHtml(delta)) : "",
                (reportUrl != null && !reportUrl.isEmpty()) ? reportUrl : "https://linqra.com");

        sendEmail(to, subject, htmlContent, true);
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

    private String formatDeltaToHtml(java.util.Map<String, Object> delta) {
        if (delta == null || delta.isEmpty())
            return "";

        StringBuilder sb = new StringBuilder();
        sb.append("<table style='width: 100%; border-collapse: collapse; font-size: 14px;'>");
        for (java.util.Map.Entry<String, Object> entry : delta.entrySet()) {
            sb.append("<tr style='border-bottom: 1px solid rgba(255, 255, 255, 0.05);'>");
            sb.append("<td style='padding: 8px 0; color: #8b949e; width: 30%; vertical-align: top;'>")
                    .append(entry.getKey()).append("</td>");
            sb.append("<td style='padding: 8px 0; color: #c9d1d9; font-family: monospace;'>");

            if (entry.getValue() instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> subMap = (java.util.Map<String, Object>) entry.getValue();
                sb.append("<div style='background: rgba(255,255,255,0.02); padding: 5px; border-radius: 4px;'>");
                for (java.util.Map.Entry<String, Object> subEntry : subMap.entrySet()) {
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