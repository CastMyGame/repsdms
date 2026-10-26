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
    private final com.reps.demogcloud.services.WorkflowAccessService access;
    private final com.reps.demogcloud.services.UserContextService users;
    private final com.reps.demogcloud.data.StudentRepository students;

    @Override
    public List<TrackedBehaviorEvent> saveTrackedBehaviorEvents(List<TrackedBehaviorRequest> requests) {
        access.requireStaff();
        String actor=users.getCurrentStaffEmail();
        List<TrackedBehaviorEvent> eventsToSave = new ArrayList<>();

        if (requests == null || requests.isEmpty() || requests.size()>200) throw new IllegalArgumentException("Select 1-200 behavior events");

        LocalDateTime now = LocalDateTime.now();
        java.util.Set<List<String>> seen = new java.util.HashSet<>();

        for (TrackedBehaviorRequest request : requests) {
            if (request == null || request.getStudentEmail() == null || request.getStudentEmail().isBlank())
                throw new IllegalArgumentException("Student required");
            access.requireStudentAccess(request.getStudentEmail());
            var student=students.findByStudentEmailIgnoreCase(request.getStudentEmail());
            if(student==null || student.isArchived() || !access.sameSchool(student.getSchool())) denied();
            if((request.getTeacherEmail()!=null && !request.getTeacherEmail().isBlank() && !actor.equalsIgnoreCase(request.getTeacherEmail()))
                    || (request.getSchool()!=null && !request.getSchool().isBlank() && !student.getSchool().equalsIgnoreCase(request.getSchool()))) denied();
            if(request.getClassPeriod()!=null && request.getClassPeriod().length()>100) throw new IllegalArgumentException("Invalid class period");

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

            if(!seen.add(java.util.Arrays.asList(student.getStudentEmail().toLowerCase(java.util.Locale.ROOT),request.getBehaviorCode(),request.getConsequenceCode(),request.getClassPeriod())))
                throw new IllegalArgumentException("Duplicate behavior event in batch");
            TrackedBehaviorEvent event = TrackedBehaviorEvent.builder()
                    .studentEmail(student.getStudentEmail())
                    .teacherEmail(actor)
                    .school(student.getSchool())
                    .classPeriod(request.getClassPeriod())
                    .behaviorCode(request.getBehaviorCode())
                    .behaviorName(TrackedBehaviorType.getDisplayNameByCode(request.getBehaviorCode()))
                    .consequenceCode(request.getConsequenceCode())
                    .consequenceName(consequenceName(request.getConsequenceCode()))
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
        requireHistoryAccess(studentEmail);
        return visible(trackedBehaviorEventRepository.findByStudentEmailOrderByTimeCreatedDesc(studentEmail),List.of(studentEmail));
    }

    @Override
    public Map<String, Integer> getStudentTrackedBehaviorTotals(String studentEmail) {
        List<TrackedBehaviorEvent> events = getStudentTrackedBehaviorTimeline(studentEmail);

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

        if(request==null || request.getStudentEmails()==null || request.getStudentEmails().isEmpty() || request.getStudentEmails().size()>200)
            throw new IllegalArgumentException("Select 1-200 students");
        request.getStudentEmails().forEach(this::requireHistoryAccess);
        String school=users.getCurrentUserSchool();
        if(request.getSchool()!=null && !request.getSchool().isBlank() && !school.equalsIgnoreCase(request.getSchool())) denied();

        List<TrackedBehaviorEvent> events = trackedBehaviorEventRepository
                .findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                        school,
                        request.getStudentEmails()
                );
        events=visible(events,request.getStudentEmails());

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
        if(access.isStudentActor()) access.requireStudent(); else access.requireStaff();
        return TrackedBehaviorType.getAll().stream()
                .map(type -> TrackedBehaviorTypeResponse.builder()
                        .code(type.getCode())
                        .displayName(type.getDisplayName())
                        .consequences(type.getConsequences())
                        .build())
                .toList();
    }
    private void requireHistoryAccess(String email) {
        if(email==null || email.isBlank()) throw new IllegalArgumentException("Student identifier required");
        if(access.hasCurrentRole("ADMIN")) {
            access.requireStaff();
            var student=students.findByStudentEmailIgnoreCase(email);
            if(student==null || !access.sameSchool(student.getSchool()))denied();
        } else access.requireStudentAccess(email);
    }
    private List<TrackedBehaviorEvent> visible(List<TrackedBehaviorEvent> events,List<String> emails) {
        return events.stream().filter(e -> e.getSchool()!=null && access.sameSchool(e.getSchool())
                && e.getStudentEmail()!=null && emails.stream().anyMatch(e.getStudentEmail()::equalsIgnoreCase)).toList();
    }
    private String consequenceName(String code) {
        return java.util.Arrays.stream(code.toLowerCase(java.util.Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0))+word.substring(1)).collect(java.util.stream.Collectors.joining(" "));
    }
    private static void denied() { throw new org.springframework.security.access.AccessDeniedException("Access denied"); }
}