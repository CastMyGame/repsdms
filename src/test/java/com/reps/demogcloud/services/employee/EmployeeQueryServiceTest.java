package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.utils.SchoolUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeQueryServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private SchoolUtils schoolUtils;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private EmployeeQueryService employeeQueryService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findAll_shouldReturnEmployeesForCurrentSchool_whenEmployeesExist() {
        Employee employee1 = new Employee();
        employee1.setEmployeeId("E1");
        employee1.setFirstName("John");
        employee1.setLastName("Doe");
        employee1.setSchool("Test School");
        employee1.setArchived(false);

        Employee employee2 = new Employee();
        employee2.setEmployeeId("E2");
        employee2.setFirstName("Jane");
        employee2.setLastName("Smith");
        employee2.setSchool("Test School");
        employee2.setArchived(false);

        List<Employee> employees = List.of(employee1, employee2);

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(employeeRepository.findByArchivedAndSchool(false, "Test School")).thenReturn(employees);

        List<Employee> result = employeeQueryService.findAll();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("E1", result.get(0).getEmployeeId());
        assertEquals("E2", result.get(1).getEmployeeId());

        verify(schoolUtils, times(1)).fetchSchoolName();
        verify(employeeRepository, times(1)).findByArchivedAndSchool(false, "Test School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }

    @Test
    void findAll_shouldReturnEmptyList_whenNoEmployeesExist() {
        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(employeeRepository.findByArchivedAndSchool(false, "Test School")).thenReturn(new ArrayList<>());

        List<Employee> result = employeeQueryService.findAll();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(schoolUtils, times(1)).fetchSchoolName();
        verify(employeeRepository, times(1)).findByArchivedAndSchool(false, "Test School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }

    @Test
    void findAllByRole_shouldReturnEmployeesWithMatchingRole_sortedByLastName() {
        RoleModel teacherRole = mock(RoleModel.class);
        when(teacherRole.getRole()).thenReturn("TEACHER");

        RoleModel adminRole = mock(RoleModel.class);
        when(adminRole.getRole()).thenReturn("ADMIN");

        Employee employee1 = new Employee();
        employee1.setEmployeeId("E1");
        employee1.setFirstName("Amy");
        employee1.setLastName("Zimmer");
        employee1.setRoles(Set.of(teacherRole));
        employee1.setSchool("Test School");
        employee1.setArchived(false);

        Employee employee2 = new Employee();
        employee2.setEmployeeId("E2");
        employee2.setFirstName("Bob");
        employee2.setLastName("Anderson");
        employee2.setRoles(Set.of(teacherRole));
        employee2.setSchool("Test School");
        employee2.setArchived(false);

        Employee employee3 = new Employee();
        employee3.setEmployeeId("E3");
        employee3.setFirstName("Carl");
        employee3.setLastName("Morris");
        employee3.setRoles(Set.of(adminRole));
        employee3.setSchool("Test School");
        employee3.setArchived(false);

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(employeeRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(employee1, employee2, employee3));

        Optional<List<Employee>> result = employeeQueryService.findAllByRole("TEACHER");

        assertTrue(result.isPresent());
        assertEquals(2, result.get().size());

        assertEquals("Anderson", result.get().get(0).getLastName());
        assertEquals("Zimmer", result.get().get(1).getLastName());

        verify(schoolUtils, times(1)).fetchSchoolName();
        verify(employeeRepository, times(1)).findByArchivedAndSchool(false, "Test School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }

    @Test
    void findAllByRole_shouldReturnPresentEmptyList_whenEmployeesExistButNoRoleMatches() {
        RoleModel adminRole = mock(RoleModel.class);
        when(adminRole.getRole()).thenReturn("ADMIN");

        Employee employee = new Employee();
        employee.setEmployeeId("E1");
        employee.setFirstName("Carl");
        employee.setLastName("Morris");
        employee.setRoles(Set.of(adminRole));
        employee.setSchool("Test School");
        employee.setArchived(false);

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(employeeRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(employee));

        Optional<List<Employee>> result = employeeQueryService.findAllByRole("TEACHER");

        assertTrue(result.isPresent());
        assertNotNull(result.get());
        assertTrue(result.get().isEmpty());

        verify(schoolUtils, times(1)).fetchSchoolName();
        verify(employeeRepository, times(1)).findByArchivedAndSchool(false, "Test School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }

    @Test
    void findAllByRole_shouldFilterOutEmployeesWithNullRoles() {
        RoleModel teacherRole = mock(RoleModel.class);
        when(teacherRole.getRole()).thenReturn("TEACHER");

        Employee employeeWithNullRoles = new Employee();
        employeeWithNullRoles.setEmployeeId("E1");
        employeeWithNullRoles.setLastName("NullRoles");
        employeeWithNullRoles.setRoles(null);

        Employee teacherEmployee = new Employee();
        teacherEmployee.setEmployeeId("E2");
        teacherEmployee.setLastName("Teacher");
        teacherEmployee.setRoles(Set.of(teacherRole));

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(employeeRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(employeeWithNullRoles, teacherEmployee));

        Optional<List<Employee>> result = employeeQueryService.findAllByRole("TEACHER");

        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertEquals("E2", result.get().get(0).getEmployeeId());

        verify(schoolUtils, times(1)).fetchSchoolName();
        verify(employeeRepository, times(1)).findByArchivedAndSchool(false, "Test School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }

    @Test
    void findAllByRole_shouldReturnEmptyOptional_whenNoEmployeesExist() {
        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(employeeRepository.findByArchivedAndSchool(false, "Test School")).thenReturn(new ArrayList<>());

        Optional<List<Employee>> result = employeeQueryService.findAllByRole("TEACHER");

        assertTrue(result.isEmpty());

        verify(schoolUtils, times(1)).fetchSchoolName();
        verify(employeeRepository, times(1)).findByArchivedAndSchool(false, "Test School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }

    @Test
    void findByLoggedInEmployee_shouldReturnEmployee_whenAuthenticatedEmployeeExists() throws Exception {
        Employee employee = new Employee();
        employee.setEmployeeId("E1");
        employee.setEmail("teacher@school.com");

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("teacher@school.com");
        when(employeeRepository.findByEmailIgnoreCase("teacher@school.com")).thenReturn(employee);

        Employee result = employeeQueryService.findByLoggedInEmployee();

        assertNotNull(result);
        assertEquals("E1", result.getEmployeeId());
        assertEquals("teacher@school.com", result.getEmail());

        verify(securityContext, times(1)).getAuthentication();
        verify(authentication, times(1)).getName();
        verify(employeeRepository, times(1)).findByEmailIgnoreCase("teacher@school.com");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils, securityContext, authentication);
    }

    @Test
    void findByLoggedInEmployee_shouldThrowException_whenAuthenticatedEmployeeDoesNotExist() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("missing@school.com");
        when(employeeRepository.findByEmailIgnoreCase("missing@school.com")).thenReturn(null);

        Exception exception = assertThrows(
                Exception.class,
                () -> employeeQueryService.findByLoggedInEmployee()
        );

        assertEquals("No employee with that email exists", exception.getMessage());

        verify(securityContext, times(1)).getAuthentication();
        verify(authentication, times(1)).getName();
        verify(employeeRepository, times(1)).findByEmailIgnoreCase("missing@school.com");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils, securityContext, authentication);
    }

    @Test
    void getEmployeeSchool_shouldReturnSchool_whenEmployeeAndSchoolExist() {
        Employee employee = new Employee();
        employee.setEmployeeId("E1");
        employee.setEmail("teacher@school.com");
        employee.setSchool("Test School");

        School school = new School();

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("teacher@school.com");
        when(employeeRepository.findByEmailIgnoreCase("teacher@school.com")).thenReturn(employee);
        when(schoolRepository.findBySchoolNameIgnoreCase("Test School")).thenReturn(Optional.of(school));

        Optional<School> result = employeeQueryService.getEmployeeSchool();

        assertNotNull(result);
        assertTrue(result.isPresent());
        assertSame(school, result.get());

        verify(securityContext, times(1)).getAuthentication();
        verify(authentication, times(1)).getName();
        verify(employeeRepository, times(1)).findByEmailIgnoreCase("teacher@school.com");
        verify(schoolRepository, times(1)).findBySchoolNameIgnoreCase("Test School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils, securityContext, authentication);
    }

    @Test
    void getEmployeeSchool_shouldReturnEmptyOptional_whenSchoolDoesNotExist() {
        Employee employee = new Employee();
        employee.setEmployeeId("E1");
        employee.setEmail("teacher@school.com");
        employee.setSchool("Unknown School");

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("teacher@school.com");
        when(employeeRepository.findByEmailIgnoreCase("teacher@school.com")).thenReturn(employee);
        when(schoolRepository.findBySchoolNameIgnoreCase("Unknown School")).thenReturn(Optional.empty());

        Optional<School> result = employeeQueryService.getEmployeeSchool();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(securityContext, times(1)).getAuthentication();
        verify(authentication, times(1)).getName();
        verify(employeeRepository, times(1)).findByEmailIgnoreCase("teacher@school.com");
        verify(schoolRepository, times(1)).findBySchoolNameIgnoreCase("Unknown School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils, securityContext, authentication);
    }

    @Test
    void findByUserName_shouldReturnEmployee_whenEmployeeExists() {
        Employee employee = new Employee();
        employee.setEmployeeId("E1");
        employee.setEmail("teacher@school.com");

        when(employeeRepository.findByEmailIgnoreCase("teacher@school.com")).thenReturn(employee);

        Employee result = employeeQueryService.findByUserName("teacher@school.com");

        assertNotNull(result);
        assertEquals("E1", result.getEmployeeId());
        assertEquals("teacher@school.com", result.getEmail());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase("teacher@school.com");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }

    @Test
    void findByUserName_shouldThrowResourceNotFoundException_whenEmployeeDoesNotExist() {
        when(employeeRepository.findByEmailIgnoreCase("missing@school.com")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> employeeQueryService.findByUserName("missing@school.com")
        );

        assertEquals("No employees with that email exist", exception.getMessage());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase("missing@school.com");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }

    @Test
    void fetchEmployeeDataByArchivedAndSchool_shouldReturnEmployees_whenRepositoryReturnsEmployees() {
        Employee employee1 = new Employee();
        employee1.setEmployeeId("E1");

        Employee employee2 = new Employee();
        employee2.setEmployeeId("E2");

        List<Employee> employees = List.of(employee1, employee2);

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(employeeRepository.findByArchivedAndSchool(false, "Test School")).thenReturn(employees);

        List<Employee> result = employeeQueryService.fetchEmployeeDataByArchivedAndSchool(false);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("E1", result.get(0).getEmployeeId());
        assertEquals("E2", result.get(1).getEmployeeId());

        verify(schoolUtils, times(1)).fetchSchoolName();
        verify(employeeRepository, times(1)).findByArchivedAndSchool(false, "Test School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }

    @Test
    void fetchEmployeeDataByArchivedAndSchool_shouldReturnEmptyList_whenRepositoryReturnsEmptyList() {
        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(employeeRepository.findByArchivedAndSchool(true, "Test School")).thenReturn(new ArrayList<>());

        List<Employee> result = employeeQueryService.fetchEmployeeDataByArchivedAndSchool(true);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(schoolUtils, times(1)).fetchSchoolName();
        verify(employeeRepository, times(1)).findByArchivedAndSchool(true, "Test School");
        verifyNoMoreInteractions(employeeRepository, schoolRepository, schoolUtils);
    }
}
