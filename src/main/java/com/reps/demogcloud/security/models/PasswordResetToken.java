package com.reps.demogcloud.security.models;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

@Document(collection = "passwordResetTokens")
public class PasswordResetToken {

    @Id
    private String id;

    private UserModel user;

    private String token;

    private Date expiryDate;

    public PasswordResetToken() {
        // Default constructor
    }

    public PasswordResetToken(UserModel user, String token, int expiryTimeInMinutes) {
        this.user = user;
        this.token = token;
        this.expiryDate = new Date(System.currentTimeMillis() + (expiryTimeInMinutes * 60 * 1000));
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public UserModel getUser() {
        return user;
    }

    public void setUser(UserModel user) {
        this.user = user;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(int expiryTimeInMinutes) {
        this.expiryDate = new Date(System.currentTimeMillis() + (expiryTimeInMinutes * 60 * 1000));;
    }

    public boolean isExpired() {
        return this.expiryDate.before(new Date());
    }
}
