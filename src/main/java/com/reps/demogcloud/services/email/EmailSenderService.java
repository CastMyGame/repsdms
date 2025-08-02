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

    public void sendHtmlEmail(String templateName, String toEmail, String subject, Map<String, Object> templateModel) throws MessagingException {
        Context context = new Context();
        context.setVariables(templateModel);

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setSubject(subject);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(toEmail);
        String htmlContent = templateEngine.process(templateName, context);
        helper.setText(htmlContent, true);

        javaMailSender.send(mimeMessage);
    }

    public void sendEmail(String toEmail, String subject, String msg) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setSubject(subject);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(toEmail);
        helper.setText(msg, true);

        javaMailSender.send(message);
    }

    public void sendBulkEmail(String to, List<String> cc, String subject, String msg, List<String> bcc) throws MessagingException {
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
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom(from);
        helper.setSubject(subject);
        for (String email : classEmails) {
            helper.addCc(email);
        }
        helper.setText(messageBody, true);
        javaMailSender.send(message);
    }

    public void sendSafe(String toEmail, String subject, String msg) {
        try {
            sendEmail(toEmail, subject, msg);
        } catch (MailException | MessagingException e) {
            log.error("Exception occurred while sending email: {}", e.getMessage());
        }
    }

    public void sendDetailedEmail(String parentEmail, String teacherEmail, String studentEmail, List<String> spotters, String msg, String subject) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(parentEmail);

        helper.addCc(teacherEmail);
        helper.addCc(studentEmail);
        for (String email : spotters) {
            helper.addBcc(email);
        }
        helper.setText(msg, true);
        javaMailSender.send(message);
    }


    public void sendGenericEmail(List<String> ccEmails, String recipientEmail, String subject, String msg) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(message, true);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(recipientEmail);
        for (String email : ccEmails) {
            helper.addBcc(email);
        }
        helper.setSubject(subject);
        helper.setText(msg, true);
        javaMailSender.send(message);
    }

}
