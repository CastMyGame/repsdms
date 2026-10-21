package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.RestClientException;
import org.springframework.beans.factory.annotation.Autowired;
import jakarta.mail.MessagingException;

import jakarta.mail.Message;
import jakarta.mail.Session;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Properties;
import java.util.List;

@Service
@Slf4j
public class GoogleMailService {

    private static final String GMAIL_SEND_ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final GoogleOAuthTokenStore tokenStore;
    private final RestTemplate restTemplate;

    @Autowired
    public GoogleMailService(GoogleOAuthTokenStore tokenStore) {
        this(tokenStore, mailHttp());
    }

    private static RestTemplate mailHttp() {
        var factory=new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000); factory.setReadTimeout(15000);
        return new RestTemplate(factory);
    }

    // Reuses the existing HTTP implementation; permits fully intercepted HTTP tests.
    public GoogleMailService(GoogleOAuthTokenStore tokenStore, RestTemplate restTemplate) {
        this.tokenStore = tokenStore;
        this.restTemplate = restTemplate;
    }

    public void sendEmailOnBehalf(String username, GoogleMailSendRequest request) throws Exception {
        sendWithReceipt(username, request);
    }

    public java.util.Map<String,String> sendWithReceipt(String username, GoogleMailSendRequest request) throws Exception {
        if (username == null || username.isBlank() || request == null
                || (request.getFrom() != null && !username.equalsIgnoreCase(request.getFrom()))) {
            throw new MessagingException("GMAIL_SENDER_MISMATCH: sending account must match the authorized identity");
        }
        GoogleOAuthTokenStore.GoogleOAuthToken token = tokenStore.getToken(username)
                .orElseThrow(() -> new MessagingException("GMAIL_RECONNECT_REQUIRED: no sending authorization"));

        if (token.getAccessTokenExpiresAt() == null || !token.getAccessTokenExpiresAt().isAfter(java.time.Instant.now())
                || token.getScopes() == null || !token.getScopes().contains(GoogleOAuthTokenStore.GMAIL_SCOPE)) {
            throw new MessagingException("GMAIL_RECONNECT_REQUIRED: sending scope or valid access token is missing");
        }

        String rawMessage = buildMimeMessage(
                username,
                request.getTo() != null ? request.getTo() : username,
                request.getSubject(),
                request.getBody(),
                request.getBcc(),
                request.getCc(), request
        );

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        var payloadBody = new java.util.LinkedHashMap<String,String>();
        payloadBody.put("raw",rawMessage);
        if (request.getThreadId() != null) payloadBody.put("threadId",request.getThreadId());
        String payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(payloadBody);

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    GMAIL_SEND_ENDPOINT,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            log.info("Gmail API send response: {}", response.getStatusCode());
            String id="", thread="";
            try {
                var receipt=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getBody());
                if(receipt!=null) { id=receipt.path("id").asText(""); thread=receipt.path("threadId").asText(""); }
            } catch(Exception ignored) { /* HTTP success remains accepted even if optional metadata is absent. */ }
            return java.util.Map.of("provider","GMAIL","providerMessageId",id,"gmailThreadId",thread);
        } catch (HttpStatusCodeException ex) {
            log.warn("Gmail provider returned HTTP {}", ex.getStatusCode().value());
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403) {
                tokenStore.providerAuthorizationRejected(username, token.getAccessToken());
                throw new MessagingException("GMAIL_RECONNECT_REQUIRED: provider denied sending authorization");
            }
            if (ex.getStatusCode().is4xxClientError()) {
                throw new MessagingException("GMAIL_SEND_REJECTED: provider did not accept the request");
            }
            throw new MessagingException("GMAIL_SEND_UNCERTAIN: do not resend automatically");
        } catch (RestClientException ex) {
            throw new MessagingException("GMAIL_SEND_UNCERTAIN: do not resend automatically");
        }
    }

    private String buildMimeMessage(
            String from,
            String to,
            String subject,
            String body,
            List<String> bcc, // Added Bcc list parameter
            List<String> cc,
            GoogleMailSendRequest request
    ) throws Exception {
        Properties props = new Properties();
        Session session = Session.getInstance(props, null);
        MimeMessage mimeMessage = new MimeMessage(session);

        // Set From and To
        mimeMessage.setFrom(new InternetAddress(from));
        mimeMessage.setRecipients(Message.RecipientType.TO, InternetAddress.parse(to));

        // Set CC (Carbon Copy) Recipients
        if (cc != null && !cc.isEmpty()) {
            String ccString = String.join(",", cc);
            mimeMessage.setRecipients(Message.RecipientType.CC, InternetAddress.parse(ccString));
        }

        // Set BCC (Blind Carbon Copy) Recipients
        if (bcc != null && !bcc.isEmpty()) {
            String bccString = String.join(",", bcc);
            mimeMessage.setRecipients(Message.RecipientType.BCC, InternetAddress.parse(bccString));
        }

        mimeMessage.setSubject(subject, StandardCharsets.UTF_8.name());
        mimeMessage.setText(body, StandardCharsets.UTF_8.name(), "html");
        mimeMessage.saveChanges();
        if(request.getRfcMessageId()!=null) mimeMessage.setHeader("Message-ID",request.getRfcMessageId());
        if(request.getInReplyTo()!=null) mimeMessage.setHeader("In-Reply-To",request.getInReplyTo());
        if(request.getReferences()!=null) mimeMessage.setHeader("References",request.getReferences());

        // Finalize and encode the message
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(buffer.toByteArray());
    }
}
