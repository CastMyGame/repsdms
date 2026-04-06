package com.reps.demogcloud.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.CustomUserDetailsService;
import com.reps.demogcloud.security.services.GoogleOAuthTokenStore;
import com.reps.demogcloud.security.services.UserAccountService;
import com.reps.demogcloud.security.utils.JwtUtils;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.env.Environment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.RedirectStrategy;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginSuccessHandlerTest {

    @Mock
    private JwtUtils jwtUtils;
    @Mock
    private UserAccountService userAccountService;
    @Mock
    private CustomUserDetailsService customUserDetailsService;
    @Mock
    private Environment env;
    @Mock
    private OAuth2AuthorizedClientService authorizedClientService;
    @Mock
    private GoogleOAuthTokenStore tokenStore;
    @Mock
    private StudentRepository studentRepository;
    @Mock
    private EmployeeRepository employeeRepository;
    @Mock
    private Authentication authentication;
    @Mock
    private RedirectStrategy redirectStrategy;

    private ObjectMapper objectMapper;
    private UserDetails userDetails;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        userDetails = User.withUsername("user@test.com")
                .password("password")
                .authorities("ROLE_USER")
                .build();
    }

    @Test
    void onAuthenticationSuccess_shouldRedirectAndCreateEmployeeAndStoreToken() throws Exception {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.of(authorizedClientService),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );
        handler.setRedirectStrategy(redirectStrategy);

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(authentication.getName()).thenReturn("Teacher@Test.com");
        when(oidcUser.getEmail()).thenReturn("Teacher@Test.com ");

        UserModel userModel = buildUser("teacher@test.com", true, "TEACHER");
        when(userAccountService.loadUserModelByUsername("teacher@test.com")).thenReturn(userModel);
        when(employeeRepository.findByEmailIgnoreCase("teacher@test.com")).thenReturn(null);
        when(customUserDetailsService.loadUserByUsername("teacher@test.com")).thenReturn(userDetails);
        when(jwtUtils.generateToken(userDetails)).thenReturn("jwt-token");
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("http://localhost:3000/oauth-success");

        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        OAuth2AccessToken accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                "access-token",
                Instant.now(),
                Instant.now().plusSeconds(3600)
        );
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("refresh-token", Instant.now());

        when(authorizedClientService.loadAuthorizedClient("google", "Teacher@Test.com")).thenReturn(client);
        when(client.getAccessToken()).thenReturn(accessToken);
        when(client.getRefreshToken()).thenReturn(refreshToken);

        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(request, response, authentication);

        verify(employeeRepository).save(any(Employee.class));
        verify(tokenStore).storeToken(eq("teacher@test.com"), any(GoogleOAuthTokenStore.GoogleOAuthToken.class));

        ArgumentCaptor<String> redirectCaptor = ArgumentCaptor.forClass(String.class);
        verify(redirectStrategy).sendRedirect(eq(request), eq(response), redirectCaptor.capture());

        String redirect = redirectCaptor.getValue();
        assertTrue(redirect.contains("token=jwt-token"));
        assertTrue(redirect.contains("userName=teacher%40test.com"));
        assertTrue(redirect.contains("schoolName=Test+School"));
        assertTrue(redirect.contains("email=teacher%40test.com"));
        assertTrue(redirect.contains("role=TEACHER"));
        assertTrue(redirect.contains("&user="));
    }

    @Test
    void onAuthenticationSuccess_shouldReturnJsonAndCreateStudent_whenOAuth2UserPrincipal() throws Exception {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OAuth2User oauth2User = mock(OAuth2User.class);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttribute("email")).thenReturn("student@test.com");

        UserModel userModel = buildUser("student@test.com", true, "STUDENT");
        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);
        when(customUserDetailsService.loadUserByUsername("student@test.com")).thenReturn(userDetails);
        when(jwtUtils.generateToken(userDetails)).thenReturn("student-jwt");
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("");

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verify(studentRepository).save(any(Student.class));
        verifyNoInteractions(tokenStore);

        assertEquals(200, response.getStatus());
        assertTrue(response.getContentType().startsWith("application/json"));
        assertEquals("UTF-8", response.getCharacterEncoding());
        assertTrue(response.getContentAsString().contains("student-jwt"));
        assertTrue(response.getContentAsString().contains("student@test.com"));
    }

    @Test
    void onAuthenticationSuccess_shouldReturnJsonWhenAuthorizedClientServicePresentButClientNull() throws Exception {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.of(authorizedClientService),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(authentication.getName()).thenReturn("student@test.com");
        when(oidcUser.getEmail()).thenReturn("student@test.com");

        UserModel userModel = buildUser("student@test.com", true, "STUDENT");
        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(new Student());
        when(customUserDetailsService.loadUserByUsername("student@test.com")).thenReturn(userDetails);
        when(jwtUtils.generateToken(userDetails)).thenReturn("jwt");
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("");
        when(authorizedClientService.loadAuthorizedClient("google", "student@test.com")).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verifyNoInteractions(tokenStore);
        assertEquals(200, response.getStatus());
    }

    @Test
    void onAuthenticationSuccess_shouldReturnJsonWhenAuthorizedClientHasNullAccessToken() throws Exception {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.of(authorizedClientService),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(authentication.getName()).thenReturn("student@test.com");
        when(oidcUser.getEmail()).thenReturn("student@test.com");

        UserModel userModel = buildUser("student@test.com", true, "STUDENT");
        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(new Student());
        when(customUserDetailsService.loadUserByUsername("student@test.com")).thenReturn(userDetails);
        when(jwtUtils.generateToken(userDetails)).thenReturn("jwt");
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("");

        OAuth2AuthorizedClient client = mock(OAuth2AuthorizedClient.class);
        when(authorizedClientService.loadAuthorizedClient("google", "student@test.com")).thenReturn(client);
        when(client.getAccessToken()).thenReturn(null);

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verifyNoInteractions(tokenStore);
        assertEquals(200, response.getStatus());
    }

    @Test
    void onAuthenticationSuccess_shouldSwallowTokenStoreExceptions() throws Exception {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.of(authorizedClientService),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(authentication.getName()).thenReturn("student@test.com");
        when(oidcUser.getEmail()).thenReturn("student@test.com");

        UserModel userModel = buildUser("student@test.com", true, "STUDENT");
        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(new Student());
        when(customUserDetailsService.loadUserByUsername("student@test.com")).thenReturn(userDetails);
        when(jwtUtils.generateToken(userDetails)).thenReturn("jwt");
        when(env.getProperty("auth.sso.google.redirect-url", "")).thenReturn("");
        when(authorizedClientService.loadAuthorizedClient("google", "student@test.com"))
                .thenThrow(new RuntimeException("boom"));

        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationSuccess(new MockHttpServletRequest(), response, authentication);

        verifyNoInteractions(tokenStore);
        assertEquals(200, response.getStatus());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenEmailBlank() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getEmail()).thenReturn("   ");

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("Google account email was not provided.", ex.getMessage());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenOAuth2EmailAttributeMissing() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OAuth2User oauth2User = mock(OAuth2User.class);
        when(authentication.getPrincipal()).thenReturn(oauth2User);
        when(oauth2User.getAttribute("email")).thenReturn(null);

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("Google account email was not provided.", ex.getMessage());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenPrincipalUnsupported() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        when(authentication.getPrincipal()).thenReturn(new Object());

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("Unsupported OAuth principal type.", ex.getMessage());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenUserNotFound() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getEmail()).thenReturn("missing@test.com");
        when(userAccountService.loadUserModelByUsername("missing@test.com")).thenReturn(null);

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("No account found for missing@test.com. Please contact your school/admin.", ex.getMessage());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenUserDisabled() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getEmail()).thenReturn("disabled@test.com");

        UserModel userModel = buildUser("disabled@test.com", false, "TEACHER");
        when(userAccountService.loadUserModelByUsername("disabled@test.com")).thenReturn(userModel);

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("Account is disabled for disabled@test.com.", ex.getMessage());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenEmployeeArchived() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getEmail()).thenReturn("teacher@test.com");

        UserModel userModel = buildUser("teacher@test.com", true, "ADMIN");
        when(userAccountService.loadUserModelByUsername("teacher@test.com")).thenReturn(userModel);

        Employee employee = new Employee();
        employee.setArchived(true);
        when(employeeRepository.findByEmailIgnoreCase("teacher@test.com")).thenReturn(employee);

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("Employee profile is archived for teacher@test.com.", ex.getMessage());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenStudentMissingProvisionedFields() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getEmail()).thenReturn("student@test.com");

        UserModel userModel = buildUser("student@test.com", true, "STUDENT");
        userModel.setFirstName(" ");
        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("Student profile missing for student@test.com. School must provision this student.", ex.getMessage());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenStudentArchived() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getEmail()).thenReturn("student@test.com");

        UserModel userModel = buildUser("student@test.com", true, "STUDENT");
        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);

        Student student = new Student();
        student.setArchived(true);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("Student profile is archived for student@test.com.", ex.getMessage());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenNoRolesPresent() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getEmail()).thenReturn("norole@test.com");

        UserModel userModel = new UserModel();
        userModel.setUsername("norole@test.com");
        userModel.setEnabled(true);
        userModel.setRoles(null);

        when(userAccountService.loadUserModelByUsername("norole@test.com")).thenReturn(userModel);

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("No valid role assigned for norole@test.com.", ex.getMessage());
    }

    @Test
    void onAuthenticationSuccess_shouldThrowWhenRolesContainNullEntriesOnly() {
        OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(
                jwtUtils,
                userAccountService,
                customUserDetailsService,
                env,
                Optional.empty(),
                tokenStore,
                objectMapper,
                studentRepository,
                employeeRepository
        );

        OidcUser oidcUser = mock(OidcUser.class);
        when(authentication.getPrincipal()).thenReturn(oidcUser);
        when(oidcUser.getEmail()).thenReturn("norole2@test.com");

        UserModel userModel = new UserModel();
        userModel.setUsername("norole2@test.com");
        userModel.setEnabled(true);

        Set<RoleModel> roles = new LinkedHashSet<>();
        roles.add(null);

        RoleModel nullNameRole = new RoleModel();
        nullNameRole.setRole(null);
        roles.add(nullNameRole);

        userModel.setRoles(roles);

        when(userAccountService.loadUserModelByUsername("norole2@test.com")).thenReturn(userModel);

        ServletException ex = assertThrows(
                ServletException.class,
                () -> handler.onAuthenticationSuccess(new MockHttpServletRequest(), new MockHttpServletResponse(), authentication)
        );

        assertEquals("No valid role assigned for norole2@test.com.", ex.getMessage());
    }

    private UserModel buildUser(String email, boolean enabled, String... roles) {
        UserModel userModel = new UserModel();
        userModel.setUsername(email);
        userModel.setEnabled(enabled);
        userModel.setFirstName("Chris");
        userModel.setLastName("User");
        userModel.setSchool("Test School");

        Set<RoleModel> roleModels = new LinkedHashSet<>();
        for (String role : roles) {
            RoleModel roleModel = new RoleModel();
            roleModel.setRole(role);
            roleModels.add(roleModel);
        }

        userModel.setRoles(roleModels);
        return userModel;
    }
}