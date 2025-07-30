package com.reps.demogcloud.services.email;

import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final EmailSenderService emailSenderService;
    private final EmailTemplateBuilderService templateBuilderService;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    @Async
    public void sendPtsEmail(String parentEmail, String teacherEmail, String studentEmail, String msg, String subject) throws MessagingException {
        Student student = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if (student == null) {
            throw new IllegalArgumentException("Student not found for email: " + studentEmail);
        }
        List<String> spotters = student.getSpotters() != null ? student.getSpotters() : new ArrayList<>();
        emailSenderService.sendBulkEmail(parentEmail, List.of(teacherEmail, studentEmail), subject, msg, spotters);
    }

    @Async
    public void sendPositiveShoutOut(String toEmail, String studentName) {
        try {
            emailSenderService.sendHtmlEmail("positive-shout-out", toEmail,
                    "Positive Shout out for " + studentName,
                    java.util.Map.of("studentName", studentName));
        } catch (Exception e) {
            log.error("Failed to send positive shoutout: {}", e.getMessage());
        }
    }

    @Async
    public void sendContactUsMail(ContactUsRequest request) {
        emailSenderService.sendContactEmail(request.getEmail(), request.getSubject(), request.getMessage());
    }

    @Async
    public void sendClassAnnouncement(ClassAnnouncementRequest request) throws MessagingException {
        var teacher = employeeRepository.findByEmailIgnoreCase(request.getTeacherEmail());
        var optionalClass = teacher.getClasses().stream()
                .filter(c -> c.getClassName().equals(request.getClassName())).findFirst();
        if (optionalClass.isPresent()) {
            emailSenderService.sendClassAnnouncement(
                    request.getTeacherEmail(),
                    optionalClass.get().getClassRoster(),
                    request.getSubject(),
                    request.getMsg()
            );
        }
    }

    @Async
    public void sendAlertEmail(String alertType, Punishment punishment) throws MessagingException {
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        if (student == null) return;

        String intro = "Hello, This message is to inform you that ";
        String action = " has an assignment that they have yet to complete in REPS. If they do not complete this assignment by the beginning of the school day tomorrow they will receive ";
        String followup = " and must complete it during that time. If the assignment is completed before then you will receive a confirmation email and can disregard this message. If you have any questions you can hit REPLY ALL and communicate with the teacher who created the original parent contact.";

        String detentionType = alertType.equals("ISS") ? "ISS" : "lunch detention";
        String subject = alertType + " REMINDER";
        String message = intro + student.getFirstName() + " " + student.getLastName() + action + detentionType + followup;

        sendPtsEmail(student.getParentEmail(), punishment.getTeacherEmail(), student.getStudentEmail(), message, subject);
    }
}
