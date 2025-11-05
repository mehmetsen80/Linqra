package org.lite.gateway.config;

import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessagePreparator;

import java.util.Properties;

@Configuration
public class EmailConfig {
    @Value("${notifications.email.host}")
    private String host;

    @Value("${notifications.email.port}")
    private int port;

    @Value("${notifications.email.username}")
    private String username;

    @Value("${notifications.email.password}")
    private String password;

    @Value("${notifications.email.enabled:false}")
    private boolean emailEnabled;

    @Bean
    public JavaMailSender javaMailSender() {
        if (!emailEnabled) {
            // Create a dummy JavaMailSender that does nothing when email is disabled
            return new JavaMailSenderImpl() {
                @Override
                public void send(@NotNull SimpleMailMessage simpleMessage) {
                    // Do nothing - email is disabled
                }
                
                @Override
                public void send(@NotNull SimpleMailMessage... simpleMessages) {
                    // Do nothing - email is disabled
                }
                
                @Override
                public void send(@NotNull MimeMessagePreparator mimeMessagePreparator) {
                    // Do nothing - email is disabled
                }
                
                @Override
                public void send(@NotNull MimeMessagePreparator... mimeMessagePreparators) {
                    // Do nothing - email is disabled
                }
            };
        }

        // Create the real JavaMailSender when email is enabled
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(host);
        mailSender.setPort(port);
        mailSender.setUsername(username);
        mailSender.setPassword(password);

        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.debug", "false");

        return mailSender;
    }
} 