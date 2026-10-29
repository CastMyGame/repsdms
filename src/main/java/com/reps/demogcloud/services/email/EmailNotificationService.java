package com.reps.demogcloud.services.email;

import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.services.UserContextService;
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
import org.springframework.security.access.AccessDeniedException;
import java.util.Objects;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailNotificationService {

    private final EmailSenderService emailSenderService;
    private final EmailTemplateBuilderService templateBuilderService;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;
    private final UserContextService userContextService;
    private final ClassAnnouncementAccess announcementAccess;

    // Keep identity-sensitive orchestration on the request thread until a durable actor is recorded (Phase 3).
    public void sendPtsEmail(String parentEmail, String teacherEmail, String studentEmail, String msg, String subject, String languageCode) throws MessagingException {

        String sender = getCurrentUserEmail();
        log.debug("Sending Email using {}", sender);

        Student student = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if (student == null) {
            throw new IllegalArgumentException("Student not found for email: " + studentEmail);
        }

        if (emailSenderService.isPhase1RoutingEnabled()) {
            sendVerifiedPts(student, parentEmail, teacherEmail, studentEmail, msg, subject, false);
            return;
        }

        List<String> spotters = student.getSpotters() != null ? student.getSpotters().stream().filter(e -> userContextService.hasIndependentStudentAccess(e, student)).toList() : new ArrayList<>();

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

    public void sendClassAnnouncement(ClassAnnouncementRequest request) throws MessagingException {
        sendClassAnnouncements(List.of(request));
    }

    public void sendClassAnnouncements(List<ClassAnnouncementRequest> requests) throws MessagingException {
        if (requests == null || requests.isEmpty()) throw new IllegalArgumentException("Choose a class before sending an announcement");
        // Validate ALL requested classes and recipients before translation, persistence or provider calls.
        var audiences = requests.stream().map(announcementAccess::resolve).toList();
        for (int i = 0; i < requests.size(); i++) sendAnnouncement(requests.get(i), audiences.get(i));
    }

    private void sendAnnouncement(ClassAnnouncementRequest request, ClassAnnouncementAccess.Audience audience) throws MessagingException {
        Map<String, List<String>> recipientsByLang = new HashMap<>();
        for (Student student : audience.students()) {
            String lang = templateBuilderService.normalizeLanguage(student.getPreferredLanguage());
            recipientsByLang.computeIfAbsent(lang, k -> new ArrayList<>()).add(student.getStudentEmail());
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
                    audience.sender(),
                    recipients,
                    subjectByLang.get(lang),
                    EmailContent.render("<p style=\"white-space:pre-wrap\">{{message}}</p>",Map.of("message",bodyByLang.get(lang)),true)
            );
        }
    }

    public void sendAlertEmail(String alertType, Punishment punishment, String languageCode) throws MessagingException {
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        if (student == null) return;

        String message=templateBuilderService.buildCaseMessage("punishment","REMINDER_"+("ISS".equals(alertType)?"ISS":"DETENTION"),
                Map.of("infractionName",Objects.toString(punishment.getInfractionName(),"")),student.getFirstName(),student.getLastName(),punishment.getTeacherEmail(),student.getPreferredLanguage());
        String reminderSubject=templateBuilderService.buildCommunicationSubject("punishment",Map.of(),student.getSchool(),student.getFirstName(),student.getLastName(),student.getPreferredLanguage());

        if (emailSenderService.isPhase1RoutingEnabled()) {
            sendVerifiedPts(student, student.getParentEmail(), punishment.getTeacherEmail(),
                    student.getStudentEmail(), message, reminderSubject, true);
            return;
        }
        sendPtsEmail(
                student.getParentEmail(),
                punishment.getTeacherEmail(),
                student.getStudentEmail(),
                message,
                reminderSubject,
                student.getPreferredLanguage()
        );
    }

    public void notifyParentViaTextAndEmail(Punishment punishment, Student student, Infraction infraction, PunishmentResponse response) throws MessagingException {

        String message = templateBuilderService.createEmailText(
                student.getFirstName(),
                student.getLastName(),
                infraction.getInfractionLevel(),
                infraction.getInfractionName(),
                punishment.getInfractionDescription().get(0),
                student.getStudentEmail(),
                student.getPreferredLanguage()
        );

        response.setMessage(message);

        sendPtsEmail(
                response.getParentToEmail(),
                response.getTeacherToEmail(),
                response.getStudentToEmail(),
                response.getMessage(),
                response.getSubject(),
                student.getPreferredLanguage()
        );
    }

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : null;
    }

    private String eligibleStaff(String email, Student student) {
        if (email == null || email.isBlank()) return null;
        try {
            userContextService.requireStaffInStudentSchool(email, student);
            return userContextService.getVerifiedStaffEmail(email);
        } catch (AccessDeniedException exception) {
            return null; // Former staff/spotters must not retain participation automatically.
        }
    }

    private void sendVerifiedPts(Student student, String parentEmail, String originalTeacher,
                                 String studentEmail, String body, String subject, boolean system) throws MessagingException {
        if (student.isArchived() || !Objects.equals(parentEmail, student.getParentEmail())
                || !Objects.equals(studentEmail, student.getStudentEmail())) {
            throw new AccessDeniedException("Notification recipients must match the active student record");
        }
        String teacher = eligibleStaff(originalTeacher, student);
        String actor = null;
        if (!system) {
            if (userContextService.isCurrentUserStudent()) {
                if (!studentEmail.equalsIgnoreCase(userContextService.getCurrentUserEmail())) {
                    throw new AccessDeniedException("Students may trigger only their own notifications");
                }
                system = true;
            } else {
                actor = userContextService.getCurrentStaffEmail();
                userContextService.requireCaseCommunicationAccess(originalTeacher, student);
            }
        }
        List<String> cc = new ArrayList<>();
        if (teacher != null) cc.add(teacher);
        cc.add(studentEmail);
        List<String> bcc = new ArrayList<>();
        if (student.getSpotters() != null) {
            for (String spotter : student.getSpotters()) {
                if (!userContextService.hasIndependentStudentAccess(spotter, student)) continue;
                String eligible = eligibleStaff(spotter, student);
                if (eligible != null && !cc.contains(eligible) && !bcc.contains(eligible)) bcc.add(eligible);
            }
        }
        if (system) {
            emailSenderService.sendSystemBulkEmail(parentEmail, cc, subject, body, bcc, teacher);
        } else {
            emailSenderService.sendStaffBulkEmail(parentEmail, cc, subject, body, bcc, actor);
        }
    }

    public void sendCaseResources(List<String> participants, String studentEmail, String subject,
                                  String body) throws MessagingException {
        Student student = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        String actor = userContextService.getCurrentStaffEmail();
        userContextService.requireStaffInStudentSchool(actor, student);
        if (student.isArchived()) throw new AccessDeniedException("Student is archived");
        // The caller supplies participants from the saved guidance case, never arbitrary frontend addresses.
        String originalTeacher = participants.stream()
                .filter(participant -> participant != null && !participant.equalsIgnoreCase(student.getGuidanceEmail()))
                .findFirst().orElse(null);
        userContextService.requireCaseCommunicationAccess(originalTeacher, student);
        List<String> bcc = new ArrayList<>();
        for (String participant : participants) {
            String eligible = eligibleStaff(participant, student);
            if (eligible != null && !bcc.contains(eligible)) bcc.add(eligible);
        }
        emailSenderService.sendStaffBulkEmail(student.getStudentEmail(), null, subject, body, bcc, actor);
    }
}
