package com.reps.demogcloud.services.email;

import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailSenderServiceTest {

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private EmailRoutingService emailRoutingService;

    @InjectMocks
    private EmailSenderService service;

    // -----------------------
    // sendEmail
    // -----------------------

    @Test
    void sendEmail_basic_shouldDelegateToRouting() throws MessagingException {
        service.sendEmail("to@test.com", "subject", "msg");

        verify(emailRoutingService).sendEmail("to@test.com", "subject", "msg", null);
    }

    @Test
    void sendEmail_withLanguage_shouldDelegate() throws MessagingException {
        service.sendEmail("to@test.com", "subject", "msg", "en");

        verify(emailRoutingService).sendEmail("to@test.com", "subject", "msg", null);
    }

    @Test
    void sendEmail_withFrom_shouldDelegate() throws MessagingException {
        service.sendEmail("to@test.com", "subject", "msg", "from@test.com", "en");

        verify(emailRoutingService).sendEmail("to@test.com", "subject", "msg", "from@test.com");
    }

    // -----------------------
    // sendBulkEmail
    // -----------------------

    @Test
    void sendBulkEmail_shouldDelegate() throws MessagingException {
        service.sendBulkEmail("to@test.com", List.of("cc@test.com"), "sub", "msg", List.of("bcc@test.com"));

        verify(emailRoutingService).sendBulkEmail(
                "to@test.com",
                List.of("cc@test.com"),
                "sub",
                "msg",
                List.of("bcc@test.com"),
                null
        );
    }

    @Test
    void sendBulkEmail_withFrom_shouldDelegate() throws MessagingException {
        service.sendBulkEmail("to@test.com", List.of("cc@test.com"), "sub", "msg", List.of("bcc@test.com"), "from@test.com", "en");

        verify(emailRoutingService).sendBulkEmail(
                "to@test.com",
                List.of("cc@test.com"),
                "sub",
                "msg",
                List.of("bcc@test.com"),
                "from@test.com"
        );
    }

    // -----------------------
    // sendContactEmail
    // -----------------------

    @Test
    void sendContactEmail_shouldBuildAndSendMessage() {
        service.sendContactEmail("to@test.com", "subject", "body", "en");

        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);

        verify(javaMailSender).send(captor.capture());

        SimpleMailMessage message = captor.getValue();

        assert message.getTo()[0].equals("to@test.com");
        assert message.getSubject().equals("subject");
        assert message.getText().equals("body");
        assert message.getFrom().equals("REPS.DMS@GMAIL.COM");
    }

    // -----------------------
    // sendClassAnnouncement
    // -----------------------

    @Test
    void sendClassAnnouncement_shouldSend_whenMultipleRecipients() throws MessagingException {
        service.sendClassAnnouncement(
                "teacher@test.com",
                List.of("r1@test.com", "r2@test.com"),
                "sub",
                "msg"
        );

        verify(emailRoutingService).sendBulkEmail(
                "r1@test.com",
                List.of("r2@test.com"),
                "sub",
                "msg",
                null,
                "teacher@test.com"
        );
    }

    @Test
    void sendClassAnnouncement_shouldSend_whenSingleRecipient() throws MessagingException {
        service.sendClassAnnouncement(
                "teacher@test.com",
                List.of("r1@test.com"),
                "sub",
                "msg"
        );

        verify(emailRoutingService).sendBulkEmail(
                "r1@test.com",
                null,
                "sub",
                "msg",
                null,
                "teacher@test.com"
        );
    }

    @Test
    void sendClassAnnouncement_shouldDoNothing_whenEmpty() throws MessagingException {
        service.sendClassAnnouncement("teacher@test.com", List.of(), "sub", "msg");

        verifyNoInteractions(emailRoutingService);
    }

    // -----------------------
    // sendSafe
    // -----------------------

    @Test
    void sendSafe_shouldNotThrow_whenExceptionOccurs() throws MessagingException {
        doThrow(new MessagingException("fail"))
                .when(emailRoutingService)
                .sendEmail(any(), any(), any(), any());

        assertDoesNotThrow(() ->
                service.sendSafe("to@test.com", "sub", "msg", "en")
        );
    }

    // -----------------------
    // sendDetailedEmail
    // -----------------------

    @Test
    void sendDetailedEmail_shouldDelegate() throws MessagingException {
        service.sendDetailedEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                List.of("spot@test.com"),
                "msg",
                "sub",
                "en"
        );

        verify(emailRoutingService).sendDetailedEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                List.of("spot@test.com"),
                "msg",
                "sub",
                null
        );
    }

    @Test
    void sendDetailedEmail_withFrom_shouldDelegate() throws MessagingException {
        service.sendDetailedEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                List.of("spot@test.com"),
                "msg",
                "sub",
                "from@test.com",
                "en"
        );

        verify(emailRoutingService).sendDetailedEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                List.of("spot@test.com"),
                "msg",
                "sub",
                "from@test.com"
        );
    }

    // -----------------------
    // sendGenericEmail
    // -----------------------

    @Test
    void sendGenericEmail_shouldDelegate() throws MessagingException {
        service.sendGenericEmail(
                List.of("cc@test.com"),
                "to@test.com",
                "sub",
                "msg",
                "en"
        );

        verify(emailRoutingService).sendGenericEmail(
                List.of("cc@test.com"),
                "to@test.com",
                "sub",
                "msg",
                null
        );
    }

    @Test
    void sendGenericEmail_withFrom_shouldDelegate() throws MessagingException {
        service.sendGenericEmail(
                List.of("cc@test.com"),
                "to@test.com",
                "sub",
                "msg",
                "from@test.com",
                "en"
        );

        verify(emailRoutingService).sendGenericEmail(
                List.of("cc@test.com"),
                "to@test.com",
                "sub",
                "msg",
                "from@test.com"
        );
    }
}
