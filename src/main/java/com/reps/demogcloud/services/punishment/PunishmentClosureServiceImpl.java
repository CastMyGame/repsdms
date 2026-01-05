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
import com.reps.demogcloud.utils.PunishmentUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PunishmentClosureServiceImpl implements PunishmentClosureService {

    private final PunishmentUtils punishmentUtils;
    private final PunishRepository punishRepository;
    private final StudentRepository studentRepository;
    private final InfractionRepository infractionRepository;
    private final EmailService emailService;
    private final EmailTemplateBuilderService emailTemplateBuilderService;

    @Override
    public PunishmentResponse closePunishment(String infractionName, String studentEmail, List<StudentAnswer> studentAnswers) throws MessagingException {
        List<Punishment> openPunishments = punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(studentEmail, infractionName, "OPEN");

        // Filter out archived punishments
        List<Punishment> activeOpenPunishments = openPunishments.stream()
                .filter(p -> !p.isArchived())
                .toList();

        if (activeOpenPunishments.isEmpty()) {
            throw new ResourceNotFoundException("No open punishments found for the given criteria.");
        }

        Punishment punishmentToClose = activeOpenPunishments.get(0);
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishmentToClose.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishmentToClose.getInfractionId());

        if (!studentAnswers.isEmpty()) {
            // Add student answers to the infraction description
            ArrayList<String> updatedDescriptions = new ArrayList<>(punishmentToClose.getInfractionDescription());
            for (StudentAnswer answer : studentAnswers) {
                updatedDescriptions.add(answer.toString());
            }
            punishmentToClose.setInfractionDescription(updatedDescriptions);
            punishmentToClose.setStatus("PENDING");
            punishRepository.save(punishmentToClose);

            PunishmentResponse response = new PunishmentResponse();
            response.setPunishment(punishmentToClose);
            return response;
        } else {
            // Mark punishment as closed
            punishmentToClose.setStatus("CLOSED");
            punishmentToClose.setClosedTimes(punishmentToClose.getClosedTimes() + 1);
            punishmentToClose.setTimeClosed(LocalDate.now());
            punishRepository.save(punishmentToClose);

            PunishmentResponse response = new PunishmentResponse();
            response.setPunishment(punishmentToClose);

            String message = emailTemplateBuilderService.buildCompletionMessage(student.getFirstName(), student.getLastName(), punishmentToClose.getInfractionName(), punishmentToClose.getTeacherEmail(), student.getPreferredLanguage());
            String subject = emailTemplateBuilderService.buildCompletionSubject(student.getSchool(), student.getFirstName(), student.getLastName(), student.getPreferredLanguage(), true);
            response.setMessage(message);
            response.setSubject(subject);
            response.setParentToEmail(student.getParentEmail());
            response.setStudentToEmail(student.getStudentEmail());
            response.setTeacherToEmail(punishmentToClose.getTeacherEmail());

            emailService.sendPtsEmail(
                    response.getParentToEmail(),
                    response.getTeacherToEmail(),
                    response.getStudentToEmail(),
                    response.getMessage(),
                    response.getSubject(),
                    student.getPreferredLanguage()
            );

            return response;
        }
    }

    @Override
    public PunishmentResponse closeByPunishmentId(String punishmentId) throws MessagingException {
        Punishment punishment = punishRepository.findByPunishmentId(punishmentId);
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
    public Punishment rejectLevelThree(String punishmentId) throws MessagingException {
        Punishment punishment = punishRepository.findByPunishmentId(punishmentId);
        if (punishment == null) throw new ResourceNotFoundException("No punishment with ID " + punishmentId);

        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        ArrayList<String> infractionContext = punishment.getInfractionDescription();
        String resetContext = infractionContext.get(1);
        List<String> contextToStore = infractionContext.subList(1, infractionContext.size());

        ArrayList<String> newDescription = new ArrayList<>();
        newDescription.add("");
        newDescription.add(resetContext);

        punishment.setInfractionDescription(newDescription);
        punishment.setStatus("OPEN");
        punishment.setMapIndex(0);

        Date currentDate = new Date();

        punishment.getAnswerHistory().put(currentDate, new ArrayList<>(contextToStore));

        punishRepository.save(punishment);

        String message = "Hello,\nUnfortunately your answers were not acceptable. You must resubmit with better responses.\n\n" +
                "Feedback: " + contextToStore + "\n\nYou may reply to this message with any questions.";

        String subject = "Level Three Answers Not Accepted for " + student.getFirstName() + " " + student.getLastName();

        emailService.sendPtsEmail(student.getParentEmail(), punishment.getTeacherEmail(),
                student.getStudentEmail(), message, subject, student.getPreferredLanguage());

        return punishment;
    }

    @Override
    public Punishment archiveRecord(String punishmentId, String userId, String explanation) throws MessagingException {
        Punishment punishment = punishRepository.findByPunishmentId(punishmentId);
        if (punishment == null) throw new ResourceNotFoundException("Punishment not found");

        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());

        punishment.setArchived(true);
        punishment.setArchivedOn(LocalDate.now());
        punishment.setArchivedBy(userId);
        punishment.setArchivedExplanation(explanation);

        punishRepository.save(punishment);

        String message = "Hello,\nThe referral for your child, " + student.getFirstName() + " " + student.getLastName() +
                ", was entered in error and has been removed. The infraction was for " + infraction.getInfractionName() +
                " (Level " + infraction.getInfractionLevel() + ").\n\nThank you for your patience.";

        String subject = student.getSchool() + " Punishment Deleted for " + student.getFirstName() + " " + student.getLastName();

        emailService.sendPtsEmail(student.getParentEmail(), punishment.getTeacherEmail(),
                student.getStudentEmail(),message, subject, student.getPreferredLanguage());

        return punishment;
    }

    @Override
    public Punishment restoreRecord(String punishmentId) throws MessagingException {
        Punishment punishment = punishRepository.findByPunishmentIdAndArchived(punishmentId, true);
        if (punishment == null) throw new ResourceNotFoundException("Archived punishment not found");

        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());

        punishment.setArchived(false);
        punishment.setArchivedOn(null);
        punishment.setArchivedBy(null);
        punishment.setArchivedExplanation(null);

        punishRepository.save(punishment);

        String message = "Hello,\nA referral for your child, " + student.getFirstName() + " " + student.getLastName() +
                ", was unintentionally removed and has now been restored.\nPlease have them complete their assignment at repsdiscipline.vercel.app/student-login.";

        String subject = student.getSchool() + " Punishment Restored for " + student.getFirstName() + " " + student.getLastName();

        emailService.sendPtsEmail(student.getParentEmail(), punishment.getTeacherEmail(),
                student.getStudentEmail(), message, subject, student.getPreferredLanguage());

        return punishment;
    }

}
