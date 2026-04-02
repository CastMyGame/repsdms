package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorStudentTotalsRequest;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorTypeResponse;
import com.reps.demogcloud.services.trackedBehavior.TrackedBehaviorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TrackedBehaviorControllerTest {

    @Mock
    private TrackedBehaviorService trackedBehaviorService;

    @InjectMocks
    private TrackedBehaviorController trackedBehaviorController;

    @Test
    void saveTrackedBehaviorBatch_shouldReturnOkAndSavedEvents() {
        TrackedBehaviorRequest request = new TrackedBehaviorRequest();

        TrackedBehaviorEvent event1 = new TrackedBehaviorEvent();
        TrackedBehaviorEvent event2 = new TrackedBehaviorEvent();
        List<TrackedBehaviorEvent> savedEvents = List.of(event1, event2);

        when(trackedBehaviorService.saveTrackedBehaviorBatch(request)).thenReturn(savedEvents);

        ResponseEntity<List<TrackedBehaviorEvent>> response =
                trackedBehaviorController.saveTrackedBehaviorBatch(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertSame(savedEvents, response.getBody());

        verify(trackedBehaviorService).saveTrackedBehaviorBatch(request);
    }

    @Test
    void saveTrackedBehaviorBatch_shouldReturnOkAndEmptyList_whenNoEventsSaved() {
        TrackedBehaviorRequest request = new TrackedBehaviorRequest();

        when(trackedBehaviorService.saveTrackedBehaviorBatch(request)).thenReturn(List.of());

        ResponseEntity<List<TrackedBehaviorEvent>> response =
                trackedBehaviorController.saveTrackedBehaviorBatch(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(trackedBehaviorService).saveTrackedBehaviorBatch(request);
    }

    @Test
    void getStudentTrackedBehaviorTimeline_shouldReturnOkAndTimeline() {
        String studentEmail = "student@test.com";

        TrackedBehaviorEvent event1 = new TrackedBehaviorEvent();
        TrackedBehaviorEvent event2 = new TrackedBehaviorEvent();
        List<TrackedBehaviorEvent> timeline = List.of(event1, event2);

        when(trackedBehaviorService.getStudentTrackedBehaviorTimeline(studentEmail)).thenReturn(timeline);

        ResponseEntity<List<TrackedBehaviorEvent>> response =
                trackedBehaviorController.getStudentTrackedBehaviorTimeline(studentEmail);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertSame(timeline, response.getBody());

        verify(trackedBehaviorService).getStudentTrackedBehaviorTimeline(studentEmail);
    }

    @Test
    void getStudentTrackedBehaviorTimeline_shouldReturnOkAndEmptyList_whenNoTimelineExists() {
        String studentEmail = "student@test.com";

        when(trackedBehaviorService.getStudentTrackedBehaviorTimeline(studentEmail)).thenReturn(List.of());

        ResponseEntity<List<TrackedBehaviorEvent>> response =
                trackedBehaviorController.getStudentTrackedBehaviorTimeline(studentEmail);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(trackedBehaviorService).getStudentTrackedBehaviorTimeline(studentEmail);
    }

    @Test
    void getStudentTrackedBehaviorTotals_shouldReturnOkAndTotals() {
        String studentEmail = "student@test.com";

        Map<String, Integer> totals = new HashMap<>();
        totals.put("positive", 5);
        totals.put("negative", 2);

        when(trackedBehaviorService.getStudentTrackedBehaviorTotals(studentEmail)).thenReturn(totals);

        ResponseEntity<Map<String, Integer>> response =
                trackedBehaviorController.getStudentTrackedBehaviorTotals(studentEmail);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertEquals(5, response.getBody().get("positive"));
        assertEquals(2, response.getBody().get("negative"));

        verify(trackedBehaviorService).getStudentTrackedBehaviorTotals(studentEmail);
    }

    @Test
    void getStudentTrackedBehaviorTotals_shouldReturnOkAndEmptyMap_whenNoTotalsExist() {
        String studentEmail = "student@test.com";

        when(trackedBehaviorService.getStudentTrackedBehaviorTotals(studentEmail)).thenReturn(Map.of());

        ResponseEntity<Map<String, Integer>> response =
                trackedBehaviorController.getStudentTrackedBehaviorTotals(studentEmail);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(trackedBehaviorService).getStudentTrackedBehaviorTotals(studentEmail);
    }

    @Test
    void getTrackedBehaviorTypes_shouldReturnOkAndTypes() {
        TrackedBehaviorTypeResponse type1 = new TrackedBehaviorTypeResponse();
        TrackedBehaviorTypeResponse type2 = new TrackedBehaviorTypeResponse();
        List<TrackedBehaviorTypeResponse> types = List.of(type1, type2);

        when(trackedBehaviorService.getTrackedBehaviorTypes()).thenReturn(types);

        ResponseEntity<List<TrackedBehaviorTypeResponse>> response =
                trackedBehaviorController.getTrackedBehaviorTypes();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertSame(types, response.getBody());

        verify(trackedBehaviorService).getTrackedBehaviorTypes();
    }

    @Test
    void getTrackedBehaviorTypes_shouldReturnOkAndEmptyList_whenNoTypesExist() {
        when(trackedBehaviorService.getTrackedBehaviorTypes()).thenReturn(List.of());

        ResponseEntity<List<TrackedBehaviorTypeResponse>> response =
                trackedBehaviorController.getTrackedBehaviorTypes();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(trackedBehaviorService).getTrackedBehaviorTypes();
    }

    @Test
    void getTrackedBehaviorTotalsForStudents_shouldReturnOkAndTotalsMap() {
        TrackedBehaviorStudentTotalsRequest request = new TrackedBehaviorStudentTotalsRequest();

        Map<String, Map<String, Integer>> totalsByStudent = new HashMap<>();
        Map<String, Integer> studentTotals = new HashMap<>();
        studentTotals.put("positive", 3);
        studentTotals.put("negative", 1);
        totalsByStudent.put("student1@test.com", studentTotals);

        when(trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request))
                .thenReturn(totalsByStudent);

        ResponseEntity<Map<String, Map<String, Integer>>> response =
                trackedBehaviorController.getTrackedBehaviorTotalsForStudents(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(1, response.getBody().size());
        assertTrue(response.getBody().containsKey("student1@test.com"));
        assertEquals(3, response.getBody().get("student1@test.com").get("positive"));
        assertEquals(1, response.getBody().get("student1@test.com").get("negative"));

        verify(trackedBehaviorService).getTrackedBehaviorTotalsForStudents(request);
    }

    @Test
    void getTrackedBehaviorTotalsForStudents_shouldReturnOkAndEmptyMap_whenNoStudentTotalsExist() {
        TrackedBehaviorStudentTotalsRequest request = new TrackedBehaviorStudentTotalsRequest();

        when(trackedBehaviorService.getTrackedBehaviorTotalsForStudents(request)).thenReturn(Map.of());

        ResponseEntity<Map<String, Map<String, Integer>>> response =
                trackedBehaviorController.getTrackedBehaviorTotalsForStudents(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(trackedBehaviorService).getTrackedBehaviorTotalsForStudents(request);
    }
}