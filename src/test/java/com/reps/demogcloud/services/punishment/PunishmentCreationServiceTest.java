package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.enums.InfractionType;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.AssignmentService;
import com.reps.demogcloud.utils.PunishmentUtils;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PunishmentCreationServiceTest {

    @Mock
    private PunishmentUtils punishmentUtils;

    @Mock
    private AssignmentService assignmentService;

    @InjectMocks
    private PunishmentCreationService punishmentCreationService;

    @Test
    void createNewPunishForm_shouldHandlePositiveShoutout() throws Exception {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@school.com");

        Student student = new Student();
        student.setSchool("Test School");
        student.setPreferredLanguage("en");

        School school = new School();
        school.setMaxPunishLevel(4);

        Infraction infraction = new Infraction();
        infraction.setInfractionName(InfractionType.POSITIVE.getLabel());

        Punishment punishment = new Punishment();
        punishment.setInfractionLevel("1");
        punishment.setTeacherEmail("teacher@school.com");
        punishment.setClosedTimes(0);

        AssignmentTemplate template = mock(AssignmentTemplate.class);
        when(template.getId()).thenReturn("template-1");

        PunishmentResponse response = mock(PunishmentResponse.class);

        when(punishmentUtils.fetchStudent("student@school.com")).thenReturn(student);
        when(punishmentUtils.fetchSchool("Test School")).thenReturn(Optional.of(school));
        when(punishmentUtils.resolveInfraction(request, school, 4)).thenReturn(infraction);
        when(punishmentUtils.buildPunishment(eq(request), eq(infraction), eq(student), eq(4), any(LocalDate.class)))
                .thenReturn(punishment);
        when(assignmentService.resolveTemplateForInfraction("teacher@school.com", "Test School",
                InfractionType.POSITIVE.getLabel(), 1)).thenReturn(template);
        when(punishmentUtils.handlePositiveShoutout(eq(request), eq(punishment), eq(student), any(LocalDate.class)))
                .thenReturn(response);

        PunishmentResponse result = punishmentCreationService.createNewPunishForm(request);

        assertSame(response, result);
        assertEquals("template-1", punishment.getAssignmentTemplateId());

        verify(punishmentUtils).validateFormRequest(request);
        verify(punishmentUtils).fetchStudent("student@school.com");
        verify(punishmentUtils).fetchSchool("Test School");
        verify(punishmentUtils).resolveInfraction(request, school, 4);
        verify(punishmentUtils).buildPunishment(eq(request), eq(infraction), eq(student), eq(4), any(LocalDate.class));
        verify(assignmentService).resolveTemplateForInfraction("teacher@school.com", "Test School",
                InfractionType.POSITIVE.getLabel(), 1);
        verify(punishmentUtils).savePhoneLogIfNeeded(eq(request), eq(student), any(LocalDate.class));
        verify(punishmentUtils).handlePositiveShoutout(eq(request), eq(punishment), eq(student), any(LocalDate.class));
        verifyNoMoreInteractions(punishmentUtils, assignmentService);
    }

    @Test
    void createNewPunishForm_shouldHandleBehavioralBasicClose() throws MessagingException {
        PunishmentFormRequest request = baseRequest(false);
        Student student = baseStudent();
        School school = baseSchool(4);
        Infraction infraction = baseInfraction(InfractionType.BEHAVIORAL.getLabel());
        Punishment punishment = basePunishment("2", 0);

        PunishmentResponse response = mock(PunishmentResponse.class);

        mockCommonFlow(request, student, school, infraction, punishment);
        when(punishmentUtils.handleBasicClose(eq("BC"), eq(request), eq(punishment), eq(student), any(LocalDate.class)))
                .thenReturn(response);

        PunishmentResponse result = punishmentCreationService.createNewPunishForm(request);

        assertSame(response, result);
        verify(punishmentUtils).handleBasicClose(eq("BC"), eq(request), eq(punishment), eq(student), any(LocalDate.class));
    }

    @Test
    void createNewPunishForm_shouldHandleAcademicBasicClose() throws MessagingException {
        PunishmentFormRequest request = baseRequest(false);
        Student student = baseStudent();
        School school = baseSchool(4);
        Infraction infraction = baseInfraction(InfractionType.ACADEMIC.getLabel());
        Punishment punishment = basePunishment("2", 0);

        PunishmentResponse response = mock(PunishmentResponse.class);

        mockCommonFlow(request, student, school, infraction, punishment);
        when(punishmentUtils.handleBasicClose(eq("AC"), eq(request), eq(punishment), eq(student), any(LocalDate.class)))
                .thenReturn(response);

        PunishmentResponse result = punishmentCreationService.createNewPunishForm(request);

        assertSame(response, result);
        verify(punishmentUtils).handleBasicClose(eq("AC"), eq(request), eq(punishment), eq(student), any(LocalDate.class));
    }

    @Test
    void createNewPunishForm_shouldHandleIncompleteWorkBasicClose() throws MessagingException {
        PunishmentFormRequest request = baseRequest(false);
        Student student = baseStudent();
        School school = baseSchool(4);
        Infraction infraction = baseInfraction(InfractionType.INCOMPLETE_WORK.getLabel());
        Punishment punishment = basePunishment("2", 0);

        PunishmentResponse response = mock(PunishmentResponse.class);

        mockCommonFlow(request, student, school, infraction, punishment);
        when(punishmentUtils.handleBasicClose(eq("PENDING"), eq(request), eq(punishment), eq(student), any(LocalDate.class)))
                .thenReturn(response);

        PunishmentResponse result = punishmentCreationService.createNewPunishForm(request);

        assertSame(response, result);
        verify(punishmentUtils).handleBasicClose(eq("PENDING"), eq(request), eq(punishment), eq(student), any(LocalDate.class));
    }

    @Test
    void createNewPunishForm_shouldHandleLevelFourReferral_whenClosedTimesAtLeastFour() throws MessagingException {
        PunishmentFormRequest request = baseRequest(false);
        Student student = baseStudent();
        School school = baseSchool(4);
        Infraction infraction = baseInfraction("Tardy");
        Punishment punishment = basePunishment("2", 4);

        PunishmentResponse response = mock(PunishmentResponse.class);

        mockCommonFlow(request, student, school, infraction, punishment);
        when(punishmentUtils.handleLevelFourReferral(request, punishment, student)).thenReturn(response);

        PunishmentResponse result = punishmentCreationService.createNewPunishForm(request);

        assertSame(response, result);
        verify(punishmentUtils).handleLevelFourReferral(request, punishment, student);
    }

    @Test
    void createNewPunishForm_shouldHandleAdminReferral_whenAdminReferralAndNotOtherSpecialCase() throws MessagingException {
        PunishmentFormRequest request = baseRequest(true);
        Student student = baseStudent();
        School school = baseSchool(4);
        Infraction infraction = baseInfraction("Tardy");
        Punishment punishment = basePunishment("2", 0);

        PunishmentResponse response = mock(PunishmentResponse.class);

        mockCommonFlow(request, student, school, infraction, punishment);
        when(punishmentUtils.handleAdminReferral(eq(request), eq(punishment), eq(student), any(LocalDate.class)))
                .thenReturn(response);

        PunishmentResponse result = punishmentCreationService.createNewPunishForm(request);

        assertSame(response, result);
        verify(punishmentUtils).handleAdminReferral(eq(request), eq(punishment), eq(student), any(LocalDate.class));
    }

    @Test
    void createNewPunishForm_shouldHandleDefaultOpen_whenNoSpecialConditionsMatch() throws MessagingException {
        PunishmentFormRequest request = baseRequest(false);
        Student student = baseStudent();
        School school = baseSchool(4);
        Infraction infraction = baseInfraction("Tardy");
        Punishment punishment = basePunishment("2", 0);

        PunishmentResponse response = mock(PunishmentResponse.class);

        mockCommonFlow(request, student, school, infraction, punishment);
        when(punishmentUtils.handleDefaultOpen(request, punishment, student)).thenReturn(response);

        PunishmentResponse result = punishmentCreationService.createNewPunishForm(request);

        assertSame(response, result);
        verify(punishmentUtils).handleDefaultOpen(request, punishment, student);
    }

    @Test
    void createNewPunishForm_shouldUseMaxLevelWhenPunishmentInfractionLevelIsNotNumeric() throws Exception {
        PunishmentFormRequest request = baseRequest(false);
        Student student = baseStudent();
        School school = baseSchool(7);
        Infraction infraction = baseInfraction("Tardy");
        Punishment punishment = basePunishment("NOT_A_NUMBER", 0);

        PunishmentResponse response = mock(PunishmentResponse.class);
        AssignmentTemplate template = mock(AssignmentTemplate.class);
        when(template.getId()).thenReturn("template-max");

        mockCommonFlow(request, student, school, infraction, punishment);
        when(assignmentService.resolveTemplateForInfraction("teacher@school.com", "Test School", "Tardy", 7))
                .thenReturn(template);
        when(punishmentUtils.handleDefaultOpen(request, punishment, student)).thenReturn(response);

        PunishmentResponse result = punishmentCreationService.createNewPunishForm(request);

        assertSame(response, result);
        assertEquals("template-max", punishment.getAssignmentTemplateId());
        verify(assignmentService).resolveTemplateForInfraction("teacher@school.com", "Test School", "Tardy", 7);
    }

    @Test
    void createNewPunishForm_shouldProceedWhenTemplateResolutionFails() throws Exception {
        PunishmentFormRequest request = baseRequest(false);
        Student student = baseStudent();
        School school = baseSchool(4);
        Infraction infraction = baseInfraction("Tardy");
        Punishment punishment = basePunishment("2", 0);

        PunishmentResponse response = mock(PunishmentResponse.class);

        mockCommonFlow(request, student, school, infraction, punishment);
        when(assignmentService.resolveTemplateForInfraction("teacher@school.com", "Test School", "Tardy", 2))
                .thenThrow(new RuntimeException("template lookup failed"));
        when(punishmentUtils.handleDefaultOpen(request, punishment, student)).thenReturn(response);

        PunishmentResponse result = punishmentCreationService.createNewPunishForm(request);

        assertSame(response, result);
        assertNull(punishment.getAssignmentTemplateId());
        verify(punishmentUtils).savePhoneLogIfNeeded(eq(request), eq(student), any(LocalDate.class));
        verify(punishmentUtils).handleDefaultOpen(request, punishment, student);
    }

    @Test
    void createNewPunishForm_shouldThrowIllegalStateException_whenSchoolNotFound() {
        PunishmentFormRequest request = baseRequest(false);
        Student student = baseStudent();

        when(punishmentUtils.fetchStudent("student@school.com")).thenReturn(student);
        when(punishmentUtils.fetchSchool("Test School")).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> punishmentCreationService.createNewPunishForm(request)
        );

        assertEquals("School not found: Test School", exception.getMessage());
        verify(punishmentUtils).validateFormRequest(request);
        verify(punishmentUtils).fetchStudent("student@school.com");
        verify(punishmentUtils).fetchSchool("Test School");
        verifyNoMoreInteractions(punishmentUtils, assignmentService);
    }

    @Test
    void createNewPunishFormBulk_shouldCreateEachPunishmentResponse() throws Exception {
        PunishmentFormRequest request1 = spy(baseRequest(false));
        request1.setStudentEmail("student1@school.com");

        PunishmentFormRequest request2 = spy(baseRequest(true));
        request2.setStudentEmail("student2@school.com");

        Student student1 = baseStudent();
        student1.setStudentEmail("student1@school.com");

        Student student2 = baseStudent();
        student2.setStudentEmail("student2@school.com");

        School school = baseSchool(4);

        Infraction infraction1 = baseInfraction("Tardy");
        Infraction infraction2 = baseInfraction("Tardy");

        Punishment punishment1 = basePunishment("2", 0);
        Punishment punishment2 = basePunishment("2", 0);

        PunishmentResponse response1 = mock(PunishmentResponse.class);
        PunishmentResponse response2 = mock(PunishmentResponse.class);

        when(punishmentUtils.fetchStudent("student1@school.com")).thenReturn(student1);
        when(punishmentUtils.fetchStudent("student2@school.com")).thenReturn(student2);
        when(punishmentUtils.fetchSchool("Test School")).thenReturn(Optional.of(school));
        when(punishmentUtils.resolveInfraction(request1, school, 4)).thenReturn(infraction1);
        when(punishmentUtils.resolveInfraction(request2, school, 4)).thenReturn(infraction2);
        when(punishmentUtils.buildPunishment(eq(request1), eq(infraction1), eq(student1), eq(4), any(LocalDate.class)))
                .thenReturn(punishment1);
        when(punishmentUtils.buildPunishment(eq(request2), eq(infraction2), eq(student2), eq(4), any(LocalDate.class)))
                .thenReturn(punishment2);
        when(assignmentService.resolveTemplateForInfraction(anyString(), anyString(), anyString(), anyInt()))
                .thenThrow(new RuntimeException("ignore template"));
        when(punishmentUtils.handleDefaultOpen(request1, punishment1, student1)).thenReturn(response1);
        when(punishmentUtils.handleAdminReferral(eq(request2), eq(punishment2), eq(student2), any(LocalDate.class)))
                .thenReturn(response2);

        List<PunishmentResponse> results =
                punishmentCreationService.createNewPunishFormBulk(List.of(request1, request2));

        assertNotNull(results);
        assertEquals(2, results.size());
        assertSame(response1, results.get(0));
        assertSame(response2, results.get(1));
    }

    private void mockCommonFlow(
            PunishmentFormRequest request,
            Student student,
            School school,
            Infraction infraction,
            Punishment punishment
    ) {
        when(punishmentUtils.fetchStudent(request.getStudentEmail())).thenReturn(student);
        when(punishmentUtils.fetchSchool(student.getSchool())).thenReturn(Optional.of(school));
        when(punishmentUtils.resolveInfraction(request, school, school.getMaxPunishLevel())).thenReturn(infraction);
        when(punishmentUtils.buildPunishment(eq(request), eq(infraction), eq(student), eq(school.getMaxPunishLevel()), any(LocalDate.class)))
                .thenReturn(punishment);
    }

    private PunishmentFormRequest baseRequest(boolean adminReferral) {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@school.com");
        request.setTeacherEmail("teacher@school.com");
        request.setAdminReferral(adminReferral);
        return request;
    }

    private Student baseStudent() {
        Student student = new Student();
        student.setStudentEmail("student@school.com");
        student.setSchool("Test School");
        student.setPreferredLanguage("en");
        return student;
    }

    private School baseSchool(int maxLevel) {
        School school = new School();
        school.setMaxPunishLevel(maxLevel);
        return school;
    }

    private Infraction baseInfraction(String infractionName) {
        Infraction infraction = new Infraction();
        infraction.setInfractionName(infractionName);
        return infraction;
    }

    private Punishment basePunishment(String infractionLevel, int closedTimes) {
        Punishment punishment = new Punishment();
        punishment.setTeacherEmail("teacher@school.com");
        punishment.setInfractionLevel(infractionLevel);
        punishment.setClosedTimes(closedTimes);
        return punishment;
    }
}