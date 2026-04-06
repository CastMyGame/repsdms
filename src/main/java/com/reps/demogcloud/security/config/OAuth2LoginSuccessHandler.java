package com.reps.demogcloud.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.AuthenticationResponse;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.CustomUserDetailsService;
import com.reps.demogcloud.security.services.GoogleOAuthTokenStore;
import com.reps.demogcloud.security.services.UserAccountService;
import com.reps.demogcloud.security.utils.JwtUtils;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    private final JwtUtils jwtUtils;
    private final UserAccountService userAccountService;
    private final CustomUserDetailsService customUserDetailsService;
    private final Environment env;
    private final Optional<OAuth2AuthorizedClientService> authorizedClientService;
    private final GoogleOAuthTokenStore tokenStore;
    private final ObjectMapper objectMapper;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                        HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {

        String email = extractEmail(authentication);

        if (email.isBlank()) {
            throw new ServletException("Google account email was not provided.");
        }

        UserModel userModel = userAccountService.loadUserModelByUsername(email);
        if (userModel == null) {
            throw new ServletException("No account found for " + email + ". Please contact your school/admin.");
        }

        if (!userModel.isEnabled()) {
            throw new ServletException("Account is disabled for " + email + ".");
        }

        boolean isStudent = hasRole(userModel, "STUDENT");
        boolean isTeacher = hasRole(userModel, "TEACHER");
        boolean isAdmin = hasRole(userModel, "ADMIN");

        if (isTeacher || isAdmin) {
            Employee employee = employeeRepository.findByEmailIgnoreCase(email);

            if (employee == null) {
                Employee newEmployee = new Employee();
                newEmployee.setEmail(email);
                newEmployee.setFirstName(userModel.getFirstName());
                newEmployee.setLastName(userModel.getLastName());
                newEmployee.setSchool(userModel.getSchool());
                newEmployee.setRoles(userModel.getRoles());
                employeeRepository.save(newEmployee);
            } else if (employee.isArchived()) {
                throw new ServletException("Employee profile is archived for " + email + ".");
            }

        } else if (isStudent) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(email);

            if (student == null) {
                if (isBlank(userModel.getFirstName()) || isBlank(userModel.getLastName()) || isBlank(userModel.getSchool())) {
                    throw new ServletException("Student profile missing for " + email + ". School must provision this student.");
                }

                Student newStudent = new Student();
                newStudent.setStudentEmail(email);
                newStudent.setFirstName(userModel.getFirstName());
                newStudent.setLastName(userModel.getLastName());
                newStudent.setSchool(userModel.getSchool());
                studentRepository.save(newStudent);
            } else if (student.isArchived()) {
                throw new ServletException("Student profile is archived for " + email + ".");
            }

        } else {
            throw new ServletException("No valid role assigned for " + email + ".");
        }

        UserDetails userDetails = customUserDetailsService.loadUserByUsername(email);
        storeGoogleTokens(authentication, userModel);

        String token = jwtUtils.generateToken(userDetails);

        String redirectUrl = env.getProperty("auth.sso.google.redirect-url", "");

        if (!redirectUrl.isEmpty()) {
            String userName = userModel.getUsername() != null ? userModel.getUsername() : "";
            String schoolName = userModel.getSchool() != null ? userModel.getSchool() : "";
            String userEmail = userModel.getUsername() != null ? userModel.getUsername() : "";
            String role = "";

            if (userModel.getRoles() != null && !userModel.getRoles().isEmpty()) {
                RoleModel firstRole = userModel.getRoles().iterator().next();
                if (firstRole != null && firstRole.getRole() != null) {
                    role = firstRole.getRole();
                }
            }

            String redirectWithToken = redirectUrl
                    + "?token=" + URLEncoder.encode(token, StandardCharsets.UTF_8)
                    + "&userName=" + URLEncoder.encode(userName, StandardCharsets.UTF_8)
                    + "&schoolName=" + URLEncoder.encode(schoolName, StandardCharsets.UTF_8)
                    + "&email=" + URLEncoder.encode(userEmail, StandardCharsets.UTF_8)
                    + "&role=" + URLEncoder.encode(role, StandardCharsets.UTF_8)
                    + "&user=" + URLEncoder.encode(objectMapper.writeValueAsString(userModel), StandardCharsets.UTF_8);

            getRedirectStrategy().sendRedirect(request, response, redirectWithToken);
        } else {
            AuthenticationResponse authResponse = new AuthenticationResponse(token, userModel);

            response.setContentType("application/json");
            response.setCharacterEncoding("UTF-8");
            response.setStatus(HttpServletResponse.SC_OK);
            response.getWriter().write(objectMapper.writeValueAsString(authResponse));
            response.getWriter().flush();
        }

        clearAuthenticationAttributes(request);
    }

    private String extractEmail(Authentication authentication) throws ServletException {
        Object principal = authentication.getPrincipal();

        if (principal instanceof OidcUser oidcUser) {
            return oidcUser.getEmail() == null ? "" : oidcUser.getEmail().trim().toLowerCase();
        }

        if (principal instanceof OAuth2User oauth2User) {
            Object emailAttr = oauth2User.getAttribute("email");
            return emailAttr == null ? "" : emailAttr.toString().trim().toLowerCase();
        }

        throw new ServletException("Unsupported OAuth principal type.");
    }

    private void storeGoogleTokens(Authentication authentication, UserModel userModel) {
        try {
            if (authorizedClientService.isEmpty()) {
                return;
            }

            OAuth2AuthorizedClient authorizedClient =
                    authorizedClientService.get().loadAuthorizedClient("google", authentication.getName());

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
        } catch (Exception ignored) {
            // best effort only
        }
    }

    private boolean hasRole(UserModel userModel, String roleName) {
        if (userModel.getRoles() == null) {
            return false;
        }

        for (RoleModel role : userModel.getRoles()) {
            if (role != null && role.getRole() != null && role.getRole().equalsIgnoreCase(roleName)) {
                return true;
            }
        }

        return false;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}