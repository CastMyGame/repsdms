package com.reps.demogcloud.models;


import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String token;

    private String newPassword;
}
