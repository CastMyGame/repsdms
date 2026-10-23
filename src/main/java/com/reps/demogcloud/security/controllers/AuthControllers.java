package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.data.PasswordResetTokenRepository;
import com.reps.demogcloud.models.ResetPasswordRequest;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.AuthenticationResponse;
import com.reps.demogcloud.security.models.AuthCapabilitiesResponse;
import com.reps.demogcloud.security.models.ForgotPasswordRequest;
import com.reps.demogcloud.security.models.PasswordResetToken;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import com.reps.demogcloud.security.models.contactus.ContactUsResponse;
import com.reps.demogcloud.security.services.CustomUserDetailsService;
import com.reps.demogcloud.security.services.GoogleOAuthTokenStore;
import com.reps.demogcloud.security.services.UserAccountService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.security.utils.TokenStatus;
import com.reps.demogcloud.services.EmailService;
import jakarta.mail.MessagingException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class AuthControllers {

    private final EmailService emailService;
    private final UserAccountService userAccountService;
    private final CustomUserDetailsService customUserDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final com.reps.demogcloud.security.services.AccountAccessService accountAccess;
    private final JwtUtils jwtUtils;
    private final UserRepository userRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final AuthenticationManager authenticationManager;
    private final GoogleOAuthTokenStore googleOAuthTokenStore;
    private final com.reps.demogcloud.services.UserContextService userContextService;
    private final com.reps.demogcloud.services.email.EmailRoutingService emailRoutingService;

    // Explicit bearer authentication prevents cookie-based account-linking CSRF even if a login session exists.
    private String gmailAccount(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer "))
            throw new org.springframework.security.access.AccessDeniedException("Bearer authentication is required");
        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        String account = userContextService.getCurrentStaffEmail();
        String bearerAccount;
        try {
            String token = header.substring(7);
            bearerAccount = jwtUtils.extractUserName(token);
            if (!jwtUtils.validateToken(token, customUserDetailsService.loadUserByUsername(account)))
                throw new IllegalArgumentException();
        }
        catch (Exception ex) { throw new org.springframework.security.access.AccessDeniedException("Valid bearer authentication is required"); }
        if (current == null || !account.equalsIgnoreCase(bearerAccount) || !current.getName().equalsIgnoreCase(bearerAccount))
            throw new org.springframework.security.access.AccessDeniedException("Gmail must belong to the authenticated REPS account");
        return account;
    }

    @GetMapping("/auth/gmail/status")
    public ResponseEntity<?> gmailStatus(HttpServletRequest request) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(googleOAuthTokenStore.status(gmailAccount(request)));
    }

    @PostMapping("/auth/gmail/connect")
    public ResponseEntity<?> connectGmail(HttpServletRequest request, @RequestBody Map<String, String> body) {
        String account = gmailAccount(request);
        try {
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                    .body(Map.of("authorizationUrl", googleOAuthTokenStore.beginConnection(account, body.get("browserProof"))));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("message", "Gmail connection is not configured or is temporarily unavailable."));
        }
    }

    @PostMapping("/auth/gmail/complete")
    public ResponseEntity<?> completeGmail(HttpServletRequest request, @RequestBody Map<String, String> body) {
        String account = gmailAccount(request);
        try {
            googleOAuthTokenStore.completeConnection(account, body.get("state"), body.get("code"), body.get("browserProof"));
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(googleOAuthTokenStore.status(account));
        } catch (IllegalStateException ex) {
            return ResponseEntity.badRequest().body(Map.of("message", "Gmail connection failed. Start Connect Gmail again."));
        }
    }

    @PostMapping("/auth/gmail/disconnect")
    public ResponseEntity<?> disconnectGmail(HttpServletRequest request) {
        String account = gmailAccount(request);
        try {
            boolean revoked = googleOAuthTokenStore.disconnect(account);
            return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore()).body(Map.of(
                    "state", "DISCONNECTED", "providerRevocationConfirmed", revoked,
                    "message", revoked ? "Gmail disconnected. REPS SMTP selected." :
                    "Gmail disconnected in REPS. Google revocation was not confirmed; remove REPS access in your Google account."));
        } catch (IllegalStateException ex) {
            return ResponseEntity.status(503).body(Map.of("message", "Disconnection could not be saved. Try again."));
        }
    }

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
        // The supported public registration flow uses verified Stripe provisioning.
        throw new org.springframework.security.access.AccessDeniedException("Legacy registration is disabled");
    }

    @PostMapping("/auth")
    public ResponseEntity<?> authenticateUser(@RequestBody AuthenticationRequest authenticationRequest) {

        String username = authenticationRequest.getUsername().toLowerCase();
        String password = authenticationRequest.getPassword();

        log.info("Password authentication requested");

        try {
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username, password));
        } catch (Exception e) {
            log.warn("Password authentication failed");
            return ResponseEntity.ok(new AuthenticationResponse("Error Authenticating user: " + username, null));
        }

        try {
            UserDetails loadedUser = customUserDetailsService.loadUserByUsername(username);
            String generatedToken = jwtUtils.generateToken(loadedUser, "PASSWORD");
            UserModel userModel = userAccountService.loadUserModelByUsername(username);

            log.info("Password authentication succeeded");
            AuthenticationResponse response = new AuthenticationResponse(generatedToken, userModel);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Session creation failed");
            throw e;
        }
    }

    @GetMapping("/auth/capabilities")
    public ResponseEntity<AuthCapabilitiesResponse> getAuthCapabilities(HttpServletRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? "" : authentication.getName();
        String loginMethod = "PASSWORD";

        String authorizationHeader = request.getHeader("Authorization");
        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            loginMethod = jwtUtils.extractLoginMethod(authorizationHeader.substring(7));
        }

        boolean staffEligible;
        try { staffEligible = username.equalsIgnoreCase(userContextService.getCurrentStaffEmail()); }
        catch (org.springframework.security.access.AccessDeniedException | IllegalStateException ex) { staffEligible = false; }
        boolean googlePermission = staffEligible && googleOAuthTokenStore.hasUsableGmailSendToken(username);
        boolean canSendAsUser = googlePermission && emailRoutingService.isStaffGmailPolicyEnabled();
        GoogleOAuthTokenStore.ConnectionState state = googleOAuthTokenStore.getConnectionState(username);
        boolean blocked = staffEligible && emailRoutingService.isPhase1RoutingEnabled() && emailRoutingService.isStaffGmailPolicyEnabled()
                && (state == GoogleOAuthTokenStore.ConnectionState.CONNECTED
                || state == GoogleOAuthTokenStore.ConnectionState.RECONNECT_REQUIRED || state == GoogleOAuthTokenStore.ConnectionState.UNKNOWN)
                && !canSendAsUser;
        String emailProvider = canSendAsUser ? "GOOGLE_GMAIL" : (blocked ? "RECONNECT_REQUIRED" : "REPS_SMTP");
        String emailPermission = googlePermission ? "GRANTED" : (blocked ? "RECONNECT_REQUIRED" : "NOT_GRANTED");

        return ResponseEntity.ok(new AuthCapabilitiesResponse(
                loginMethod.toUpperCase(),
                username,
                emailProvider,
                emailPermission,
                canSendAsUser
        ));
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
        accountAccess.requestReset(forgotPasswordRequest.getEmail());
        return ResponseEntity.accepted().body("If account access is available, setup instructions have been requested. Students without private email should ask their school.");
    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest request) {
        accountAccess.complete(null,request.getToken(),request.getNewPassword());
        return ResponseEntity.ok("Password reset successfully");
    }

    @PostMapping("/contact-us")
    public ResponseEntity<ContactUsResponse> contactUs(@RequestBody ContactUsRequest request) {
        ContactUsResponse response = userAccountService.contactUs(request);
        return ResponseEntity.ok(response);
    }
}
