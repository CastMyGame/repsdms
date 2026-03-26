package com.reps.demogcloud.services.email;

import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailNotificationService {

    private final EmailSenderService emailSenderService;
    private final EmailTemplateBuilderService templateBuilderService;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final EmailTemplateBuilderService emailTemplateBuilderService;

    @Async
    public void sendPtsEmail(String parentEmail, String teacherEmail, String studentEmail, String msg, String subject, String languageCode) throws MessagingException {

        String sender = ( getCurrentUserEmail() !=null && !getCurrentUserEmail().isEmpty()) ? getCurrentUserEmail():null;

        System.out.println("Sending Email using " + sender);


        Student student = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if (student == null) {
            throw new IllegalArgumentException("Student not found for email: " + studentEmail);
        }
        List<String> spotters = student.getSpotters() != null ? student.getSpotters() : new ArrayList<>();
        emailSenderService.sendBulkEmail(parentEmail, List.of(teacherEmail, studentEmail), subject, msg, spotters,sender, languageCode);
    }

    @Async
    public void sendContactUsMail(ContactUsRequest request) {
        emailSenderService.sendContactEmail(request.getEmail(), request.getSubject(), request.getMessage(), request.getPreferredLanguage());
    }

    @Async
    public void sendClassAnnouncement(ClassAnnouncementRequest request) throws MessagingException {
        var teacher = employeeRepository.findByEmailIgnoreCase(request.getTeacherEmail());
        var optionalClass = teacher.getClasses().stream()
                .filter(c -> c.getClassName().equals(request.getClassName()))
                .findFirst();

        if (optionalClass.isEmpty()) return;

        List<String> rosterEmails = optionalClass.get().getClassRoster();
        if (rosterEmails == null || rosterEmails.isEmpty()) return;

        // 1) Group recipients by preferred language
        // NOTE: decide whether rosterEmails are student emails or parent emails.
        // If rosterEmails are student emails:
        //   Student s = studentRepository.findByStudentEmailIgnoreCase(email)
        // If rosterEmails are parent emails:
        //   Student s = studentRepository.findByParentEmailIgnoreCase(email) (if you have it)
        Map<String, List<String>> recipientsByLang = new java.util.HashMap<>();

        for (String email : rosterEmails) {
            if (email == null || email.isBlank()) continue;

            Student s = studentRepository.findByStudentEmailIgnoreCase(email); // adjust if needed
            String lang = (s == null) ? "en" : emailTemplateBuilderService.normalizeLanguage(s.getPreferredLanguage());

            recipientsByLang.computeIfAbsent(lang, k -> new java.util.ArrayList<>()).add(email);
        }

        // 2) Translate ONCE per language (only user input fields)
        Map<String, String> subjectByLang = new java.util.HashMap<>();
        Map<String, String> bodyByLang = new java.util.HashMap<>();

        for (String lang : recipientsByLang.keySet()) {
            if ("en".equals(lang)) {
                subjectByLang.put(lang, request.getSubject());
                bodyByLang.put(lang, request.getMsg());
            } else {
                // Translate only teacher-entered fields
                subjectByLang.put(lang, emailTemplateBuilderService.translateUserInput(request.getSubject(), lang));
                bodyByLang.put(lang, emailTemplateBuilderService.translateUserInput(request.getMsg(), lang));
            }
        }

        // 3) Send one bulk email per language group
        for (var entry : recipientsByLang.entrySet()) {
            String lang = entry.getKey();
            List<String> recipients = entry.getValue();

            emailSenderService.sendClassAnnouncement(
                    request.getTeacherEmail(),
                    recipients,
                    subjectByLang.get(lang),
                    bodyByLang.get(lang)
            );
        }
    }

    @Async
    public void sendAlertEmail(String alertType, Punishment punishment, String languageCode) throws MessagingException {
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        if (student == null) return;

        String intro = "Hello, This message is to inform you that ";
        String action = " has an assignment that they have yet to complete in REPS. If they do not complete this assignment by the beginning of the school day tomorrow they will receive ";
        String followup = " and must complete it during that time. If the assignment is completed before then you will receive a confirmation email and can disregard this message. If you have any questions you can hit REPLY ALL and communicate with the teacher who created the original parent contact.";

        String detentionType = alertType.equals("ISS") ? "ISS" : "lunch detention";
        String subject = alertType + " REMINDER";
        String message = intro + student.getFirstName() + " " + student.getLastName() + action + detentionType + followup;

        sendPtsEmail(student.getParentEmail(), punishment.getTeacherEmail(), student.getStudentEmail(), message, subject, languageCode);
    }

    public void notifyParentViaTextAndEmail(Punishment punishment, Student student, Infraction infraction, PunishmentResponse response) throws MessagingException {
        String message = templateBuilderService.createEmailText(
                student.getFirstName(),
                student.getLastName(),
                infraction.getInfractionLevel(),
                infraction.getInfractionName(),
                templateBuilderService.replaceString(punishment.getInfractionDescription().get(0)),
                student.getStudentEmail(), student.getPreferredLanguage()
        );

        response.setMessage(message);

        sendPtsEmail(
                response.getParentToEmail(),
                response.getTeacherToEmail(),
                response.getStudentToEmail(),
                response.getSubject(),
                response.getMessage(),
                student.getPreferredLanguage()
        );
    }


    public String getCurrentUserEmail() {
        // 1. Get the Authentication object from the SecurityContext
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()) {
            // 2. The principal is typically the UserDetails object (or a custom user object)
            Object principal = authentication.getPrincipal();

            // Check if the principal is the standard Spring User object
            if (principal instanceof org.springframework.security.core.userdetails.User userDetails) {
                // Spring's default UserDetails doesn't have an email field, but the
                // username field is often used for the email address.
                return userDetails.getUsername();
            }

        }
        return null; // No user logged in or authentication failed
    }

}
