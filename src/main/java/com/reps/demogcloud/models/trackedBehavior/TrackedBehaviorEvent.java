package com.reps.demogcloud.models.trackedBehavior;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "TrackedBehaviorEvents")
public class TrackedBehaviorEvent {
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String tenantSchoolId;
    @org.springframework.data.annotation.Version
    @com.fasterxml.jackson.annotation.JsonIgnore
    private Long persistenceRevision;

    @Id
    private String trackedBehaviorEventId;

    private String studentEmail;
    private String teacherEmail;
    private String school;
    private String classPeriod;

    private String behaviorCode;
    private String behaviorName;

    private String consequenceCode;
    private String consequenceName;

    private LocalDateTime timeCreated;
}
