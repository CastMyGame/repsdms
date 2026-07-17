package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.assignments.AssignmentTemplateBinding;
import com.reps.demogcloud.models.dto.ClearTeacherDefaultBindingRequest;
import com.reps.demogcloud.models.dto.SetTeacherDefaultBindingRequest;
import com.reps.demogcloud.services.AssignmentService;
import com.reps.demogcloud.services.UserContextService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AssignmentBindingControllerTest {

    @Mock
    private AssignmentService assignmentService;

    @Mock
    private UserContextService userContextService;

    @InjectMocks
    private AssignmentBindingController assignmentBindingController;

    @BeforeEach
    void setUp() {
        lenient().when(userContextService.hasRole("ADMIN")).thenReturn(true);
    }

    @Test
    void setTeacherDefault_shouldReturnOkAndBinding_whenRequestIsValid() throws Exception {
        SetTeacherDefaultBindingRequest request = new SetTeacherDefaultBindingRequest();
        request.setTeacherEmail("teacher@test.com");
        request.setSchoolId("school-1");
        request.setInfractionName("Tardy");
        request.setLevel(2);
        request.setAssignmentTemplateId("template-123");

        AssignmentTemplateBinding binding = new AssignmentTemplateBinding();

        when(assignmentService.setTeacherDefaultTemplate(
                "teacher@test.com",
                "school-1",
                "Tardy",
                2,
                "template-123"
        )).thenReturn(binding);

        ResponseEntity<AssignmentTemplateBinding> response =
                assignmentBindingController.setTeacherDefault(request);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertSame(binding, response.getBody());

        verify(assignmentService).setTeacherDefaultTemplate(
                "teacher@test.com",
                "school-1",
                "Tardy",
                2,
                "template-123"
        );
    }

    @Test
    void setTeacherDefault_shouldPropagateException_whenServiceThrows() throws Exception {
        SetTeacherDefaultBindingRequest request = new SetTeacherDefaultBindingRequest();
        request.setTeacherEmail("teacher@test.com");
        request.setSchoolId("school-1");
        request.setInfractionName("Tardy");
        request.setLevel(2);
        request.setAssignmentTemplateId("template-123");

        Exception exception = new Exception("Service failure");

        when(assignmentService.setTeacherDefaultTemplate(
                "teacher@test.com",
                "school-1",
                "Tardy",
                2,
                "template-123"
        )).thenThrow(exception);

        Exception thrown = assertThrows(
                Exception.class,
                () -> assignmentBindingController.setTeacherDefault(request)
        );

        assertEquals("Service failure", thrown.getMessage());

        verify(assignmentService).setTeacherDefaultTemplate(
                "teacher@test.com",
                "school-1",
                "Tardy",
                2,
                "template-123"
        );
    }

    @Test
    void clearTeacherDefault_shouldReturnNoContent_andCallService() {
        ClearTeacherDefaultBindingRequest request = new ClearTeacherDefaultBindingRequest();
        request.setTeacherEmail("teacher@test.com");
        request.setInfractionName("Disrespect");
        request.setLevel(3);

        ResponseEntity<Void> response =
                assignmentBindingController.clearTeacherDefault(request);

        assertNotNull(response);
        assertEquals(HttpStatus.NO_CONTENT, response.getStatusCode());
        assertNull(response.getBody());

        verify(assignmentService).clearTeacherDefaultTemplate(
                "teacher@test.com",
                "Disrespect",
                3
        );
    }

    @Test
    void getTeacherDefaults_shouldReturnOkAndBindings() {
        String teacherEmail = "teacher@test.com";

        AssignmentTemplateBinding binding1 = new AssignmentTemplateBinding();
        AssignmentTemplateBinding binding2 = new AssignmentTemplateBinding();
        List<AssignmentTemplateBinding> bindings = List.of(binding1, binding2);

        when(assignmentService.getActiveBindingsForTeacher(teacherEmail))
                .thenReturn(bindings);

        ResponseEntity<List<AssignmentTemplateBinding>> response =
                assignmentBindingController.getTeacherDefaults(teacherEmail);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertSame(bindings, response.getBody());

        verify(assignmentService).getActiveBindingsForTeacher(teacherEmail);
    }

    @Test
    void getTeacherDefaults_shouldReturnOkAndEmptyList_whenNoBindingsExist() {
        String teacherEmail = "teacher@test.com";

        when(assignmentService.getActiveBindingsForTeacher(teacherEmail))
                .thenReturn(List.of());

        ResponseEntity<List<AssignmentTemplateBinding>> response =
                assignmentBindingController.getTeacherDefaults(teacherEmail);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(assignmentService).getActiveBindingsForTeacher(teacherEmail);
    }

    @Test
    void getBindingsForTemplate_shouldReturnOkAndBindings() {
        String templateId = "template-123";

        AssignmentTemplateBinding binding1 = new AssignmentTemplateBinding();
        AssignmentTemplateBinding binding2 = new AssignmentTemplateBinding();
        List<AssignmentTemplateBinding> bindings = List.of(binding1, binding2);

        when(assignmentService.getActiveBindingsForTemplate(templateId))
                .thenReturn(bindings);

        ResponseEntity<List<AssignmentTemplateBinding>> response =
                assignmentBindingController.getBindingsForTemplate(templateId);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals(2, response.getBody().size());
        assertSame(bindings, response.getBody());

        verify(assignmentService).getActiveBindingsForTemplate(templateId);
    }

    @Test
    void getBindingsForTemplate_shouldReturnOkAndEmptyList_whenNoBindingsExist() {
        String templateId = "template-123";

        when(assignmentService.getActiveBindingsForTemplate(templateId))
                .thenReturn(List.of());

        ResponseEntity<List<AssignmentTemplateBinding>> response =
                assignmentBindingController.getBindingsForTemplate(templateId);

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().isEmpty());

        verify(assignmentService).getActiveBindingsForTemplate(templateId);
    }
}
