package com.reps.demogcloud.security.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final String DEFAULT_ERROR_MESSAGE =
            "Authentication failed. You are not authorized to use this application.";
    private static final String ERROR_TYPE = "UNAUTHORIZED";

    private final Environment env;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException {

        String errorMessage = resolveErrorMessage(exception);
        String redirectUrl = env.getProperty("auth.sso.google.redirect-url", "");

        if (redirectUrl != null && !redirectUrl.isBlank()) {
            String encodedError = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8);
            String redirectWithError = redirectUrl
                    + "?error=" + encodedError
                    + "&errorType=" + ERROR_TYPE;

            getRedirectStrategy().sendRedirect(request, response, redirectWithError);
            return;
        }

        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");

        String jsonResponse = "{\"error\":\"" + ERROR_TYPE + "\",\"message\":\""
                + escapeJson(errorMessage) + "\"}";

        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }

    private String resolveErrorMessage(AuthenticationException exception) {
        if (exception == null || exception.getMessage() == null || exception.getMessage().isBlank()) {
            return DEFAULT_ERROR_MESSAGE;
        }
        return exception.getMessage();
    }

    private String escapeJson(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n")
                .replace("\t", "\\t");
    }
}