package com.reps.demogcloud.services.email;

import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import com.reps.demogcloud.security.services.GoogleMailService;
import com.reps.demogcloud.security.services.GoogleOAuthTokenStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Optional;

/**
 * Email routing service that can send emails via either Gmail SMTP or Gmail API
 * based on configuration, email type (user vs system), and availability of user OAuth tokens.
 * 
 * Routing Logic:
 * - User-initiated emails: Can use Gmail API if user has OAuth token and feature flag enabled
 * - System/scheduled emails: Always use SMTP (configurable)
 * - Fallback: If Gmail API unavailable, always falls back to SMTP
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EmailRoutingService {

    private static final String PROVIDER_SMTP = "smtp";
    private static final String PROVIDER_GMAIL_API = "gmail-api";
    private static final String PROVIDER_HYBRID = "hybrid";
    private final Environment env;
    private final JavaMailSender javaMailSender;
    private final GoogleMailService googleMailService;
    private final GoogleOAuthTokenStore tokenStore;

    /**
     * Get the configured email provider mode
     */
    private String getEmailProvider() {
        return env.getProperty("mail.service.provider", PROVIDER_SMTP).toLowerCase();
    }

    /**
     * Check if user emails should use Gmail API
     */
    private boolean isUserEmailsEnabled() {
        return Boolean.parseBoolean(env.getProperty("mail.service.user-emails.enabled", "false"));
    }

    /**
     * Get provider for system emails
     */
    private String getSystemEmailProvider() {
        return env.getProperty("mail.service.system-emails.provider", PROVIDER_SMTP).toLowerCase();
    }

    /**
     * Determine if we should try Gmail API for this email
     * @param fromEmail Sender email (null for system emails)
     * @param isUserInitiated Whether this is a user-initiated action
     */
    private boolean shouldTryGmailAPI(String fromEmail, boolean isUserInitiated) {
        String provider = getEmailProvider();
        
        // If provider is SMTP-only, never use Gmail API
        if (PROVIDER_SMTP.equals(provider)) {
            return false;
        }
        
        // For system emails, check system email provider setting
        if (!isUserInitiated) {
            return PROVIDER_GMAIL_API.equals(getSystemEmailProvider()) && fromEmail != null;
        }
        
        // For user emails in hybrid mode, check feature flag
        if (PROVIDER_HYBRID.equals(provider)) {
            return isUserEmailsEnabled() && fromEmail != null && !fromEmail.isEmpty();
        }
        
        // For gmail-api mode, try if we have a sender
        return PROVIDER_GMAIL_API.equals(provider) && fromEmail != null && !fromEmail.isEmpty();
    }

    /**
     * Send email using the configured provider.
     * If Gmail API is configured but user doesn't have token, falls back to SMTP.
     * 
     * @param toEmail Recipient email
     * @param subject Email subject
     * @param msg Email body (HTML supported)
     * @param fromEmail Optional sender email (for Gmail API, uses this user's account if available)
     * @param isUserInitiated Whether this is a user-initiated action (true) or system/scheduled (false)
     */
    public void sendEmail(String toEmail, String subject, String msg, String fromEmail, boolean isUserInitiated) throws MessagingException {
        // Try Gmail API if conditions are met
        if (shouldTryGmailAPI(fromEmail, isUserInitiated)) {
            Optional<GoogleOAuthTokenStore.GoogleOAuthToken> token = tokenStore.getToken(fromEmail);
            if (token.isPresent()) {
                try {
                    GoogleMailSendRequest request = new GoogleMailSendRequest();
                    request.setTo(toEmail);
                    request.setSubject(subject);
                    request.setBody(msg);
                    request.setFrom(fromEmail);
                    
                    googleMailService.sendEmailOnBehalf(fromEmail, request);
                    log.info("Email sent via Gmail API on behalf of {} (user-initiated: {})", fromEmail, isUserInitiated);
                    return;
                } catch (Exception e) {
                    log.warn("Failed to send via Gmail API for {}, falling back to SMTP: {}", fromEmail, e.getMessage());
                    // Fall through to SMTP
                }
            } else {
                log.debug("No Gmail OAuth token for {}, using SMTP", fromEmail);
            }
        }
        
        // Use SMTP (default or fallback)
        sendEmailViaSMTP(toEmail, subject, msg);
    }

    /**
     * Send email (defaults to system email - uses SMTP)
     */
    public void sendEmail(String toEmail, String subject, String msg, String fromEmail) throws MessagingException {
        sendEmail(toEmail, subject, msg, fromEmail, false); // Default to system email
    }

    /**
     * Send email via SMTP using REPS.DMS@gmail.com
     */
    private void sendEmailViaSMTP(String toEmail, String subject, String msg) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setSubject(subject);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(toEmail);
        helper.setText(msg, true);
        javaMailSender.send(message);
        log.debug("Email sent via SMTP to {}", toEmail);
    }

    /**
     * Send bulk email with CC and BCC
     * Note: Gmail API doesn't handle CC/BCC well, so bulk emails typically use SMTP
     */
    public void sendBulkEmail(String to, List<String> cc, String subject, String msg, 
                             List<String> bcc, String fromEmail, boolean isUserInitiated) throws MessagingException {
        // Gmail API has limitations with CC/BCC, so only try for simple single-recipient emails
//        boolean hasMultipleRecipients = (cc != null && !cc.isEmpty()) || (bcc != null && !bcc.isEmpty());
        
        if (  shouldTryGmailAPI(fromEmail, isUserInitiated)) {
            Optional<GoogleOAuthTokenStore.GoogleOAuthToken> token = tokenStore.getToken(fromEmail);
            if (token.isPresent()) {
                try {
                    GoogleMailSendRequest request = new GoogleMailSendRequest();
                    request.setTo(to);
                    request.setSubject(subject);
                    request.setBody(msg);
                    request.setFrom(fromEmail);
                    request.setCc(cc);
                    request.setBcc(bcc);
                    
                    googleMailService.sendEmailOnBehalf(fromEmail, request);
                    log.info("Email sent via Gmail API on behalf of {} (user-initiated: {})", fromEmail, isUserInitiated);
                    return;
                } catch (Exception e) {
                    log.warn("Failed to send via Gmail API, falling back to SMTP: {}", e.getMessage());
                }
            }
        }
        
        // Use SMTP for bulk emails or fallback
        sendBulkEmailViaSMTP(to, cc, subject, msg, bcc);
    }

    /**
     * Send bulk email (defaults to system email)
     */
    public void sendBulkEmail(String to, List<String> cc, String subject, String msg, 
                             List<String> bcc, String fromEmail) throws MessagingException {
        sendBulkEmail(to, cc, subject, msg, bcc, fromEmail, true);
    }

    private void sendBulkEmailViaSMTP(String to, List<String> cc, String subject, String msg, List<String> bcc) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setSubject(subject);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(to);
        if (cc != null) cc.forEach(email -> {
            try {
                helper.addCc(email);
            } catch (MessagingException ignored) {
            }
        });
        if (bcc != null) bcc.forEach(email -> {
            try {
                helper.addBcc(email);
            } catch (MessagingException ignored) {
            }
        });
        helper.setText(msg, true);
        javaMailSender.send(message);
        log.debug("Bulk email sent via SMTP to {}", to);
    }

    /**
     * Send detailed email with multiple recipients
     * Note: These always use SMTP due to multiple recipients (CC/BCC)
     */
    public void sendDetailedEmail(String parentEmail, String teacherEmail, String studentEmail, 
                                 List<String> spotters, String msg, String subject, String fromEmail, boolean isUserInitiated) throws MessagingException {
        // Detailed emails with multiple recipients always use SMTP (Gmail API limitation)
        sendDetailedEmailViaSMTP(parentEmail, teacherEmail, studentEmail, spotters, msg, subject);
    }

    /**
     * Send detailed email (defaults to system email)
     */
    public void sendDetailedEmail(String parentEmail, String teacherEmail, String studentEmail, 
                                 List<String> spotters, String msg, String subject, String fromEmail) throws MessagingException {
        sendDetailedEmail(parentEmail, teacherEmail, studentEmail, spotters, msg, subject, fromEmail, false);
    }

    private void sendDetailedEmailViaSMTP(String parentEmail, String teacherEmail, String studentEmail, 
                                         List<String> spotters, String msg, String subject) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(parentEmail);
        helper.addCc(teacherEmail);
        helper.addCc(studentEmail);
        if (spotters != null) {
            for (String email : spotters) {
                helper.addBcc(email);
            }
        }
        helper.setText(msg, true);
        javaMailSender.send(message);
        log.debug("Detailed email sent via SMTP");
    }

    /**
     * Send generic email with CC list
     * Note: These always use SMTP due to CC recipients
     */
    public void sendGenericEmail(List<String> ccEmails, String recipientEmail, String subject, 
                                String msg, String fromEmail, boolean isUserInitiated) throws MessagingException {
        // Generic emails with CC always use SMTP (Gmail API limitation)
        sendGenericEmailViaSMTP(ccEmails, recipientEmail, subject, msg);
    }

    /**
     * Send generic email (defaults to system email)
     */
    public void sendGenericEmail(List<String> ccEmails, String recipientEmail, String subject, 
                                String msg, String fromEmail) throws MessagingException {
        sendGenericEmail(ccEmails, recipientEmail, subject, msg, fromEmail, false);
    }

    private void sendGenericEmailViaSMTP(List<String> ccEmails, String recipientEmail, String subject, String msg) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(recipientEmail);
        if (ccEmails != null) {
            for (String email : ccEmails) {
                helper.addBcc(email);
            }
        }
        helper.setSubject(subject);
        helper.setText(msg, true);
        javaMailSender.send(message);
        log.debug("Generic email sent via SMTP");
    }
}

