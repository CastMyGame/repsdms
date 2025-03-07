package com.reps.demogcloud.models.student;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Document(collection = "students")
@JsonIgnoreProperties(ignoreUnknown = true)
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
    private Integer currency;
    private Integer stateStudentId;
    private List<ThreadEvent> notesArray;
    private List<String> spotters;

    // Time bank field to track detention hours and minutes
    private TimeBank timeBank;

    // Constructor, Getters, and Setters for timeBank would be generated by Lombok

    // Inner class to represent the time bank in hours and minutes
    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class TimeBank {
        // Set initial values to 0
        private int hours = 0;
        private int minutes = 0;
    }

}
