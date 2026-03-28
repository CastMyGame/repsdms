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

    @Id
    private String trackedBehaviorEventId;

    private String studentEmail;
    private String teacherEmail;
    private String school;
    private String classPeriod;

    private String behaviorCode;
    private String behaviorName;

    private int adjustmentValue;

    private LocalDateTime timeCreated;
}
