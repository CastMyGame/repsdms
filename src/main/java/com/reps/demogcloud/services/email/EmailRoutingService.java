package com.reps.demogcloud.services.email;

import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import com.reps.demogcloud.security.services.GoogleMailService;
import com.reps.demogcloud.security.services.GoogleOAuthTokenStore;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Email routing service that can send emails via either Gmail SMTP or Gmail API
 * based on configuration, email type (user vs system), and availability of user OAuth tokens.
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

    private String getEmailProvider() {
        return env.getProperty("mail.service.provider", PROVIDER_SMTP).toLowerCase();
    }

    private boolean isUserEmailsEnabled() {
        return Boolean.parseBoolean(env.getProperty("mail.service.user-emails.enabled", "false"));
    }

    private String getSystemEmailProvider() {
        return env.getProperty("mail.service.system-emails.provider", PROVIDER_SMTP).toLowerCase();
    }

    private boolean shouldTryGmailAPI(String fromEmail, boolean isUserInitiated) {
        String provider = getEmailProvider();

        if (PROVIDER_SMTP.equals(provider)) {
            return false;
        }

        if (!isUserInitiated) {
            return PROVIDER_GMAIL_API.equals(getSystemEmailProvider())
                    && fromEmail != null
                    && !fromEmail.isEmpty();
        }

        if (PROVIDER_HYBRID.equals(provider)) {
            return isUserEmailsEnabled() && fromEmail != null && !fromEmail.isEmpty();
        }

        return PROVIDER_GMAIL_API.equals(provider) && fromEmail != null && !fromEmail.isEmpty();
    }

    public void sendEmail(String toEmail, String subject, String msg, String fromEmail, boolean isUserInitiated)
            throws MessagingException {

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
                }
            } else {
                log.debug("No Gmail OAuth token for {}, using SMTP", fromEmail);
            }
        }

        sendEmailViaSMTP(toEmail, subject, msg);
    }

    public void sendEmail(String toEmail, String subject, String msg, String fromEmail) throws MessagingException {
        sendEmail(toEmail, subject, msg, fromEmail, false);
    }

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

    public void sendBulkEmail(
            String to,
            List<String> cc,
            String subject,
            String msg,
            List<String> bcc,
            String fromEmail,
            boolean isUserInitiated
    ) throws MessagingException {

        if (shouldTryGmailAPI(fromEmail, isUserInitiated)) {
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

        sendBulkEmailViaSMTP(to, cc, subject, msg, bcc);
    }

    public void sendBulkEmail(
            String to,
            List<String> cc,
            String subject,
            String msg,
            List<String> bcc,
            String fromEmail
    ) throws MessagingException {
        sendBulkEmail(to, cc, subject, msg, bcc, fromEmail, true);
    }

    private void sendBulkEmailViaSMTP(
            String to,
            List<String> cc,
            String subject,
            String msg,
            List<String> bcc
    ) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setSubject(subject);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(to);

        if (cc != null) {
            for (String email : cc) {
                try {
                    helper.addCc(email);
                } catch (MessagingException ignored) {
                }
            }
        }

        if (bcc != null) {
            for (String email : bcc) {
                try {
                    helper.addBcc(email);
                } catch (MessagingException ignored) {
                }
            }
        }

        helper.setText(msg, true);
        javaMailSender.send(message);
        log.debug("Bulk email sent via SMTP to {}", to);
    }

    public void sendDetailedEmail(
            String parentEmail,
            String teacherEmail,
            String studentEmail,
            List<String> spotters,
            String msg,
            String subject,
            String fromEmail,
            boolean isUserInitiated
    ) throws MessagingException {
        sendDetailedEmailViaSMTP(parentEmail, teacherEmail, studentEmail, spotters, msg, subject);
    }

    public void sendDetailedEmail(
            String parentEmail,
            String teacherEmail,
            String studentEmail,
            List<String> spotters,
            String msg,
            String subject,
            String fromEmail
    ) throws MessagingException {
        sendDetailedEmail(parentEmail, teacherEmail, studentEmail, spotters, msg, subject, fromEmail, false);
    }

    private void sendDetailedEmailViaSMTP(
            String parentEmail,
            String teacherEmail,
            String studentEmail,
            List<String> spotters,
            String msg,
            String subject
    ) throws MessagingException {
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

    public void sendGenericEmail(
            List<String> ccEmails,
            String recipientEmail,
            String subject,
            String msg,
            String fromEmail,
            boolean isUserInitiated
    ) throws MessagingException {
        sendGenericEmailViaSMTP(ccEmails, recipientEmail, subject, msg);
    }

    public void sendGenericEmail(
            List<String> ccEmails,
            String recipientEmail,
            String subject,
            String msg,
            String fromEmail
    ) throws MessagingException {
        sendGenericEmail(ccEmails, recipientEmail, subject, msg, fromEmail, false);
    }

    private void sendGenericEmailViaSMTP(
            List<String> ccEmails,
            String recipientEmail,
            String subject,
            String msg
    ) throws MessagingException {
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

