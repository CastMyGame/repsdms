package com.reps.demogcloud.models.punishment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


public class PunishmentResponse {
    private Punishment punishment;
    private String error;
    private String toEmail;
    private String subject;
    private String message;


    public PunishmentResponse() {}

    public PunishmentResponse(Punishment punishment, String error, String toEmail, String subject, String message) {
        this.punishment = punishment;
        this.error = error;
        this.toEmail = toEmail;
        this.subject = subject;
        this.message = message;
    }

    public Punishment getPunishment() {
        return punishment;
    }

    public void setPunishment(Punishment punishment) {
        this.punishment = punishment;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public String getToEmail() {
        return toEmail;
    }

    public void setToEmail(String toEmail) {
        this.toEmail = toEmail;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
