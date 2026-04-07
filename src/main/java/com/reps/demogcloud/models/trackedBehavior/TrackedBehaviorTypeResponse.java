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
public class TrackedBehaviorTypeResponse {

    private String code;
    private String displayName;

    private List<String> consequences;
}
