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
public class GuidanceReferral implements org.springframework.data.domain.Persistable<String> {

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
    public String getId() { return guidanceId; }

    @Id
    private String guidanceId;
    private boolean archived;
    private LocalDate archivedOn;
    private String archivedBy;
    private String requestedByEmail;
    private String studentEmail;
    private String school;
    private LocalDate timeCreated;
    private LocalDate timeClosed;
    private String classPeriod;
    private String teacherEmail;
    private String guidanceEmail;
    private List<String> referralDescription;
    private String status;
    private List<ThreadEvent> notesArray;
    private String linkToPunishment;
    private LocalDate followUpDate;
    private String infractionName;



}
