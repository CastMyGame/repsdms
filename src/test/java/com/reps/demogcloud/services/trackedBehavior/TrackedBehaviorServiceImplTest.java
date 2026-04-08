package com.reps.demogcloud.services.trackedBehavior;

import com.reps.demogcloud.data.TrackedBehaviorEventRepository;
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

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackedBehaviorServiceImplTest {

    @Mock
    private TrackedBehaviorEventRepository trackedBehaviorEventRepository;

    @InjectMocks
    private TrackedBehaviorServiceImpl trackedBehaviorService;

    private String validBehaviorCode;
    private String secondValidBehaviorCode;
    private String validConsequenceCode;
    private String secondValidConsequenceCode;

    @BeforeEach
    void setUp() {
        TrackedBehaviorType firstType = TrackedBehaviorType.getAll().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No tracked behavior types found"));

        validBehaviorCode = firstType.getCode();
        validConsequenceCode = firstType.getConsequences().stream()
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No consequences found for first tracked behavior type"));

        TrackedBehaviorType secondType = TrackedBehaviorType.getAll().stream()
                .filter(type -> !type.getCode().equals(validBehaviorCode))
                .findFirst()
                .orElse(firstType);

        secondValidBehaviorCode = secondType.getCode();
        secondValidConsequenceCode = secondType.getConsequences().stream()
                .findFirst()
                .orElse(validConsequenceCode);
    }

    @Test
    void saveTrackedBehaviorEvents_shouldReturnEmptyList_whenRequestsIsNull() {
        List<TrackedBehaviorEvent> result = trackedBehaviorService.saveTrackedBehaviorEvents(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void saveTrackedBehaviorEvents_shouldReturnEmptyList_whenRequestsIsEmpty() {
        List<TrackedBehaviorEvent> result = trackedBehaviorService.saveTrackedBehaviorEvents(List.of());

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void saveTrackedBehaviorEvents_shouldSkipNullRequest() {
        List<TrackedBehaviorEvent> result =
                trackedBehaviorService.saveTrackedBehaviorEvents(Collections.singletonList(null));

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void saveTrackedBehaviorEvents_shouldSkipRequest_whenStudentEmailIsNull() {
        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setStudentEmail(null);
        request.setTeacherEmail("teacher@test.com");
        request.setSchool("Test School");
        request.setClassPeriod("1st");
        request.setBehaviorCode(validBehaviorCode);
        request.setConsequenceCode(validConsequenceCode);
        request.setConsequenceName("Lunch Detention");

        List<TrackedBehaviorEvent> result =
                trackedBehaviorService.saveTrackedBehaviorEvents(List.of(request));

        assertNotNull(result);
        assertTrue(result.isEmpty());
        verifyNoInteractions(trackedBehaviorEventRepository);
    }

    @Test
    void saveTrackedBehaviorEvents_shouldSaveValidEvents() {
        TrackedBehaviorRequest request1 = new TrackedBehaviorRequest();
        request1.setStudentEmail("student1@test.com");
        request1.setTeacherEmail("teacher@test.com");
        request1.setSchool("Test School");
        request1.setClassPeriod("1st");
        request1.setBehaviorCode(validBehaviorCode);
        request1.setConsequenceCode(validConsequenceCode);
        request1.setConsequenceName("Lunch Detention");

        TrackedBehaviorRequest request2 = new TrackedBehaviorRequest();
        request2.setStudentEmail("student2@test.com");
        request2.setTeacherEmail("teacher@test.com");
        request2.setSchool("Test School");
        request2.setClassPeriod("2nd");
        request2.setBehaviorCode(secondValidBehaviorCode);
        request2.setConsequenceCode(secondValidConsequenceCode);
        request2.setConsequenceName("Parent Contact");

        when(trackedBehaviorEventRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<TrackedBehaviorEvent> result =
                trackedBehaviorService.saveTrackedBehaviorEvents(List.of(request1, request2));

        assertNotNull(result);
        assertEquals(2, result.size());

        TrackedBehaviorEvent firstEvent = result.get(0);
        assertEquals("student1@test.com", firstEvent.getStudentEmail());
        assertEquals("teacher@test.com", firstEvent.getTeacherEmail());
        assertEquals("Test School", firstEvent.getSchool());
        assertEquals("1st", firstEvent.getClassPeriod());
        assertEquals(validBehaviorCode, firstEvent.getBehaviorCode());
        assertEquals(TrackedBehaviorType.getDisplayNameByCode(validBehaviorCode), firstEvent.getBehaviorName());
        assertEquals(validConsequenceCode, firstEvent.getConsequenceCode());
        assertEquals("Lunch Detention", firstEvent.getConsequenceName());
        assertNotNull(firstEvent.getTimeCreated());

        TrackedBehaviorEvent secondEvent = result.get(1);
        assertEquals("student2@test.com", secondEvent.getStudentEmail());
        assertEquals(secondValidBehaviorCode, secondEvent.getBehaviorCode());
        assertEquals(secondValidConsequenceCode, secondEvent.getConsequenceCode());
        assertEquals("Parent Contact", secondEvent.getConsequenceName());
        assertNotNull(secondEvent.getTimeCreated());

        verify(trackedBehaviorEventRepository).saveAll(anyList());
    }

    @Test
    void saveTrackedBehaviorEvents_shouldUseSameTimestampForAllEventsInSingleCall() {
        TrackedBehaviorRequest request1 = new TrackedBehaviorRequest();
        request1.setStudentEmail("student1@test.com");
        request1.setTeacherEmail("teacher@test.com");
        request1.setSchool("Test School");
        request1.setClassPeriod("1st");
        request1.setBehaviorCode(validBehaviorCode);
        request1.setConsequenceCode(validConsequenceCode);
        request1.setConsequenceName("Lunch Detention");

        TrackedBehaviorRequest request2 = new TrackedBehaviorRequest();
        request2.setStudentEmail("student2@test.com");
        request2.setTeacherEmail("teacher@test.com");
        request2.setSchool("Test School");
        request2.setClassPeriod("1st");
        request2.setBehaviorCode(validBehaviorCode);
        request2.setConsequenceCode(validConsequenceCode);
        request2.setConsequenceName("Lunch Detention");

        when(trackedBehaviorEventRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<TrackedBehaviorEvent> result =
                trackedBehaviorService.saveTrackedBehaviorEvents(List.of(request1, request2));

        assertEquals(2, result.size());
        assertEquals(result.get(0).getTimeCreated(), result.get(1).getTimeCreated());
    }

    @Test
    void saveTrackedBehaviorEvents_shouldThrowException_whenBehaviorCodeIsInvalid() {
        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setStudentEmail("student1@test.com");
        request.setTeacherEmail("teacher@test.com");
        request.setSchool("Test School");
        request.setClassPeriod("1st");
        request.setBehaviorCode("INVALID_CODE");
        request.setConsequenceCode(validConsequenceCode);
        request.setConsequenceName("Lunch Detention");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> trackedBehaviorService.saveTrackedBehaviorEvents(List.of(request))
        );

        assertTrue(ex.getMessage().contains("Invalid tracked behavior code"));
        verify(trackedBehaviorEventRepository, never()).saveAll(anyList());
    }

    @Test
    void saveTrackedBehaviorEvents_shouldThrowException_whenConsequenceCodeIsInvalidForBehavior() {
        String invalidConsequenceCode = "INVALID_CONSEQUENCE";

        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setStudentEmail("student1@test.com");
        request.setTeacherEmail("teacher@test.com");
        request.setSchool("Test School");
        request.setClassPeriod("1st");
        request.setBehaviorCode(validBehaviorCode);
        request.setConsequenceCode(invalidConsequenceCode);
        request.setConsequenceName("Bad Consequence");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> trackedBehaviorService.saveTrackedBehaviorEvents(List.of(request))
        );

        assertTrue(ex.getMessage().contains("Invalid consequence for behavior"));
        assertTrue(ex.getMessage().contains("behaviorCode=" + validBehaviorCode));
        assertTrue(ex.getMessage().contains("consequenceCode=" + invalidConsequenceCode));
        verify(trackedBehaviorEventRepository, never()).saveAll(anyList());
    }

    @Test
    void saveTrackedBehaviorEvents_shouldSaveOnlyValidNonSkippedRequests() {
        TrackedBehaviorRequest skippedNoStudent = new TrackedBehaviorRequest();
        skippedNoStudent.setStudentEmail(null);
        skippedNoStudent.setTeacherEmail("teacher@test.com");
        skippedNoStudent.setSchool("Test School");
        skippedNoStudent.setClassPeriod("1st");
        skippedNoStudent.setBehaviorCode(validBehaviorCode);
        skippedNoStudent.setConsequenceCode(validConsequenceCode);
        skippedNoStudent.setConsequenceName("Lunch Detention");

        TrackedBehaviorRequest validRequest = new TrackedBehaviorRequest();
        validRequest.setStudentEmail("student1@test.com");
        validRequest.setTeacherEmail("teacher@test.com");
        validRequest.setSchool("Test School");
        validRequest.setClassPeriod("1st");
        validRequest.setBehaviorCode(validBehaviorCode);
        validRequest.setConsequenceCode(validConsequenceCode);
        validRequest.setConsequenceName("Lunch Detention");

        when(trackedBehaviorEventRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        List<TrackedBehaviorEvent> result =
                trackedBehaviorService.saveTrackedBehaviorEvents(
                        Arrays.asList(null, skippedNoStudent, validRequest)
                );

        assertEquals(1, result.size());
        assertEquals("student1@test.com", result.get(0).getStudentEmail());
        verify(trackedBehaviorEventRepository).saveAll(anyList());
    }

    @Test
    void saveTrackedBehaviorEvents_shouldPassBuiltEventsToRepository() {
        TrackedBehaviorRequest request = new TrackedBehaviorRequest();
        request.setStudentEmail("student1@test.com");
        request.setTeacherEmail("teacher@test.com");
        request.setSchool("Test School");
        request.setClassPeriod("4th");
        request.setBehaviorCode(validBehaviorCode);
        request.setConsequenceCode(validConsequenceCode);
        request.setConsequenceName("Lunch Detention");

        when(trackedBehaviorEventRepository.saveAll(anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        trackedBehaviorService.saveTrackedBehaviorEvents(List.of(request));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrackedBehaviorEvent>> captor = ArgumentCaptor.forClass(List.class);

        verify(trackedBehaviorEventRepository).saveAll(captor.capture());

        List<TrackedBehaviorEvent> savedEvents = captor.getValue();
        assertEquals(1, savedEvents.size());
        assertEquals("student1@test.com", savedEvents.get(0).getStudentEmail());
        assertEquals("teacher@test.com", savedEvents.get(0).getTeacherEmail());
        assertEquals("Test School", savedEvents.get(0).getSchool());
        assertEquals("4th", savedEvents.get(0).getClassPeriod());
        assertEquals(validBehaviorCode, savedEvents.get(0).getBehaviorCode());
        assertEquals(validConsequenceCode, savedEvents.get(0).getConsequenceCode());
        assertEquals("Lunch Detention", savedEvents.get(0).getConsequenceName());
    }

    @Test
    void getStudentTrackedBehaviorTimeline_shouldReturnRepositoryResult() {
        List<TrackedBehaviorEvent> expected = List.of(
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
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
        List<TrackedBehaviorEvent> events = List.of(
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .build(),
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .build(),
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(secondValidBehaviorCode)
                        .build()
        );

        when(trackedBehaviorEventRepository.findByStudentEmailOrderByTimeCreatedDesc("student1@test.com"))
                .thenReturn(events);

        Map<String, Integer> result =
                trackedBehaviorService.getStudentTrackedBehaviorTotals("student1@test.com");

        assertEquals(2, result.size());
        assertEquals(2, result.get(validBehaviorCode));
        assertEquals(1, result.get(secondValidBehaviorCode));
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
        TrackedBehaviorStudentTotalsRequest request = new TrackedBehaviorStudentTotalsRequest();
        request.setSchool("Test School");
        request.setStudentEmails(List.of("student1@test.com", "student2@test.com"));

        List<TrackedBehaviorEvent> events = List.of(
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .build(),
                TrackedBehaviorEvent.builder()
                        .studentEmail("student1@test.com")
                        .behaviorCode(validBehaviorCode)
                        .build(),
                TrackedBehaviorEvent.builder()
                        .studentEmail("student2@test.com")
                        .behaviorCode(secondValidBehaviorCode)
                        .build()
        );

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                "Test School",
                List.of("student1@test.com", "student2@test.com")
        )).thenReturn(events);

        Map<String, Map<String, Integer>> result =
                trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request);

        assertEquals(2, result.size());
        assertEquals(2, result.get("student1@test.com").get(validBehaviorCode));
        assertEquals(1, result.get("student2@test.com").get(secondValidBehaviorCode));
    }

    @Test
    void getTrackedBehaviorTotalsForStudents_shouldInitializeUnknownStudentFromReturnedEvents() {
        TrackedBehaviorStudentTotalsRequest request = new TrackedBehaviorStudentTotalsRequest();
        request.setSchool("Test School");
        request.setStudentEmails(List.of("student1@test.com"));

        List<TrackedBehaviorEvent> events = List.of(
                TrackedBehaviorEvent.builder()
                        .studentEmail("student2@test.com")
                        .behaviorCode(validBehaviorCode)
                        .build()
        );

        when(trackedBehaviorEventRepository.findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(
                "Test School",
                List.of("student1@test.com")
        )).thenReturn(events);

        Map<String, Map<String, Integer>> result =
                trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("student1@test.com"));
        assertTrue(result.containsKey("student2@test.com"));
        assertEquals(1, result.get("student2@test.com").get(validBehaviorCode));
    }

    @Test
    void getTrackedBehaviorTypes_shouldReturnAllTrackedBehaviorTypes() {
        List<TrackedBehaviorTypeResponse> result = trackedBehaviorService.getTrackedBehaviorTypes();

        assertNotNull(result);
        assertEquals(TrackedBehaviorType.getAll().size(), result.size());

        for (TrackedBehaviorTypeResponse response : result) {
            assertNotNull(response.getCode());
            assertNotNull(response.getDisplayName());
            assertNotNull(response.getConsequences());
        }
    }
}