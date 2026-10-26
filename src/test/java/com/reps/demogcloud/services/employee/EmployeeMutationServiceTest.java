package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.CurrencyTransferRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.CurrencySpendRequest;
import com.reps.demogcloud.models.student.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeMutationServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock com.reps.demogcloud.services.currency.CurrencyService currency;

    @InjectMocks
    private EmployeeMutationService employeeMutationService;

    @Test
    void deleteEmployee_shouldDeleteEmployee_whenEmployeeExists() throws Exception {
        String employeeId = "E1";
        Employee employee = new Employee();
        employee.setEmployeeId(employeeId);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        doNothing().when(employeeRepository).deleteById(employeeId);

        assertDoesNotThrow(() -> employeeMutationService.deleteEmployee(employeeId));

        verify(employeeRepository, times(1)).findById(employeeId);
        verify(employeeRepository, times(1)).deleteById(employeeId);
        verifyNoMoreInteractions(employeeRepository);
        verifyNoInteractions(studentRepository);
    }

    @Test
    void deleteEmployee_shouldThrowException_whenEmployeeDoesNotExist() {
        String employeeId = "MISSING";

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.empty());

        Exception exception = assertThrows(
                Exception.class,
                () -> employeeMutationService.deleteEmployee(employeeId)
        );

        assertEquals("Employee with ID MISSING does not exist", exception.getMessage());

        verify(employeeRepository, times(1)).findById(employeeId);
        verify(employeeRepository, never()).deleteById(anyString());
        verifyNoMoreInteractions(employeeRepository);
        verifyNoInteractions(studentRepository);
    }

    @Test
    void deleteEmployee_shouldPropagateException_whenDeleteByIdFails() throws Exception {
        String employeeId = "E1";
        Employee employee = new Employee();
        employee.setEmployeeId(employeeId);

        when(employeeRepository.findById(employeeId)).thenReturn(Optional.of(employee));
        doThrow(new RuntimeException("database failure")).when(employeeRepository).deleteById(employeeId);

        RuntimeException exception = assertThrows(
                RuntimeException.class,
                () -> employeeMutationService.deleteEmployee(employeeId)
        );

        assertEquals("database failure", exception.getMessage());

        verify(employeeRepository, times(1)).findById(employeeId);
        verify(employeeRepository, times(1)).deleteById(employeeId);
        verifyNoMoreInteractions(employeeRepository);
        verifyNoInteractions(studentRepository);
    }













    @Test
    void updateAllEmployees_shouldInitializeClassesOnlyForEmployeesWithNullClasses() {
        Employee teacherWithNullClasses = new Employee();
        teacherWithNullClasses.setEmployeeId("E1");
        teacherWithNullClasses.setClasses(null);

        Employee.ClassRoster existingRoster = new Employee.ClassRoster();
        existingRoster.setClassName("Math");
        existingRoster.setClassPeriod("1");

        Employee teacherWithExistingClasses = new Employee();
        teacherWithExistingClasses.setEmployeeId("E2");
        teacherWithExistingClasses.setClasses(List.of(existingRoster));

        when(employeeRepository.findAll()).thenReturn(List.of(teacherWithNullClasses, teacherWithExistingClasses));
        when(employeeRepository.save(teacherWithNullClasses)).thenReturn(teacherWithNullClasses);

        List<Employee> result = employeeMutationService.updateAllEmployees();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("E1", result.get(0).getEmployeeId());

        assertNotNull(teacherWithNullClasses.getClasses());
        assertEquals(1, teacherWithNullClasses.getClasses().size());

        Employee.ClassRoster createdRoster = teacherWithNullClasses.getClasses().get(0);
        assertEquals("", createdRoster.getClassName());
        assertEquals("", createdRoster.getClassPeriod());
        assertEquals(0, createdRoster.getPunishmentsThisWeek());
        assertNotNull(createdRoster.getClassRoster());
        assertTrue(createdRoster.getClassRoster().isEmpty());

        assertEquals(1, teacherWithExistingClasses.getClasses().size());
        assertEquals("Math", teacherWithExistingClasses.getClasses().get(0).getClassName());
        assertEquals("1", teacherWithExistingClasses.getClasses().get(0).getClassPeriod());

        verify(employeeRepository, times(1)).findAll();
        verify(employeeRepository, times(1)).save(teacherWithNullClasses);
        verify(employeeRepository, never()).save(teacherWithExistingClasses);
        verifyNoMoreInteractions(employeeRepository);
        verifyNoInteractions(studentRepository);
    }

    @Test
    void updateAllEmployees_shouldReturnEmptyList_whenNoEmployeesNeedUpdating() {
        Employee.ClassRoster existingRoster = new Employee.ClassRoster();
        existingRoster.setClassName("Science");
        existingRoster.setClassPeriod("2");
        existingRoster.setClassRoster(new ArrayList<>());
        existingRoster.setPunishmentsThisWeek(0);

        Employee teacher = new Employee();
        teacher.setEmployeeId("E1");
        teacher.setClasses(List.of(existingRoster));

        when(employeeRepository.findAll()).thenReturn(List.of(teacher));

        List<Employee> result = employeeMutationService.updateAllEmployees();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(employeeRepository, times(1)).findAll();
        verify(employeeRepository, never()).save(any(Employee.class));
        verifyNoMoreInteractions(employeeRepository);
        verifyNoInteractions(studentRepository);
    }

    @Test
    void updateAllEmployees_shouldReturnEmptyList_whenRepositoryFindAllReturnsEmpty() {
        when(employeeRepository.findAll()).thenReturn(List.of());

        List<Employee> result = employeeMutationService.updateAllEmployees();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(employeeRepository, times(1)).findAll();
        verify(employeeRepository, never()).save(any(Employee.class));
        verifyNoMoreInteractions(employeeRepository);
        verifyNoInteractions(studentRepository);
    }
    @Test void currencyOperationsDelegateToTransactionBoundary() {
        var requests=List.<CurrencySpendRequest>of();
        when(currency.spend(requests)).thenReturn(List.of());
        when(currency.resetSchool("School")).thenReturn(List.of());
        assertEquals(List.of(),employeeMutationService.spendCurrency(requests));
        assertEquals(List.of(),employeeMutationService.editSchool("School"));
        verify(currency).spend(requests);verify(currency).resetSchool("School");
        verifyNoInteractions(studentRepository,employeeRepository);
    }
    @Test void directUnrecordedTransferIsRejected() {
        assertThrows(IllegalStateException.class,()->employeeMutationService.transferCurrency(new CurrencyTransferRequest("a","b",1)));
        verifyNoInteractions(studentRepository,employeeRepository,currency);
    }
}
