package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.AdminOverviewDTO;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.services.EmployeeService;
import com.reps.demogcloud.services.OfficeReferralService;
import com.reps.demogcloud.services.PunishmentService;
import com.reps.demogcloud.services.StudentService;
import com.reps.demogcloud.utils.DtoUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDtoServiceTest {

    @Mock
    private PunishmentService punishmentService;

    @Mock
    private EmployeeService employeeService;

    @Mock
    private StudentService studentService;

    @Mock
    private OfficeReferralService officeReferralService;

    @Mock
    private DtoUtils dtoUtils;

    @InjectMocks
    private AdminDtoService adminDtoService;

    @Test
    void getAdminOverData_shouldReturnOverview_whenAllDataExists() throws Exception {
        List<Punishment> allSchoolPunishments = List.of(new Punishment(), new Punishment());
        List<OfficeReferral> allSchoolReferrals = List.of(new OfficeReferral());

        TeacherDTO writeUp = buildTeacherDto("Class Disruption");
        TeacherDTO shoutOut = buildTeacherDto("Positive Behavior Shout Out!");
        TeacherDTO academicConcern = buildTeacherDto("Academic Concern");

        List<TeacherDTO> punishmentDisplayList = List.of(writeUp, shoutOut, academicConcern);
        List<TeacherDTO> shoutOutList = List.of(shoutOut);

        Employee loggedInEmployee = new Employee();
        loggedInEmployee.setSchool("Test School");

        Employee teacher1 = new Employee();
        Employee teacher2 = new Employee();
        List<Employee> teachers = List.of(teacher1, teacher2);

        School school = new School();

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(punishmentDisplayList);
        when(dtoUtils.listOfShoutOuts(punishmentDisplayList)).thenReturn(shoutOutList);
        when(employeeService.findAllByRole("TEACHER")).thenReturn(Optional.of(teachers));
        when(employeeService.findByLoggedInEmployee()).thenReturn(loggedInEmployee);
        when(employeeService.getEmployeeSchool()).thenReturn(Optional.of(school));

        AdminOverviewDTO result = adminDtoService.getAdminOverData();

        assertNotNull(result);

        verify(punishmentService).findAllSchool();
        verify(officeReferralService).findAllSchool();
        verify(punishmentService).getTeacherResponse(allSchoolPunishments);
        verify(dtoUtils).listOfShoutOuts(punishmentDisplayList);
        verify(employeeService).findAllByRole("TEACHER");
        verify(employeeService).findByLoggedInEmployee();
        verify(employeeService).getEmployeeSchool();
        verifyNoInteractions(studentService);
    }

    @Test
    void getAdminOverData_shouldReturnOverview_whenTeachersOptionalIsEmpty() throws Exception {
        List<Punishment> allSchoolPunishments = List.of();
        List<OfficeReferral> allSchoolReferrals = List.of();
        List<TeacherDTO> punishmentDisplayList = List.of();
        List<TeacherDTO> shoutOutList = List.of();

        Employee loggedInEmployee = new Employee();
        loggedInEmployee.setSchool("Test School");

        School school = new School();

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(punishmentDisplayList);
        when(dtoUtils.listOfShoutOuts(punishmentDisplayList)).thenReturn(shoutOutList);
        when(employeeService.findAllByRole("TEACHER")).thenReturn(Optional.empty());
        when(employeeService.findByLoggedInEmployee()).thenReturn(loggedInEmployee);
        when(employeeService.getEmployeeSchool()).thenReturn(Optional.of(school));

        AdminOverviewDTO result = adminDtoService.getAdminOverData();

        assertNotNull(result);

        verify(employeeService).findAllByRole("TEACHER");
    }

    @Test
    void getAdminOverData_shouldThrow_whenLoggedInEmployeeIsNull() throws Exception {
        List<Punishment> allSchoolPunishments = List.of();
        List<OfficeReferral> allSchoolReferrals = List.of();
        List<TeacherDTO> punishmentDisplayList = List.of();
        List<TeacherDTO> shoutOutList = List.of();

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(punishmentDisplayList);
        when(dtoUtils.listOfShoutOuts(punishmentDisplayList)).thenReturn(shoutOutList);
        when(employeeService.findAllByRole("TEACHER")).thenReturn(Optional.of(List.of()));
        when(employeeService.findByLoggedInEmployee()).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> adminDtoService.getAdminOverData()
        );

        assertSame(IllegalStateException.class, ex.getClass());
        verify(employeeService).findByLoggedInEmployee();
    }

    @Test
    void getAdminOverData_shouldThrow_whenSchoolMissing() throws Exception {
        List<Punishment> allSchoolPunishments = List.of();
        List<OfficeReferral> allSchoolReferrals = List.of();
        List<TeacherDTO> punishmentDisplayList = List.of();
        List<TeacherDTO> shoutOutList = List.of();

        Employee loggedInEmployee = new Employee();
        loggedInEmployee.setSchool("Missing School");

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(punishmentDisplayList);
        when(dtoUtils.listOfShoutOuts(punishmentDisplayList)).thenReturn(shoutOutList);
        when(employeeService.findAllByRole("TEACHER")).thenReturn(Optional.of(List.of()));
        when(employeeService.findByLoggedInEmployee()).thenReturn(loggedInEmployee);
        when(employeeService.getEmployeeSchool()).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> adminDtoService.getAdminOverData()
        );

        assertSame(IllegalStateException.class, ex.getClass());
        verify(employeeService).getEmployeeSchool();
    }

    @Test
    void getAdminOverData_shouldPropagateRuntimeException_whenPunishmentFindAllSchoolFails() throws Exception {
        RuntimeException expected = new RuntimeException("punishment failure");
        when(punishmentService.findAllSchool()).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> adminDtoService.getAdminOverData()
        );

        assertSame(expected, actual);
    }

    @Test
    void getAdminOverData_shouldPropagateRuntimeException_whenOfficeReferralFindAllSchoolFails() throws Exception {
        when(punishmentService.findAllSchool()).thenReturn(List.of());
        RuntimeException expected = new RuntimeException("referral failure");
        when(officeReferralService.findAllSchool()).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> adminDtoService.getAdminOverData()
        );

        assertSame(expected, actual);
    }

    @Test
    void getAdminOverData_shouldPropagateRuntimeException_whenTeacherResponseFails() throws Exception {
        List<Punishment> allSchoolPunishments = List.of(new Punishment());

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(List.of());

        RuntimeException expected = new RuntimeException("teacher response failure");
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> adminDtoService.getAdminOverData()
        );

        assertSame(expected, actual);
    }

    @Test
    void getAdminOverData_shouldPropagateRuntimeException_whenShoutOutFilteringFails() throws Exception {
        List<Punishment> allSchoolPunishments = List.of();
        List<OfficeReferral> allSchoolReferrals = List.of();
        List<TeacherDTO> punishmentDisplayList = List.of(buildTeacherDto("Class Disruption"));

        when(punishmentService.findAllSchool()).thenReturn(allSchoolPunishments);
        when(officeReferralService.findAllSchool()).thenReturn(allSchoolReferrals);
        when(punishmentService.getTeacherResponse(allSchoolPunishments)).thenReturn(punishmentDisplayList);

        RuntimeException expected = new RuntimeException("shout out failure");
        when(dtoUtils.listOfShoutOuts(punishmentDisplayList)).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> adminDtoService.getAdminOverData()
        );

        assertSame(expected, actual);
    }

    private TeacherDTO buildTeacherDto(String infractionName) {
        TeacherDTO dto = new TeacherDTO();
        dto.setInfractionName(infractionName);
        return dto;
    }
}