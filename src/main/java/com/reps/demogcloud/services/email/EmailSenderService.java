package com.reps.demogcloud.services.email;

import com.reps.demogcloud.services.translation.TranslationService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.MimeMessageHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailSenderService {

    private final JavaMailSender javaMailSender;
    private final SpringTemplateEngine templateEngine;
    private final EmailRoutingService emailRoutingService;

    public void sendHtmlEmail(String templateName, String toEmail, String subject, Map<String, Object> templateModel) throws MessagingException {
        emailRoutingService.sendHtmlEmail(templateName, toEmail, subject, templateModel, null);
    }

    /**
     * Send HTML email with optional sender email (for Gmail API)
     */
    public void sendHtmlEmail(String templateName, String toEmail, String subjectLocalized,
                              Map<String, Object> templateModel, String fromEmail, String languageCode) throws MessagingException {
        Context context = new Context();
        context.setVariables(templateModel);
        String htmlLocalized = templateEngine.process(templateName, context);

        emailRoutingService.sendEmail(toEmail, subjectLocalized, htmlLocalized, fromEmail);
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

        emailRoutingService.sendBulkEmail(
                recipients.get(0),
                recipients.size() > 1 ? recipients.subList(1, recipients.size()) : null,
                subjectLocalized,
                messageBodyLocalized,
                null,
                from
        );
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
            log.error("Exception occurred while sending email: {}", e.getMessage());
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
