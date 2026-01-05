package com.reps.demogcloud.services.email;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.mail.MessagingException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class EmailReferralService {

    private final EmailNotificationService emailNotificationService;
    private final EmailTemplateBuilderService emailTemplateBuilderService;
    private final PunishRepository punishRepository;
    private final StudentRepository studentRepository;
    private final InfractionRepository infractionRepository;
    private final SchoolRepository schoolRepository;

    public PunishmentResponse sendEmailBasedOnType(PunishmentFormRequest formRequest, Punishment punishment, EmailService emailService) throws MessagingException {
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
        School ourSchool = schoolRepository.findSchoolBySchoolName(student.getSchool());

        PunishmentResponse response = setUpPunishmentResponse(punishment, student);
        response.setSubject(ourSchool.getSchoolName() + " Referral for " + student.getFirstName() + " " + student.getLastName());

        if (punishment.getClosedTimes() == ourSchool.getMaxPunishLevel()) {
            punishment.setTimeClosed(LocalDate.now());
            punishment.setStatus("REFERRAL");
            punishRepository.save(punishment);

            List<Punishment> history = new ArrayList<>();
            history.addAll(punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndArchived(student.getStudentEmail(), infraction.getInfractionName(), "CLOSED", false));
            history.addAll(punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndArchived(student.getStudentEmail(), infraction.getInfractionName(), "REFERRAL", false));
            history.addAll(punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndArchived(student.getStudentEmail(), infraction.getInfractionName(), "CFR", false));

            List<String> summaryLines = new ArrayList<>();
            for (Punishment past : history) {
                String timeCreated = String.valueOf(past.getTimeCreated());
                String timeClosed = String.valueOf(past.getTimeClosed());

                // teacher/student entered content (user input)
                String rawDescription = String.valueOf(past.getInfractionDescription());
                String translatedDescription = emailTemplateBuilderService.translateUserInput(rawDescription, student.getPreferredLanguage());

                String line =
                        "Infraction took place on " + timeCreated +
                                ", Description: " + translatedDescription +
                                ". Assignment completed on " + timeClosed;

                summaryLines.add(emailTemplateBuilderService.replaceString(line));
            }

// join lines so the template can insert it nicely
            String summary = String.join("\n", summaryLines);

            response.setSubject(emailTemplateBuilderService.buildSubject(
                    ourSchool.getSchoolName(),
                    student.getFirstName(),
                    student.getLastName(),
                    student.getPreferredLanguage(),
                    true
            ));
            response.setMessage(emailTemplateBuilderService.buildOfficeReferralMessage(
                    student.getFirstName(),
                    student.getLastName(),
                    infraction.getInfractionName(),
                    summary,
                    student.getPreferredLanguage()
            ));

            emailService.sendEmail(response.getTeacherToEmail(), response.getSubject(), response.getMessage(),student.getPreferredLanguage());
        } else {
            sendInfractionNotification(punishment, emailService, student, infraction, response);
        }

        return response;
    }

    public void sendInfractionNotification(Punishment punishment, EmailService emailService, Student student, Infraction infraction, PunishmentResponse response) throws MessagingException {
        String description0 = "";
        if (punishment.getInfractionDescription() != null && !punishment.getInfractionDescription().isEmpty()) {
            description0 = punishment.getInfractionDescription().get(0);
        }
        String msg = emailTemplateBuilderService.createEmailText(
                student.getFirstName(),
                student.getLastName(),
                infraction.getInfractionLevel(),
                infraction.getInfractionName(),
                emailTemplateBuilderService.replaceString(description0),
                student.getStudentEmail(),
                student.getPreferredLanguage()
        );
        String subject = emailTemplateBuilderService.buildSubject(
                student.getSchool(),
                student.getFirstName(),
                student.getLastName(),
                student.getPreferredLanguage(),
                false
        );
        response.setMessage(msg);
        response.setSubject(subject);
        emailService.sendPtsEmail(response.getParentToEmail(), response.getTeacherToEmail(), response.getStudentToEmail(), msg, subject, student.getPreferredLanguage());
    }

    public PunishmentResponse sendCFREmailBasedOnType(Punishment punishment) {
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
        School school = schoolRepository.findSchoolBySchoolName(student.getSchool());

        PunishmentResponse response = setUpPunishmentResponse(punishment, student);
        response.setSubject(emailTemplateBuilderService.buildSubject(
                school.getSchoolName(),
                student.getFirstName(),
                student.getLastName(),
                student.getPreferredLanguage(),
                false
        ));
        punishment.setTimeClosed(LocalDate.now());

        String message = emailTemplateBuilderService.createCFRMessage(
                student.getFirstName(), student.getLastName(),
                infraction.getInfractionName(), response.getTeacherToEmail(), student.getStudentEmail(), student.getPreferredLanguage()
        );

        response.setMessage(message);
        return response;
    }

    private PunishmentResponse setUpPunishmentResponse(Punishment punishment, Student student) {
        PunishmentResponse response = new PunishmentResponse();
        response.setParentToEmail(student.getParentEmail());
        response.setStudentToEmail(student.getStudentEmail());
        response.setTeacherToEmail(punishment.getTeacherEmail());
        response.setPunishment(punishment);
        return response;
    }
}
