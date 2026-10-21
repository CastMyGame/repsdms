package com.reps.demogcloud.security.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

/** No Spring application, credentials, database, or sockets: every HTTP request is intercepted. */
class GoogleMailServiceTest {
    private final GoogleOAuthTokenStore store = org.mockito.Mockito.mock(GoogleOAuthTokenStore.class);
    private MockRestServiceServer server;
    private GoogleMailService service;

    @BeforeEach
    void setup() {
        RestTemplate http = new RestTemplate();
        server = MockRestServiceServer.bindTo(http).build();
        service = new GoogleMailService(store, http);
        org.mockito.Mockito.when(store.getToken("staff@example.test")).thenReturn(java.util.Optional.of(new GoogleOAuthTokenStore.GoogleOAuthToken(
                "synthetic-access", Instant.now().plusSeconds(3600), "synthetic-refresh",
                Set.of("https://www.googleapis.com/auth/gmail.send"))));
    }

    private GoogleMailSendRequest message() {
        GoogleMailSendRequest request = new GoogleMailSendRequest();
        request.setFrom("staff@example.test");
        request.setTo("parent@example.test");
        request.setCc(List.of("teacher@example.test"));
        request.setBcc(List.of("spotter@example.test"));
        request.setSubject("Synthetic closure");
        request.setBody("<p>Closed — synthetic data only.</p>");
        return request;
    }

    @Test
    void sendsHtmlMimeWithVerifiedFromAndParticipants() throws Exception {
        server.expect(requestTo("https://gmail.googleapis.com/gmail/v1/users/me/messages/send"))
                .andExpect(header("Authorization", "Bearer synthetic-access"))
                .andExpect(request -> {
                    String json = ((org.springframework.mock.http.client.MockClientHttpRequest) request)
                            .getBodyAsString(StandardCharsets.UTF_8);
                    String raw = new ObjectMapper().readTree(json).get("raw").asText();
                    try {
                        MimeMessage mime = new MimeMessage(Session.getInstance(new Properties()),
                                new ByteArrayInputStream(Base64.getUrlDecoder().decode(raw)));
                        assertTrue(mime.isMimeType("text/html"));
                        assertEquals("staff@example.test", mime.getFrom()[0].toString());
                        assertEquals("teacher@example.test", mime.getHeader("Cc", null));
                        assertEquals("spotter@example.test", mime.getHeader("Bcc", null));
                        assertEquals("Synthetic closure", mime.getSubject());
                        assertTrue(mime.getContent().toString().contains("synthetic data"));
                    } catch (MessagingException exception) {
                        throw new AssertionError(exception);
                    }
                }).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        service.sendEmailOnBehalf("staff@example.test", message());
        server.verify();
    }

    @Test
    void rejectsSpoofedFromWithoutAnyHttp() {
        GoogleMailSendRequest request = message();
        request.setFrom("other@example.test");
        assertThrows(MessagingException.class, () -> service.sendEmailOnBehalf("staff@example.test", request));
        server.verify();
    }

    @Test
    void persistsProviderIdsAndSendsRecordedThreadHeaders() throws Exception {
        GoogleMailSendRequest request=message(); request.setRfcMessageId("<case-action@reps.test>");
        request.setInReplyTo("<parent-action@reps.test>"); request.setReferences("<parent-action@reps.test>"); request.setThreadId("same-account-thread");
        server.expect(anything()).andExpect(httpRequest -> {
            String body=((org.springframework.mock.http.client.MockClientHttpRequest)httpRequest).getBodyAsString(StandardCharsets.UTF_8);
            var payload=new ObjectMapper().readTree(body); assertEquals("same-account-thread",payload.get("threadId").asText());
            try {
                MimeMessage mime=new MimeMessage(Session.getInstance(new Properties()),new ByteArrayInputStream(Base64.getUrlDecoder().decode(payload.get("raw").asText())));
                assertEquals("<case-action@reps.test>",mime.getMessageID()); assertEquals("<parent-action@reps.test>",mime.getHeader("In-Reply-To",null));
            } catch(MessagingException failure) { throw new AssertionError(failure); }
        }).andRespond(withSuccess("{\"id\":\"provider-id\",\"threadId\":\"provider-thread\"}",MediaType.APPLICATION_JSON));
        var receipt=service.sendWithReceipt("staff@example.test",request);
        assertEquals("provider-id",receipt.get("providerMessageId")); assertEquals("provider-thread",receipt.get("gmailThreadId")); server.verify();
    }

    @Test
    void expiredOrMissingScopeCannotReachProvider() {
        org.mockito.Mockito.when(store.getToken("staff@example.test")).thenReturn(java.util.Optional.of(new GoogleOAuthTokenStore.GoogleOAuthToken(
                "synthetic-access", Instant.now().minusSeconds(1), null,
                Set.of("https://www.googleapis.com/auth/gmail.send"))));
        assertThrows(MessagingException.class, () -> service.sendEmailOnBehalf("staff@example.test", message()));
        org.mockito.Mockito.when(store.getToken("staff@example.test")).thenReturn(java.util.Optional.of(new GoogleOAuthTokenStore.GoogleOAuthToken(
                "synthetic-access", Instant.now().plusSeconds(3600), null, Set.of("openid"))));
        assertThrows(MessagingException.class, () -> service.sendEmailOnBehalf("staff@example.test", message()));
        server.verify();
    }

    @Test
    void providerDenialDoesNotExposeSensitiveResponse() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN).body("sensitive-provider-body"));
        MessagingException failure = assertThrows(MessagingException.class,
                () -> service.sendEmailOnBehalf("staff@example.test", message()));
        assertEquals("GMAIL_RECONNECT_REQUIRED: provider denied sending authorization", failure.getMessage());
        assertNull(failure.getCause());
        server.verify();
    }

    @Test
    void timeoutIsUncertainWithoutRetry() {
        server.expect(anything()).andRespond(withException(new IOException("synthetic timeout")));
        MessagingException failure = assertThrows(MessagingException.class,
                () -> service.sendEmailOnBehalf("staff@example.test", message()));
        assertTrue(failure.getMessage().startsWith("GMAIL_SEND_UNCERTAIN"));
        server.verify();
    }

    @Test
    void connectionStateAndTokenStringDoNotInventHistoryOrExposeCredentials() {
        String representation = store.getToken("staff@example.test").orElseThrow().toString();
        assertFalse(representation.contains("synthetic-access"));
        assertFalse(representation.contains("synthetic-refresh"));
    }
}
