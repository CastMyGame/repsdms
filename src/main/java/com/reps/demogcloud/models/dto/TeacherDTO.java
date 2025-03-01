package com.reps.demogcloud.models.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.ArrayList;

@Data
public class TeacherDTO {
    private String studentEmail;
    private String studentFirstName;
    private String studentLastName;
    private String infractionName;
    private LocalDate timeCreated;
    private ArrayList<String> infractionDescription;
    private String classPeriod;
    private String teacherEmail;
    private String status;
    private String infractionLevel;

}
