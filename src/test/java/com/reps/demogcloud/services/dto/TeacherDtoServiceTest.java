package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.dto.TeacherOverviewDTO;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.services.EmployeeService;
import com.reps.demogcloud.services.OfficeReferralService;
import com.reps.demogcloud.services.PunishmentService;
import com.reps.demogcloud.utils.DtoUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TeacherDtoServiceTest {

    @Mock
    private PunishmentService punishmentService;

    @Mock
    private OfficeReferralService officeReferralService;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private DtoUtils dtoUtils;

    @InjectMocks
    private TeacherDtoService teacherDtoService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getTeacherOverData_shouldReturnFilteredOverview() throws Exception {
        setAuthenticatedUser("teacher@test.com");

        List<Punishment> allSchoolPunishments = List.of(new Punishment(), new Punishment(), new Punishment());
        List<OfficeReferral> allSchoolReferrals = List.of(new OfficeReferral(), new OfficeReferral());

        TeacherDTO writeUp = buildTeacherDto(
                "teacher@test.com",
                "student1@test.com",
                "Class Disruption",
                LocalDate.of(2026, 3, 10)
        );

        TeacherDTO shoutOutOlder = buildTeacherDto(
                "teacher@test.com",
                "student2@test.com",
                "Positive Behavior Shout Out!",
                LocalDate.of(2026, 3, 9)
        );

        TeacherDTO shoutOutNewer = buildTeacherDto(
                "teacher@test.com",
                "student1@test.com",
                "Positive Behavior Shout Out!",
                LocalDate.of(2026, 3, 11)
        );

        TeacherDTO behaviorConcern = buildTeacherDto(
                "teacher@test.com",
                "student2@test.com",
                "Behavioral Concern",
                LocalDate.of(2026, 3, 8)
        );

        TeacherDTO wrongTeacher = buildTeacherDto(
                "otherteacher@test.com",
                "student1@test.com",
                "Class Disruption",
                LocalDate.of(2026, 3, 12)
        );

        TeacherDTO notInRoster = buildTeacherDto(
                "teacher@test.com",
                "student99@test.com",
                "Class Disruption",
                LocalDate.of(2026, 3, 12)
        );

        TeacherDTO referralInRoster = buildTeacherDto(
                "teacher@test.com",
                "student1@test.com",
                "Referral",
                LocalDate.of(2026, 3, 10)
        );

        TeacherDTO referralNotInRoster = buildTeacherDto(
                "teacher@test.com",
                "student99@test.com",
                "Referral",
                LocalDate.of(2026, 3, 10)
        );

        Employee teacher = buildTeacherWithRoster(
                "Test School",
                List.of("student1@test.com", "student2@test.com")
        );
        School school = new School();

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(
                List.of(writeUp, shoutOutOlder, shoutOutNewer, behaviorConcern, wrongTeacher, notInRoster)
        );
        when(officeReferralService.getTeacherResponse(allSchoolReferrals)).thenReturn(
                List.of(referralInRoster, referralNotInRoster)
        );
        when(employeeService.findByLoggedInEmployee()).thenReturn(teacher);
        when(employeeService.getEmployeeSchool()).thenReturn(Optional.of(school));

        TeacherOverviewDTO result = teacherDtoService.getTeacherOverData();

        assertNotNull(result);
        assertSame(teacher, result.getTeacher());
        assertSame(school, result.getSchool());

        assertEquals(4, result.getPunishmentResponse().size());
        assertEquals(1, result.getWriteUpResponse().size());
        assertEquals("Class Disruption", result.getWriteUpResponse().get(0).getInfractionName());

        assertEquals(2, result.getShoutOutsResponse().size());
        assertEquals(LocalDate.of(2026, 3, 11), result.getShoutOutsResponse().get(0).getTimeCreated());
        assertEquals(LocalDate.of(2026, 3, 9), result.getShoutOutsResponse().get(1).getTimeCreated());

        assertEquals(1, result.getOfficeReferrals().size());
        assertEquals("student1@test.com", result.getOfficeReferrals().get(0).getStudentEmail());

        verify(dtoUtils).listOfShoutOuts(anyList());
        verify(dtoUtils).updateWeeklyPunishmentsForTeacherClasses(eq(teacher), eq(result.getPunishmentResponse()));
    }

    @Test
    void getTeacherOverData_shouldReturnEmptyFilteredLists_whenTeacherHasNullClasses() throws Exception {
        setAuthenticatedUser("teacher@test.com");

        List<Punishment> allSchoolPunishments = List.of(new Punishment());
        List<OfficeReferral> allSchoolReferrals = List.of(new OfficeReferral());

        TeacherDTO punishmentDto = buildTeacherDto(
                "teacher@test.com",
                "student1@test.com",
                "Class Disruption",
                LocalDate.of(2026, 3, 10)
        );

        TeacherDTO referralDto = buildTeacherDto(
                "teacher@test.com",
                "student1@test.com",
                "Referral",
                LocalDate.of(2026, 3, 10)
        );

        Employee teacher = new Employee();
        teacher.setSchool("Test School");
        teacher.setClasses(null);

        School school = new School();

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(List.of(punishmentDto));
        when(officeReferralService.getTeacherResponse(allSchoolReferrals)).thenReturn(List.of(referralDto));
        when(employeeService.findByLoggedInEmployee()).thenReturn(teacher);
        when(employeeService.getEmployeeSchool()).thenReturn(Optional.of(school));

        TeacherOverviewDTO result = teacherDtoService.getTeacherOverData();

        assertNotNull(result);
        assertEquals(0, result.getPunishmentResponse().size());
        assertEquals(0, result.getWriteUpResponse().size());
        assertEquals(0, result.getShoutOutsResponse().size());
        assertEquals(0, result.getOfficeReferrals().size());

        verify(dtoUtils).updateWeeklyPunishmentsForTeacherClasses(eq(teacher), eq(result.getPunishmentResponse()));
    }

    @Test
    void getTeacherOverData_shouldThrowIllegalStateException_whenAuthenticationMissing() {
        SecurityContextHolder.clearContext();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> teacherDtoService.getTeacherOverData()
        );

        assertEquals("No authenticated user found", ex.getMessage());
        verifyNoInteractions(punishmentService, officeReferralService, employeeService, dtoUtils);
    }

    @Test
    void getTeacherOverData_shouldThrowIllegalStateException_whenTeacherIsNull() throws Exception {
        setAuthenticatedUser("teacher@test.com");

        List<Punishment> allSchoolPunishments = List.of();
        List<OfficeReferral> allSchoolReferrals = List.of();

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(List.of());
        when(officeReferralService.getTeacherResponse(allSchoolReferrals)).thenReturn(List.of());
        when(employeeService.findByLoggedInEmployee()).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> teacherDtoService.getTeacherOverData()
        );

        assertEquals("Logged in teacher not found", ex.getMessage());

        verify(dtoUtils).listOfShoutOuts(anyList());
        verify(employeeService).findByLoggedInEmployee();
        verify(employeeService, never()).getEmployeeSchool();
        verify(dtoUtils, never()).updateWeeklyPunishmentsForTeacherClasses(eq(null), anyList());
    }

    @Test
    void getTeacherOverData_shouldThrowIllegalStateException_whenSchoolMissing() throws Exception {
        setAuthenticatedUser("teacher@test.com");

        List<Punishment> allSchoolPunishments = List.of();
        List<OfficeReferral> allSchoolReferrals = List.of();

        Employee teacher = new Employee();
        teacher.setSchool("Test School");

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(List.of());
        when(officeReferralService.getTeacherResponse(allSchoolReferrals)).thenReturn(List.of());
        when(employeeService.findByLoggedInEmployee()).thenReturn(teacher);
        when(employeeService.getEmployeeSchool()).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> teacherDtoService.getTeacherOverData()
        );

        assertEquals("School not found: Test School", ex.getMessage());

        verify(dtoUtils).listOfShoutOuts(anyList());
        verify(employeeService).findByLoggedInEmployee();
        verify(employeeService).getEmployeeSchool();
        verify(dtoUtils, never()).updateWeeklyPunishmentsForTeacherClasses(eq(teacher), anyList());
    }

    @Test
    void getTeacherOverData_shouldPropagateRuntimeException_whenPunishmentServiceFindAllSchoolFails() throws Exception {
        setAuthenticatedUser("teacher@test.com");

        RuntimeException expected = new RuntimeException("punishment failure");
        when(punishmentService.findAllSchool()).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> teacherDtoService.getTeacherOverData()
        );

        assertSame(expected, actual);
    }

    @Test
    void getTeacherOverData_shouldPropagateRuntimeException_whenOfficeReferralServiceFindAllSchoolFails() throws Exception {
        setAuthenticatedUser("teacher@test.com");

        when(punishmentService.findAllSchool()).thenReturn(List.of());
        RuntimeException expected = new RuntimeException("office referral failure");
        when(officeReferralService.findAllSchool()).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> teacherDtoService.getTeacherOverData()
        );

        assertSame(expected, actual);
    }

    @Test
    void getTeacherOverData_shouldPropagateRuntimeException_whenPunishmentTeacherResponseFails() throws Exception {
        setAuthenticatedUser("teacher@test.com");

        List<Punishment> allSchoolPunishments = List.of(new Punishment());

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(List.of());

        RuntimeException expected = new RuntimeException("teacher response failure");
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> teacherDtoService.getTeacherOverData()
        );

        assertSame(expected, actual);
    }

    @Test
    void getTeacherOverData_shouldPropagateRuntimeException_whenOfficeReferralTeacherResponseFails() throws Exception {
        setAuthenticatedUser("teacher@test.com");

        List<Punishment> allSchoolPunishments = List.of();
        List<OfficeReferral> allSchoolReferrals = List.of(new OfficeReferral());

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(List.of());

        RuntimeException expected = new RuntimeException("referral response failure");
        when(officeReferralService.getTeacherResponse(allSchoolReferrals)).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> teacherDtoService.getTeacherOverData()
        );

        assertSame(expected, actual);
    }

    private void setAuthenticatedUser(String username) {
        TestingAuthenticationToken auth = new TestingAuthenticationToken(username, "password");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private TeacherDTO buildTeacherDto(String teacherEmail, String studentEmail, String infractionName, LocalDate timeCreated) {
        TeacherDTO dto = new TeacherDTO();
        dto.setTeacherEmail(teacherEmail);
        dto.setStudentEmail(studentEmail);
        dto.setInfractionName(infractionName);
        dto.setTimeCreated(timeCreated);
        return dto;
    }

    private Employee buildTeacherWithRoster(String schoolName, List<String> rosterEmails) {
        Employee teacher = new Employee();
        teacher.setSchool(schoolName);

        Employee.ClassRoster classRoster = new Employee.ClassRoster();
        classRoster.setClassName("Homeroom");
        classRoster.setClassPeriod("1");
        classRoster.setClassRoster(rosterEmails);

        teacher.setClasses(List.of(classRoster));
        return teacher;
    }
}