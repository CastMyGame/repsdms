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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationService {

    private final EmailSenderService emailSenderService;
    private final EmailTemplateBuilderService templateBuilderService;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    @Async
    public void sendPtsEmail(String parentEmail, String teacherEmail, String studentEmail, String msg, String subject, String languageCode) throws MessagingException {

        String sender = getCurrentUserEmail();
        log.debug("Sending Email using {}", sender);

        Student student = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if (student == null) {
            throw new IllegalArgumentException("Student not found for email: " + studentEmail);
        }

        List<String> spotters = student.getSpotters() != null ? student.getSpotters() : new ArrayList<>();

        emailSenderService.sendBulkEmail(
                parentEmail,
                List.of(teacherEmail, studentEmail),
                subject,
                msg,
                spotters,
                sender,
                languageCode
        );
    }

    @Async
    public void sendContactUsMail(ContactUsRequest request) {
        emailSenderService.sendContactEmail(
                request.getEmail(),
                request.getSubject(),
                request.getMessage(),
                request.getPreferredLanguage()
        );
    }

    @Async
    public void sendClassAnnouncement(ClassAnnouncementRequest request) throws MessagingException {
        var teacher = employeeRepository.findByEmailIgnoreCase(request.getTeacherEmail());

        if (teacher == null || teacher.getClasses() == null) return;

        var optionalClass = teacher.getClasses().stream()
                .filter(c -> c.getClassName().equals(request.getClassName()))
                .findFirst();

        if (optionalClass.isEmpty()) return;

        List<String> rosterEmails = optionalClass.get().getClassRoster();
        if (rosterEmails == null || rosterEmails.isEmpty()) return;

        Map<String, List<String>> recipientsByLang = new HashMap<>();

        for (String email : rosterEmails) {
            if (email == null || email.isBlank()) continue;

            Student s = studentRepository.findByStudentEmailIgnoreCase(email);
            String lang = (s == null)
                    ? "en"
                    : templateBuilderService.normalizeLanguage(s.getPreferredLanguage());

            recipientsByLang.computeIfAbsent(lang, k -> new ArrayList<>()).add(email);
        }

        Map<String, String> subjectByLang = new HashMap<>();
        Map<String, String> bodyByLang = new HashMap<>();

        for (String lang : recipientsByLang.keySet()) {
            if ("en".equals(lang)) {
                subjectByLang.put(lang, request.getSubject());
                bodyByLang.put(lang, request.getMsg());
            } else {
                subjectByLang.put(lang, templateBuilderService.translateUserInput(request.getSubject(), lang));
                bodyByLang.put(lang, templateBuilderService.translateUserInput(request.getMsg(), lang));
            }
        }

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

        String detentionType = alertType.equals("ISS") ? "ISS" : "lunch detention";

        String message = "Hello, This message is to inform you that "
                + student.getFirstName() + " " + student.getLastName()
                + " has an assignment that they have yet to complete in REPS. If they do not complete this assignment by the beginning of the school day tomorrow they will receive "
                + detentionType
                + " and must complete it during that time.";

        sendPtsEmail(
                student.getParentEmail(),
                punishment.getTeacherEmail(),
                student.getStudentEmail(),
                message,
                alertType + " REMINDER",
                languageCode
        );
    }

    public void notifyParentViaTextAndEmail(Punishment punishment, Student student, Infraction infraction, PunishmentResponse response) throws MessagingException {

        String message = templateBuilderService.createEmailText(
                student.getFirstName(),
                student.getLastName(),
                infraction.getInfractionLevel(),
                infraction.getInfractionName(),
                templateBuilderService.replaceString(punishment.getInfractionDescription().get(0)),
                student.getStudentEmail(),
                student.getPreferredLanguage()
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
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }
}
