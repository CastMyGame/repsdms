package com.reps.demogcloud.services.trackedBehavior;

import com.reps.demogcloud.data.TrackedBehaviorEventRepository;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorStudentTotalsRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorType;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorTypeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TrackedBehaviorServiceImpl implements TrackedBehaviorService {

    private final TrackedBehaviorEventRepository trackedBehaviorEventRepository;

    @Override
    public List<TrackedBehaviorEvent> saveTrackedBehaviorEvents(List<TrackedBehaviorRequest> requests) {
        List<TrackedBehaviorEvent> eventsToSave = new ArrayList<>();

        if (requests == null || requests.isEmpty()) {
            return eventsToSave;
        }

        LocalDateTime now = LocalDateTime.now();

        for (TrackedBehaviorRequest request : requests) {
            if (request == null || request.getStudentEmail() == null) {
                continue;
            }

            // Validate behavior
            if (!TrackedBehaviorType.isValidCode(request.getBehaviorCode())) {
                throw new IllegalArgumentException(
                        "Invalid tracked behavior code: " + request.getBehaviorCode()
                );
            }

            // Validate consequence belongs to behavior
            List<String> validConsequences =
                    TrackedBehaviorType.getConsequencesByCode(request.getBehaviorCode());

            if (!validConsequences.contains(request.getConsequenceCode())) {
                throw new IllegalArgumentException(
                        "Invalid consequence for behavior. behaviorCode="
                                + request.getBehaviorCode()
                                + ", consequenceCode="
                                + request.getConsequenceCode()
                );
            }

            TrackedBehaviorEvent event = TrackedBehaviorEvent.builder()
                    .studentEmail(request.getStudentEmail())
                    .teacherEmail(request.getTeacherEmail())
                    .school(request.getSchool())
                    .classPeriod(request.getClassPeriod())
                    .behaviorCode(request.getBehaviorCode())
                    .behaviorName(TrackedBehaviorType.getDisplayNameByCode(request.getBehaviorCode()))
                    .consequenceCode(request.getConsequenceCode())
                    .consequenceName(request.getConsequenceName())
                    .timeCreated(now)
                    .build();

            eventsToSave.add(event);
        }

        if (eventsToSave.isEmpty()) {
            return eventsToSave;
        }

        return trackedBehaviorEventRepository.saveAll(eventsToSave);
    }

    @Override
    public List<TrackedBehaviorEvent> getStudentTrackedBehaviorTimeline(String studentEmail) {
        return trackedBehaviorEventRepository.findByStudentEmailOrderByTimeCreatedDesc(studentEmail);
    }

    @Override
    public Map<String, Integer> getStudentTrackedBehaviorTotals(String studentEmail) {
        List<TrackedBehaviorEvent> events =
                trackedBehaviorEventRepository.findByStudentEmailOrderByTimeCreatedDesc(studentEmail);

        Map<String, Integer> totals = new LinkedHashMap<>();

        for (TrackedBehaviorEvent event : events) {
            totals.put(
                    event.getBehaviorCode(),
                    totals.getOrDefault(event.getBehaviorCode(), 0) + 1
            );
        }

        return totals;
    }

    @Override
    public Map<String, Map<String, Integer>> getTrackedBehaviorTotalsForStudents(
            TrackedBehaviorStudentTotalsRequest request
    ) {
        Map<String, Map<String, Integer>> totalsByStudent = new LinkedHashMap<>();

        if (request == null || request.getStudentEmails() == null || request.getStudentEmails().isEmpty()) {
            return totalsByStudent;
        }

        List<TrackedBehaviorEvent> events = trackedBehaviorEventRepository
                .findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                        request.getSchool(),
                        request.getStudentEmails()
                );

        for (String studentEmail : request.getStudentEmails()) {
            totalsByStudent.put(studentEmail, new LinkedHashMap<>());
        }

        for (TrackedBehaviorEvent event : events) {
            totalsByStudent.putIfAbsent(event.getStudentEmail(), new LinkedHashMap<>());

            Map<String, Integer> studentTotals = totalsByStudent.get(event.getStudentEmail());

            studentTotals.put(
                    event.getBehaviorCode(),
                    studentTotals.getOrDefault(event.getBehaviorCode(), 0) + 1
            );
        }

        for (Map<String, Integer> studentTotals : totalsByStudent.values()) {
            for (Map.Entry<String, Integer> entry : studentTotals.entrySet()) {
                if (entry.getValue() < 0) {
                    entry.setValue(0);
                }
            }
        }

        return totalsByStudent;
    }

    @Override
    public List<TrackedBehaviorTypeResponse> getTrackedBehaviorTypes() {
        return TrackedBehaviorType.getAll().stream()
                .map(type -> TrackedBehaviorTypeResponse.builder()
                        .code(type.getCode())
                        .displayName(type.getDisplayName())
                        .consequences(type.getConsequences())
                        .build())
                .toList();
    }
}