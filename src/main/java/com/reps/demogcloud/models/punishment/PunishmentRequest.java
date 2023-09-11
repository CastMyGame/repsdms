package com.reps.demogcloud.models.punishment;

import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.student.Student;

public class PunishmentRequest {
    private Student student;
    private Infraction infraction;
    private String error;

    public PunishmentRequest() {
    }

    public PunishmentRequest(Student student, Infraction infraction, String error) {
        this.student = student;
        this.infraction = infraction;
        this.error = error;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public Infraction getInfraction() {
        return infraction;
    }

    public void setInfraction(Infraction infraction) {
        this.infraction = infraction;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}
