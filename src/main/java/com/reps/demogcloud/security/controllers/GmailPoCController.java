package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest;
import com.reps.demogcloud.security.services.GoogleMailService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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
    public ResponseEntity<String> sendTestEmail(@Validated @RequestBody GoogleMailSendRequest request,
                                                Authentication authentication) throws Exception {
        String username = authentication.getName();
        googleMailService.sendEmailOnBehalf(username, request);
        return ResponseEntity.ok("Email sent via Gmail API on behalf of " + username);
    }
}

