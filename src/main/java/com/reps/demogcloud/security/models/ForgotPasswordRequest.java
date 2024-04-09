package com.reps.demogcloud.security.models;

public class ForgotPasswordRequest {

    private String email;

    // Constructors, getters, and setters

    public ForgotPasswordRequest() {
    }

    public ForgotPasswordRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
