package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.employee.CurrencyTransferRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.CurrencySpendRequest;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.EmployeeService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeCreationService employeeCreationService;

    @Mock
    private EmployeeQueryService employeeQueryService;

    @Mock
    private EmployeeMutationService employeeMutationService;

    @Mock
    private EmployeeClassService employeeClassService;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    void createNewEmployee_shouldReturnEmployeeResponse() {
        Employee request = new Employee();
        request.setEmail("teacher@school.com");
        request.setFirstName("John");
        request.setLastName("Doe");

        EmployeeResponse response = new EmployeeResponse();

        when(employeeCreationService.createNewEmployee(request)).thenReturn(response);

        EmployeeResponse result = employeeService.createNewEmployee(request);

        assertNotNull(result);
        assertSame(response, result);

        verify(employeeCreationService, times(1)).createNewEmployee(request);
        verifyNoInteractions(employeeQueryService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeCreationService);
    }

    @Test
    void createNewEmployee_shouldReturnNull_whenCreationServiceReturnsNull() {
        Employee request = new Employee();
        request.setEmail("teacher@school.com");

        when(employeeCreationService.createNewEmployee(request)).thenReturn(null);

        EmployeeResponse result = employeeService.createNewEmployee(request);

        assertNull(result);

        verify(employeeCreationService, times(1)).createNewEmployee(request);
        verifyNoInteractions(employeeQueryService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeCreationService);
    }

    @Test
    void createNewEmployeeList_shouldReturnEmployeeResponseList() {
        Employee employee1 = new Employee();
        employee1.setEmail("teacher1@school.com");

        Employee employee2 = new Employee();
        employee2.setEmail("teacher2@school.com");

        List<Employee> request = List.of(employee1, employee2);

        EmployeeResponse response1 = new EmployeeResponse();
        EmployeeResponse response2 = new EmployeeResponse();
        List<EmployeeResponse> responses = List.of(response1, response2);

        when(employeeCreationService.createNewEmployeeList(request)).thenReturn(responses);

        List<EmployeeResponse> result = employeeService.createNewEmployeeList(request);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertSame(responses, result);

        verify(employeeCreationService, times(1)).createNewEmployeeList(request);
        verifyNoInteractions(employeeQueryService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeCreationService);
    }

    @Test
    void createNewEmployeeList_shouldReturnEmptyList() {
        List<Employee> request = List.of();

        when(employeeCreationService.createNewEmployeeList(request)).thenReturn(List.of());

        List<EmployeeResponse> result = employeeService.createNewEmployeeList(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(employeeCreationService, times(1)).createNewEmployeeList(request);
        verifyNoInteractions(employeeQueryService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeCreationService);
    }

    @Test
    void findAll_shouldReturnEmployeeList() throws ResourceNotFoundException {
        Employee employee1 = new Employee();
        employee1.setEmployeeId("E1");
        employee1.setEmail("teacher1@school.com");

        Employee employee2 = new Employee();
        employee2.setEmployeeId("E2");
        employee2.setEmail("teacher2@school.com");

        List<Employee> employees = List.of(employee1, employee2);

        when(employeeQueryService.findAll()).thenReturn(employees);

        List<Employee> result = employeeService.findAll();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertSame(employees, result);

        verify(employeeQueryService, times(1)).findAll();
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void findAll_shouldThrowResourceNotFoundException() throws ResourceNotFoundException {
        when(employeeQueryService.findAll()).thenThrow(new ResourceNotFoundException("No employees found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> employeeService.findAll()
        );

        assertEquals("No employees found", exception.getMessage());

        verify(employeeQueryService, times(1)).findAll();
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void findByLoggedInEmployee_shouldReturnEmployee() throws Exception {
        Employee employee = new Employee();
        employee.setEmployeeId("E1");
        employee.setEmail("teacher@school.com");

        when(employeeQueryService.findByLoggedInEmployee()).thenReturn(employee);

        Employee result = employeeService.findByLoggedInEmployee();

        assertNotNull(result);
        assertSame(employee, result);
        assertEquals("teacher@school.com", result.getEmail());

        verify(employeeQueryService, times(1)).findByLoggedInEmployee();
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void findByLoggedInEmployee_shouldThrowException() throws Exception {
        when(employeeQueryService.findByLoggedInEmployee()).thenThrow(new Exception("Logged in employee not found"));

        Exception exception = assertThrows(
                Exception.class,
                () -> employeeService.findByLoggedInEmployee()
        );

        assertEquals("Logged in employee not found", exception.getMessage());

        verify(employeeQueryService, times(1)).findByLoggedInEmployee();
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void getEmployeeSchool_shouldReturnOptionalSchool() {
        School school = new School();
        Optional<School> optionalSchool = Optional.of(school);

        when(employeeQueryService.getEmployeeSchool()).thenReturn(optionalSchool);

        Optional<School> result = employeeService.getEmployeeSchool();

        assertNotNull(result);
        assertTrue(result.isPresent());
        assertSame(optionalSchool, result);

        verify(employeeQueryService, times(1)).getEmployeeSchool();
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void getEmployeeSchool_shouldReturnEmptyOptional() {
        when(employeeQueryService.getEmployeeSchool()).thenReturn(Optional.empty());

        Optional<School> result = employeeService.getEmployeeSchool();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(employeeQueryService, times(1)).getEmployeeSchool();
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void findAllByRole_shouldReturnOptionalEmployeeList() {
        Employee employee = new Employee();
        employee.setEmployeeId("E1");
        employee.setEmail("teacher@school.com");

        Optional<List<Employee>> employees = Optional.of(List.of(employee));

        when(employeeQueryService.findAllByRole("TEACHER")).thenReturn(employees);

        Optional<List<Employee>> result = employeeService.findAllByRole("TEACHER");

        assertNotNull(result);
        assertTrue(result.isPresent());
        assertEquals(1, result.get().size());
        assertSame(employees, result);

        verify(employeeQueryService, times(1)).findAllByRole("TEACHER");
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void findAllByRole_shouldReturnEmptyOptional() {
        when(employeeQueryService.findAllByRole("ADMIN")).thenReturn(Optional.empty());

        Optional<List<Employee>> result = employeeService.findAllByRole("ADMIN");

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(employeeQueryService, times(1)).findAllByRole("ADMIN");
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void findByUserName_shouldReturnEmployee() {
        Employee employee = new Employee();
        employee.setEmployeeId("E1");
        employee.setEmail("teacher@school.com");

        when(employeeQueryService.findByUserName("teacher@school.com")).thenReturn(employee);

        Employee result = employeeService.findByUserName("teacher@school.com");

        assertNotNull(result);
        assertSame(employee, result);
        assertEquals("teacher@school.com", result.getEmail());

        verify(employeeQueryService, times(1)).findByUserName("teacher@school.com");
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void findByUserName_shouldReturnNull_whenNotFound() {
        when(employeeQueryService.findByUserName("missing@school.com")).thenReturn(null);

        Employee result = employeeService.findByUserName("missing@school.com");

        assertNull(result);

        verify(employeeQueryService, times(1)).findByUserName("missing@school.com");
        verifyNoInteractions(employeeCreationService, employeeMutationService, employeeClassService);
        verifyNoMoreInteractions(employeeQueryService);
    }

    @Test
    void deleteEmployee_shouldDelegateSuccessfully() throws Exception {
        doNothing().when(employeeMutationService).deleteEmployee("E1");

        assertDoesNotThrow(() -> employeeService.deleteEmployee("E1"));

        verify(employeeMutationService, times(1)).deleteEmployee("E1");
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void deleteEmployee_shouldThrowException() throws Exception {
        doThrow(new Exception("Delete failed")).when(employeeMutationService).deleteEmployee("E1");

        Exception exception = assertThrows(
                Exception.class,
                () -> employeeService.deleteEmployee("E1")
        );

        assertEquals("Delete failed", exception.getMessage());

        verify(employeeMutationService, times(1)).deleteEmployee("E1");
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void spendCurrency_shouldReturnStudentList() {
        CurrencySpendRequest request = new CurrencySpendRequest();
        List<CurrencySpendRequest> requests = List.of(request);

        Student student = new Student();
        List<Student> students = List.of(student);

        when(employeeMutationService.spendCurrency(requests)).thenReturn(students);

        List<Student> result = employeeService.spendCurrency(requests);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertSame(students, result);

        verify(employeeMutationService, times(1)).spendCurrency(requests);
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void spendCurrency_shouldReturnEmptyList() {
        List<CurrencySpendRequest> requests = List.of();

        when(employeeMutationService.spendCurrency(requests)).thenReturn(List.of());

        List<Student> result = employeeService.spendCurrency(requests);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(employeeMutationService, times(1)).spendCurrency(requests);
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void editSchool_shouldReturnUpdatedEmployees() {
        Employee employee = new Employee();
        employee.setSchool("New School");

        List<Employee> employees = List.of(employee);

        when(employeeMutationService.editSchool("New School")).thenReturn(employees);

        List<Employee> result = employeeService.editSchool("New School");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertSame(employees, result);

        verify(employeeMutationService, times(1)).editSchool("New School");
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void editSchool_shouldReturnEmptyList() {
        when(employeeMutationService.editSchool("Unknown School")).thenReturn(List.of());

        List<Employee> result = employeeService.editSchool("Unknown School");

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(employeeMutationService, times(1)).editSchool("Unknown School");
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void transferCurrency_shouldDelegateSuccessfully() {
        CurrencyTransferRequest request = new CurrencyTransferRequest();

        doNothing().when(employeeMutationService).transferCurrency(request);

        assertDoesNotThrow(() -> employeeService.transferCurrency(request));

        verify(employeeMutationService, times(1)).transferCurrency(request);
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void transferCurrency_shouldThrowRuntimeException() {
        CurrencyTransferRequest request = new CurrencyTransferRequest();

        doThrow(new RuntimeException("Transfer failed")).when(employeeMutationService).transferCurrency(request);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> employeeService.transferCurrency(request)
        );

        assertEquals("Transfer failed", exception.getMessage());

        verify(employeeMutationService, times(1)).transferCurrency(request);
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void updateAllEmployees_shouldReturnUpdatedEmployees() {
        Employee employee1 = new Employee();
        employee1.setEmployeeId("E1");

        Employee employee2 = new Employee();
        employee2.setEmployeeId("E2");

        List<Employee> employees = List.of(employee1, employee2);

        when(employeeMutationService.updateAllEmployees()).thenReturn(employees);

        List<Employee> result = employeeService.updateAllEmployees();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertSame(employees, result);

        verify(employeeMutationService, times(1)).updateAllEmployees();
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void updateAllEmployees_shouldReturnEmptyList() {
        when(employeeMutationService.updateAllEmployees()).thenReturn(List.of());

        List<Employee> result = employeeService.updateAllEmployees();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(employeeMutationService, times(1)).updateAllEmployees();
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeClassService);
        verifyNoMoreInteractions(employeeMutationService);
    }

    @Test
    void addOrUpdateClassToEmployee_shouldReturnUpdatedEmployee() {
        ClassRequest classRequest = new ClassRequest();
        Employee employee = new Employee();
        employee.setEmail("teacher@school.com");

        when(employeeClassService.addOrUpdateClassToEmployee("teacher@school.com", classRequest)).thenReturn(employee);

        Employee result = employeeService.addOrUpdateClassToEmployee("teacher@school.com", classRequest);

        assertNotNull(result);
        assertSame(employee, result);
        assertEquals("teacher@school.com", result.getEmail());

        verify(employeeClassService, times(1)).addOrUpdateClassToEmployee("teacher@school.com", classRequest);
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeMutationService);
        verifyNoMoreInteractions(employeeClassService);
    }

    @Test
    void addOrUpdateClassToEmployee_shouldThrowNullPointerException() {
        ClassRequest classRequest = new ClassRequest();

        when(employeeClassService.addOrUpdateClassToEmployee("teacher@school.com", classRequest))
                .thenThrow(new NullPointerException("Class cannot be null"));

        NullPointerException exception = assertThrows(
                NullPointerException.class,
                () -> employeeService.addOrUpdateClassToEmployee("teacher@school.com", classRequest)
        );

        assertEquals("Class cannot be null", exception.getMessage());

        verify(employeeClassService, times(1)).addOrUpdateClassToEmployee("teacher@school.com", classRequest);
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeMutationService);
        verifyNoMoreInteractions(employeeClassService);
    }

    @Test
    void removeClassFromEmployee_shouldReturnUpdatedEmployee() throws ResourceNotFoundException {
        ClassRequest classRequest = new ClassRequest();
        Employee employee = new Employee();
        employee.setEmail("teacher@school.com");

        when(employeeClassService.removeClassFromEmployee("teacher@school.com", classRequest)).thenReturn(employee);

        Employee result = employeeService.removeClassFromEmployee("teacher@school.com", classRequest);

        assertNotNull(result);
        assertSame(employee, result);
        assertEquals("teacher@school.com", result.getEmail());

        verify(employeeClassService, times(1)).removeClassFromEmployee("teacher@school.com", classRequest);
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeMutationService);
        verifyNoMoreInteractions(employeeClassService);
    }

    @Test
    void removeClassFromEmployee_shouldThrowResourceNotFoundException() throws ResourceNotFoundException {
        ClassRequest classRequest = new ClassRequest();

        when(employeeClassService.removeClassFromEmployee("teacher@school.com", classRequest))
                .thenThrow(new ResourceNotFoundException("Class not found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> employeeService.removeClassFromEmployee("teacher@school.com", classRequest)
        );

        assertEquals("Class not found", exception.getMessage());

        verify(employeeClassService, times(1)).removeClassFromEmployee("teacher@school.com", classRequest);
        verifyNoInteractions(employeeCreationService, employeeQueryService, employeeMutationService);
        verifyNoMoreInteractions(employeeClassService);
    }
}
