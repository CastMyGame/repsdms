package com.reps.demogcloud.models.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
public class StudentRequest {
    private Student student;
    private String error;

    public StudentRequest(){

    }

    public StudentRequest(Student student, String error) {
        this.student = student;
        this.error = error;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public void setError(String error) {
        this.error = error;
    }
}