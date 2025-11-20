package com.reps.demogcloud.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.security.models.AuthenticationResponse;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.GoogleOAuthTokenStore;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final UserService userService;
    private final Environment env;
    private final OAuth2AuthorizedClientService authorizedClientService;
    private final GoogleOAuthTokenStore tokenStore;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String email = oidcUser.getEmail().toLowerCase();

        // Load user from database to get full user details
        UserModel userModel = userService.loadUserModelByUsername(email);
        UserDetails userDetails = userService.loadUserByUsername(email);

        // Persist Google OAuth tokens for Gmail PoC
        storeGoogleTokens(authentication, userModel);

        // Generate JWT token
        String token = jwtUtils.generateToken(userDetails);

        // Check if we should redirect to frontend or return JSON
        String redirectUrl = env.getProperty("auth.sso.google.redirect-url", "");
        
        if (redirectUrl != null && !redirectUrl.isEmpty()) {
            // Extract user information for sessionStorage
            String userName = userModel.getUsername(); // email is the username
            String schoolName = userModel.getSchool() != null ? userModel.getSchool() : "";
            String userEmail = userModel.getUsername(); // username is the email
            String role = "";
            
            // Get the first role (or combine all roles)
            if (userModel.getRoles() != null && !userModel.getRoles().isEmpty()) {
                role = userModel.getRoles().iterator().next().getRole();
            }
            
            // Build redirect URL with individual fields for easy extraction
            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8.toString());
            String encodedUserName = URLEncoder.encode(userName, StandardCharsets.UTF_8.toString());
            String encodedSchoolName = URLEncoder.encode(schoolName, StandardCharsets.UTF_8.toString());
            String encodedEmail = URLEncoder.encode(userEmail, StandardCharsets.UTF_8.toString());
            String encodedRole = URLEncoder.encode(role, StandardCharsets.UTF_8.toString());
            String encodedUserModel = URLEncoder.encode(objectMapper.writeValueAsString(userModel), StandardCharsets.UTF_8.toString());
            
            // Include both individual fields (for easy sessionStorage) and full user object (for compatibility)
            String redirectWithToken = redirectUrl + 
                    "?token=" + encodedToken +
                    "&userName=" + encodedUserName +
                    "&schoolName=" + encodedSchoolName +
                    "&email=" + encodedEmail +
                    "&role=" + encodedRole +
                    "&user=" + encodedUserModel; // Full user object for compatibility
            
            getRedirectStrategy().sendRedirect(request, response, redirectWithToken);
        } else {
            // Return JSON response for API clients
            AuthenticationResponse authResponse = new AuthenticationResponse(token, userModel);
            
            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            
            response.getWriter().write(objectMapper.writeValueAsString(authResponse));
            response.getWriter().flush();
        }

        clearAuthenticationAttributes(request);
    }

    private void storeGoogleTokens(Authentication authentication, UserModel userModel) {
        try {
            OAuth2AuthorizedClient authorizedClient =
                    authorizedClientService.loadAuthorizedClient("google", authentication.getName());

            if (authorizedClient != null && authorizedClient.getAccessToken() != null) {
                tokenStore.storeToken(
                        userModel.getUsername(),
                        new GoogleOAuthTokenStore.GoogleOAuthToken(
                                authorizedClient.getAccessToken().getTokenValue(),
                                authorizedClient.getAccessToken().getExpiresAt(),
                                authorizedClient.getRefreshToken() != null
                                        ? authorizedClient.getRefreshToken().getTokenValue()
                                        : null
                        )
                );
            }
        } catch (Exception ex) {
            // Gmail PoC is best-effort; log and continue
        }
    }
}

