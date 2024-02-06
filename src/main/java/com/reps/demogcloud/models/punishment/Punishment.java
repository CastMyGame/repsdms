package com.reps.demogcloud.models.punishment;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.student.Student;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Document(collection = "Punishments")
public class Punishment {

    @Id
    private String punishmentId;
    private Student student;
    private Infraction infraction;
    private String status;
    private int closedTimes;
    private String closedExplanation;
    //    private int infractionTimes;
    private LocalDateTime timeCreated;
    private LocalDateTime timeClosed;
    private String classPeriod;
    private String teacherEmail;
    //Set initial value to true
    private boolean isArchived = false;
    private String archivedBy;
    private String archivedExplanation;
    private LocalDateTime archivedOn;
    private int mapIndex = 0;
}