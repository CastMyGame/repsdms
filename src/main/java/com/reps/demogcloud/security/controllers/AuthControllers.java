package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.data.PasswordResetTokenRepository;
import com.reps.demogcloud.models.ResetPasswordRequest;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.AuthenticationResponse;
import com.reps.demogcloud.security.models.ForgotPasswordRequest;
import com.reps.demogcloud.security.models.PasswordResetToken;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import com.reps.demogcloud.security.models.contactus.ContactUsResponse;
import com.reps.demogcloud.security.services.CustomUserDetailsService;
import com.reps.demogcloud.security.services.UserAccountService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.security.utils.TokenStatus;
import com.reps.demogcloud.services.EmailService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "https://repsdiscipline.vercel.app",
                "https://repsdev.vercel.app"
        }
)
@RestController
@RequiredArgsConstructor
public class AuthControllers {

    private final EmailService emailService;
    private final UserAccountService userAccountService;
    private final CustomUserDetailsService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuthenticationManager authenticationManager;

    @PostMapping("/v1/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null && authentication.getDetails() != null) {
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7);
                jwtUtils.blacklistToken(token);
                return ResponseEntity.ok("Logout successful");
            } else {
                return ResponseEntity.badRequest().body("Token not found in Authorization header");
            }
        } else {
            return ResponseEntity.badRequest().body("No active session or token found");
        }
    }

    @GetMapping("/test")
    public String testingToken() {
        return "I WORKS";
    }

    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody AuthenticationRequest authenticationRequest) {
        String username = authenticationRequest.getUsername();
        String password = authenticationRequest.getPassword();
        String firstName = authenticationRequest.getFirstName();
        String lastName = authenticationRequest.getLastName();
        String school = authenticationRequest.getSchool();

        UserModel userModel = new UserModel();
        userModel.setUsername(username);
        userModel.setFirstName(firstName);
        userModel.setLastName(lastName);
        userModel.setSchool(school);
        userModel.setPassword(passwordEncoder.encode(password));

        try {
            userRepository.save(userModel);
            return ResponseEntity.ok(new AuthenticationResponse("Successfully Registered " + username, null));
        } catch (Exception e) {
            return ResponseEntity.ok(new AuthenticationResponse("Error During Registration of user: " + username, null));
        }
    }

    @PostMapping("/auth")
    public ResponseEntity<?> authenticateUser(@RequestBody AuthenticationRequest authenticationRequest) {

        String username = authenticationRequest.getUsername().toLowerCase();
        String password = authenticationRequest.getPassword();

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (Exception e) {
            return ResponseEntity.ok(new AuthenticationResponse("Error Authenticating user: " + username, null));
        }

        UserDetails loadedUser = customUserDetailsService.loadUserByUsername(username);
        String generatedToken = jwtUtils.generateToken(loadedUser);
        UserModel userModel = userAccountService.loadUserModelByUsername(username);

        AuthenticationResponse response = new AuthenticationResponse(generatedToken, userModel);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/create/{school}")
    public ResponseEntity<List<UserModel>> createNewUsers(@PathVariable String school) {
        List<UserModel> createdUsers = userAccountService.createUsersForSchool(school);
        return ResponseEntity.ok(createdUsers);
    }

    @GetMapping("/v1/token-status")
    public ResponseEntity<?> getTokenStatus(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);

            try {
                TokenStatus tokenStatus = jwtUtils.getTokenStatus(token);

                Map<String, Object> response = new HashMap<>();
                response.put("isExpired", tokenStatus.isExpired());
                response.put("timeUntilExpiration", tokenStatus.getTimeUntilExpiration());

                return ResponseEntity.ok(response);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body("Invalid token or error parsing token");
            }
        } else {
            return ResponseEntity.badRequest().body("Token not found in Authorization header");
        }
    }

    @PostMapping("/v1/renew-token")
    public ResponseEntity<Map<String, String>> renewToken(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7);

            try {
                String newToken = jwtUtils.renewTokenWithBlacklist(token);

                Map<String, String> response = new HashMap<>();
                response.put("newToken", newToken);

                return ResponseEntity.ok()
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(response);
            } catch (Exception e) {
                return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Token renewal failed"));
            }
        } else {
            return ResponseEntity.badRequest().body(Collections.singletonMap("error", "Token not found in Authorization header"));
        }
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<?> forgotPassword(@RequestBody ForgotPasswordRequest forgotPasswordRequest) throws MessagingException {
        String email = forgotPasswordRequest.getEmail();
        UserModel user = userRepository.findByUsername(email);

        if (user == null) {
            return ResponseEntity.badRequest().body("User not found for email " + email);
        }

        String resetToken = UUID.randomUUID().toString();

        PasswordResetToken passwordResetToken = new PasswordResetToken();
        passwordResetToken.setUser(user);
        passwordResetToken.setToken(resetToken);
        passwordResetToken.setExpiryDate(24 * 60);

        passwordResetTokenRepository.save(passwordResetToken);

        String link = "https://repsdiscipline.vercel.app/reset-password/" + resetToken;
        emailService.sendEmail(
                user.getUsername(),
                "Reset Your Password",
                "Click the Link Below to Reset Your Password " + link,
                "en"
        );

        return ResponseEntity.ok("Password reset link sent to " + email);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest resetPasswordRequest) {
        String token = resetPasswordRequest.getToken();
        String newPassword = resetPasswordRequest.getNewPassword();

        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(token);

        if (passwordResetToken == null || passwordResetToken.isExpired()) {
            return ResponseEntity.badRequest().body("Invalid or expired token");
        }

        UserModel user = passwordResetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        passwordResetTokenRepository.delete(passwordResetToken);

        return ResponseEntity.ok("Password reset successfully");
    }

    @PostMapping("/contact-us")
    public ResponseEntity<ContactUsResponse> contactUs(@RequestBody ContactUsRequest request) {
        ContactUsResponse response = userAccountService.contactUs(request);
        return ResponseEntity.ok(response);
    }
}