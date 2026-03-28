package com.reps.demogcloud.services.trackedBehavior;

import com.reps.demogcloud.data.TrackedBehaviorEventRepository;
import com.reps.demogcloud.models.trackedBehavior.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class TrackedBehaviorServiceImpl implements TrackedBehaviorService {

    private final TrackedBehaviorEventRepository trackedBehaviorEventRepository;

    @Override
    public List<TrackedBehaviorEvent> saveTrackedBehaviorBatch(TrackedBehaviorRequest request) {
        List<TrackedBehaviorEvent> eventsToSave = new ArrayList<>();

        if (request == null || request.getAdjustments() == null || request.getAdjustments().isEmpty()) {
            return eventsToSave;
        }

        List<String> studentEmails = request.getAdjustments().stream()
                .filter(Objects::nonNull)
                .map(TrackedBehaviorAdjustmentRequest::getStudentEmail)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        List<TrackedBehaviorEvent> existingEvents =
                trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                        request.getSchool(),
                        studentEmails
                );

        Map<String, Map<String, Integer>> currentTotals = new HashMap<>();

        for (TrackedBehaviorEvent existingEvent : existingEvents) {
            currentTotals.putIfAbsent(existingEvent.getStudentEmail(), new HashMap<>());
            Map<String, Integer> studentTotals = currentTotals.get(existingEvent.getStudentEmail());

            studentTotals.put(
                    existingEvent.getBehaviorCode(),
                    studentTotals.getOrDefault(existingEvent.getBehaviorCode(), 0) + existingEvent.getAdjustmentValue()
            );
        }

        for (TrackedBehaviorAdjustmentRequest adjustment : request.getAdjustments()) {
            if (adjustment == null || adjustment.getAdjustmentValue() == 0) {
                continue;
            }

            if (!TrackedBehaviorType.isValidCode(adjustment.getBehaviorCode())) {
                throw new IllegalArgumentException(
                        "Invalid tracked behavior code: " + adjustment.getBehaviorCode()
                );
            }

            currentTotals.putIfAbsent(adjustment.getStudentEmail(), new HashMap<>());
            Map<String, Integer> studentTotals = currentTotals.get(adjustment.getStudentEmail());

            int currentValue = studentTotals.getOrDefault(adjustment.getBehaviorCode(), 0);
            int newValue = currentValue + adjustment.getAdjustmentValue();

            if (newValue < 0) {
                throw new IllegalArgumentException(
                        "Tracked behavior total cannot go below zero for studentEmail="
                                + adjustment.getStudentEmail()
                                + ", behaviorCode="
                                + adjustment.getBehaviorCode()
                );
            }

            studentTotals.put(adjustment.getBehaviorCode(), newValue);
        }

        LocalDateTime now = LocalDateTime.now();

        for (TrackedBehaviorAdjustmentRequest adjustment : request.getAdjustments()) {
            if (adjustment == null || adjustment.getAdjustmentValue() == 0) {
                continue;
            }

            TrackedBehaviorEvent event = TrackedBehaviorEvent.builder()
                    .studentEmail(adjustment.getStudentEmail())
                    .teacherEmail(request.getTeacherEmail())
                    .school(request.getSchool())
                    .classPeriod(request.getClassPeriod())
                    .behaviorCode(adjustment.getBehaviorCode())
                    .behaviorName(TrackedBehaviorType.getDisplayNameByCode(adjustment.getBehaviorCode()))
                    .adjustmentValue(adjustment.getAdjustmentValue())
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
                    totals.getOrDefault(event.getBehaviorCode(), 0) + event.getAdjustmentValue()
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
                    studentTotals.getOrDefault(event.getBehaviorCode(), 0) + event.getAdjustmentValue()
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
                        .build())
                .toList();
    }
}
