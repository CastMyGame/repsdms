package com.reps.demogcloud.services;

import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailService {

    JavaMailSender javaMailSender;

    @Autowired
    public EmailService(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    @Async
    public void sendEmail (String toEmail, String subject, String msg) throws MessagingException {

        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message,true);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(toEmail);
        helper.setText(msg,true);

//        SimpleMailMessage mailMessage = new SimpleMailMessage();
//        mailMessage.setTo(toEmail);
//        mailMessage.setSubject(subject);
//        mailMessage.setText(message);
//        mailMessage.setFrom("REPS.DMS@gmail.com");
        javaMailSender.send(message);
    }
    @Async
    public void sendPtsEmail (String parentEmail,
                              String teacherEmail,
                              String studentEmail,
                              String subject,
                              String msg) throws MessagingException {

        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message,true);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(parentEmail);
        String[] cssArray = {teacherEmail,studentEmail};
        helper.setCc(cssArray);
        helper.setText(msg,true);


//        mailMessage.setTo(parentEmail);
//        mailMessage.setCc(teacherEmail, studentEmail);
//        mailMessage.setSubject(subject);
//        mailMessage.setText(message);
//        mailMessage.setFrom("REPS.DMS@gmail.com");
        javaMailSender.send(message);
    }

    @Async
    public void sendEmailGeneric (String [] ccEmails,
                              String recipientEmail,
                              String subject,
                              String msg) throws MessagingException {

        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message,true);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(recipientEmail);
        helper.setCc(ccEmails);
        helper.setText(msg,true);


//        mailMessage.setTo(parentEmail);
//        mailMessage.setCc(teacherEmail, studentEmail);
//        mailMessage.setSubject(subject);
//        mailMessage.setText(message);
//        mailMessage.setFrom("REPS.DMS@gmail.com");
        javaMailSender.send(message);
    }

    @Async
    public void sendContactUsMail(ContactUsRequest request) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(request.getEmail());
        mailMessage.setCc("REPS.DMS@GMAIL.COM");
        mailMessage.setSubject(request.getSubject());
        mailMessage.setText(request.getMessage());
        mailMessage.setFrom("REPS.DMS@GMAIL.COM");
        javaMailSender.send(mailMessage);
    }
}
