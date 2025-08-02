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

            List<String> messages = new ArrayList<>();
            for (Punishment past : history) {
                messages.add(emailTemplateBuilderService.replaceString(
                        "Infraction took place on " + past.getTimeCreated() + ", Description: " + past.getInfractionDescription() + ". Assignment completed on " + past.getTimeClosed()
                ));
            }

            response.setSubject(ourSchool.getSchoolName() + " Office Referral for " + student.getFirstName() + " " + student.getLastName());
            response.setMessage("Thank you for using the teacher managed referral. Because " + student.getFirstName() + " " + student.getLastName() +
                    " has received their fourth or greater offense for " + infraction.getInfractionName() + ", they must now receive an office referral.\n" +
                    "Please complete an office referral for Failure to Comply with Disciplinary Action.\n" +
                    "Summary: " + messages);

            emailService.sendEmail(response.getTeacherToEmail(), response.getSubject(), response.getMessage());
        } else {
            sendInfractionNotification(punishment, emailService, student, infraction, response);
        }

        return response;
    }

    public void sendInfractionNotification(Punishment punishment, EmailService emailService, Student student, Infraction infraction, PunishmentResponse response) throws MessagingException {
        String msg = emailTemplateBuilderService.createEmailText(
                student.getFirstName(), student.getLastName(),
                infraction.getInfractionLevel(), infraction.getInfractionName(),
                emailTemplateBuilderService.replaceString(punishment.getInfractionDescription().get(0)),
                student.getStudentEmail()
        );
        response.setMessage(msg);
        emailService.sendPtsEmail(response.getParentToEmail(), response.getTeacherToEmail(), response.getStudentToEmail(), response.getSubject(), msg);
    }

    public PunishmentResponse sendCFREmailBasedOnType(Punishment punishment) {
        Student student = studentRepository.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        Infraction infraction = infractionRepository.findByInfractionId(punishment.getInfractionId());
        School school = schoolRepository.findSchoolBySchoolName(student.getSchool());

        PunishmentResponse response = setUpPunishmentResponse(punishment, student);
        response.setSubject(school.getSchoolName() + " referral for " + student.getFirstName() + " " + student.getLastName());
        punishment.setTimeClosed(LocalDate.now());

        String message = emailTemplateBuilderService.createCFRMessage(
                student.getFirstName(), student.getLastName(),
                infraction.getInfractionName(), response.getTeacherToEmail(), student.getStudentEmail()
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
