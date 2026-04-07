package com.reps.demogcloud.services.trackedBehavior;

import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorStudentTotalsRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorTypeResponse;

import java.util.List;
import java.util.Map;

public interface TrackedBehaviorService {

    List<TrackedBehaviorEvent> saveTrackedBehaviorEvents(List<TrackedBehaviorRequest> requests);

    List<TrackedBehaviorEvent> getStudentTrackedBehaviorTimeline(String studentEmail);

    Map<String, Integer> getStudentTrackedBehaviorTotals(String studentEmail);

    Map<String, Map<String, Integer>> getTrackedBehaviorTotalsForStudents(
            TrackedBehaviorStudentTotalsRequest request
    );

    List<TrackedBehaviorTypeResponse> getTrackedBehaviorTypes();
}