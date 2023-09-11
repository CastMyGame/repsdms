package com.reps.demogcloud.models.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class StudentRequest {
    private Student student;
    private String error;

    public StudentRequest(){

    }

    public StudentRequest(Student student, String error) {
        this.student = student;
        this.error = error;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }
}