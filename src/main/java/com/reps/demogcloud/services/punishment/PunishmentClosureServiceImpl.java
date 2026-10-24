package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.punishment.StudentAnswer;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.EmailService;
import com.reps.demogcloud.services.email.EmailTemplateBuilderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PunishmentClosureServiceImpl implements PunishmentClosureService {

    private final PunishRepository punishRepository;
    private final StudentRepository studentRepository;
    private final InfractionRepository infractionRepository;
    private final EmailService emailService;
    private final EmailTemplateBuilderService emailTemplateBuilderService;
    private final com.reps.demogcloud.services.email.RequiredNotificationService requiredNotifications;

    private Punishment managedChange(Punishment punishment, String event, List<StudentAnswer> answers, String explanation) {
        if (punishment == null) throw new ResourceNotFoundException("Case not found");
        if ((Set.of("CLOSED","COMPLETED").contains(event) && "CLOSED".equals(punishment.getStatus()))
                || ("SUBMITTED".equals(event) && "PENDING".equals(punishment.getStatus()))
                || ("REJECTED".equals(event) && "OPEN".equals(punishment.getStatus()))
                || ("ARCHIVED".equals(event) && punishment.isArchived())
                || ("RESTORED".equals(event) && !punishment.isArchived())) {
            requiredNotifications.requireActionAccess(punishment, event);
            return punishment;
        }
        if (!requiredNotifications.prepare(punishment, event)) return punishment;
        switch (event) {
            case "CLOSED", "COMPLETED" -> {
                punishment.setStatus("CLOSED"); punishment.setClosedTimes(punishment.getClosedTimes()+1); punishment.setTimeClosed(LocalDate.now());
            }
            case "SUBMITTED" -> {
                var description = new ArrayList<>(punishment.getInfractionDescription()==null?List.<String>of():punishment.getInfractionDescription());
                if (answers != null) for(var answer:answers) description.add(answer.toString());
                punishment.setInfractionDescription(description); punishment.setStatus("PENDING");
            }
            case "REJECTED" -> {
                var description = punishment.getInfractionDescription();
                if(description==null || description.size()<2) throw new IllegalArgumentException("Submitted answers are missing");
                punishment.setAnswerHistory(new Date(),new ArrayList<>(description.subList(1,description.size())));
                punishment.setInfractionDescription(new ArrayList<>(List.of("",description.get(1)))); punishment.setStatus("OPEN"); punishment.setMapIndex(0);
            }
            case "ARCHIVED" -> {
                punishment.setArchived(true); punishment.setArchivedOn(LocalDate.now());
                punishment.setArchivedBy(requiredNotifications.currentStaff()); punishment.setArchivedExplanation(explanation);
            }
            case "RESTORED" -> { punishment.setArchived(false); punishment.setArchivedOn(null); punishment.setArchivedBy(null); punishment.setArchivedExplanation(null); }
            default -> throw new IllegalArgumentException("Unsupported disciplinary action");
        }
        requiredNotifications.captureContent(punishment);
        return punishRepository.save(punishment);
    }
    @Override
    public PunishmentResponse closePunishment(String infractionName, String studentEmail, List<StudentAnswer> studentAnswers) {
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "Submit a specific assignment through its case page. Legacy work requires explicit staff recovery.");
    }

    @Override
    public PunishmentResponse closeByPunishmentId(String punishmentId) throws MessagingException {
        Punishment checked = punishRepository.findByPunishmentId(punishmentId);
        if(checked==null) throw new ResourceNotFoundException("Case not found");
        requiredNotifications.requireActionAccess(checked,"CLOSED");
        if("CLOSED".equals(checked.getStatus())) {
            var response=new PunishmentResponse(); response.setPunishment(checked); return response;
        }
        if(checked.getAssignmentWork()!=null) throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.CONFLICT,"Review this assignment through its case page");
        if (requiredNotifications.isEnabled()) return requiredNotifications.response(managedChange(
                punishRepository.findByPunishmentId(punishmentId), "CLOSED", null, null));
        Punishment punishment = checked;
        if (punishment == null) throw new ResourceNotFoundException("No punishment with ID " + punishmentId);

        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());

        punishment.setStatus("CLOSED");
        punishment.setClosedTimes(punishment.getClosedTimes() + 1);
        punishment.setTimeClosed(LocalDate.now());
        punishRepository.save(punishment);

        String message = emailTemplateBuilderService.buildCompletionMessage(
                student.getFirstName(),
                student.getLastName(),
                infraction.getInfractionName(),
                punishment.getTeacherEmail(),
                student.getPreferredLanguage()
        );

        String subject = emailTemplateBuilderService.buildCompletionSubject(
                null, // no school in this subject variant
                student.getFirstName(),
                student.getLastName(),
                student.getPreferredLanguage(),
                false
        );

        PunishmentResponse response = new PunishmentResponse();
        response.setPunishment(punishment);
        response.setMessage(message);
        response.setSubject(subject);
        response.setParentToEmail(student.getParentEmail());
        response.setStudentToEmail(student.getStudentEmail());
        response.setTeacherToEmail(punishment.getTeacherEmail());

        emailService.sendPtsEmail(response.getParentToEmail(), response.getTeacherToEmail(),
                response.getStudentToEmail(), response.getMessage(), response.getSubject(), student.getPreferredLanguage());

        return response;
    }

    @Override
    public Punishment rejectLevelThree(String punishmentId) {
        Punishment checked=punishRepository.findByPunishmentId(punishmentId);
        if(checked==null) throw new ResourceNotFoundException("Case not found");
        requiredNotifications.requireActionAccess(checked,"REJECTED");
        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                "Review or explicitly recover this assignment through its case page; existing history is preserved");
    }

    @Override
    public Punishment archiveRecord(String punishmentId, String userId, String explanation) throws MessagingException {
        if (requiredNotifications.isEnabled()) return managedChange(punishRepository.findByPunishmentId(punishmentId), "ARCHIVED", null, explanation);
        Punishment punishment = punishRepository.findByPunishmentId(punishmentId);
        if (punishment == null) throw new ResourceNotFoundException("Punishment not found");

        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());

        punishment.setArchived(true);
        punishment.setArchivedOn(LocalDate.now());
        punishment.setArchivedBy(userId);
        punishment.setArchivedExplanation(explanation);

        punishRepository.save(punishment);

        String message = emailTemplateBuilderService.buildPunishmentDeletedMessage(student.getFirstName(), student.getLastName(), punishment.getInfractionName(), String.valueOf(infraction.getInfractionLevel()), explanation, student.getPreferredLanguage());
        String subject = emailTemplateBuilderService.buildPunishmentDeletedSubject(
                student.getSchool(),
                student.getFirstName(),
                student.getLastName(),
                student.getPreferredLanguage()
        );
        emailService.sendPtsEmail(student.getParentEmail(), punishment.getTeacherEmail(),
                student.getStudentEmail(),message, subject, student.getPreferredLanguage());

        return punishment;
    }

    @Override
    public Punishment restoreRecord(String punishmentId) throws MessagingException {
        if (requiredNotifications.isEnabled()) return managedChange(punishRepository.findByPunishmentId(punishmentId), "RESTORED", null, null);
        Punishment punishment = punishRepository.findByPunishmentIdAndArchived(punishmentId, true);
        if (punishment == null) throw new ResourceNotFoundException("Archived punishment not found");

        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());

        punishment.setArchived(false);
        punishment.setArchivedOn(null);
        punishment.setArchivedBy(null);
        punishment.setArchivedExplanation(null);

        punishRepository.save(punishment);


        String subject = emailTemplateBuilderService.buildPunishmentRestoredSubject(
                student.getSchool(),
                student.getFirstName(),
                student.getLastName(),
                student.getPreferredLanguage()
        );

        String message = emailTemplateBuilderService.buildPunishmentRestoredMessage(
                student.getFirstName(),
                student.getLastName(),
                student.getPreferredLanguage()
        );

        emailService.sendPtsEmail(
                student.getParentEmail(),
                punishment.getTeacherEmail(),
                student.getStudentEmail(),
                message,
                subject,
                student.getPreferredLanguage()
        );

        return punishment;
    }

}
