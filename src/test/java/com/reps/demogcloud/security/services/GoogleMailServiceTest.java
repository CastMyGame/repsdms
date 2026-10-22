package com.reps.demogcloud.security.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
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
        assertEquals("GMAIL_SEND_REJECTED: FORBIDDEN", failure.getMessage());
        assertNull(failure.getCause());
        org.mockito.Mockito.verify(store, org.mockito.Mockito.never()).providerAuthorizationRejected(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        server.verify();
    }

    static java.util.stream.Stream<Arguments> providerFailures() {
        return java.util.stream.Stream.of(
                Arguments.of(401, "{}", "INVALID_AUTHORIZATION", true),
                Arguments.of(403, legacyError("insufficientPermissions"), "INSUFFICIENT_SCOPE", true),
                Arguments.of(403, modernError("ACCESS_TOKEN_SCOPE_INSUFFICIENT"), "INSUFFICIENT_SCOPE", true),
                Arguments.of(403, legacyError("accessNotConfigured"), "SERVICE_DISABLED", false),
                Arguments.of(403, modernError("SERVICE_DISABLED"), "SERVICE_DISABLED", false),
                Arguments.of(403, legacyError("domainPolicy"), "DOMAIN_POLICY", false),
                Arguments.of(403, legacyError("dailyLimitExceeded"), "QUOTA_EXCEEDED", false),
                Arguments.of(403, legacyError("userRateLimitExceeded"), "RATE_LIMITED", false),
                Arguments.of(403, legacyError("rateLimitExceeded"), "RATE_LIMITED", false),
                Arguments.of(429, "{}", "RATE_LIMITED", false),
                Arguments.of(403, "not-json", "FORBIDDEN", false),
                Arguments.of(403, legacyError("private-recipient@example.test"), "FORBIDDEN", false),
                Arguments.of(503, "{}", "PROVIDER_UNCERTAIN", false),
                Arguments.of(408, "{}", "PROVIDER_UNCERTAIN", false));
    }
    private static String legacyError(String reason) {
        return "{\"error\":{\"errors\":[{\"reason\":\"" + reason + "\"}],\"message\":\"synthetic-access private-recipient@example.test\"}}";
    }
    private static String modernError(String reason) {
        return "{\"error\":{\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"" + reason
                + "\",\"metadata\":{\"private\":\"synthetic-refresh private-recipient@example.test\"}}]}}";
    }
    @ParameterizedTest @MethodSource("providerFailures")
    void classifiesProviderFailuresWithoutInvalidatingUnrelatedGrantsOrExposingResponse(int status, String body, String reason, boolean reconnect) {
        var logger = (ch.qos.logback.classic.Logger) org.slf4j.LoggerFactory.getLogger(GoogleMailService.class);
        var appender = new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();
        appender.start(); logger.addAppender(appender);
        try {
            server.expect(anything()).andRespond(withStatus(HttpStatus.valueOf(status)).contentType(MediaType.APPLICATION_JSON).body(body));
            var failure = assertThrows(GoogleMailService.SendFailure.class,
                    () -> service.sendWithReceipt("staff@example.test", message()));
            assertEquals("GMAIL_HTTP_" + status + "_" + reason, failure.diagnostic());
            assertEquals(reconnect, failure.getMessage().startsWith("GMAIL_RECONNECT_REQUIRED"));
            assertNull(failure.getCause()); assertNull(failure.getNextException());
            org.mockito.Mockito.verify(store, org.mockito.Mockito.times(reconnect ? 1 : 0))
                    .providerAuthorizationRejected("staff@example.test", "synthetic-access");
            for (var entry : appender.list) {
                String logged = entry.getFormattedMessage();
                assertFalse(logged.contains("synthetic-access")); assertFalse(logged.contains("synthetic-refresh"));
                assertFalse(logged.contains("private-recipient@example.test")); assertNull(entry.getThrowableProxy());
            }
            assertTrue(appender.list.stream().anyMatch(entry -> entry.getFormattedMessage().contains("reason=" + reason)));
            server.verify();
        } finally { logger.detachAppender(appender); appender.stop(); }
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
