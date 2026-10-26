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
    @org.junit.jupiter.api.BeforeEach void verifiedStaffFixture() { org.mockito.Mockito.lenient().when(userContextService.hasIndependentStudentAccess(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any())).thenReturn(true); org.mockito.Mockito.lenient().when(userContextService.getCurrentStaffEmail()).thenReturn("teacher@test.com"); }
    @org.mockito.Mock
    private com.reps.demogcloud.services.email.RequiredNotificationService requiredNotifications;

    @org.mockito.Mock
    private com.reps.demogcloud.services.email.EmailSenderService emailSenderService;
    @org.mockito.Mock
    private UserContextService userContextService;

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

    @Test
    void phase1CreationRecordsVerifiedActorInsteadOfFrontendTeacher() {
        when(userContextService.getCurrentStaffEmail()).thenReturn("admin@test.com");
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setSchool("School");
        School school = new School();
        school.setSchoolName("School");
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(schoolRepository.findBySchoolNameIgnoreCase("School")).thenReturn(Optional.of(school));
        when(officeReferralRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        OfficeReferralRequest request = new OfficeReferralRequest();
        request.setStudentEmail("student@test.com");
        request.setTeacherEmail("spoofed@test.com");
        OfficeReferral saved = officeReferralService.createNewOfficeReferral(request);
        assertEquals("admin@test.com", saved.getTeacherEmail());
        verify(userContextService).requireStaffInStudentSchool("admin@test.com", student);
    }

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

        org.springframework.security.access.AccessDeniedException ex = assertThrows(
                org.springframework.security.access.AccessDeniedException.class,
                () -> officeReferralService.createNewOfficeReferral(request)
        );

        assertEquals("Access denied", ex.getMessage());
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
    @Test void officeReferralsHaveNoStudentAssignmentWrites() {
        assertThrows(IllegalArgumentException.class, () -> officeReferralService.updateMapIndex("case",1));
        assertThrows(IllegalArgumentException.class, () -> officeReferralService.submitByReferralId("case"));
        assertThrows(IllegalArgumentException.class, () -> officeReferralService.rejectAnswers("case"));
        verifyNoInteractions(officeReferralRepository,emailService);
    }
}
