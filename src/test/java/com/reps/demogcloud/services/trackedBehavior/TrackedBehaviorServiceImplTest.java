package com.reps.demogcloud.services.trackedBehavior;

import com.reps.demogcloud.data.TrackedBehaviorEventRepository;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorAdjustmentRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorStudentTotalsRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorType;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorTypeResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackedBehaviorServiceImplTest {

    @Mock
    private TrackedBehaviorEventRepository trackedBehaviorEventRepository;

    @InjectMocks
    private TrackedBehaviorServiceImpl trackedBehaviorService;

    private String validBehaviorCode;

    @BeforeEach
    void setUp() {
        validBehaviorCode = TrackedBehaviorType.getAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tracked behavior types found"))
                .getCode();
    }

    @Test
    void saveTrackedBehaviorBatch_shouldReturnEmptyList_whenRequestIsNull() {
        List<TrackedBehaviorEvent> result = trackedBehaviorService.saveTrackedBehaviorBatch(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void saveTrackedBehaviorBatch_shouldReturnEmptyList_whenAdjustmentsAreNull() {
        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setAdjustments(null);

        List<TrackedBehaviorEvent> result = trackedBehaviorService.saveTrackedBehaviorBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void saveTrackedBehaviorBatch_shouldReturnEmptyList_whenAdjustmentsAreEmpty() {
        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setAdjustments(new ArrayList<>());

        List<TrackedBehaviorEvent> result = trackedBehaviorService.saveTrackedBehaviorBatch(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void saveTrackedBehaviorBatch_shouldSkipNullAndZeroAdjustments_andSaveValidEvents() {
        TrackedBehaviorAdjustmentRequest validAdjustment = new TrackedBehaviorAdjustmentRequest();
        validAdjustment.setStudentEmail("student1@test.com");
        validAdjustment.setBehaviorCode(validBehaviorCode);
        validAdjustment.setAdjustmentValue(2);

        TrackedBehaviorAdjustmentRequest zeroAdjustment = new TrackedBehaviorAdjustmentRequest();
        zeroAdjustment.setStudentEmail("student2@test.com");
        zeroAdjustment.setBehaviorCode(validBehaviorCode);
        zeroAdjustment.setAdjustmentValue(0);

        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setSchool("Test School");
        request.setTeacherEmail("teacher@test.com");
        request.setClassPeriod("1st");

        List<TrackedBehaviorAdjustmentRequest> adjustments = new ArrayList<>();
        adjustments.add(null);
        adjustments.add(zeroAdjustment);
        adjustments.add(validAdjustment);
        request.setAdjustments(adjustments);

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                eq("Test School"),
                anyList()
        )).thenReturn(List.of());

        when(trackedBehaviorEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<TrackedBehaviorEvent> result = trackedBehaviorService.saveTrackedBehaviorBatch(request);

        assertNotNull(result);
        assertEquals(1, result.size());

        TrackedBehaviorEvent savedEvent = result.get(0);
        assertEquals("student1@test.com", savedEvent.getStudentEmail());
        assertEquals("teacher@test.com", savedEvent.getTeacherEmail());
        assertEquals("Test School", savedEvent.getSchool());
        assertEquals("1st", savedEvent.getClassPeriod());
        assertEquals(validBehaviorCode, savedEvent.getBehaviorCode());
        assertEquals(2, savedEvent.getAdjustmentValue());
        assertNotNull(savedEvent.getBehaviorName());
        assertNotNull(savedEvent.getTimeCreated());

        verify(trackedBehaviorEventRepository).saveAll(anyList());
    }

    @Test
    void saveTrackedBehaviorBatch_shouldThrowException_whenBehaviorCodeIsInvalid() {
        TrackedBehaviorAdjustmentRequest invalidAdjustment = new TrackedBehaviorAdjustmentRequest();
        invalidAdjustment.setStudentEmail("student1@test.com");
        invalidAdjustment.setBehaviorCode("INVALID_CODE");
        invalidAdjustment.setAdjustmentValue(1);

        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setSchool("Test School");
        request.setTeacherEmail("teacher@test.com");
        request.setClassPeriod("1st");
        request.setAdjustments(List.of(invalidAdjustment));

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                eq("Test School"),
                anyList()
        )).thenReturn(List.of());

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> trackedBehaviorService.saveTrackedBehaviorBatch(request)
        );

        assertTrue(ex.getMessage().contains("Invalid tracked behavior code"));
        verify(trackedBehaviorEventRepository, never()).saveAll(anyList());
    }

    @Test
    void saveTrackedBehaviorBatch_shouldThrowException_whenAdjustmentWouldMakeTotalNegative() {
        TrackedBehaviorEvent existingEvent = TrackedBehaviorEvent.builder()
                .studentEmail("student1@test.com")
                .school("Test School")
                .behaviorCode(validBehaviorCode)
                .adjustmentValue(1)
                .timeCreated(LocalDateTime.now())
                .build();

        TrackedBehaviorAdjustmentRequest adjustment = new TrackedBehaviorAdjustmentRequest();
        adjustment.setStudentEmail("student1@test.com");
        adjustment.setBehaviorCode(validBehaviorCode);
        adjustment.setAdjustmentValue(-2);

        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setSchool("Test School");
        request.setTeacherEmail("teacher@test.com");
        request.setClassPeriod("1st");
        request.setAdjustments(List.of(adjustment));

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                eq("Test School"),
                anyList()
        )).thenReturn(List.of(existingEvent));

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> trackedBehaviorService.saveTrackedBehaviorBatch(request)
        );

        assertTrue(ex.getMessage().contains("Tracked behavior total cannot go below zero"));
        verify(trackedBehaviorEventRepository, never()).saveAll(anyList());
    }

    @Test
    void saveTrackedBehaviorBatch_shouldAllowNegativeAdjustment_whenItDoesNotMakeTotalNegative() {
        TrackedBehaviorEvent existingEvent = TrackedBehaviorEvent.builder()
                .studentEmail("student1@test.com")
                .school("Test School")
                .behaviorCode(validBehaviorCode)
                .adjustmentValue(3)
                .timeCreated(LocalDateTime.now())
                .build();

        TrackedBehaviorAdjustmentRequest adjustment = new TrackedBehaviorAdjustmentRequest();
        adjustment.setStudentEmail("student1@test.com");
        adjustment.setBehaviorCode(validBehaviorCode);
        adjustment.setAdjustmentValue(-1);

        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setSchool("Test School");
        request.setTeacherEmail("teacher@test.com");
        request.setClassPeriod("2nd");
        request.setAdjustments(List.of(adjustment));

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                eq("Test School"),
                anyList()
        )).thenReturn(List.of(existingEvent));

        when(trackedBehaviorEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<TrackedBehaviorEvent> result = trackedBehaviorService.saveTrackedBehaviorBatch(request);

        assertEquals(1, result.size());
        assertEquals(-1, result.get(0).getAdjustmentValue());
        verify(trackedBehaviorEventRepository).saveAll(anyList());
    }

    @Test
    void saveTrackedBehaviorBatch_shouldUseDistinctNonNullStudentEmails_whenLookingUpExistingEvents() {
        TrackedBehaviorAdjustmentRequest adjustment1 = new TrackedBehaviorAdjustmentRequest();
        adjustment1.setStudentEmail("student1@test.com");
        adjustment1.setBehaviorCode(validBehaviorCode);
        adjustment1.setAdjustmentValue(1);

        TrackedBehaviorAdjustmentRequest adjustment2 = new TrackedBehaviorAdjustmentRequest();
        adjustment2.setStudentEmail("student1@test.com");
        adjustment2.setBehaviorCode(validBehaviorCode);
        adjustment2.setAdjustmentValue(1);

        TrackedBehaviorAdjustmentRequest adjustment3 = new TrackedBehaviorAdjustmentRequest();
        adjustment3.setStudentEmail(null);
        adjustment3.setBehaviorCode(validBehaviorCode);
        adjustment3.setAdjustmentValue(1);

        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setSchool("Test School");
        request.setTeacherEmail("teacher@test.com");
        request.setClassPeriod("3rd");
        request.setAdjustments(List.of(adjustment1, adjustment2, adjustment3));

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                eq("Test School"),
                anyList()
        )).thenReturn(List.of());

        when(trackedBehaviorEventRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        trackedBehaviorService.saveTrackedBehaviorBatch(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);

        verify(trackedBehaviorEventRepository).findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                eq("Test School"),
                captor.capture()
        );

        List<String> capturedStudentEmails = captor.getValue();
        assertEquals(1, capturedStudentEmails.size());
        assertEquals("student1@test.com", capturedStudentEmails.get(0));
    }

    @Test
    void getStudentTrackedBehaviorTimeline_shouldReturnRepositoryResult() {
        List<TrackedBehaviorEvent> expected = List.of(
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .adjustmentValue(1)
                        .build()
        );

        when(trackedBehaviorEventRepository.findByStudentEmailOrderByTimeCreatedDesc("student1@test.com"))
                .thenReturn(expected);

        List<TrackedBehaviorEvent> result =
                trackedBehaviorService.getStudentTrackedBehaviorTimeline("student1@test.com");

        assertEquals(expected, result);
        verify(trackedBehaviorEventRepository).findByStudentEmailOrderByTimeCreatedDesc("student1@test.com");
    }

    @Test
    void getStudentTrackedBehaviorTotals_shouldReturnEmptyMap_whenNoEventsFound() {
        when(trackedBehaviorEventRepository.findByStudentEmailOrderByTimeCreatedDesc("student1@test.com"))
                .thenReturn(List.of());

        Map<String, Integer> result =
                trackedBehaviorService.getStudentTrackedBehaviorTotals("student1@test.com");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getStudentTrackedBehaviorTotals_shouldAggregateTotalsByBehaviorCode() {
        String secondCode = TrackedBehaviorType.getAll().stream()
                .map(TrackedBehaviorType::getCode)
                .filter(code -> !code.equals(validBehaviorCode))
                .findFirst()
                .orElse(validBehaviorCode + "_SECOND");

        List<TrackedBehaviorEvent> events = List.of(
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .adjustmentValue(2)
                        .build(),
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .adjustmentValue(-1)
                        .build(),
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(secondCode)
                        .adjustmentValue(3)
                        .build()
        );

        when(trackedBehaviorEventRepository.findByStudentEmailOrderByTimeCreatedDesc("student1@test.com"))
                .thenReturn(events);

        Map<String, Integer> result =
                trackedBehaviorService.getStudentTrackedBehaviorTotals("student1@test.com");

        assertEquals(2, result.size());
        assertEquals(1, result.get(validBehaviorCode));
        assertEquals(3, result.get(secondCode));
    }

    @Test
    void getTrackedBehaviorTotalsForStudents_shouldReturnEmptyMap_whenRequestIsNull() {
        Map<String, Map<String, Integer>> result =
                trackedBehaviorService.getTrackedBehaviorTotalsForStudents(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void getTrackedBehaviorTotalsForStudents_shouldReturnEmptyMap_whenStudentEmailsAreNull() {
        TrackedBehaviorStudentTotalsRequest request = new TrackedBehaviorStudentTotalsRequest();
        request.setStudentEmails(null);

        Map<String, Map<String, Integer>> result =
                trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void getTrackedBehaviorTotalsForStudents_shouldReturnEmptyMap_whenStudentEmailsAreEmpty() {
        TrackedBehaviorStudentTotalsRequest request = new TrackedBehaviorStudentTotalsRequest();
        request.setStudentEmails(List.of());

        Map<String, Map<String, Integer>> result =
                trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void getTrackedBehaviorTotalsForStudents_shouldInitializeAllRequestedStudentsEvenWithoutEvents() {
        TrackedBehaviorStudentTotalsRequest request = new TrackedBehaviorStudentTotalsRequest();
        request.setSchool("Test School");
        request.setStudentEmails(List.of("student1@test.com", "student2@test.com"));

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                "Test School",
                List.of("student1@test.com", "student2@test.com")
        )).thenReturn(List.of());

        Map<String, Map<String, Integer>> result =
                trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("student1@test.com"));
        assertTrue(result.containsKey("student2@test.com"));
        assertTrue(result.get("student1@test.com").isEmpty());
        assertTrue(result.get("student2@test.com").isEmpty());
    }

    @Test
    void getTrackedBehaviorTotalsForStudents_shouldAggregateTotalsPerStudent() {
        String secondCode = TrackedBehaviorType.getAll().stream()
                .map(TrackedBehaviorType::getCode)
                .filter(code -> !code.equals(validBehaviorCode))
                .findFirst()
                .orElse(validBehaviorCode + "_SECOND");

        TrackedBehaviorStudentTotalsRequest request = new TrackedBehaviorStudentTotalsRequest();
        request.setSchool("Test School");
        request.setStudentEmails(List.of("student1@test.com", "student2@test.com"));

        List<TrackedBehaviorEvent> events = List.of(
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .adjustmentValue(2)
                        .build(),
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .adjustmentValue(-1)
                        .build(),
                TrackedBehaviorEvent.builder()
                        .studentEmail("student2@test.com")
                        .behaviorCode(secondCode)
                        .adjustmentValue(5)
                        .build()
        );

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                "Test School",
                List.of("student1@test.com", "student2@test.com")
        )).thenReturn(events);

        Map<String, Map<String, Integer>> result =
                trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request);

        assertEquals(2, result.size());
        assertEquals(1, result.get("student1@test.com").get(validBehaviorCode));
        assertEquals(5, result.get("student2@test.com").get(secondCode));
    }

    @Test
    void getTrackedBehaviorTotalsForStudents_shouldClampNegativeTotalsToZero() {
        TrackedBehaviorStudentTotalsRequest request = new TrackedBehaviorStudentTotalsRequest();
        request.setSchool("Test School");
        request.setStudentEmails(List.of("student1@test.com"));

        List<TrackedBehaviorEvent> events = List.of(
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .adjustmentValue(-3)
                        .build()
        );

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                "Test School",
                List.of("student1@test.com")
        )).thenReturn(events);

        Map<String, Map<String, Integer>> result =
                trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request);

        assertEquals(0, result.get("student1@test.com").get(validBehaviorCode));
    }

    @Test
    void getTrackedBehaviorTypes_shouldReturnAllTrackedBehaviorTypes() {
        List<TrackedBehaviorTypeResponse> result = trackedBehaviorService.getTrackedBehaviorTypes();

        assertNotNull(result);
        assertEquals(TrackedBehaviorType.getAll().size(), result.size());

        for (TrackedBehaviorTypeResponse response : result) {
            assertNotNull(response.getCode());
            assertNotNull(response.getDisplayName());
        }
    }
}
