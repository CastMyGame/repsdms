package com.reps.demogcloud.models.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


@Document(collection = "students")
public class Student {
    @Id
    private String studentIdNumber;
    private String firstName;
    private String lastName;
    private String parentEmail;
    private String studentEmail;
    private String guidanceEmail;
    private String adminEmail;
    private String address;
    private String grade;
    private String parentPhoneNumber;
    private String studentPhoneNumber;

    public Student(){

    }

    public Student(String studentIdNumber, String firstName, String lastName, String parentEmail, String studentEmail, String guidanceEmail, String adminEmail, String address, String grade, String parentPhoneNumber, String studentPhoneNumber) {
        this.studentIdNumber = studentIdNumber;
        this.firstName = firstName;
        this.lastName = lastName;
        this.parentEmail = parentEmail;
        this.studentEmail = studentEmail;
        this.guidanceEmail = guidanceEmail;
        this.adminEmail = adminEmail;
        this.address = address;
        this.grade = grade;
        this.parentPhoneNumber = parentPhoneNumber;
        this.studentPhoneNumber = studentPhoneNumber;
    }

    public String getStudentIdNumber() {
        return studentIdNumber;
    }

    public void setStudentIdNumber(String studentIdNumber) {
        this.studentIdNumber = studentIdNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getParentEmail() {
        return parentEmail;
    }

    public void setParentEmail(String parentEmail) {
        this.parentEmail = parentEmail;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getGuidanceEmail() {
        return guidanceEmail;
    }

    public void setGuidanceEmail(String guidanceEmail) {
        this.guidanceEmail = guidanceEmail;
    }

    public String getAdminEmail() {
        return adminEmail;
    }

    public void setAdminEmail(String adminEmail) {
        this.adminEmail = adminEmail;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getParentPhoneNumber() {
        return parentPhoneNumber;
    }

    public void setParentPhoneNumber(String parentPhoneNumber) {
        this.parentPhoneNumber = parentPhoneNumber;
    }

    public String getStudentPhoneNumber() {
        return studentPhoneNumber;
    }

    public void setStudentPhoneNumber(String studentPhoneNumber) {
        this.studentPhoneNumber = studentPhoneNumber;
    }
}
