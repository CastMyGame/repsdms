package com.reps.demogcloud.services.email;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private final JavaMailSender javaMailSender;
    private final EmailRoutingService emailRoutingService;

    public boolean isPhase1RoutingEnabled() {
        return emailRoutingService.isPhase1RoutingEnabled();
    }

    public void sendStaffBulkEmail(String to, List<String> cc, String subject, String body,
                                  List<String> bcc, String actor) throws MessagingException {
        emailRoutingService.sendBulkEmail(to, cc, subject, body, bcc, actor, true);
    }

    public void sendSystemBulkEmail(String to, List<String> cc, String subject, String body,
                                   List<String> bcc, String replyTo) throws MessagingException {
        emailRoutingService.sendBulkEmail(to, cc, subject, body, bcc, replyTo, false);
    }

    public void sendEmail(String toEmail, String subject, String msg) throws MessagingException {
        emailRoutingService.sendEmail(toEmail, subject, msg, null);
    }

    public void sendEmail(
            String toEmail,
            String subjectEn,
            String msgEn,
            String languageCode
    ) throws MessagingException {
        sendEmail(toEmail, subjectEn, msgEn, null, languageCode);
    }

    /**
     * Send email with optional sender email (for Gmail API)
     */
    public void sendEmail(String toEmail, String subjectEn, String msgEn, String fromEmail, String languageCode) throws MessagingException {
        emailRoutingService.sendEmail(toEmail, subjectEn, msgEn, fromEmail);
    }

    public void sendBulkEmail(String to, List<String> cc, String subjectEn, String msgEn, List<String> bcc) throws MessagingException {
        emailRoutingService.sendBulkEmail(to, cc, subjectEn, msgEn, bcc, null);
    }

    /**
     * Send bulk email with optional sender email (for Gmail API)
     */
    public void sendBulkEmail(String to, List<String> cc, String subjectEn, String msgEn, List<String> bcc, String fromEmail, String languageCode) throws MessagingException {
        emailRoutingService.sendBulkEmail(to, cc, subjectEn, msgEn, bcc, fromEmail);
    }

    public void sendContactEmail(String to, String subjectEn, String bodyEn, String languageCode) {

        EmailContent.requireRendered(subjectEn,bodyEn);
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setCc("REPS.DMS@GMAIL.COM");
        message.setSubject(subjectEn);
        message.setText(bodyEn);
        message.setFrom("REPS.DMS@GMAIL.COM");
        javaMailSender.send(message);
    }

    public void sendClassAnnouncement(
            String from,
            List<String> recipients,
            String subjectLocalized,
            String messageBodyLocalized
    ) throws MessagingException {
        if (recipients == null || recipients.isEmpty()) return;

        if (isPhase1RoutingEnabled()) {
            // A class roster is not permission to disclose classmates' addresses to one another.
            for (String recipient : recipients) {
                sendStaffBulkEmail(recipient, null, subjectLocalized, messageBodyLocalized, null, from);
            }
            return;
        }

        for(String recipient : recipients) emailRoutingService.sendBulkEmail(recipient,null,subjectLocalized,messageBodyLocalized,null,from);
    }

    public void sendSafe(
            String toEmail,
            String subjectEn,
            String msgEn,
            String languageCode
    ) {
        try {
            sendEmail(toEmail, subjectEn, msgEn, languageCode);
        } catch (MailException | MessagingException e) {
            log.error("Email send failed");
        }
    }

    public void sendDetailedEmail(
            String parentEmail,
            String teacherEmail,
            String studentEmail,
            List<String> spotters,
            String msgEn,
            String subjectEn,
            String languageCode
    ) throws MessagingException {
        sendDetailedEmail(parentEmail, teacherEmail, studentEmail, spotters, msgEn, subjectEn, null, languageCode);
    }


    /**
     * Send detailed email with optional sender email (for Gmail API)
     */
    public void sendDetailedEmail(
            String parentEmail,
            String teacherEmail,
            String studentEmail,
            List<String> spotters,
            String msgEn,
            String subjectEn,
            String fromEmail,
            String languageCode
    ) throws MessagingException {
        emailRoutingService.sendDetailedEmail(parentEmail, teacherEmail, studentEmail, spotters, msgEn, subjectEn, fromEmail);
    }

    public void sendGenericEmail(
            List<String> ccEmails,
            String recipientEmail,
            String subjectEn,
            String msgEn,
            String languageCode
    ) throws MessagingException {
        sendGenericEmail(ccEmails, recipientEmail, subjectEn, msgEn, null, languageCode);
    }
    /**
     * Send generic email with optional sender email (for Gmail API)
     */
    public void sendGenericEmail(
            List<String> ccEmails,
            String recipientEmail,
            String subjectEn,
            String msgEn,
            String fromEmail,
            String languageCode
    ) throws MessagingException {
        emailRoutingService.sendGenericEmail(ccEmails, recipientEmail, subjectEn, msgEn, fromEmail);
    }

}
