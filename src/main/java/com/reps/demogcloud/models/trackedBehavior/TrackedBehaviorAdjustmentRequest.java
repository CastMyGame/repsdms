package com.reps.demogcloud.models.trackedBehavior;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class TrackedBehaviorAdjustmentRequest {

    private String studentEmail;

    private String behaviorCode;
    private String behaviorName;

    private int adjustmentValue;
}
