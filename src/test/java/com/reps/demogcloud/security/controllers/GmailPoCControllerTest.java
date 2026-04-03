package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import com.reps.demogcloud.security.services.GoogleMailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GmailPoCControllerTest {

    @Mock
    private GoogleMailService googleMailService;

    @Mock
    private Authentication authentication;

    private GmailPoCController controller;

    @BeforeEach
    void setUp() {
        controller = new GmailPoCController(googleMailService);
    }

    @Test
    void sendTestEmail_shouldReturnUnauthorized_whenAuthenticationIsNull() throws Exception {
        GoogleMailSendRequest request = new GoogleMailSendRequest();

        ResponseEntity<String> response = controller.sendTestEmail(request, null);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication required", response.getBody());
        verify(googleMailService, never()).sendEmailOnBehalf(anyString(), any(GoogleMailSendRequest.class));
    }

    @Test
    void sendTestEmail_shouldReturnUnauthorized_whenAuthenticationNameIsNull() throws Exception {
        GoogleMailSendRequest request = new GoogleMailSendRequest();
        when(authentication.getName()).thenReturn(null);

        ResponseEntity<String> response = controller.sendTestEmail(request, authentication);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication required", response.getBody());
        verify(googleMailService, never()).sendEmailOnBehalf(anyString(), any(GoogleMailSendRequest.class));
    }

    @Test
    void sendTestEmail_shouldReturnUnauthorized_whenAuthenticationNameIsBlank() throws Exception {
        GoogleMailSendRequest request = new GoogleMailSendRequest();
        when(authentication.getName()).thenReturn("   ");

        ResponseEntity<String> response = controller.sendTestEmail(request, authentication);

        assertNotNull(response);
        assertEquals(HttpStatus.UNAUTHORIZED, response.getStatusCode());
        assertEquals("Authentication required", response.getBody());
        verify(googleMailService, never()).sendEmailOnBehalf(anyString(), any(GoogleMailSendRequest.class));
    }

    @Test
    void sendTestEmail_shouldSendEmailAndReturnOk_whenAuthenticationIsValid() throws Exception {
        GoogleMailSendRequest request = new GoogleMailSendRequest();
        when(authentication.getName()).thenReturn("teacher@test.com");

        ResponseEntity<String> response = controller.sendTestEmail(request, authentication);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals("Email sent via Gmail API on behalf of teacher@test.com", response.getBody());
        verify(googleMailService).sendEmailOnBehalf("teacher@test.com", request);
    }
}