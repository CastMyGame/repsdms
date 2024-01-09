package com.reps.demogcloud.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailService {

    @Autowired JavaMailSender javaMailSender;

    @Async
    public void sendEmail (String toEmail, String subject, String message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(toEmail);
        mailMessage.setSubject(subject);
        mailMessage.setText(message);
        mailMessage.setFrom("REPS.DMS@gmail.com");
        javaMailSender.send(mailMessage);
    }
    @Async
    public void sendPtsEmail (String parentEmail,
                              String teacherEmail,
                              String studentEmail,
                              String subject,
                              String message) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(parentEmail);
        mailMessage.setCc(teacherEmail, studentEmail);
        mailMessage.setSubject(subject);
        mailMessage.setText(message);
        mailMessage.setFrom("REPS.DMS@gmail.com");
        javaMailSender.send(mailMessage);
    }
}
