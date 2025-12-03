package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.mail.Message;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Properties;
import java.util.List; // Import List

@Service
@RequiredArgsConstructor
@Slf4j
public class GoogleMailService {

    private static final String GMAIL_SEND_ENDPOINT = "https://gmail.googleapis.com/gmail/v1/users/me/messages/send";

    private final GoogleOAuthTokenStore tokenStore;
    private final RestTemplate restTemplate = new RestTemplate();

    public void sendEmailOnBehalf(String username, GoogleMailSendRequest request) throws Exception {
        GoogleOAuthTokenStore.GoogleOAuthToken token = tokenStore.getToken(username)
                .orElseThrow(() -> new IllegalStateException("No Google OAuth token stored for user. Ask them to login with Google again."));

        if (token.getAccessTokenExpiresAt() != null && token.getAccessTokenExpiresAt().isBefore(Instant.now())) {
            throw new IllegalStateException("Stored Google access token is expired. Refresh flow not implemented in PoC.");
        }

        // --- No changes needed here, you correctly pass the cc and bcc lists
        String rawMessage = buildMimeMessage(
                request.getFrom() != null ? request.getFrom() : username,
                request.getTo() != null ? request.getTo() : username,
                request.getSubject(),
                request.getBody(),
                request.getBcc(),
                request.getCc()
        );

        HttpHeaders headers = new HttpHeaders();
        // ... (omitted rest of sendEmailOnBehalf)
        headers.setBearerAuth(token.getAccessToken());
        headers.setContentType(MediaType.APPLICATION_JSON);

        String payload = "{\"raw\":\"" + rawMessage + "\"}";

        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    GMAIL_SEND_ENDPOINT,
                    new HttpEntity<>(payload, headers),
                    String.class
            );
            log.info("Gmail API send response: {}", response.getStatusCode());
        } catch (HttpStatusCodeException ex) {
            log.error("Failed to send email via Gmail API: {}", ex.getResponseBodyAsString(), ex);
            throw ex;
        }
    }

    // --- FIXES APPLIED IN THIS METHOD ---
    private String buildMimeMessage(
            String from,
            String to,
            String subject,
            String body,
            List<String> bcc, // Added Bcc list parameter
            List<String> cc   // Added Cc list parameter
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
        mimeMessage.setText(body, StandardCharsets.UTF_8.name());

        // Finalize and encode the message
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        mimeMessage.writeTo(buffer);

        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(buffer.toByteArray());
    }
}