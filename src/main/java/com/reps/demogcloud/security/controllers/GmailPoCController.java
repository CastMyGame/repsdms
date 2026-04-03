package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import com.reps.demogcloud.security.services.GoogleMailService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/gmail")
@RequiredArgsConstructor
public class GmailPoCController {

    private final GoogleMailService googleMailService;

    @PostMapping("/send-test")
    public ResponseEntity<String> sendTestEmail(@Valid @RequestBody GoogleMailSendRequest request,
                                                Authentication authentication) throws Exception {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Authentication required");
        }

        String username = authentication.getName();
        googleMailService.sendEmailOnBehalf(username, request);
        return ResponseEntity.ok("Email sent via Gmail API on behalf of " + username);
    }
}