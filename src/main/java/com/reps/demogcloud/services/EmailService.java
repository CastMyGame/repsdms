package com.reps.demogcloud.services;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
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

@Service
@RequiredArgsConstructor
public class EmailService {

    JavaMailSender javaMailSender;
    private final StudentRepository studentRepository;

    @Autowired
    public EmailService(JavaMailSender javaMailSender, StudentRepository studentRepository) {
        this.javaMailSender = javaMailSender;
        this.studentRepository = studentRepository;
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
    public void sendContactUsMail(ContactUsRequest request) {
        SimpleMailMessage mailMessage = new SimpleMailMessage();
        mailMessage.setTo(request.getEmail());
        mailMessage.setCc("REPS.DMS@GMAIL.COM");
        mailMessage.setSubject(request.getSubject());
        mailMessage.setText(request.getMessage());
        mailMessage.setFrom("REPS.DMS@GMAIL.COM");
        javaMailSender.send(mailMessage);
    }

    public void sendAlertEmail(String detention, Punishment punishment) throws MessagingException {
        System.out.println("Sending Email Alert");
        if(detention.equals("DETENTION")) {
            Student findMe = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            String msg = "Hello, This message is to inform you that " + findMe.getFirstName() + " " + findMe.getLastName() +
                " has an assignment that they have yet to complete in REPS. If they do not complete this assignment by the beginning of the school day tomorrow" +
                    " they will receive lunch detention and must complete it during that time. If the assignment is completed before then you will receive a confirmation" +
                    " email and can disregard this message. If you have any questions you can hit REPLY ALL and communicate with the teacher who created the original parent contact.";

            MimeMessage message = javaMailSender.createMimeMessage();
            message.setSubject("DETENTION REMINDER");
            MimeMessageHelper helper;
            helper = new MimeMessageHelper(message, true);
            helper.setFrom("REPS.DMS@gmail.com");
            helper.setTo(findMe.getParentEmail());
            String[] cssArray = {punishment.getTeacherEmail(), findMe.getStudentEmail()};
            helper.setCc(cssArray);
            helper.setText(msg, true);
            javaMailSender.send(message);


        } else if(detention.equals("ISS")) {
            Student findMe = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            String msg = "Hello, This message is to inform you that " + findMe.getFirstName() + " " + findMe.getLastName() +
                    " has an assignment that they have yet to complete in REPS. If they do not complete this assignment by the beginning of the school day tomorrow" +
                    " they will receive ISS and must complete it during that time. If the assignment is completed before then you will receive a confirmation" +
                    " email and can disregard this message. If you have any questions you can hit REPLY ALL and communicate with the teacher who created the original parent contact.";

            MimeMessage message = javaMailSender.createMimeMessage();
            message.setSubject("DETENTION REMINDER");
            MimeMessageHelper helper;
            helper = new MimeMessageHelper(message, true);
            helper.setFrom("REPS.DMS@gmail.com");
            helper.setTo(findMe.getParentEmail());
            String[] cssArray = {punishment.getTeacherEmail(), findMe.getStudentEmail()};
            helper.setCc(cssArray);
            helper.setText(msg, true);
            javaMailSender.send(message);

        }
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

        javaMailSender.send(message);
    }
}
