package com.reps.demogcloud.models.punishment;

import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.student.Student;
import lombok.Getter;

@Getter
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

    public void setStudent(Student student) {
        this.student = student;
    }

    public void setInfraction(Infraction infraction) {
        this.infraction = infraction;
    }

    public void setError(String error) {
        this.error = error;
    }
}
