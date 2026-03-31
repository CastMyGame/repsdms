package com.reps.demogcloud.services.email;

import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import com.reps.demogcloud.services.EmailService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailServiceTest {

    @Mock
    private EmailNotificationService emailNotificationService;

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private EmailReferralService emailReferralService;

    @InjectMocks
    private EmailService emailService;

    @Test
    void createEmailAndSend_shouldDelegateToEmailSenderService() throws MessagingException {
        List<String> spotters = List.of("spotter1@test.com", "spotter2@test.com");

        doNothing().when(emailSenderService).sendDetailedEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                spotters,
                "message",
                "subject",
                "en"
        );

        assertDoesNotThrow(() ->
                emailService.createEmailAndSend(
                        "parent@test.com",
                        "teacher@test.com",
                        "student@test.com",
                        spotters,
                        "message",
                        "subject",
                        "en"
                )
        );

        verify(emailSenderService, times(1)).sendDetailedEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                spotters,
                "message",
                "subject",
                "en"
        );
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void createEmailAndSend_shouldPropagateMessagingException() throws MessagingException {
        List<String> spotters = List.of("spotter@test.com");

        doThrow(new MessagingException("send failed")).when(emailSenderService).sendDetailedEmail(
                anyString(), anyString(), anyString(), anyList(), anyString(), anyString(), anyString()
        );

        MessagingException exception = assertThrows(
                MessagingException.class,
                () -> emailService.createEmailAndSend(
                        "parent@test.com",
                        "teacher@test.com",
                        "student@test.com",
                        spotters,
                        "message",
                        "subject",
                        "en"
                )
        );

        assertEquals("send failed", exception.getMessage());
        verify(emailSenderService, times(1)).sendDetailedEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                spotters,
                "message",
                "subject",
                "en"
        );
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendEmail_shouldDelegateToEmailSenderService() throws MessagingException {
        doNothing().when(emailSenderService).sendEmail("to@test.com", "subject", "message", "en");

        assertDoesNotThrow(() -> emailService.sendEmail("to@test.com", "subject", "message", "en"));

        verify(emailSenderService, times(1)).sendEmail("to@test.com", "subject", "message", "en");
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendEmail_shouldPropagateMessagingException() throws MessagingException {
        doThrow(new MessagingException("email failed"))
                .when(emailSenderService).sendEmail("to@test.com", "subject", "message", "en");

        MessagingException exception = assertThrows(
                MessagingException.class,
                () -> emailService.sendEmail("to@test.com", "subject", "message", "en")
        );

        assertEquals("email failed", exception.getMessage());
        verify(emailSenderService, times(1)).sendEmail("to@test.com", "subject", "message", "en");
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendPtsEmail_shouldDelegateToNotificationService() throws MessagingException {
        doNothing().when(emailNotificationService).sendPtsEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                "message",
                "subject",
                "en"
        );

        assertDoesNotThrow(() ->
                emailService.sendPtsEmail(
                        "parent@test.com",
                        "teacher@test.com",
                        "student@test.com",
                        "message",
                        "subject",
                        "en"
                )
        );

        verify(emailNotificationService, times(1)).sendPtsEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                "message",
                "subject",
                "en"
        );
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendPtsEmail_shouldPropagateMessagingException() throws MessagingException {
        doThrow(new MessagingException("pts failed")).when(emailNotificationService).sendPtsEmail(
                anyString(), anyString(), anyString(), anyString(), anyString(), anyString()
        );

        MessagingException exception = assertThrows(
                MessagingException.class,
                () -> emailService.sendPtsEmail(
                        "parent@test.com",
                        "teacher@test.com",
                        "student@test.com",
                        "message",
                        "subject",
                        "en"
                )
        );

        assertEquals("pts failed", exception.getMessage());
        verify(emailNotificationService, times(1)).sendPtsEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                "message",
                "subject",
                "en"
        );
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendContactUsMail_shouldDelegateToEmailSenderService() {
        ContactUsRequest request = new ContactUsRequest();
        request.setEmail("user@test.com");
        request.setSubject("Help");
        request.setMessage("Need support");
        request.setPreferredLanguage("en");

        doNothing().when(emailSenderService).sendContactEmail("user@test.com", "Help", "Need support", "en");

        assertDoesNotThrow(() -> emailService.sendContactUsMail(request));

        verify(emailSenderService, times(1)).sendContactEmail("user@test.com", "Help", "Need support", "en");
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendAlertEmail_shouldDelegateToNotificationService() throws MessagingException {
        Punishment punishment = new Punishment();

        doNothing().when(emailNotificationService).sendAlertEmail("DETENTION", punishment, "en");

        assertDoesNotThrow(() -> emailService.sendAlertEmail("DETENTION", punishment, "en"));

        verify(emailNotificationService, times(1)).sendAlertEmail("DETENTION", punishment, "en");
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendAlertEmail_shouldPropagateMessagingException() throws MessagingException {
        Punishment punishment = new Punishment();

        doThrow(new MessagingException("alert failed"))
                .when(emailNotificationService).sendAlertEmail("DETENTION", punishment, "en");

        MessagingException exception = assertThrows(
                MessagingException.class,
                () -> emailService.sendAlertEmail("DETENTION", punishment, "en")
        );

        assertEquals("alert failed", exception.getMessage());
        verify(emailNotificationService, times(1)).sendAlertEmail("DETENTION", punishment, "en");
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendEmailGeneric_shouldDelegateToEmailSenderService() throws MessagingException {
        ArrayList<String> ccEmails = new ArrayList<>(List.of("cc1@test.com", "cc2@test.com"));

        doNothing().when(emailSenderService).sendGenericEmail(ccEmails, "to@test.com", "subject", "message", "en");

        assertDoesNotThrow(() ->
                emailService.sendEmailGeneric(ccEmails, "to@test.com", "subject", "message", "en")
        );

        verify(emailSenderService, times(1)).sendGenericEmail(ccEmails, "to@test.com", "subject", "message", "en");
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendEmailGeneric_shouldPropagateMessagingException() throws MessagingException {
        ArrayList<String> ccEmails = new ArrayList<>(List.of("cc@test.com"));

        doThrow(new MessagingException("generic failed"))
                .when(emailSenderService).sendGenericEmail(ccEmails, "to@test.com", "subject", "message", "en");

        MessagingException exception = assertThrows(
                MessagingException.class,
                () -> emailService.sendEmailGeneric(ccEmails, "to@test.com", "subject", "message", "en")
        );

        assertEquals("generic failed", exception.getMessage());
        verify(emailSenderService, times(1)).sendGenericEmail(ccEmails, "to@test.com", "subject", "message", "en");
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendClassAnnouncement_shouldDelegateToNotificationService() throws MessagingException {
        ClassAnnouncementRequest request = new ClassAnnouncementRequest();

        doNothing().when(emailNotificationService).sendClassAnnouncement(request);

        assertDoesNotThrow(() -> emailService.sendClassAnnouncement(request));

        verify(emailNotificationService, times(1)).sendClassAnnouncement(request);
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendClassAnnouncement_shouldPropagateMessagingException() throws MessagingException {
        ClassAnnouncementRequest request = new ClassAnnouncementRequest();

        doThrow(new MessagingException("announcement failed"))
                .when(emailNotificationService).sendClassAnnouncement(request);

        MessagingException exception = assertThrows(
                MessagingException.class,
                () -> emailService.sendClassAnnouncement(request)
        );

        assertEquals("announcement failed", exception.getMessage());
        verify(emailNotificationService, times(1)).sendClassAnnouncement(request);
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendEmailBasedOnType_shouldDelegateToReferralService() throws MessagingException {
        PunishmentFormRequest formRequest = new PunishmentFormRequest();
        Punishment punishment = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(emailReferralService.sendEmailBasedOnType(formRequest, punishment, emailService)).thenReturn(response);

        PunishmentResponse result = emailService.sendEmailBasedOnType(formRequest, punishment, emailService);

        assertNotNull(result);
        assertSame(response, result);

        verify(emailReferralService, times(1)).sendEmailBasedOnType(formRequest, punishment, emailService);
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendEmailBasedOnType_shouldPropagateMessagingException() throws MessagingException {
        PunishmentFormRequest formRequest = new PunishmentFormRequest();
        Punishment punishment = new Punishment();

        when(emailReferralService.sendEmailBasedOnType(formRequest, punishment, emailService))
                .thenThrow(new MessagingException("referral failed"));

        MessagingException exception = assertThrows(
                MessagingException.class,
                () -> emailService.sendEmailBasedOnType(formRequest, punishment, emailService)
        );

        assertEquals("referral failed", exception.getMessage());
        verify(emailReferralService, times(1)).sendEmailBasedOnType(formRequest, punishment, emailService);
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendTextAndEmail_shouldDelegateToNotificationService() throws MessagingException {
        Punishment punishment = new Punishment();
        Student student = new Student();
        Infraction infraction = new Infraction();
        PunishmentResponse response = new PunishmentResponse();

        doNothing().when(emailNotificationService)
                .notifyParentViaTextAndEmail(punishment, student, infraction, response);

        assertDoesNotThrow(() ->
                emailService.sendTextAndEmail(punishment, student, infraction, response)
        );

        verify(emailNotificationService, times(1))
                .notifyParentViaTextAndEmail(punishment, student, infraction, response);
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendTextAndEmail_shouldPropagateMessagingException() throws MessagingException {
        Punishment punishment = new Punishment();
        Student student = new Student();
        Infraction infraction = new Infraction();
        PunishmentResponse response = new PunishmentResponse();

        doThrow(new MessagingException("text/email failed"))
                .when(emailNotificationService)
                .notifyParentViaTextAndEmail(punishment, student, infraction, response);

        MessagingException exception = assertThrows(
                MessagingException.class,
                () -> emailService.sendTextAndEmail(punishment, student, infraction, response)
        );

        assertEquals("text/email failed", exception.getMessage());
        verify(emailNotificationService, times(1))
                .notifyParentViaTextAndEmail(punishment, student, infraction, response);
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }

    @Test
    void sendCFREmailBasedOnType_shouldDelegateToReferralService() {
        Punishment punishment = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(emailReferralService.sendCFREmailBasedOnType(punishment)).thenReturn(response);

        PunishmentResponse result = emailService.sendCFREmailBasedOnType(punishment);

        assertNotNull(result);
        assertSame(response, result);

        verify(emailReferralService, times(1)).sendCFREmailBasedOnType(punishment);
        verifyNoMoreInteractions(emailSenderService, emailNotificationService, emailReferralService);
    }
}