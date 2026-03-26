package com.reps.demogcloud.security.models.gmail;
import java.util.List;
import lombok.Data;
import jakarta.validation.constraints.NotBlank;

@Data
public class GoogleMailSendRequest {

    private String from; // Note: 'from' should probably be validated as an email format if present

    @NotBlank
    private String to; // Primary recipient (single email string)

    // Recommended: Use a List<String> for multiple CC recipients
    private List<String> cc;

    @NotBlank
    private String subject;

    @NotBlank
    private String body;

    // Recommended: Use a List<String> for multiple BCC recipients
    private List<String> bcc;
}

