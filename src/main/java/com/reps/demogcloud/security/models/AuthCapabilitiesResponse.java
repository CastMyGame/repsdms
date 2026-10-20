package com.reps.demogcloud.security.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuthCapabilitiesResponse {
    private String loginMethod;
    private String userEmail;
    private String emailProvider;
    private String emailPermission;
    private boolean canSendAsUser;
}
