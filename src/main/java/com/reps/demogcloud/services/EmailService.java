package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.thymeleaf.context.Context;
import org.thymeleaf.spring5.SpringTemplateEngine;

import javax.mail.MessagingException;
import javax.mail.internet.MimeMessage;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    private final SpringTemplateEngine springTemplateEngine;
    JavaMailSender javaMailSender;

    @Autowired
    public EmailService(JavaMailSender javaMailSender, StudentRepository studentRepository, EmployeeRepository employeeRepository, SpringTemplateEngine springTemplateEngine) {
        this.javaMailSender = javaMailSender;
        this.studentRepository = studentRepository;
        this.employeeRepository = employeeRepository;
        this.springTemplateEngine = springTemplateEngine;
    }

    public void createEmailAndSend(
//            String parentEmail,
            String teacherEmail,
//            String studentEmail,
            List<String> spotters, String msg, String subject) throws MessagingException {
        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message, true, "UTF-8");
        helper.setFrom("REPS.DMS@gmail.com");
//        helper.setTo(parentEmail);

        helper.addCc(teacherEmail);
//        helper.addCc(studentEmail);
        for (String email : spotters) {
            helper.addBcc(email);
        }
        helper.setText(msg, true);
        javaMailSender.send(message);
    }

    public void sendHtmlEmail(String templateName, String toEmail, String subject, Map<String, Object> templateModel) throws MessagingException {
        Context context = new Context();
        context.setVariables(templateModel);

        MimeMessage mimeMessage = javaMailSender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
        helper.setSubject(subject);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(toEmail);
        String htmlContent = springTemplateEngine.process(templateName, context);
        helper.setText(htmlContent, true);
        javaMailSender.send(mimeMessage);
    }

    @Async
    public void sendEmail(String toEmail, String subject, String msg) throws MessagingException {

        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message, true);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(toEmail);
        helper.setText(msg, true);

        javaMailSender.send(message);
    }

    @Async
    public void sendPtsEmail(String parentEmail,
                             String teacherEmail,
                             String studentEmail,
                             String subject,
                             String msg) throws MessagingException {
        Student findMe = studentRepository.findByStudentEmailIgnoreCase(studentEmail);

        // Check if getSpotters() is null
        List<String> spotters = new ArrayList<>();
        if (findMe == null) {
            spotters = new ArrayList<>(); // Substitute with an empty array
            throw new IllegalArgumentException("Student not found for given email");
        } else {
            spotters = findMe.getSpotters();
        }

        createEmailAndSend(teacherEmail, spotters, msg, subject);
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
        if (detention.equals("DETENTION")) {

            Student findMe = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            String msg = "Hello, This message is to inform you that " + findMe.getFirstName() + " " + findMe.getLastName() +
                    " has an assignment that they have yet to complete in REPS. If they do not complete this assignment by the beginning of the school day tomorrow" +
                    " they will receive lunch detention and must complete it during that time. If the assignment is completed before then you will receive a confirmation" +
                    " email and can disregard this message. If you have any questions you can hit REPLY ALL and communicate with the teacher who created the original parent contact.";

            createEmailAndSend(punishment.getTeacherEmail(), findMe.getSpotters(), msg, "DETENTION REMINDER");


        } else if (detention.equals("ISS")) {
            Student findMe = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            String msg = "Hello, This message is to inform you that " + findMe.getFirstName() + " " + findMe.getLastName() +
                    " has an assignment that they have yet to complete in REPS. If they do not complete this assignment by the beginning of the school day tomorrow" +
                    " they will receive ISS and must complete it during that time. If the assignment is completed before then you will receive a confirmation" +
                    " email and can disregard this message. If you have any questions you can hit REPLY ALL and communicate with the teacher who created the original parent contact.";

            createEmailAndSend(punishment.getTeacherEmail(), findMe.getSpotters(), msg, "ISS REMINDER");
        }
    }

    @Async
    public void sendEmailGeneric(ArrayList<String> ccEmails,
                                 String recipientEmail,
                                 String subject,
                                 String msg) throws MessagingException {

        MimeMessage message = javaMailSender.createMimeMessage();
        message.setSubject(subject);
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(message, true);
        helper.setFrom("REPS.DMS@gmail.com");
        helper.setTo(recipientEmail);
        for (String email : ccEmails) {
            helper.addBcc(email);
        }
        helper.setText(msg, true);

        javaMailSender.send(message);
    }

    @Async
    public void sendClassAnnouncement(ClassAnnouncementRequest request) throws MessagingException {

        Employee teacher = employeeRepository.findByEmailIgnoreCase(request.getTeacherEmail());
        List<Employee.ClassRoster> roster = teacher.getClasses();

        Optional<Employee.ClassRoster> announceClass = roster.stream().filter(name -> name.getClassName().equals(request.getClassName())).findFirst();

        MimeMessage classAnnouncement = javaMailSender.createMimeMessage();

        classAnnouncement.setSubject(request.getSubject());
        MimeMessageHelper helper;
        helper = new MimeMessageHelper(classAnnouncement, true);
        helper.setFrom(request.getTeacherEmail());
        if(announceClass.isPresent()) {
            for (String email : announceClass.get().getClassRoster()) {
                helper.addCc(email);
            }
        }
        helper.setText(request.getMsg(), true);

        javaMailSender.send(classAnnouncement);

    }
    @Async
    public void sendPositiveShoutOut(String toEmail, String studentName) {
        Map<String, Object> positiveTemplateData = new HashMap<>();
        positiveTemplateData.put("studentName", studentName);

        try {
            sendHtmlEmail("positive-shout-out", toEmail, "Positive Shout out for " + studentName, positiveTemplateData);
        } catch (MailException | MessagingException e) {
            log.error("Exception occurred while sending email: {}", e.getMessage());
        }
    }
}
