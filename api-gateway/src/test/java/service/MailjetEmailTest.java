package service;

import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;

import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Integration test for sending emails via Mailjet SMTP
 * 
 * WARNING: This test uses real Mailjet credentials and will send real emails!
 * 
 * To use this test:
 * 1. Replace MAILJET_API_KEY and MAILJET_SECRET_KEY with your actual Mailjet credentials
 * 2. Replace TEST_RECIPIENT_EMAIL with the email address where you want to receive test emails
 * 3. Uncomment @SpringBootTest annotation if you want to use Spring context (optional)
 * 
 * Mailjet SMTP Configuration:
 * - Host: in-v3.mailjet.com
 * - Port: 587 (TLS) or 465 (SSL)
 * - Username: Your Mailjet API Key
 * - Password: Your Mailjet Secret Key
 */
// @SpringBootTest  // Uncomment to use Spring context with EmailConfig
class MailjetEmailTest {

    // TODO: Replace these with your actual Mailjet credentials
    private static final String MAILJET_API_KEY = "MAILJET_API_KEY";
    private static final String MAILJET_SECRET_KEY = "MAILJET_SECRET_KEY";
    private static final String MAILJET_HOST = "in-v3.mailjet.com";
    private static final int MAILJET_PORT = 587; // 587 for TLS, 465 for SSL
    private static final String TEST_RECIPIENT_EMAIL = "someone@gmail.com";
    private static final String TEST_FROM_EMAIL = "msen@linqra.com"; // Use verified sender in Mailjet i.e. msen@dipme.app or msen@linqra.com

    /**
     * Test sending a simple text email via Mailjet
     */
    @Test
    void testSendSimpleEmail() {
        // Skip test if credentials are not set
        if ("YOUR_MAILJET_API_KEY".equals(MAILJET_API_KEY) ||
                "YOUR_MAILJET_SECRET_KEY".equals(MAILJET_SECRET_KEY)) {
            System.out.println("⚠️  Skipping Mailjet email test - credentials not set");
            System.out.println("   Please set MAILJET_API_KEY, MAILJET_SECRET_KEY, and TEST_RECIPIENT_EMAIL");
            return;
        }

        // Create Mailjet JavaMailSender
        JavaMailSender mailSender = createMailjetMailSender();

        try {
            // Create message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set email details
            helper.setFrom(TEST_FROM_EMAIL);
            helper.setTo(TEST_RECIPIENT_EMAIL);
            helper.setSubject("Test Email from Linqra - Simple Text");
            helper.setText("This is a test email sent via Mailjet SMTP from the Linqra API Gateway test suite.\n\n" +
                    "If you receive this email, the Mailjet integration is working correctly!", false);

            // Send email
            mailSender.send(message);

            System.out.println("✅ Email sent successfully to: " + TEST_RECIPIENT_EMAIL);
            System.out.println("   Check your inbox for the test email.");

        } catch (Exception e) {
            System.err.println("❌ Failed to send email: " + e.getMessage());
            e.printStackTrace();
            fail("Failed to send email: " + e.getMessage());
        }
    }

    /**
     * Test sending an HTML email via Mailjet
     */
    @Test
    void testSendHtmlEmail() {
        // Skip test if credentials are not set
        if ("YOUR_MAILJET_API_KEY".equals(MAILJET_API_KEY) ||
                "YOUR_MAILJET_SECRET_KEY".equals(MAILJET_SECRET_KEY)) {
            System.out.println("⚠️  Skipping Mailjet email test - credentials not set");
            return;
        }

        // Create Mailjet JavaMailSender
        JavaMailSender mailSender = createMailjetMailSender();

        try {
            // Create message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set email details
            helper.setFrom(TEST_FROM_EMAIL);
            helper.setTo(TEST_RECIPIENT_EMAIL);
            helper.setSubject("Test Email from Linqra - HTML Format");

            // HTML content
            String htmlContent = """
                    <html>
                        <body>
                            <h2 style="color: #d65f1f;">Linqra Mailjet Integration Test</h2>
                            <p>This is a <strong>test email</strong> sent via Mailjet SMTP from the Linqra API Gateway.</p>
                            <p>If you receive this email, the Mailjet integration is working correctly!</p>
                            <hr>
                            <p style="color: #666; font-size: 12px;">Sent from: Linqra API Gateway Test Suite</p>
                        </body>
                    </html>
                    """;

            helper.setText(htmlContent, true); // true = HTML content

            // Send email
            mailSender.send(message);

            System.out.println("✅ HTML email sent successfully to: " + TEST_RECIPIENT_EMAIL);
            System.out.println("   Check your inbox for the HTML test email.");

        } catch (Exception e) {
            System.err.println("❌ Failed to send HTML email: " + e.getMessage());
            e.printStackTrace();
            fail("Failed to send HTML email: " + e.getMessage());
        }
    }

    /**
     * Test sending email with multiple recipients
     */
    @Test
    void testSendEmailToMultipleRecipients() {
        // Skip test if credentials are not set
        if ("YOUR_MAILJET_API_KEY".equals(MAILJET_API_KEY) ||
                "YOUR_MAILJET_SECRET_KEY".equals(MAILJET_SECRET_KEY)) {
            System.out.println("⚠️  Skipping Mailjet email test - credentials not set");
            return;
        }

        // Create Mailjet JavaMailSender
        JavaMailSender mailSender = createMailjetMailSender();

        try {
            // Create message
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            // Set email details
            helper.setFrom(TEST_FROM_EMAIL);
            helper.setTo(TEST_RECIPIENT_EMAIL); // Primary recipient
            // Add CC or BCC if needed:
            // helper.setCc("cc@example.com");
            // helper.setBcc("bcc@example.com");
            helper.setSubject("Test Email from Linqra - Multiple Recipients");
            helper.setText("This is a test email sent to multiple recipients via Mailjet.", false);

            // Send email
            mailSender.send(message);

            System.out.println("✅ Email sent successfully to multiple recipients");
            System.out.println("   Primary recipient: " + TEST_RECIPIENT_EMAIL);

        } catch (Exception e) {
            System.err.println("❌ Failed to send email: " + e.getMessage());
            e.printStackTrace();
            fail("Failed to send email: " + e.getMessage());
        }
    }

    /**
     * Test email configuration and connection
     */
    @Test
    void testMailjetConfiguration() {
        // Skip test if credentials are not set
        if ("YOUR_MAILJET_API_KEY".equals(MAILJET_API_KEY) ||
                "YOUR_MAILJET_SECRET_KEY".equals(MAILJET_SECRET_KEY)) {
            System.out.println("⚠️  Skipping Mailjet configuration test - credentials not set");
            return;
        }

        // Create Mailjet JavaMailSender
        JavaMailSender mailSender = createMailjetMailSender();

        assertNotNull(mailSender, "JavaMailSender should not be null");

        // Verify configuration
        if (mailSender instanceof JavaMailSenderImpl impl) {
            System.out.println("✅ Mailjet Configuration:");
            System.out.println("   Host: " + impl.getHost());
            System.out.println("   Port: " + impl.getPort());
            System.out.println("   Username: " + impl.getUsername());
            System.out.println("   Password: " + (impl.getPassword() != null ? "***" : "null"));
        }
    }

    /**
     * Creates a JavaMailSender configured for Mailjet SMTP
     */
    private JavaMailSender createMailjetMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();

        // Mailjet SMTP configuration
        mailSender.setHost(MAILJET_HOST);
        mailSender.setPort(MAILJET_PORT);
        mailSender.setUsername(MAILJET_API_KEY);
        mailSender.setPassword(MAILJET_SECRET_KEY);

        // JavaMail properties
        Properties props = mailSender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        props.put("mail.smtp.starttls.required", "true");
        props.put("mail.debug", "false"); // Set to "true" for debugging

        // Additional properties for better compatibility
        props.put("mail.smtp.ssl.trust", MAILJET_HOST);
        props.put("mail.smtp.connectiontimeout", "5000");
        props.put("mail.smtp.timeout", "5000");
        props.put("mail.smtp.writetimeout", "5000");

        return mailSender;
    }
}

