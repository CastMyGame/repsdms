package com.reps.demogcloud.models.officeReferral;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;
import java.util.*;
@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Document(collection = "OfficeReferrals")
public class OfficeReferral implements Comparable<OfficeReferral>{
    @Id
    private String officeReferralId;
    private OfficeReferralCode referralCode;
    private String infractionName;
private String infractionLevel;
    private String studentEmail;
    private String adminEmail;
    private String teacherEmail;
    private String schoolName;
    private String status;
    private String closedExplanation;
    private LocalDate timeCreated;
    private LocalDate timeClosed;
    private String classPeriod;
    //Set initial value to false
    private boolean isArchived = false;
    // Set initial value to false until saved in review360 or other state discipline system
    private boolean isStateFiled = false;
    private String stateIncidentNumber;
    private String archivedBy;
    private String archivedExplanation;
    private LocalDate archivedOn;
    private int mapIndex = 0;
    private Map<Date, List<String>> answerHistory;
    private ArrayList<String> referralDescription;



    public void setAnswerHistory(Date date, List<String> context) {
        if (answerHistory == null) {
            answerHistory = new HashMap<>();
        }

        answerHistory.put(date, context);
    }



    public int compareTo(OfficeReferral o) {
        return getTimeCreated().compareTo(o.getTimeCreated());
    }
}

