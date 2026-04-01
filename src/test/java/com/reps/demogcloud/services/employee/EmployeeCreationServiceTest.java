package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.services.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeCreationServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private AuthService authService;

    @InjectMocks
    private EmployeeCreationService employeeCreationService;

    @Test
    void createNewEmployee_shouldCreateEmployeeAndReturnSuccessResponse_whenEmailDoesNotExist() {
        RoleModel role = new RoleModel();
        role.setRole("TEACHER");

        Employee request = new Employee();
        request.setEmployeeId("E1");
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("JohnDoe@school.com");
        request.setSchool("Test School");
        request.setRoles(Set.of(role));

        Employee savedEmployee = new Employee();
        savedEmployee.setEmployeeId("E1");
        savedEmployee.setFirstName("John");
        savedEmployee.setLastName("Doe");
        savedEmployee.setEmail("JohnDoe@school.com");
        savedEmployee.setSchool("Test School");
        savedEmployee.setRoles(Set.of(role));

        when(employeeRepository.findByEmailIgnoreCase("JohnDoe@school.com")).thenReturn(null);
        when(employeeRepository.save(request)).thenReturn(savedEmployee);

        EmployeeResponse result = employeeCreationService.createNewEmployee(request);

        assertNotNull(result);
        assertEquals("", result.getError());
        assertNotNull(result.getEmployee());
        assertEquals("E1", result.getEmployee().getEmployeeId());
        assertEquals("JohnDoe@school.com", result.getEmployee().getEmail());

        ArgumentCaptor<AuthenticationRequest> authCaptor = ArgumentCaptor.forClass(AuthenticationRequest.class);
        verify(authService, times(1)).createEmployeeUser(authCaptor.capture());

        AuthenticationRequest capturedAuthRequest = authCaptor.getValue();
        assertEquals("johndoe@school.com", capturedAuthRequest.getUsername());
        assertEquals("JohnDoe", capturedAuthRequest.getPassword());
        assertEquals("John", capturedAuthRequest.getFirstName());
        assertEquals("Doe", capturedAuthRequest.getLastName());
        assertEquals("Test School", capturedAuthRequest.getSchool());
        assertEquals(Set.of(role), capturedAuthRequest.getRoles());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase("JohnDoe@school.com");
        verify(employeeRepository, times(1)).save(request);
        verifyNoMoreInteractions(employeeRepository, authService);
    }

    @Test
    void createNewEmployee_shouldReturnErrorResponse_whenEmailAlreadyExists() {
        Employee request = new Employee();
        request.setFirstName("John");
        request.setLastName("Doe");
        request.setEmail("teacher@school.com");
        request.setSchool("Test School");

        Employee existingEmployee = new Employee();
        existingEmployee.setEmployeeId("EXISTING");
        existingEmployee.setEmail("teacher@school.com");

        when(employeeRepository.findByEmailIgnoreCase("teacher@school.com")).thenReturn(existingEmployee);

        EmployeeResponse result = employeeCreationService.createNewEmployee(request);

        assertNotNull(result);
        assertEquals("Error: Email Already Registered In System", result.getError());
        assertNull(result.getEmployee());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase("teacher@school.com");
        verify(employeeRepository, never()).save(any(Employee.class));
        verify(authService, never()).createEmployeeUser(any(AuthenticationRequest.class));
        verifyNoMoreInteractions(employeeRepository, authService);
    }

    @Test
    void createNewEmployee_shouldReturnErrorResponse_whenAuthServiceThrowsIllegalArgumentException() {
        RoleModel role = new RoleModel();
        role.setRole("ADMIN");

        Employee request = new Employee();
        request.setFirstName("Jane");
        request.setLastName("Smith");
        request.setEmail("jane@school.com");
        request.setSchool("Test School");
        request.setRoles(Set.of(role));

        when(employeeRepository.findByEmailIgnoreCase("jane@school.com")).thenReturn(null);
        doThrow(new IllegalArgumentException("User creation failed"))
                .when(authService).createEmployeeUser(any(AuthenticationRequest.class));

        EmployeeResponse result = employeeCreationService.createNewEmployee(request);

        assertNotNull(result);
        assertEquals("User creation failed", result.getError());
        assertNull(result.getEmployee());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase("jane@school.com");
        verify(authService, times(1)).createEmployeeUser(any(AuthenticationRequest.class));
        verify(employeeRepository, never()).save(any(Employee.class));
        verifyNoMoreInteractions(employeeRepository, authService);
    }

    @Test
    void createNewEmployeeList_shouldReturnResponsesForAllEmployees_whenAllSucceed() {
        Employee employee1 = new Employee();
        employee1.setEmployeeId("E1");
        employee1.setFirstName("John");
        employee1.setLastName("Doe");
        employee1.setEmail("john@school.com");
        employee1.setSchool("Test School");

        Employee employee2 = new Employee();
        employee2.setEmployeeId("E2");
        employee2.setFirstName("Jane");
        employee2.setLastName("Smith");
        employee2.setEmail("jane@school.com");
        employee2.setSchool("Test School");

        when(employeeRepository.findByEmailIgnoreCase("john@school.com")).thenReturn(null);
        when(employeeRepository.findByEmailIgnoreCase("jane@school.com")).thenReturn(null);
        when(employeeRepository.save(employee1)).thenReturn(employee1);
        when(employeeRepository.save(employee2)).thenReturn(employee2);

        List<EmployeeResponse> result = employeeCreationService.createNewEmployeeList(List.of(employee1, employee2));

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals("", result.get(0).getError());
        assertNotNull(result.get(0).getEmployee());
        assertEquals("john@school.com", result.get(0).getEmployee().getEmail());

        assertEquals("", result.get(1).getError());
        assertNotNull(result.get(1).getEmployee());
        assertEquals("jane@school.com", result.get(1).getEmployee().getEmail());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase("john@school.com");
        verify(employeeRepository, times(1)).findByEmailIgnoreCase("jane@school.com");
        verify(employeeRepository, times(1)).save(employee1);
        verify(employeeRepository, times(1)).save(employee2);
        verify(authService, times(2)).createEmployeeUser(any(AuthenticationRequest.class));
        verifyNoMoreInteractions(employeeRepository, authService);
    }

    @Test
    void createNewEmployeeList_shouldReturnEmptyList_whenInputListIsEmpty() {
        List<EmployeeResponse> result = employeeCreationService.createNewEmployeeList(List.of());

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verifyNoInteractions(employeeRepository, authService);
    }

    @Test
    void createNewEmployeeList_shouldReturnMixedResponses_whenSomeSucceedAndSomeFail() {
        Employee employee1 = new Employee();
        employee1.setEmployeeId("E1");
        employee1.setFirstName("John");
        employee1.setLastName("Doe");
        employee1.setEmail("john@school.com");
        employee1.setSchool("Test School");

        Employee employee2 = new Employee();
        employee2.setEmployeeId("E2");
        employee2.setFirstName("Jane");
        employee2.setLastName("Smith");
        employee2.setEmail("jane@school.com");
        employee2.setSchool("Test School");

        Employee existingEmployee = new Employee();
        existingEmployee.setEmployeeId("EXISTING");
        existingEmployee.setEmail("jane@school.com");

        when(employeeRepository.findByEmailIgnoreCase("john@school.com")).thenReturn(null);
        when(employeeRepository.findByEmailIgnoreCase("jane@school.com")).thenReturn(existingEmployee);
        when(employeeRepository.save(employee1)).thenReturn(employee1);

        List<EmployeeResponse> result = employeeCreationService.createNewEmployeeList(List.of(employee1, employee2));

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals("", result.get(0).getError());
        assertNotNull(result.get(0).getEmployee());
        assertEquals("john@school.com", result.get(0).getEmployee().getEmail());

        assertEquals("Error: Email Already Registered In System", result.get(1).getError());
        assertNull(result.get(1).getEmployee());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase("john@school.com");
        verify(employeeRepository, times(1)).findByEmailIgnoreCase("jane@school.com");
        verify(employeeRepository, times(1)).save(employee1);
        verify(employeeRepository, never()).save(employee2);
        verify(authService, times(1)).createEmployeeUser(any(AuthenticationRequest.class));
        verifyNoMoreInteractions(employeeRepository, authService);
    }
}
