package com.reps.demogcloud.services;

import com.reps.demogcloud.data.OfficeReferralRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.officeReferral.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.utils.OfficeReferralUtils;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OfficeReferralServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private OfficeReferralRepository officeReferralRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private OfficeReferralUtils officeReferralUtils;

    @Mock
    private MongoTemplate mongoTemplate;

    @InjectMocks
    private OfficeReferralService officeReferralService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createNewAdminReferralBulk_shouldCreateAllReferrals() {
        Student student1 = new Student();
        student1.setStudentEmail("s1@test.com");
        student1.setAdminEmail("admin@test.com");
        student1.setSchool("Test School");

        Student student2 = new Student();
        student2.setStudentEmail("s2@test.com");
        student2.setAdminEmail("admin@test.com");
        student2.setSchool("Test School");

        School school = new School();
        school.setSchoolName("Test School");

        OfficeReferral saved1 = new OfficeReferral();
        saved1.setStudentEmail("s1@test.com");

        OfficeReferral saved2 = new OfficeReferral();
        saved2.setStudentEmail("s2@test.com");

        OfficeReferralRequest r1 = new OfficeReferralRequest();
        r1.setStudentEmail("s1@test.com");
        r1.setTeacherEmail("teacher@test.com");
        r1.setClassPeriod("1");
        r1.setReferralDescription(List.of("desc1"));
        r1.setReferralCode(new OfficeReferralCode(1, "R1"));

        OfficeReferralRequest r2 = new OfficeReferralRequest();
        r2.setStudentEmail("s2@test.com");
        r2.setTeacherEmail("teacher@test.com");
        r2.setClassPeriod("2");
        r2.setReferralDescription(List.of("desc2"));
        r2.setReferralCode(new OfficeReferralCode(1, "R1"));

        when(studentRepository.findByStudentEmailIgnoreCase("s1@test.com")).thenReturn(student1);
        when(studentRepository.findByStudentEmailIgnoreCase("s2@test.com")).thenReturn(student2);
        when(schoolRepository.findBySchoolNameIgnoreCase("Test School")).thenReturn(Optional.of(school));
        when(officeReferralRepository.save(any(OfficeReferral.class)))
                .thenReturn(saved1)
                .thenReturn(saved2);

        List<OfficeReferral> result = officeReferralService.createNewAdminReferralBulk(List.of(r1, r2));

        assertEquals(2, result.size());
        assertSame(saved1, result.get(0));
        assertSame(saved2, result.get(1));
    }

    @Test
    void createNewOfficeReferral_shouldSaveReferral() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setAdminEmail("admin@test.com");
        student.setSchool("Test School");

        School school = new School();
        school.setSchoolName("Test School");

        OfficeReferralRequest request = new OfficeReferralRequest();
        request.setStudentEmail("student@test.com");
        request.setTeacherEmail("teacher@test.com");
        request.setClassPeriod("1");
        request.setReferralDescription(List.of("Behavior issue"));
        request.setReferralCode(new OfficeReferralCode(1, "R1"));

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(schoolRepository.findBySchoolNameIgnoreCase("Test School")).thenReturn(Optional.of(school));
        when(officeReferralRepository.save(any(OfficeReferral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OfficeReferral result = officeReferralService.createNewOfficeReferral(request);

        assertEquals("admin@test.com", result.getAdminEmail());
        assertEquals("student@test.com", result.getStudentEmail());
        assertEquals("teacher@test.com", result.getTeacherEmail());
        assertEquals("1", result.getClassPeriod());
        assertEquals("Test School", result.getSchool());
        assertEquals("OPEN", result.getStatus());
        assertEquals("4", result.getInfractionLevel());
        assertEquals(new OfficeReferralCode(1, "R1"), result.getReferralCode());
        assertEquals(List.of("Behavior issue"), result.getReferralDescription());
        assertNotNull(result.getTimeCreated());
    }

    @Test
    void createNewOfficeReferral_shouldThrow_whenStudentMissing() {
        OfficeReferralRequest request = new OfficeReferralRequest();
        request.setStudentEmail("missing@test.com");

        when(studentRepository.findByStudentEmailIgnoreCase("missing@test.com")).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> officeReferralService.createNewOfficeReferral(request)
        );

        assertEquals("Student not found: missing@test.com", ex.getMessage());
    }

    @Test
    void updateMapIndex_shouldUpdateAndReturnReferral() {
        OfficeReferral referral = new OfficeReferral();
        referral.setMapIndex(0);

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(referral);
        when(officeReferralRepository.save(referral)).thenReturn(referral);

        OfficeReferral result = officeReferralService.updateMapIndex("r1", 3);

        assertEquals(3, result.getMapIndex());
        verify(officeReferralRepository).save(referral);
    }

    @Test
    void updateMapIndex_shouldThrow_whenReferralMissing() {
        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> officeReferralService.updateMapIndex("r1", 3));
    }

    @Test
    void rejectAnswers_shouldResetReferralAndSendEmail() throws MessagingException {
        OfficeReferral referral = new OfficeReferral();
        referral.setOfficeReferralId("r1");
        referral.setStudentEmail("student@test.com");
        referral.setTeacherEmail("teacher@test.com");
        referral.setReferralDescription(new ArrayList<>(List.of("prompt", "bad answer")));

        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setParentEmail("parent@test.com");
        student.setPreferredLanguage("en");

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(referral);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(officeReferralRepository.save(referral)).thenReturn(referral);

        OfficeReferral result = officeReferralService.rejectAnswers("r1");

        assertEquals("OPEN", result.getStatus());
        assertEquals(0, result.getMapIndex());
        assertEquals(List.of("prompt"), result.getReferralDescription());
        assertNotNull(result.getAnswerHistory());
        assertEquals(1, result.getAnswerHistory().size());

        verify(emailService).sendPtsEmail(
                eq("parent@test.com"),
                eq("teacher@test.com"),
                eq("student@test.com"),
                any(String.class),
                eq("Level Three Answers not accepted for John Doe"),
                eq("en")
        );
        verify(officeReferralRepository).save(referral);
    }

    @Test
    void rejectAnswers_shouldThrow_whenReferralMissing() {
        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> officeReferralService.rejectAnswers("r1"));
    }

    @Test
    void rejectAnswers_shouldThrow_whenStudentMissing() {
        OfficeReferral referral = new OfficeReferral();
        referral.setOfficeReferralId("r1");
        referral.setStudentEmail("missing@test.com");
        referral.setReferralDescription(List.of("prompt", "reason"));

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(referral);
        when(studentRepository.findByStudentEmailIgnoreCase("missing@test.com")).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> officeReferralService.rejectAnswers("r1")
        );

        assertEquals("Student not found: missing@test.com", ex.getMessage());
    }

    @Test
    void rejectAnswers_shouldThrow_whenDescriptionMissing() {
        OfficeReferral referral = new OfficeReferral();
        referral.setOfficeReferralId("r1");
        referral.setStudentEmail("student@test.com");
        referral.setReferralDescription(new ArrayList<>());

        Student student = new Student();
        student.setStudentEmail("student@test.com");

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(referral);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> officeReferralService.rejectAnswers("r1")
        );

        assertEquals("Referral description is missing for referral: r1", ex.getMessage());
    }

    @Test
    void findAll_shouldReturnAll() {
        List<OfficeReferral> expected = List.of(new OfficeReferral());
        when(officeReferralRepository.findAll()).thenReturn(expected);

        List<OfficeReferral> result = officeReferralService.findAll();

        assertSame(expected, result);
    }

    @Test
    void findAllSchool_shouldDelegateToUtils() {
        List<OfficeReferral> expected = List.of(new OfficeReferral());
        when(officeReferralUtils.fetchOfficeReferralsByArchivedAndSchool(false)).thenReturn(expected);

        List<OfficeReferral> result = officeReferralService.findAllSchool();

        assertSame(expected, result);
    }

    @Test
    void findByAdminEmail_shouldReturnMatchingReferrals() {
        List<OfficeReferral> expected = List.of(new OfficeReferral());
        when(officeReferralRepository.findByAdminEmail("admin@test.com")).thenReturn(expected);

        List<OfficeReferral> result = officeReferralService.findByAdminEmail("admin@test.com");

        assertSame(expected, result);
    }

    @Test
    void findByStudentEmail_shouldReturnMatchingReferrals() {
        List<OfficeReferral> expected = List.of(new OfficeReferral());
        when(officeReferralRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(expected);

        List<OfficeReferral> result = officeReferralService.findByStudentEmail("student@test.com");

        assertSame(expected, result);
    }

    @Test
    void findByLoggedInStudent_shouldReturnStudentReferrals() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("student@test.com", "password");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        Student student = new Student();
        student.setStudentEmail("student@test.com");

        List<OfficeReferral> expected = List.of(new OfficeReferral());

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(officeReferralRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(expected);

        List<OfficeReferral> result = officeReferralService.findByLoggedInStudent();

        assertSame(expected, result);
    }

    @Test
    void findByLoggedInStudent_shouldThrow_whenAuthMissing() {
        SecurityContextHolder.clearContext();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> officeReferralService.findByLoggedInStudent()
        );

        assertEquals("No authenticated user found", ex.getMessage());
        verifyNoInteractions(studentRepository, officeReferralRepository);
    }

    @Test
    void findByLoggedInStudent_shouldThrow_whenStudentMissing() {
        TestingAuthenticationToken auth = new TestingAuthenticationToken("student@test.com", "password");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> officeReferralService.findByLoggedInStudent()
        );

        assertEquals("Student not found: student@test.com", ex.getMessage());
    }

    @Test
    void findByReferralId_shouldReturnReferral() {
        OfficeReferral referral = new OfficeReferral();
        referral.setArchived(false);

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(referral);

        OfficeReferral result = officeReferralService.findByReferralId("r1");

        assertSame(referral, result);
    }

    @Test
    void findByReferralId_shouldThrow_whenMissing() {
        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> officeReferralService.findByReferralId("r1"));
    }

    @Test
    void findByReferralId_shouldThrow_whenArchived() {
        OfficeReferral referral = new OfficeReferral();
        referral.setArchived(true);

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(referral);

        assertThrows(ResourceNotFoundException.class, () -> officeReferralService.findByReferralId("r1"));
    }

    @Test
    void closeByReferralId_shouldCloseAndAppendComment() {
        OfficeReferral referral = new OfficeReferral();
        referral.setOfficeReferralId("r1");
        referral.setReferralDescription(new ArrayList<>(List.of("existing")));

        OfficeReferralCloseRequest request = new OfficeReferralCloseRequest();
        request.setId("r1");
        request.setComment("admin note");

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(referral);
        when(officeReferralRepository.save(referral)).thenReturn(referral);

        OfficeReferralResponse response = officeReferralService.closeByReferralId(request);

        assertNotNull(response);
        assertSame(referral, response.getOfficeReferral());
        assertEquals("CLOSED", referral.getStatus());
        assertNotNull(referral.getTimeClosed());
        assertEquals(List.of("existing", "admin note"), referral.getReferralDescription());
    }

    @Test
    void closeByReferralId_shouldInitializeDescriptionWhenNull() {
        OfficeReferral referral = new OfficeReferral();
        referral.setOfficeReferralId("r1");
        referral.setReferralDescription(null);

        OfficeReferralCloseRequest request = new OfficeReferralCloseRequest();
        request.setId("r1");
        request.setComment("");

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(referral);
        when(officeReferralRepository.save(referral)).thenReturn(referral);

        OfficeReferralResponse response = officeReferralService.closeByReferralId(request);

        assertNotNull(response);
        assertNotNull(referral.getReferralDescription());
        assertEquals(0, referral.getReferralDescription().size());
    }

    @Test
    void closeByReferralId_shouldThrow_whenMissing() {
        OfficeReferralCloseRequest request = new OfficeReferralCloseRequest();
        request.setId("r1");

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> officeReferralService.closeByReferralId(request));
    }

    @Test
    void updateDescriptions_shouldTrimAndSaveOnlyEligibleReferrals() {
        OfficeReferral r1 = new OfficeReferral();
        r1.setReferralDescription(new ArrayList<>(List.of("a", "b", "c")));

        OfficeReferral r2 = new OfficeReferral();
        r2.setReferralDescription(new ArrayList<>(List.of("onlyOne")));

        OfficeReferral r3 = new OfficeReferral();
        r3.setReferralDescription(null);

        when(officeReferralRepository.findAll()).thenReturn(List.of(r1, r2, r3));
        when(officeReferralRepository.save(any(OfficeReferral.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<OfficeReferral> result = officeReferralService.updateDescriptions();

        assertEquals(1, result.size());
        assertEquals(List.of("b", "c"), r1.getReferralDescription());
        verify(officeReferralRepository).save(r1);
        verify(officeReferralRepository, never()).save(r2);
        verify(officeReferralRepository, never()).save(r3);
    }

    @Test
    void submitByReferralId_shouldSetPendingAndSave() {
        OfficeReferral referral = new OfficeReferral();
        referral.setOfficeReferralId("r1");

        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(referral);
        when(officeReferralRepository.save(referral)).thenReturn(referral);

        OfficeReferralResponse response = officeReferralService.submitByReferralId("r1");

        assertNotNull(response);
        assertSame(referral, response.getOfficeReferral());
        assertEquals("PENDING", referral.getStatus());
        assertNotNull(referral.getTimeClosed());
    }

    @Test
    void submitByReferralId_shouldThrow_whenMissing() {
        when(officeReferralRepository.findByOfficeReferralId("r1")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () -> officeReferralService.submitByReferralId("r1"));
    }

    @Test
    void getTeacherResponse_shouldReturnEmptyList_whenInputEmpty() {
        List<TeacherDTO> result = officeReferralService.getTeacherResponse(List.of());

        assertNotNull(result);
        assertEquals(0, result.size());
        verifyNoInteractions(mongoTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void getTeacherResponse_shouldReturnMappedTeacherDtos() {
        OfficeReferral referral = new OfficeReferral();
        referral.setStudentEmail("student@test.com");

        TeacherDTO dto = new TeacherDTO();
        dto.setStudentEmail("student@test.com");
        dto.setStudentFirstName("John");
        dto.setStudentLastName("Doe");

        AggregationResults<TeacherDTO> results = (AggregationResults<TeacherDTO>) org.mockito.Mockito.mock(AggregationResults.class);
        when(results.getMappedResults()).thenReturn(List.of(dto));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("OfficeReferrals"), eq(TeacherDTO.class))).thenReturn(results);

        List<TeacherDTO> response = officeReferralService.getTeacherResponse(List.of(referral));

        assertEquals(1, response.size());
        assertEquals("student@test.com", response.get(0).getStudentEmail());
        assertEquals("John", response.get(0).getStudentFirstName());
        assertEquals("Doe", response.get(0).getStudentLastName());
    }
}