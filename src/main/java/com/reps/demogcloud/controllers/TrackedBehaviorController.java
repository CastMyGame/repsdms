package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorStudentTotalsRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorTypeResponse;
import com.reps.demogcloud.services.trackedBehavior.TrackedBehaviorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/tracked-behaviors/v1")
@RequiredArgsConstructor
public class TrackedBehaviorController {

    private final TrackedBehaviorService trackedBehaviorService;

    @PostMapping("/save")
    public ResponseEntity<List<TrackedBehaviorEvent>> saveTrackedBehaviorBatch(
            @RequestBody TrackedBehaviorRequest request
    ) {
        return ResponseEntity.ok(trackedBehaviorService.saveTrackedBehaviorBatch(request));
    }

    @GetMapping("/student/{studentEmail}/timeline")
    public ResponseEntity<List<TrackedBehaviorEvent>> getStudentTrackedBehaviorTimeline(
            @PathVariable String studentEmail
    ) {
        return ResponseEntity.ok(trackedBehaviorService.getStudentTrackedBehaviorTimeline(studentEmail));
    }

    @GetMapping("/student/{studentEmail}/totals")
    public ResponseEntity<Map<String, Integer>> getStudentTrackedBehaviorTotals(
            @PathVariable String studentEmail
    ) {
        return ResponseEntity.ok(trackedBehaviorService.getStudentTrackedBehaviorTotals(studentEmail));
    }

    @GetMapping("/types")
    public ResponseEntity<List<TrackedBehaviorTypeResponse>> getTrackedBehaviorTypes() {
        return ResponseEntity.ok(trackedBehaviorService.getTrackedBehaviorTypes());
    }

    @PostMapping("/students/totals")
    public ResponseEntity<Map<String, Map<String, Integer>>> getTrackedBehaviorTotalsForStudents(
            @RequestBody TrackedBehaviorStudentTotalsRequest request
    ) {
        return ResponseEntity.ok(trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request));
    }
}