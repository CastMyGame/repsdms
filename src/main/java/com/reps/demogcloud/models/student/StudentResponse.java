package com.reps.demogcloud.models.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


public class StudentResponse {
    private String error;
    private Student student;

    public StudentResponse(){

    }
    public StudentResponse(String error, Student student) {
        this.error = error;
        this.student = student;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }
}