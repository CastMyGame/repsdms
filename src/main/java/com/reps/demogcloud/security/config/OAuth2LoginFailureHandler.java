package com.reps.demogcloud.security.config;

import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final Environment env;

    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        
        // Get error message
        String errorMessage = exception.getMessage() != null ? exception.getMessage() : 
                "Authentication failed. You are not authorized to use this application.";
        
        // Get redirect URL from configuration (same as success handler)
        String redirectUrl = env.getProperty("auth.sso.google.redirect-url", "");
        
        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            // Redirect to frontend with error message as query parameter
            String encodedError = URLEncoder.encode(errorMessage, StandardCharsets.UTF_8.toString());
            String redirectWithError = redirectUrl + "?error=" + encodedError + "&errorType=UNAUTHORIZED";
            getRedirectStrategy().sendRedirect(request, response, redirectWithError);
        } else {
            // If no redirect URL configured, return JSON response
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            
            String jsonResponse = String.format("{\"error\": \"%s\", \"message\": \"%s\"}", 
                    "UNAUTHORIZED", errorMessage);
            
            response.getWriter().write(jsonResponse);
            response.getWriter().flush();
        }
    }
}

