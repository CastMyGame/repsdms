package com.reps.demogcloud.security.config;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.RedirectStrategy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginFailureHandlerTest {

    @Mock
    private Environment env;

    @Mock
    private RedirectStrategy redirectStrategy;

    private OAuth2LoginFailureHandler handler;

    @BeforeEach
    void setUp() {
        handler = new OAuth2LoginFailureHandler(env);
        handler.setRedirectStrategy(redirectStrategy);
    }

    @Test
    void onAuthenticationFailure_shouldRedirectWithEncodedError_whenRedirectUrlConfigured() throws Exception {
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("http://localhost:3000/login");

        AuthenticationException exception = new AuthenticationException("No account found for user@test.com") {
        };

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, exception);

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), redirectCaptor.capture());

        String redirectUrl = redirectCaptor.getValue();
        assertTrue(redirectUrl.startsWith("http://localhost:3000/login?error="));
        assertTrue(redirectUrl.contains("No+account+found+for+user%40test.com"));
        assertTrue(redirectUrl.endsWith("&errorType=UNAUTHORIZED"));
    }

    @Test
    void onAuthenticationFailure_shouldReturnJson_whenRedirectUrlEmpty() throws Exception {
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("");

        AuthenticationException exception = new AuthenticationException("Custom auth failure") {
        };

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertEquals("{\"error\":\"UNAUTHORIZED\",\"message\":\"Custom auth failure\"}", response.getContentAsString());
    }

    @Test
    void onAuthenticationFailure_shouldReturnJson_whenRedirectUrlNull() throws Exception {
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn(null);

        AuthenticationException exception = new AuthenticationException("Another failure") {
        };

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals("{\"error\":\"UNAUTHORIZED\",\"message\":\"Another failure\"}", response.getContentAsString());
    }

    @Test
    void onAuthenticationFailure_shouldUseDefaultMessage_whenExceptionMessageIsNull() throws Exception {
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("");

        AuthenticationException exception = new AuthenticationException(null) {
        };

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals(
                "{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication failed. You are not authorized to use this application.\"}",
                response.getContentAsString()
        );
    }

    @Test
    void onAuthenticationFailure_shouldUseDefaultMessage_whenExceptionMessageIsBlank() throws Exception {
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("");

        AuthenticationException exception = new AuthenticationException("   ") {
        };

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals(
                "{\"error\":\"UNAUTHORIZED\",\"message\":\"Authentication failed. You are not authorized to use this application.\"}",
                response.getContentAsString()
        );
    }

    @Test
    void onAuthenticationFailure_shouldEscapeJsonSpecialCharacters() throws Exception {
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("");

        AuthenticationException exception = new AuthenticationException("Bad \"quote\" slash \\ newline \n tab \t") {
        };

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(new MockHttpServletRequest(), response, exception);

        assertEquals(HttpServletResponse.SC_UNAUTHORIZED, response.getStatus());
        assertEquals(
                "{\"error\":\"UNAUTHORIZED\",\"message\":\"Bad \\\"quote\\\" slash \\\\ newline \\n tab \\t\"}",
                response.getContentAsString()
        );
    }
}