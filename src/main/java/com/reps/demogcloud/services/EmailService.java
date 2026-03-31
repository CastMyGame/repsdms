package com.reps.demogcloud.services;

import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import com.reps.demogcloud.services.email.EmailNotificationService;
import com.reps.demogcloud.services.email.EmailReferralService;
import com.reps.demogcloud.services.email.EmailSenderService;
import jakarta.mail.MessagingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class EmailService {

    private final EmailNotificationService emailNotificationService;
    private final EmailSenderService emailSenderService;
    private final EmailReferralService emailReferralService;

    public void createEmailAndSend(
            String parentEmail,
            String teacherEmail,
            String studentEmail,
            List<String> spotters,
            String msg,
            String subject,
            String languageCode
    ) throws MessagingException {
        emailSenderService.sendDetailedEmail(parentEmail, teacherEmail, studentEmail, spotters, msg, subject, languageCode);
    }

    @Async
    public void sendEmail(String toEmail, String subject, String msg, String languageCode) throws MessagingException {
        emailSenderService.sendEmail(toEmail, subject, msg, languageCode);
    }

    @Async
    public void sendPtsEmail(
            String parentEmail,
            String teacherEmail,
            String studentEmail,
            String msg,
            String subject,
            String languageCode
    ) throws MessagingException {
        emailNotificationService.sendPtsEmail(parentEmail, teacherEmail, studentEmail, msg, subject, languageCode);
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

    public void sendAlertEmail(String detention, Punishment punishment, String languageCode) throws MessagingException {
        emailNotificationService.sendAlertEmail(detention, punishment, languageCode);
    }

    @Async
    public void sendEmailGeneric(
            ArrayList<String> ccEmails,
            String recipientEmail,
            String subject,
            String msg,
            String languageCode
    ) throws MessagingException {
        emailSenderService.sendGenericEmail(ccEmails, recipientEmail, subject, msg, languageCode);
    }

    @Async
    public void sendClassAnnouncement(ClassAnnouncementRequest request) throws MessagingException {
        emailNotificationService.sendClassAnnouncement(request);
    }

    public PunishmentResponse sendEmailBasedOnType(
            PunishmentFormRequest formRequest,
            Punishment punishment,
            EmailService emailService
    ) throws MessagingException {
        return emailReferralService.sendEmailBasedOnType(formRequest, punishment, emailService);
    }

    @Async
    public void sendTextAndEmail(
            Punishment punishment,
            Student student,
            Infraction infraction,
            PunishmentResponse punishmentResponse
    ) throws MessagingException {
        emailNotificationService.notifyParentViaTextAndEmail(punishment, student, infraction, punishmentResponse);
    }

    public PunishmentResponse sendCFREmailBasedOnType(Punishment punishment) {
        return emailReferralService.sendCFREmailBasedOnType(punishment);
    }
}