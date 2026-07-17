package com.reps.demogcloud.security.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AuthenticationResponse {
    /**
     * Legacy access-token field retained while the web client migrates to
     * accessToken. New clients should use accessToken and refreshToken.
     */
    private String response;
    private String accessToken;
    private String refreshToken;
    private UserModel userModel;

    public AuthenticationResponse(String accessToken, UserModel userModel) {
        this.response = accessToken;
        this.accessToken = accessToken;
        this.userModel = userModel;
    }
}
