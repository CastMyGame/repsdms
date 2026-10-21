package com.reps.demogcloud.services.email;

import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import com.reps.demogcloud.security.services.GoogleMailService;
import com.reps.demogcloud.security.services.GoogleOAuthTokenStore;
import com.reps.demogcloud.services.UserContextService;
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
import org.springframework.security.access.AccessDeniedException;

/**
 * Email routing service that can send emails via either Gmail SMTP or Gmail API
 * based on configuration, email type (user vs system), and availability of user OAuth tokens.
 * The default legacy path retains its configured Gmail-to-SMTP fallback.
 * The separately gated Phase 1 path verifies the staff actor and sending grant,
 * keeps system messages on SMTP, and never falls back after a Gmail attempt.
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
    private final UserContextService userContextService;

    /** Off by default. Do not activate required-notification workflows before Phases 2–3. */
    public boolean isPhase1RoutingEnabled() {
        return Boolean.parseBoolean(env.getProperty("reps.email.routing.phase1-enabled", "false"));
    }

    private void sendPhase1(String to, List<String> cc, String subject, String body,
                            List<String> bcc, String sender, boolean staffInitiated) throws MessagingException {
        sendPhase1WithReceipt(to,cc,subject,body,bcc,sender,staffInitiated,null,null,null,false);
    }

    public java.util.Map<String,String> sendRequiredNotification(String to,List<String> cc,String subject,String body,
            List<String> bcc,String actor,boolean system,String messageId,String parent,String gmailThread) throws MessagingException {
        if(!isPhase1RoutingEnabled() || !Boolean.parseBoolean(env.getProperty("reps.email.notifications.enabled","false")))
            throw new MessagingException("Managed notifications are disabled");
        return sendPhase1WithReceipt(to,cc,subject,body,bcc,actor,!system,messageId,parent,gmailThread,true);
    }

    private java.util.Map<String,String> sendPhase1WithReceipt(String to,List<String> cc,String subject,String body,
            List<String> bcc,String sender,boolean staffInitiated,String messageId,String parent,String gmailThread,boolean recorded) throws MessagingException {
        String verifiedSender = null;
        if (staffInitiated) {
            verifiedSender = recorded ? userContextService.getVerifiedStaffEmail(sender) : userContextService.getCurrentStaffEmail();
            if (sender == null || !verifiedSender.equalsIgnoreCase(sender)) {
                throw new AccessDeniedException("The sending identity must match the acting staff member");
            }
        } else if (sender != null) {
            // A system notification can have a verified staff Reply-To, but never borrows their Gmail.
            verifiedSender = userContextService.getVerifiedStaffEmail(sender);
        }

        String provider = getEmailProvider();
        if (!List.of(PROVIDER_SMTP, PROVIDER_HYBRID, PROVIDER_GMAIL_API).contains(provider)) {
            throw new MessagingException("Unsupported email provider policy");
        }
        if (!staffInitiated && !PROVIDER_SMTP.equals(getSystemEmailProvider())) {
            throw new MessagingException("An explicit system sending account is required");
        }
        if (staffInitiated && !PROVIDER_SMTP.equals(provider)
                && (!PROVIDER_HYBRID.equals(provider) || isUserEmailsEnabled())) {
            GoogleOAuthTokenStore.ConnectionState state = tokenStore.getConnectionState(verifiedSender);
            if (state == GoogleOAuthTokenStore.ConnectionState.UNKNOWN || state == null) {
                throw new MessagingException("GMAIL_CONNECTION_UNKNOWN: explicit connection policy is required");
            }
            if (state == GoogleOAuthTokenStore.ConnectionState.RECONNECT_REQUIRED
                    || (state == GoogleOAuthTokenStore.ConnectionState.CONNECTED
                    && !tokenStore.hasUsableGmailSendToken(verifiedSender))) {
                throw new MessagingException("GMAIL_RECONNECT_REQUIRED: Gmail sending authorization is not usable");
            }
            if (state == GoogleOAuthTokenStore.ConnectionState.CONNECTED) {
                GoogleMailSendRequest request = new GoogleMailSendRequest();
                request.setFrom(verifiedSender);
                request.setTo(to);
                request.setCc(cc);
                request.setBcc(bcc);
                request.setSubject(subject);
                request.setBody(body);
                request.setRfcMessageId(messageId); request.setInReplyTo(parent); request.setReferences(parent); request.setThreadId(gmailThread);
                try {
                    if(messageId!=null) return googleMailService.sendWithReceipt(verifiedSender,request);
                    googleMailService.sendEmailOnBehalf(verifiedSender, request);
                } catch (MessagingException exception) {
                    throw exception;
                } catch (Exception exception) {
                    // Do not leak provider payloads or resend a possibly accepted Gmail message through SMTP.
                    throw new MessagingException("GMAIL_SEND_UNCERTAIN: no automatic retry or SMTP fallback");
                }
                return java.util.Map.of("provider","GMAIL","providerMessageId","","gmailThreadId","");
            }
        }

        MimeMessage message = javaMailSender.createMimeMessage();
        if(messageId!=null) {
            // Bound managed SMTP waits; leave configured finite limits in place.
            java.util.Properties properties=message.getSession().getProperties();
            properties.putIfAbsent("mail.smtp.connectiontimeout","10000");
            properties.putIfAbsent("mail.smtp.timeout","15000");
            properties.putIfAbsent("mail.smtp.writetimeout","15000");
            for(String property:java.util.List.of("mail.smtp.connectiontimeout","mail.smtp.timeout","mail.smtp.writetimeout")) {
                try { if(Integer.parseInt(properties.getProperty(property))<=0) throw new IllegalArgumentException(); }
                catch(Exception invalid) { throw new MessagingException("SMTP_POLICY_INVALID: finite SMTP timeouts are required"); }
            }
        }
        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setValidateAddresses(true);
        helper.setFrom("REPS Notifications <Reps.Dms@gmail.com>");
        if (verifiedSender != null) helper.setReplyTo(verifiedSender);
        helper.setTo(to);
        if (cc != null && !cc.isEmpty()) helper.setCc(cc.toArray(String[]::new));
        if (bcc != null && !bcc.isEmpty()) helper.setBcc(bcc.toArray(String[]::new));
        helper.setSubject(subject);
        helper.setText(body, true);
        message.saveChanges();
        if(messageId!=null) message.setHeader("Message-ID",messageId);
        if(parent!=null) { message.setHeader("In-Reply-To",parent); message.setHeader("References",parent); }
        javaMailSender.send(message);
        return java.util.Map.of("provider","SMTP","providerMessageId",java.util.Objects.toString(message.getMessageID(),""),"gmailThreadId","");
    }

    private String getEmailProvider() {
        return env.getProperty("mail.service.provider", PROVIDER_SMTP).toLowerCase();
    }

    private boolean isUserEmailsEnabled() {
        return Boolean.parseBoolean(env.getProperty("mail.service.user-emails.enabled", "false"));
    }

    public boolean isStaffGmailPolicyEnabled() {
        return PROVIDER_GMAIL_API.equals(getEmailProvider())
                || (PROVIDER_HYBRID.equals(getEmailProvider()) && isUserEmailsEnabled());
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

        if (isPhase1RoutingEnabled()) {
            sendPhase1(toEmail, null, subject, msg, null, fromEmail, isUserInitiated);
            return;
        }

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
                    log.warn("Legacy Gmail send failed; legacy SMTP fallback selected");
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

        if (isPhase1RoutingEnabled()) {
            sendPhase1(to, cc, subject, msg, bcc, fromEmail, isUserInitiated);
            return;
        }

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
                    log.warn("Legacy Gmail send failed; legacy SMTP fallback selected");
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
        if (isPhase1RoutingEnabled()) {
            sendPhase1(parentEmail, List.of(teacherEmail, studentEmail), subject, msg,
                    spotters, fromEmail, isUserInitiated);
            return;
        }
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
        if (isPhase1RoutingEnabled()) {
            sendPhase1(recipientEmail, null, subject, msg, ccEmails, fromEmail, isUserInitiated);
            return;
        }
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

