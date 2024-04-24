package com.reps.demogcloud.models.punishment;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.student.Student;
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
@Document(collection = "Punishments")
public class Punishment implements Comparable<Punishment>{

    @Id
    private String punishmentId;
    private String studentEmail;
    private String schoolName;
    private String infractionId;
    private String infractionName;
    private String infractionLevel;
    private String status;
    private int closedTimes;
    private String closedExplanation;
    //    private int infractionTimes;
    private LocalDate timeCreated;
    private LocalDate timeClosed;
    private String classPeriod;
    private String teacherEmail;
    //Set initial value to true
    private boolean isArchived = false;
    private String archivedBy;
    private String archivedExplanation;
    private LocalDate archivedOn;
    private int mapIndex = 0;
    private Map<Date,List<String>> answerHistory;
    private ArrayList<String> infractionDescription;

    public void setAnswerHistory(Date date, List<String> context) {
        if (answerHistory == null) {
            answerHistory = new HashMap<>();
        }

        answerHistory.put(date, context);
    }

    @Override
    public int compareTo(Punishment o) {
        return getTimeCreated().compareTo(o.getTimeCreated());
    }
}