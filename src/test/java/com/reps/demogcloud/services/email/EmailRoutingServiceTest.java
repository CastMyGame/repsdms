package com.reps.demogcloud.services.email;

import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import com.reps.demogcloud.security.services.GoogleMailService;
import com.reps.demogcloud.security.services.GoogleOAuthTokenStore;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.List;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailRoutingServiceTest {

    @Mock
    private Environment env;

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private GoogleMailService googleMailService;

    @Mock
    private GoogleOAuthTokenStore tokenStore;

    @InjectMocks
    private EmailRoutingService emailRoutingService;

    private MimeMessage mimeMessage;

    @BeforeEach
    void setUp() {
        mimeMessage = new MimeMessage(Session.getInstance(new Properties()));
    }

    @Test
    void sendEmail_shouldUseSmtp_whenProviderIsSmtp() throws Exception {
        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("smtp");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendEmail("to@test.com", "Subject", "<p>Body</p>", "from@test.com", true);

        verify(javaMailSender, times(1)).createMimeMessage();
        verify(javaMailSender, times(1)).send(mimeMessage);
        verify(tokenStore, never()).getToken(anyString());
        verifyNoInteractions(googleMailService);
    }

    @Test
    void sendEmail_shouldUseGmailApi_whenProviderIsGmailApiAndTokenExists() throws Exception {
        GoogleOAuthTokenStore.GoogleOAuthToken token = mock(GoogleOAuthTokenStore.GoogleOAuthToken.class);

        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("gmail-api");
        when(tokenStore.getToken("from@test.com")).thenReturn(Optional.of(token));

        emailRoutingService.sendEmail("to@test.com", "Subject", "<p>Body</p>", "from@test.com", true);

        ArgumentCaptor<GoogleMailSendRequest> captor = ArgumentCaptor.forClass(GoogleMailSendRequest.class);
        verify(googleMailService, times(1)).sendEmailOnBehalf(eq("from@test.com"), captor.capture());

        GoogleMailSendRequest request = captor.getValue();
        assertEquals("to@test.com", request.getTo());
        assertEquals("Subject", request.getSubject());
        assertEquals("<p>Body</p>", request.getBody());
        assertEquals("from@test.com", request.getFrom());

        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_shouldFallbackToSmtp_whenGmailApiFails() throws Exception {
        GoogleOAuthTokenStore.GoogleOAuthToken token = mock(GoogleOAuthTokenStore.GoogleOAuthToken.class);

        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("gmail-api");
        when(tokenStore.getToken("from@test.com")).thenReturn(Optional.of(token));
        doThrow(new RuntimeException("gmail api failed"))
                .when(googleMailService).sendEmailOnBehalf(eq("from@test.com"), any(GoogleMailSendRequest.class));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendEmail("to@test.com", "Subject", "<p>Body</p>", "from@test.com", true);

        verify(googleMailService, times(1)).sendEmailOnBehalf(eq("from@test.com"), any(GoogleMailSendRequest.class));
        verify(javaMailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendEmail_shouldFallbackToSmtp_whenNoTokenExists() throws Exception {
        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("gmail-api");
        when(tokenStore.getToken("from@test.com")).thenReturn(Optional.empty());
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendEmail("to@test.com", "Subject", "<p>Body</p>", "from@test.com", true);

        verify(tokenStore, times(1)).getToken("from@test.com");
        verify(javaMailSender, times(1)).send(mimeMessage);
        verifyNoInteractions(googleMailService);
    }

    @Test
    void sendEmail_shouldUseSmtp_whenFromEmailIsNullEvenIfGmailApiMode() throws Exception {
        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("gmail-api");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendEmail("to@test.com", "Subject", "<p>Body</p>", null, true);

        verify(tokenStore, never()).getToken(anyString());
        verify(javaMailSender, times(1)).send(mimeMessage);
        verifyNoInteractions(googleMailService);
    }

    @Test
    void sendEmail_shouldUseHybridForUserInitiated_whenEnabled() throws Exception {
        GoogleOAuthTokenStore.GoogleOAuthToken token = mock(GoogleOAuthTokenStore.GoogleOAuthToken.class);

        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("hybrid");
        when(env.getProperty("mail.service.user-emails.enabled", "false")).thenReturn("true");
        when(tokenStore.getToken("from@test.com")).thenReturn(Optional.of(token));

        emailRoutingService.sendEmail("to@test.com", "Subject", "Body", "from@test.com", true);

        verify(googleMailService, times(1)).sendEmailOnBehalf(eq("from@test.com"), any(GoogleMailSendRequest.class));
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendEmail_shouldUseSmtpInHybrid_whenUserEmailsDisabled() throws Exception {
        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("hybrid");
        when(env.getProperty("mail.service.user-emails.enabled", "false")).thenReturn("false");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendEmail("to@test.com", "Subject", "Body", "from@test.com", true);

        verify(javaMailSender, times(1)).send(mimeMessage);
        verify(tokenStore, never()).getToken(anyString());
        verifyNoInteractions(googleMailService);
    }

    @Test
    void sendEmail_shouldUseConfiguredSystemProvider_whenNotUserInitiated() throws Exception {
        GoogleOAuthTokenStore.GoogleOAuthToken token = mock(GoogleOAuthTokenStore.GoogleOAuthToken.class);

        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("hybrid");
        when(env.getProperty("mail.service.system-emails.provider", "smtp")).thenReturn("gmail-api");
        when(tokenStore.getToken("from@test.com")).thenReturn(Optional.of(token));

        emailRoutingService.sendEmail("to@test.com", "Subject", "Body", "from@test.com", false);

        verify(googleMailService, times(1)).sendEmailOnBehalf(eq("from@test.com"), any(GoogleMailSendRequest.class));
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    void sendBulkEmail_shouldUseGmailApi_whenEligibleAndTokenExists() throws Exception {
        GoogleOAuthTokenStore.GoogleOAuthToken token = mock(GoogleOAuthTokenStore.GoogleOAuthToken.class);

        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("gmail-api");
        when(tokenStore.getToken("from@test.com")).thenReturn(Optional.of(token));

        emailRoutingService.sendBulkEmail(
                "to@test.com",
                List.of("cc1@test.com"),
                "Subject",
                "Body",
                List.of("bcc1@test.com"),
                "from@test.com",
                true
        );

        ArgumentCaptor<GoogleMailSendRequest> captor = ArgumentCaptor.forClass(GoogleMailSendRequest.class);
        verify(googleMailService, times(1)).sendEmailOnBehalf(eq("from@test.com"), captor.capture());

        GoogleMailSendRequest request = captor.getValue();
        assertEquals("to@test.com", request.getTo());
        assertEquals(List.of("cc1@test.com"), request.getCc());
        assertEquals(List.of("bcc1@test.com"), request.getBcc());
    }

    @Test
    void sendBulkEmail_shouldFallbackToSmtp_whenGmailApiFails() throws Exception {
        GoogleOAuthTokenStore.GoogleOAuthToken token = mock(GoogleOAuthTokenStore.GoogleOAuthToken.class);

        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("gmail-api");
        when(tokenStore.getToken("from@test.com")).thenReturn(Optional.of(token));
        doThrow(new RuntimeException("gmail fail"))
                .when(googleMailService).sendEmailOnBehalf(eq("from@test.com"), any(GoogleMailSendRequest.class));
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendBulkEmail(
                "to@test.com",
                List.of("cc1@test.com"),
                "Subject",
                "Body",
                List.of("bcc1@test.com"),
                "from@test.com",
                true
        );

        verify(javaMailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendBulkEmail_shouldUseSmtp_whenProviderIsSmtp() throws Exception {
        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("smtp");
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendBulkEmail(
                "to@test.com",
                List.of("cc1@test.com"),
                "Subject",
                "Body",
                List.of("bcc1@test.com"),
                "from@test.com",
                true
        );

        verify(javaMailSender, times(1)).send(mimeMessage);
        verifyNoInteractions(googleMailService);
    }

    @Test
    void sendDetailedEmail_shouldAlwaysUseSmtp() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendDetailedEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                List.of("spotter@test.com"),
                "Body",
                "Subject",
                "from@test.com",
                true
        );

        verify(javaMailSender, times(1)).send(mimeMessage);
        verifyNoInteractions(googleMailService);
        verify(tokenStore, never()).getToken(anyString());
    }

    @Test
    void sendGenericEmail_shouldAlwaysUseSmtp() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendGenericEmail(
                List.of("cc@test.com"),
                "to@test.com",
                "Subject",
                "Body",
                "from@test.com",
                true
        );

        verify(javaMailSender, times(1)).send(mimeMessage);
        verifyNoInteractions(googleMailService);
        verify(tokenStore, never()).getToken(anyString());
    }

    @Test
    void sendBulkEmail_overloadShouldDefaultToUserInitiatedTrue() throws Exception {
        GoogleOAuthTokenStore.GoogleOAuthToken token = mock(GoogleOAuthTokenStore.GoogleOAuthToken.class);

        when(env.getProperty("mail.service.provider", "smtp")).thenReturn("gmail-api");
        when(tokenStore.getToken("from@test.com")).thenReturn(Optional.of(token));

        emailRoutingService.sendBulkEmail(
                "to@test.com",
                List.of("cc@test.com"),
                "Subject",
                "Body",
                List.of("bcc@test.com"),
                "from@test.com"
        );

        verify(googleMailService, times(1)).sendEmailOnBehalf(eq("from@test.com"), any(GoogleMailSendRequest.class));
    }

    @Test
    void sendDetailedEmail_overloadShouldDefaultToSystemEmail() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendDetailedEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                List.of("spotter@test.com"),
                "Body",
                "Subject",
                "from@test.com"
        );

        verify(javaMailSender, times(1)).send(mimeMessage);
    }

    @Test
    void sendGenericEmail_overloadShouldDefaultToSystemEmail() throws Exception {
        when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);

        emailRoutingService.sendGenericEmail(
                List.of("cc@test.com"),
                "to@test.com",
                "Subject",
                "Body",
                "from@test.com"
        );

        verify(javaMailSender, times(1)).send(mimeMessage);
    }
}