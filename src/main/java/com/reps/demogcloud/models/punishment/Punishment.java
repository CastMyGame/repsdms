package com.reps.demogcloud.models.punishment;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

import java.time.LocalDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.*;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Builder
@Document(collection = "Punishments")
public class Punishment implements Comparable<Punishment>, org.springframework.data.domain.Persistable<String>{
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String tenantSchoolId;

    @org.springframework.data.annotation.Version
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long revision;
    @org.springframework.data.annotation.Transient
    @com.fasterxml.jackson.annotation.JsonIgnore
    private boolean persisted;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String originalAuthorEmail;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String responsibleStaffEmail;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String conversationId;
    @com.fasterxml.jackson.annotation.JsonIgnore
    @lombok.ToString.Exclude
    private java.util.List<com.reps.demogcloud.models.email.RequiredNotification> requiredNotifications;
    @Override @org.springframework.data.annotation.Transient @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isNew() { return !persisted; }
    @Override @org.springframework.data.annotation.Transient @com.fasterxml.jackson.annotation.JsonIgnore
    public String getId() { return punishmentId; }

    @Id
    private String punishmentId;
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private String currencyOperationId;
    @com.fasterxml.jackson.annotation.JsonProperty(access=com.fasterxml.jackson.annotation.JsonProperty.Access.READ_ONLY)
    private Integer currencyAwarded;

    private String studentEmail;
    private String school;
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
    private boolean archived = false;
    private boolean stateFiled = false;
    private String stateIncidentNumber;
    private String archivedBy;
    private String archivedExplanation;
    private LocalDate archivedOn;
    private int mapIndex = 0;
    private Map<Date,List<String>> answerHistory;
    private List<String> infractionDescription;

    // 🔹 NEW: which assignment template this punishment uses
    private String assignmentTemplateId;
    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @lombok.ToString.Exclude
    private com.reps.demogcloud.models.assignments.AssignmentWork assignmentWork;



    public void setAnswerHistory(Date date, List<String> context) {
        if (answerHistory == null) {
            answerHistory = new HashMap<>();
        }

        answerHistory.put(date, context);
    }



    public int compareTo(Punishment o) {
        return getTimeCreated().compareTo(o.getTimeCreated());
    }
}
