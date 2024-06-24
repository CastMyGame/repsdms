package com.reps.demogcloud.models.guidance;


import com.reps.demogcloud.models.punishment.ThreadEvent;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Document(collection = "Guidance")
public class Guidance {

    @Id
    private String guidanceId;
    private String studentEmail;
    private String schoolName;
    private LocalDate timeCreated;
    private LocalDate timeClosed;
    private String classPeriod;
    private String teacherEmail;
    private String guidanceEmail;
    private ArrayList<String> referralDescription;
    private String status;
    private List<ThreadEvent> notesArray;
    private String linkToPunishment;
    private LocalDate followUpDate;


}


