package com.reps.demogcloud.services;

import com.reps.demogcloud.data.GuidanceRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.guidance.GuidanceReferral;
import com.reps.demogcloud.models.guidance.GuidanceRequest;
import com.reps.demogcloud.models.guidance.GuidanceResponse;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.ResourceOption;
import com.reps.demogcloud.models.punishment.ResourceUpdateRequest;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GuidanceServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private GuidanceRepository guidanceRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private GuidanceService guidanceService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findAll_shouldReturnAllGuidanceReferrals() {
        List<GuidanceReferral> expected = List.of(new GuidanceReferral(), new GuidanceReferral());
        when(guidanceRepository.findAll()).thenReturn(expected);

        List<GuidanceReferral> result = guidanceService.findAll();

        assertSame(expected, result);
    }

    @Test
    void findByStatus_shouldReturnMatchingGuidanceReferrals() {
        List<GuidanceReferral> expected = List.of(new GuidanceReferral());
        when(guidanceRepository.findAllByStatus("OPEN")).thenReturn(expected);

        List<GuidanceReferral> result = guidanceService.findByStatus("OPEN");

        assertSame(expected, result);
    }

    @Test
    void linkAssignmentToGuidance_shouldCreateGuidanceReferral() {
        Student student = new Student();
        student.setGuidanceEmail("guidance@test.com");
        student.setStudentEmail("student@test.com");
        student.setSchool("Test School");

        Punishment punishment = new Punishment();
        punishment.setPunishmentId("p1");
        punishment.setInfractionName("Disruption");

        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@test.com");
        request.setTeacherEmail("teacher@test.com");
        request.setGuidanceDescription("Need follow-up");

        School school = new School();
        school.setSchoolName("Test School");

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(schoolRepository.findBySchoolNameIgnoreCase("Test School")).thenReturn(Optional.of(school));
        when(guidanceRepository.save(any(GuidanceReferral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        guidanceService.LinkAssignmentToGuidance(student, request, punishment);

        ArgumentCaptor<GuidanceReferral> captor = ArgumentCaptor.forClass(GuidanceReferral.class);
        verify(guidanceRepository).save(captor.capture());

        GuidanceReferral saved = captor.getValue();
        assertEquals("student@test.com", saved.getStudentEmail());
        assertEquals("guidance@test.com", saved.getGuidanceEmail());
        assertEquals("teacher@test.com", saved.getTeacherEmail());
        assertEquals("OPEN", saved.getStatus());
        assertEquals("Disruption", saved.getInfractionName());
        assertEquals("p1", saved.getLinkToPunishment());
        assertEquals("Test School", saved.getSchool());
        assertEquals(List.of("Need follow-up"), saved.getReferralDescription());
    }

    @Test
    void createNewGuidanceForm_shouldSaveGuidance() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setGuidanceEmail("guidance@test.com");
        student.setSchool("Test School");

        School school = new School();
        school.setSchoolName("Test School");

        GuidanceReferral referral = new GuidanceReferral();
        referral.setClassPeriod("1st");

        GuidanceRequest request = new GuidanceRequest();
        request.setGuidance(referral);

        PunishmentFormRequest punishmentRequest = new PunishmentFormRequest();
        punishmentRequest.setStudentEmail("student@test.com");
        punishmentRequest.setTeacherEmail("teacher@test.com");
        punishmentRequest.setGuidanceDescription("Description");

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(schoolRepository.findBySchoolNameIgnoreCase("Test School")).thenReturn(Optional.of(school));
        when(guidanceRepository.save(any(GuidanceReferral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        guidanceService.createNewGuidanceForm(request, punishmentRequest);

        ArgumentCaptor<GuidanceReferral> captor = ArgumentCaptor.forClass(GuidanceReferral.class);
        verify(guidanceRepository).save(captor.capture());

        GuidanceReferral saved = captor.getValue();
        assertEquals("student@test.com", saved.getStudentEmail());
        assertEquals("guidance@test.com", saved.getGuidanceEmail());
        assertEquals("teacher@test.com", saved.getTeacherEmail());
        assertEquals("OPEN", saved.getStatus());
        assertEquals("Test School", saved.getSchool());
        assertEquals(List.of("Description"), saved.getReferralDescription());
        assertEquals("1st", saved.getClassPeriod());
        assertNotNull(saved.getGuidanceId());
        assertNotNull(saved.getTimeCreated());
    }

    @Test
    void createNewGuidanceForm_shouldThrow_whenStudentMissing() {
        GuidanceRequest request = new GuidanceRequest();
        request.setGuidance(new GuidanceReferral());

        PunishmentFormRequest punishmentRequest = new PunishmentFormRequest();
        punishmentRequest.setStudentEmail("missing@test.com");

        when(studentRepository.findByStudentEmailIgnoreCase("missing@test.com")).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> guidanceService.createNewGuidanceForm(request, punishmentRequest)
        );

        assertEquals("Student not found: missing@test.com", ex.getMessage());
    }

    @Test
    void createNewGuidanceFormSimple_shouldReturnResponseAndSaveGuidance() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setGuidanceEmail("guidance@test.com");
        student.setSchool("Test School");

        School school = new School();
        school.setSchoolName("Test School");

        GuidanceReferral referral = new GuidanceReferral();
        referral.setStudentEmail("student@test.com");
        referral.setTeacherEmail("teacher@test.com");
        referral.setReferralDescription(new ArrayList<>(List.of("Simple description")));
        referral.setClassPeriod("2nd");

        GuidanceRequest request = new GuidanceRequest();
        request.setGuidance(referral);

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(schoolRepository.findBySchoolNameIgnoreCase("Test School")).thenReturn(Optional.of(school));
        when(guidanceRepository.save(any(GuidanceReferral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuidanceResponse response = guidanceService.createNewGuidanceFormSimple(request);

        assertEquals("Successfully Created Guidance Referral", response.getMessage());
        assertNotNull(response.getGuidance());
        assertEquals(1, response.getGuidance().size());

        GuidanceReferral saved = response.getGuidance().get(0);
        assertEquals("student@test.com", saved.getStudentEmail());
        assertEquals("teacher@test.com", saved.getTeacherEmail());
        assertEquals("guidance@test.com", saved.getGuidanceEmail());
        assertEquals("OPEN", saved.getStatus());
        assertEquals("Test School", saved.getSchool());
        assertEquals("2nd", saved.getClassPeriod());
        assertEquals(List.of("Simple description"), saved.getReferralDescription());
    }

    @Test
    void updateGuidanceStatus_shouldUpdateStatusAndAddEvent() {
        GuidanceReferral referral = new GuidanceReferral();
        referral.setStatus("OPEN");
        referral.setNotesArray(new ArrayList<>());

        when(guidanceRepository.findById("g1")).thenReturn(Optional.of(referral));
        when(guidanceRepository.save(any(GuidanceReferral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuidanceReferral result = guidanceService.updateGuidanceStatus("g1", "CLOSED");

        assertNotNull(result);
        assertEquals("CLOSED", result.getStatus());
        assertEquals(1, result.getNotesArray().size());
        assertEquals("Status", result.getNotesArray().get(0).getEvent());
        assertEquals("The Status of This Task was Changed to CLOSED", result.getNotesArray().get(0).getContent());
    }

    @Test
    void updateGuidanceStatus_shouldReturnNull_whenNotFound() {
        when(guidanceRepository.findById("missing")).thenReturn(Optional.empty());

        GuidanceReferral result = guidanceService.updateGuidanceStatus("missing", "CLOSED");

        assertNull(result);
    }

    @Test
    void updateGuidanceFollowUp_shouldUpdateFollowUpAndStatusAndAddEvent() {
        GuidanceReferral referral = new GuidanceReferral();
        referral.setNotesArray(new ArrayList<>());

        when(guidanceRepository.findById("g1")).thenReturn(Optional.of(referral));
        when(guidanceRepository.save(any(GuidanceReferral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        LocalDate followUp = LocalDate.of(2026, 4, 15);

        GuidanceReferral result = guidanceService.updateGuidanceFollowUp("g1", followUp, "PENDING");

        assertNotNull(result);
        assertEquals(followUp, result.getFollowUpDate());
        assertEquals("PENDING", result.getStatus());
        assertEquals(1, result.getNotesArray().size());
        assertEquals("Follow Up", result.getNotesArray().get(0).getEvent());
        assertEquals("Follow up for this task has been set for 2026-04-15", result.getNotesArray().get(0).getContent());
    }

    @Test
    void updateGuidanceFollowUp_shouldReturnNull_whenNotFound() {
        when(guidanceRepository.findById("missing")).thenReturn(Optional.empty());

        GuidanceReferral result = guidanceService.updateGuidanceFollowUp("missing", LocalDate.now(), "PENDING");

        assertNull(result);
    }

    @Test
    void sendResourcesAndMakeNotes_shouldReturnResponseAndSendEmail() throws MessagingException {
        GuidanceReferral referral = new GuidanceReferral();
        referral.setStudentEmail("student@test.com");
        referral.setTeacherEmail("teacher@test.com");
        referral.setNotesArray(new ArrayList<>());

        Student student = new Student();
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setStudentEmail("student@test.com");
        student.setGuidanceEmail("guidance@test.com");
        student.setSchool("Test");
        student.setPreferredLanguage("en");

        ResourceOption option1 = new ResourceOption();
        option1.setLabel("Resource A");
        option1.setUrl("https://a.com");

        ResourceOption option2 = new ResourceOption();
        option2.setLabel("Resource B");
        option2.setUrl("https://b.com");

        ResourceUpdateRequest request = new ResourceUpdateRequest();
        request.setResourceOptionList(List.of(option1, option2));

        when(guidanceRepository.findById("g1")).thenReturn(Optional.of(referral));
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(guidanceRepository.save(any(GuidanceReferral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuidanceResponse response = guidanceService.sendResourcesAndMakeNotes("g1", request);

        assertNotNull(response);
        assertNotNull(response.getGuidance());
        assertEquals(1, response.getGuidance().size());
        assertEquals(1, referral.getNotesArray().size());
        assertEquals("Resources", referral.getNotesArray().get(0).getEvent());
        assertEquals("Resources Sent: Resource A, Resource B", referral.getNotesArray().get(0).getContent());

        verify(emailService).sendEmailGeneric(
                eq(List.of("guidance@test.com", "teacher@test.com")),
                eq("student@test.com"),
                eq("Test High School Guidance's Resources"),
                any(String.class),
                eq("en")
        );
        verify(guidanceRepository).save(referral);
    }

    @Test
    void sendResourcesAndMakeNotes_shouldReturnResponseWithMessage_whenGuidanceNotFound() throws MessagingException {
        when(guidanceRepository.findById("missing")).thenReturn(Optional.empty());

        GuidanceResponse response = guidanceService.sendResourcesAndMakeNotes("missing", new ResourceUpdateRequest());

        assertEquals("No Guidance Found by Id: missing", response.getMessage());
    }

    @Test
    void sendResourcesAndMakeNotes_shouldThrow_whenStudentMissing() {
        GuidanceReferral referral = new GuidanceReferral();
        referral.setStudentEmail("missing@test.com");
        referral.setTeacherEmail("teacher@test.com");

        ResourceUpdateRequest request = new ResourceUpdateRequest();
        request.setResourceOptionList(List.of());

        when(guidanceRepository.findById("g1")).thenReturn(Optional.of(referral));
        when(studentRepository.findByStudentEmailIgnoreCase("missing@test.com")).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> guidanceService.sendResourcesAndMakeNotes("g1", request)
        );

        assertEquals("Student not found: missing@test.com", ex.getMessage());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getLoggedInUserGuidanceReferrals_shouldReturnOnlyMatchingGuidanceUsers() {
        GuidanceReferral g1 = new GuidanceReferral();
        g1.setStudentEmail("student1@test.com");

        GuidanceReferral g2 = new GuidanceReferral();
        g2.setStudentEmail("student2@test.com");

        GuidanceReferral g3 = new GuidanceReferral();
        g3.setStudentEmail("student3@test.com");

        AggregationResults<Map> results = (AggregationResults<Map>) org.mockito.Mockito.mock(AggregationResults.class);
        when(results.getMappedResults()).thenReturn(List.of(
                Map.of("studentEmail", "student1@test.com", "guidanceEmail", "guidance@test.com"),
                Map.of("studentEmail", "student2@test.com", "guidanceEmail", "other@test.com"),
                Map.of("studentEmail", "student3@test.com", "guidanceEmail", "guidance@test.com")
        ));

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("students"), eq(Map.class))).thenReturn(results);

        List<GuidanceReferral> result = guidanceService.getLoggedInUserGuidanceReferrals(
                List.of(g1, g2, g3),
                "guidance@test.com"
        );

        assertEquals(2, result.size());
        assertEquals(List.of("student1@test.com", "student3@test.com"),
                result.stream().map(GuidanceReferral::getStudentEmail).toList());
    }

    @Test
    void updateGuidance_shouldAppendThreadEventAndSave() {
        GuidanceReferral referral = new GuidanceReferral();
        referral.setNotesArray(new ArrayList<>());

        ThreadEvent input = new ThreadEvent();
        input.setCreatedBy("admin@test.com");
        input.setEvent("Comment");
        input.setContent("Follow up with student");

        when(guidanceRepository.findById("g1")).thenReturn(Optional.of(referral));
        when(guidanceRepository.save(any(GuidanceReferral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        GuidanceReferral result = guidanceService.updateGuidance("g1", input);

        assertNotNull(result);
        assertEquals(1, result.getNotesArray().size());
        assertEquals("admin@test.com", result.getNotesArray().get(0).getCreatedBy());
        assertEquals("Comment", result.getNotesArray().get(0).getEvent());
        assertEquals("Follow up with student", result.getNotesArray().get(0).getContent());
        assertNotNull(result.getNotesArray().get(0).getDate());
    }

    @Test
    void updateGuidance_shouldReturnNull_whenNotFound() {
        when(guidanceRepository.findById("missing")).thenReturn(Optional.empty());

        GuidanceReferral result = guidanceService.updateGuidance("missing", new ThreadEvent());

        assertNull(result);
    }

    @Test
    void getAllGuidanceReferrals_shouldReturnAll_whenNotFilteringByLoggedIn() {
        List<GuidanceReferral> referrals = List.of(new GuidanceReferral(), new GuidanceReferral());
        when(guidanceRepository.findAllByStatus("OPEN")).thenReturn(referrals);

        List<GuidanceReferral> result = guidanceService.getAllGuidanceReferrals("OPEN", false);

        assertSame(referrals, result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getAllGuidanceReferrals_shouldFilterByLoggedInUser_whenRequested() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("guidance@test.com", "password");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        GuidanceReferral g1 = new GuidanceReferral();
        g1.setStudentEmail("student1@test.com");

        GuidanceReferral g2 = new GuidanceReferral();
        g2.setStudentEmail("student2@test.com");

        when(guidanceRepository.findAllByStatus("OPEN")).thenReturn(List.of(g1, g2));

        AggregationResults<Map> results = (AggregationResults<Map>) org.mockito.Mockito.mock(AggregationResults.class);
        when(results.getMappedResults()).thenReturn(List.of(
                Map.of("studentEmail", "student1@test.com", "guidanceEmail", "guidance@test.com"),
                Map.of("studentEmail", "student2@test.com", "guidanceEmail", "other@test.com")
        ));

        when(mongoTemplate.aggregate(any(Aggregation.class), eq("students"), eq(Map.class))).thenReturn(results);

        List<GuidanceReferral> result = guidanceService.getAllGuidanceReferrals("OPEN", true);

        assertEquals(1, result.size());
        assertEquals("student1@test.com", result.get(0).getStudentEmail());
    }

    @Test
    void deleteGuidanceReferral_shouldDeleteAndReturnMessage() throws ResourceNotFoundException {
        String result = guidanceService.deleteGuidanceReferral("g1");

        verify(guidanceRepository).deleteById("g1");
        assertEquals("Punishment has been deleted", result);
    }

    @Test
    void deleteGuidanceReferral_shouldThrowResourceNotFoundException_whenDeleteFails() {
        doThrow(new RuntimeException("delete failed")).when(guidanceRepository).deleteById("g1");

        ResourceNotFoundException ex = assertThrows(
                ResourceNotFoundException.class,
                () -> guidanceService.deleteGuidanceReferral("g1")
        );

        assertEquals("That infraction does not exist", ex.getMessage());
    }
}