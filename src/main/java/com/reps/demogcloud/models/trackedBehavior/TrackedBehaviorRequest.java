package com.reps.demogcloud.models.trackedBehavior;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TrackedBehaviorRequest {

    private String teacherEmail;
    private String school;
    private String classPeriod;

    private List<TrackedBehaviorAdjustmentRequest> adjustments;
}
