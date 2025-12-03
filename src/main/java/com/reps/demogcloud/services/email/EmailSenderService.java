package com.reps.demogcloud.services.email;

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
    public void sendHtmlEmail(String templateName, String toEmail, String subject, Map<String, Object> templateModel, String fromEmail) throws MessagingException {
        emailRoutingService.sendHtmlEmail(templateName, toEmail, subject, templateModel, fromEmail);
    }

    public void sendEmail(String toEmail, String subject, String msg) throws MessagingException {
        emailRoutingService.sendEmail(toEmail, subject, msg, null);
    }

    /**
     * Send email with optional sender email (for Gmail API)
     */
    public void sendEmail(String toEmail, String subject, String msg, String fromEmail) throws MessagingException {
        emailRoutingService.sendEmail(toEmail, subject, msg, fromEmail);
    }

    public void sendBulkEmail(String to, List<String> cc, String subject, String msg, List<String> bcc) throws MessagingException {
        emailRoutingService.sendBulkEmail(to, cc, subject, msg, bcc, null);
    }

    /**
     * Send bulk email with optional sender email (for Gmail API)
     */
    public void sendBulkEmail(String to, List<String> cc, String subject, String msg, List<String> bcc, String fromEmail) throws MessagingException {
        emailRoutingService.sendBulkEmail(to, cc, subject, msg, bcc, fromEmail);
    }

    public void sendContactEmail(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setCc("REPS.DMS@GMAIL.COM");
        message.setSubject(subject);
        message.setText(body);
        message.setFrom("REPS.DMS@GMAIL.COM");
        javaMailSender.send(message);
    }

    public void sendClassAnnouncement(String from, List<String> classEmails, String subject, String messageBody) throws MessagingException {
        // Class announcements are USER-INITIATED (teacher sends to class)
        // Try Gmail API if teacher has OAuth token, otherwise use SMTP
        if (classEmails != null && !classEmails.isEmpty()) {
            emailRoutingService.sendBulkEmail(
                classEmails.get(0), // First email as primary recipient
                classEmails.size() > 1 ? classEmails.subList(1, classEmails.size()) : null, // Rest as CC
                subject,
                messageBody,
                null, // No BCC
                from, // Teacher's email for Gmail API
                true  // This is a user-initiated action
            );
        }
    }

    public void sendSafe(String toEmail, String subject, String msg) {
        try {
            sendEmail(toEmail, subject, msg);
        } catch (MailException | MessagingException e) {
            log.error("Exception occurred while sending email: {}", e.getMessage());
        }
    }

    public void sendDetailedEmail(String parentEmail, String teacherEmail, String studentEmail, List<String> spotters, String msg, String subject) throws MessagingException {
        emailRoutingService.sendDetailedEmail(parentEmail, teacherEmail, studentEmail, spotters, msg, subject, null);
    }

    /**
     * Send detailed email with optional sender email (for Gmail API)
     */
    public void sendDetailedEmail(String parentEmail, String teacherEmail, String studentEmail, List<String> spotters, String msg, String subject, String fromEmail) throws MessagingException {
        emailRoutingService.sendDetailedEmail(parentEmail, teacherEmail, studentEmail, spotters, msg, subject, fromEmail);
    }

    public void sendGenericEmail(List<String> ccEmails, String recipientEmail, String subject, String msg) throws MessagingException {
        emailRoutingService.sendGenericEmail(ccEmails, recipientEmail, subject, msg, null);
    }

    /**
     * Send generic email with optional sender email (for Gmail API)
     */
    public void sendGenericEmail(List<String> ccEmails, String recipientEmail, String subject, String msg, String fromEmail) throws MessagingException {
        emailRoutingService.sendGenericEmail(ccEmails, recipientEmail, subject, msg, fromEmail);
    }

}
