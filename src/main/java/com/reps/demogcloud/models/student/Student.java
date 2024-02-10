package com.reps.demogcloud.models.student;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.joda.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
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
    //Set initial value to true
    private boolean isArchived = false;
    private String archivedBy;
    private String archivedExplanation;
    private LocalDate archivedOn;
    private Integer points;
    private String school;

}
