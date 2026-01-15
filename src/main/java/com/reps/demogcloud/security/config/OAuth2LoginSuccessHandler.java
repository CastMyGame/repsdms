package com.reps.demogcloud.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.AuthenticationResponse;
import com.reps.demogcloud.security.models.RoleModel;
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
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException, ServletException {
        
        OidcUser oidcUser = (OidcUser) authentication.getPrincipal();
        String email = oidcUser.getEmail() == null ? "" : oidcUser.getEmail().trim().toLowerCase();

        if (email.isBlank()) {
            throw new ServletException("Google account email was not provided.");
        }

        // Load user from database to get full user details
        UserModel userModel = userService.loadUserModelByUsername(email);
        if (userModel == null) {
            // This will land in your failure handler
            throw new ServletException("No account found for " + email + ". Please contact your school/admin.");
        }
        if (!userModel.isEnabled()) {
            throw new ServletException("Account is disabled for " + email + ".");
        }

        boolean isStudent = hasRole(userModel, "STUDENT");
        boolean isTeacher = hasRole(userModel, "TEACHER");
        boolean isAdmin = hasRole(userModel, "ADMIN");

        // Priority: employee roles first (prevents accidentally creating student profiles for staff)
        if (isTeacher || isAdmin) {
            Employee emp = employeeRepository.findByEmailIgnoreCase(email);
            if (emp == null) {
                // Create Employee profile from UserModel
                Employee newEmp = new Employee();
                newEmp.setEmail(email);
                newEmp.setFirstName(userModel.getFirstName());
                newEmp.setLastName(userModel.getLastName());
                newEmp.setSchool(userModel.getSchool());
                newEmp.setRoles(userModel.getRoles());
                // archived defaults false
                employeeRepository.save(newEmp);
            } else if (emp.isArchived()) {
                throw new ServletException("Employee profile is archived for " + email + ".");
            }
        } else if (isStudent) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(email);
            if (student == null) {
                // Create Student profile from UserModel ONLY if we have enough info
                if (isBlank(userModel.getFirstName()) || isBlank(userModel.getLastName()) || isBlank(userModel.getSchool())) {
                    throw new ServletException("Student profile missing for " + email + ". School must provision this student.");
                }

                Student newStudent = new Student();
                newStudent.setStudentEmail(email);
                newStudent.setFirstName(userModel.getFirstName());
                newStudent.setLastName(userModel.getLastName());
                newStudent.setSchool(userModel.getSchool());
                // archived defaults false; preferredLanguage defaults to "en"
                studentRepository.save(newStudent);
            } else if (student.isArchived()) {
                throw new ServletException("Student profile is archived for " + email + ".");
            }
        } else {
            // User exists but has no recognized role
            throw new ServletException("No valid role assigned for " + email + ".");
        }

        UserDetails userDetails = userService.loadUserByUsername(email);

        // Persist Google OAuth tokens for Gmail PoC
        storeGoogleTokens(authentication, userModel);

        // Generate JWT token
        String token = jwtUtils.generateToken(userDetails);

        // Check if we should redirect to frontend or return JSON
        String redirectUrl = env.getProperty("auth.sso.google.redirect-url", "");

        if (!redirectUrl.isEmpty()) {
            String userName = userModel.getUsername();
            String schoolName = userModel.getSchool() != null ? userModel.getSchool() : "";
            String userEmail = userModel.getUsername();
            String role = "";

            if (userModel.getRoles() != null && !userModel.getRoles().isEmpty()) {
                role = userModel.getRoles().iterator().next().getRole();
            }

            String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
            String encodedUserName = URLEncoder.encode(userName, StandardCharsets.UTF_8);
            String encodedSchoolName = URLEncoder.encode(schoolName, StandardCharsets.UTF_8);
            String encodedEmail = URLEncoder.encode(userEmail, StandardCharsets.UTF_8);
            String encodedRole = URLEncoder.encode(role, StandardCharsets.UTF_8);
            String encodedUserModel = URLEncoder.encode(objectMapper.writeValueAsString(userModel), StandardCharsets.UTF_8);

            String redirectWithToken = redirectUrl +
                    "?token=" + encodedToken +
                    "&userName=" + encodedUserName +
                    "&schoolName=" + encodedSchoolName +
                    "&email=" + encodedEmail +
                    "&role=" + encodedRole +
                    "&user=" + encodedUserModel;

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

    private boolean hasRole(UserModel userModel, String roleName) {
        if (userModel.getRoles() == null) return false;
        for (RoleModel r : userModel.getRoles()) {
            if (r != null && r.getRole() != null && r.getRole().equalsIgnoreCase(roleName)) return true;
        }
        return false;
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}

