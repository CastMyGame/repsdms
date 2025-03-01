package com.reps.demogcloud.security.controllers;


import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.reps.demogcloud.data.PasswordResetTokenRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.ResetPasswordRequest;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import com.reps.demogcloud.security.models.contactus.ContactUsResponse;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.security.utils.TokenStatus;
import com.reps.demogcloud.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import javax.servlet.http.HttpServletRequest;
import java.awt.desktop.SystemEventListener;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.*;

@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "https://repsdiscipline.vercel.app",
                "https://repsdev.vercel.app"
        }
)

@RestController
public class AuthControllers {

    @Autowired
    EmailService emailService;
    @Autowired
    UserService userService;
    @Autowired
    private PasswordEncoder passwordEncoder;
    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    private AuthenticationManager authenticationManager;



    @PostMapping("/v1/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        // Get the current authentication object
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        // Check if authentication object is not null and contains a token
        if (authentication != null && authentication.getDetails() != null ) {
            String authorizationHeader = request.getHeader("Authorization");
            if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
                String token = authorizationHeader.substring(7); // Extract token after "Bearer "
                jwtUtils.blacklistToken(token); // Assuming jwtUtils has a blacklistToken method
                return ResponseEntity.ok("Logout successful");
            } else {
                return ResponseEntity.badRequest().body("Token not found in Authorization header");
            }
        } else {
            return ResponseEntity.badRequest().body("No active session or token found");
        }
    }

    //------------------------GET Controllers----------------------
    @GetMapping("/test")
    private  String testingToken(){
        return "I WORKS";
    }

    //---------------------POST Controllers-----------------------------
    @PostMapping("/register")
    private ResponseEntity<?> registerUser(@RequestBody AuthenticationRequest authenticationRequest) {
        String username = authenticationRequest.getUsername();
        String password = authenticationRequest.getPassword();
        String firstName = authenticationRequest.getFirstName();
        String lastName = authenticationRequest.getLastName();
        String school = authenticationRequest.getSchoolName();

        UserModel userModel = new UserModel();
        userModel.setUsername(username);
        userModel.setFirstName(firstName);
        userModel.setLastName(lastName);
        userModel.setSchoolName(school);

        // Use BCryptPasswordEncoder to encode the provided password
        String encodedPassword = passwordEncoder.encode(password);
        userModel.setPassword(encodedPassword);

        try {
            userRepository.save(userModel);
            return ResponseEntity.ok(new AuthenticationResponse("Successfully Registered " + username,null));
        } catch (Exception e) {
            return ResponseEntity.ok(new AuthenticationResponse("Error During Registration of user: " + username,null));
        }
    }

    @PostMapping("/auth")
    private ResponseEntity<?> authenticateUser ( @RequestBody AuthenticationRequest authenticationRequest) throws IOException, InterruptedException {
        String username = authenticationRequest.getUsername().toLowerCase();

        String password = authenticationRequest.getPassword();
        try{
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username,password));
        }catch (Exception e){
            return ResponseEntity.ok(new AuthenticationResponse("Error Authenticating user: " + username,null));
        }
        UserDetails loadedUser = userService.loadUserByUsername(username);
        String generatedToken = jwtUtils.generateToken(loadedUser);

        // Fetch additional user-related details (e.g., UserModel) based on the username
        UserModel userModel = userService.loadUserModelByUsername(username);

        // Create a response object that includes the token and user details
        AuthenticationResponse response = new AuthenticationResponse(generatedToken, userModel);

        return ResponseEntity.ok(response);
    }

    @PostMapping("/users/create/{school}")
    private ResponseEntity<List<UserModel>> createNewUsers(@PathVariable String school){
        List<UserModel> createdUsers = userService.createUsersForSchool(school);
        return ResponseEntity.ok(createdUsers);
    }

    @GetMapping("/v1/token-status")
    public ResponseEntity<?> getTokenStatus(HttpServletRequest request) {
        String authorizationHeader = request.getHeader("Authorization");

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            String token = authorizationHeader.substring(7); // Extract token after "Bearer "

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
            String token = authorizationHeader.substring(7); // Extract token after "Bearer "

            try {
                String newToken = jwtUtils.renewTokenWithBlacklist(token);

                Map<String, String> response = new HashMap<>();
                response.put("newToken", newToken);

                // Make sure to return JSON and set the content type explicitly if needed
                return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(response);
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
        //check if email is in userRepository
        UserModel user = userRepository.findByUsername(email);
        if (user==null){
            return ResponseEntity.badRequest().body("User not found for email " + email);
        }
        // Generate unique token that will be sent to email
    String resetToken = UUID.randomUUID().toString();

        // save token into mongo to check against after user gets email
    PasswordResetToken passwordResetToken = new PasswordResetToken();
    passwordResetToken.setUser(user);
    passwordResetToken.setToken(resetToken);
    passwordResetToken.setExpiryDate(24*60); // set exipration time in minutes
    passwordResetTokenRepository.save(passwordResetToken);
    String link = "https://repsdiscipline.vercel.app/reset-password/"+resetToken;
    emailService.sendEmail(user.getUsername(), "Reset Your Password", "Click the Link Below to Reset Your Password " + link );
    return ResponseEntity.ok("Password reset link sent to " + email);


    }

    @PostMapping("/reset-password")
    public ResponseEntity<?> resetPassword(@RequestBody ResetPasswordRequest resetPasswordRequest) {
        String token = resetPasswordRequest.getToken();
        String newPassword = resetPasswordRequest.getNewPassword();

        // Find the token in the database
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(token);

        if (passwordResetToken == null || passwordResetToken.isExpired()) {
            return ResponseEntity.badRequest().body("Invalid or expired token");
        }

        // Update the user's password
        UserModel user = passwordResetToken.getUser();

        // This is where the issue might be if newPassword is null
        user.setPassword(passwordEncoder.encode(newPassword));

        userRepository.save(user);

        // Delete the used token from the database
        passwordResetTokenRepository.delete(passwordResetToken);

        return ResponseEntity.ok("Password reset successfully");
    }

    @PostMapping("/contact-us")
    public ResponseEntity<ContactUsResponse> contactUs (@RequestBody ContactUsRequest request) {
       ContactUsResponse response = userService.contactUs(request);

        return ResponseEntity.ok(response);
    }

}

 class AuthenticationResponseRenewal {
     private final String message;
     private final String token;
     private final UserModel userModel;

    public AuthenticationResponseRenewal(String message, String token, UserModel userModel) {
        this.message = message;
        this.token = token;
        this.userModel = userModel;
    }

    // Getters and setters for message, token, and userModel...
}
