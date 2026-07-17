package com.reps.demogcloud.security.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.data.PasswordResetTokenRepository;
import com.reps.demogcloud.models.ResetPasswordRequest;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.ForgotPasswordRequest;
import com.reps.demogcloud.security.models.PasswordResetToken;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import com.reps.demogcloud.security.models.contactus.ContactUsResponse;
import com.reps.demogcloud.security.services.CustomUserDetailsService;
import com.reps.demogcloud.security.services.UserAccountService;
import com.reps.demogcloud.security.services.RefreshTokenService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.security.utils.TokenStatus;
import com.reps.demogcloud.services.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthControllers.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllersTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private EmailService emailService;

    @MockitoBean
    private UserAccountService userAccountService;

    @MockitoBean
    private CustomUserDetailsService customUserDetailsService;

    @MockitoBean
    private PasswordEncoder passwordEncoder;

    @MockitoBean
    private JwtUtils jwtUtils;

    @MockitoBean
    private UserRepository userRepository;

    @MockitoBean
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @MockitoBean
    private AuthenticationManager authenticationManager;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void logout_shouldReturnOk_whenAuthenticatedAndBearerTokenPresent() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("user", "pass", List.of());
        authentication.setDetails("details");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockMvc.perform(post("/v1/logout")
                        .header("Authorization", "Bearer abc123"))
                .andExpect(status().isOk())
                .andExpect(content().string("Logout successful"));

        verify(refreshTokenService).revokeAllForUser("user");
    }

    @Test
    void logout_shouldReturnBadRequest_whenAuthenticatedButNoBearerHeader() throws Exception {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken("user", "pass", List.of());
        authentication.setDetails("details");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        mockMvc.perform(post("/v1/logout"))
                .andExpect(status().isOk())
                .andExpect(content().string("Logout successful"));
    }

    @Test
    void logout_shouldReturnBadRequest_whenNoActiveSession() throws Exception {
        SecurityContextHolder.clearContext();

        mockMvc.perform(post("/v1/logout"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("No authenticated session found"));
    }

    @Test
    void testingToken_shouldReturnExpectedValue() throws Exception {
        mockMvc.perform(get("/test"))
                .andExpect(status().isOk())
                .andExpect(content().string("I WORKS"));
    }

    @Test
    void registerUser_shouldRegisterSuccessfully() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername("teacher@test.com");
        request.setPassword("plainPass");
        request.setFirstName("Chris");
        request.setLastName("Coach");
        request.setSchool("Test School");

        when(passwordEncoder.encode("plainPass")).thenReturn("encodedPass");

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("Successfully Registered teacher@test.com")))
                .andExpect(jsonPath("$.userModel", nullValue()));

        ArgumentCaptor<UserModel> captor = ArgumentCaptor.forClass(UserModel.class);
        verify(userRepository).save(captor.capture());

        UserModel savedUser = captor.getValue();
        assertEquals("teacher@test.com", savedUser.getUsername());
        assertEquals("Chris", savedUser.getFirstName());
        assertEquals("Coach", savedUser.getLastName());
        assertEquals("Test School", savedUser.getSchool());
        assertEquals("encodedPass", savedUser.getPassword());
    }

    @Test
    void registerUser_shouldReturnErrorResponse_whenSaveFails() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername("teacher@test.com");
        request.setPassword("plainPass");
        request.setFirstName("Chris");
        request.setLastName("Coach");
        request.setSchool("Test School");

        when(passwordEncoder.encode("plainPass")).thenReturn("encodedPass");
        when(userRepository.save(any(UserModel.class))).thenThrow(new RuntimeException("save failed"));

        mockMvc.perform(post("/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("Error During Registration of user: teacher@test.com")))
                .andExpect(jsonPath("$.userModel", nullValue()));
    }

    @Test
    void authenticateUser_shouldReturnTokenAndUser_whenSuccessful() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername("Teacher@Test.com");
        request.setPassword("secret");

        UserDetails userDetails = User.withUsername("teacher@test.com")
                .password("encoded")
                .authorities(List.of())
                .build();

        UserModel userModel = new UserModel();
        userModel.setUsername("teacher@test.com");
        userModel.setFirstName("Chris");
        userModel.setEnabled(true);

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(new UsernamePasswordAuthenticationToken("teacher@test.com", "secret"));
        when(customUserDetailsService.loadUserByUsername("teacher@test.com")).thenReturn(userDetails);
        when(jwtUtils.generateToken(userDetails)).thenReturn("jwt-token");
        when(userAccountService.loadUserModelByUsername("teacher@test.com")).thenReturn(userModel);

        mockMvc.perform(post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("jwt-token")))
                .andExpect(jsonPath("$.userModel.username", is("teacher@test.com")))
                .andExpect(jsonPath("$.userModel.firstName", is("Chris")))
                .andExpect(jsonPath("$.userModel.enabled", is(true)));
    }

    @Test
    void authenticateUser_shouldReturnErrorResponse_whenAuthenticationFails() throws Exception {
        AuthenticationRequest request = new AuthenticationRequest();
        request.setUsername("Teacher@Test.com");
        request.setPassword("badpass");

        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new RuntimeException("bad credentials"));

        mockMvc.perform(post("/auth")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response", is("Error Authenticating user: teacher@test.com")))
                .andExpect(jsonPath("$.userModel", nullValue()));
    }

    @Test
    void createNewUsers_shouldReturnCreatedUsers() throws Exception {
        UserModel user1 = new UserModel();
        user1.setUsername("user1@test.com");

        UserModel user2 = new UserModel();
        user2.setUsername("user2@test.com");

        when(userAccountService.createUsersForSchool("TestSchool")).thenReturn(List.of(user1, user2));

        mockMvc.perform(post("/users/create/TestSchool"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].username", is("user1@test.com")))
                .andExpect(jsonPath("$[1].username", is("user2@test.com")));
    }

    @Test
    void getTokenStatus_shouldReturnStatus_whenValidToken() throws Exception {
        TokenStatus tokenStatus = new TokenStatus(false, 120000L);
        when(jwtUtils.getTokenStatus("valid-token")).thenReturn(tokenStatus);

        mockMvc.perform(get("/v1/token-status")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isExpired", is(false)))
                .andExpect(jsonPath("$.timeUntilExpiration", is(120000)));
    }

    @Test
    void getTokenStatus_shouldReturnBadRequest_whenHeaderMissing() throws Exception {
        mockMvc.perform(get("/v1/token-status"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Token not found in Authorization header"));
    }

    @Test
    void getTokenStatus_shouldReturnBadRequest_whenTokenParsingFails() throws Exception {
        when(jwtUtils.getTokenStatus("bad-token")).thenThrow(new RuntimeException("bad token"));

        mockMvc.perform(get("/v1/token-status")
                        .header("Authorization", "Bearer bad-token"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid token or error parsing token"));
    }

    @Test
    void renewToken_shouldReturnNewToken_whenSuccessful() throws Exception {
        when(jwtUtils.renewTokenWithBlacklist("old-token")).thenReturn("new-token");

        mockMvc.perform(post("/v1/renew-token")
                        .header("Authorization", "Bearer old-token"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.newToken", is("new-token")));
    }

    @Test
    void renewToken_shouldReturnBadRequest_whenRenewFails() throws Exception {
        when(jwtUtils.renewTokenWithBlacklist("old-token")).thenThrow(new RuntimeException("renew failed"));

        mockMvc.perform(post("/v1/renew-token")
                        .header("Authorization", "Bearer old-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Token renewal failed")));
    }

    @Test
    void renewToken_shouldReturnBadRequest_whenHeaderMissing() throws Exception {
        mockMvc.perform(post("/v1/renew-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error", is("Token not found in Authorization header")));
    }

    @Test
    void forgotPassword_shouldReturnBadRequest_whenUserNotFound() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("missing@test.com");

        when(userRepository.findByUsername("missing@test.com")).thenReturn(null);

        mockMvc.perform(post("/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("User not found for email missing@test.com"));

        verify(passwordResetTokenRepository, never()).save(any());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void forgotPassword_shouldSaveTokenAndSendEmail_whenUserExists() throws Exception {
        ForgotPasswordRequest request = new ForgotPasswordRequest();
        request.setEmail("teacher@test.com");

        UserModel user = new UserModel();
        user.setUsername("teacher@test.com");

        when(userRepository.findByUsername("teacher@test.com")).thenReturn(user);

        mockMvc.perform(post("/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset link sent to teacher@test.com"));

        ArgumentCaptor<PasswordResetToken> captor = ArgumentCaptor.forClass(PasswordResetToken.class);
        verify(passwordResetTokenRepository).save(captor.capture());

        PasswordResetToken savedToken = captor.getValue();
        assertNotNull(savedToken);
        assertNotNull(savedToken.getToken());
        assertEquals(user, savedToken.getUser());

        verify(emailService).sendEmail(
                eq("teacher@test.com"),
                eq("Reset Your Password"),
                contains("https://repsdiscipline.vercel.app/reset-password/"),
                eq("en")
        );
    }

    @Test
    void resetPassword_shouldReturnBadRequest_whenTokenNotFound() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("bad-token");
        request.setNewPassword("newPass123");

        when(passwordResetTokenRepository.findByToken("bad-token")).thenReturn(null);

        mockMvc.perform(post("/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid or expired token"));
    }

    @Test
    void resetPassword_shouldReturnBadRequest_whenTokenExpired() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("expired-token");
        request.setNewPassword("newPass123");

        PasswordResetToken passwordResetToken = mock(PasswordResetToken.class);
        when(passwordResetTokenRepository.findByToken("expired-token")).thenReturn(passwordResetToken);
        when(passwordResetToken.isExpired()).thenReturn(true);

        mockMvc.perform(post("/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Invalid or expired token"));
    }

    @Test
    void resetPassword_shouldUpdatePasswordAndDeleteToken_whenTokenValid() throws Exception {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setToken("good-token");
        request.setNewPassword("newPass123");

        UserModel user = new UserModel();
        user.setUsername("teacher@test.com");

        PasswordResetToken passwordResetToken = mock(PasswordResetToken.class);
        when(passwordResetTokenRepository.findByToken("good-token")).thenReturn(passwordResetToken);
        when(passwordResetToken.isExpired()).thenReturn(false);
        when(passwordResetToken.getUser()).thenReturn(user);
        when(passwordEncoder.encode("newPass123")).thenReturn("encoded-new-pass");

        mockMvc.perform(post("/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("Password reset successfully"));

        assertEquals("encoded-new-pass", user.getPassword());
        verify(userRepository).save(user);
        verify(passwordResetTokenRepository).delete(passwordResetToken);
    }

    @Test
    void contactUs_shouldReturnResponse() throws Exception {
        ContactUsRequest request = new ContactUsRequest();
        request.setEmail("test@test.com");
        request.setSubject("Help");
        request.setMessage("Need help");
        request.setPreferredLanguage("en");

        ContactUsResponse response = new ContactUsResponse();
        response.setRequest(request);
        response.setError(null);

        when(userAccountService.contactUs(any(ContactUsRequest.class))).thenReturn(response);

        mockMvc.perform(post("/contact-us")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.request.email", is("test@test.com")))
                .andExpect(jsonPath("$.request.subject", is("Help")))
                .andExpect(jsonPath("$.request.message", is("Need help")))
                .andExpect(jsonPath("$.request.preferredLanguage", is("en")))
                .andExpect(jsonPath("$.error", nullValue()));
    }
}
