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
    void spendCurrency_shouldSpendCurrencyForEachStudent() {
        CurrencySpendRequest request1 = new CurrencySpendRequest();
        request1.setStudentEmail("student1@school.com");
        request1.setCurrencyTransferred(5);

        CurrencySpendRequest request2 = new CurrencySpendRequest();
        request2.setStudentEmail("student2@school.com");
        request2.setCurrencyTransferred(3);

        Student student1 = new Student();
        student1.setStudentEmail("student1@school.com");
        student1.setCurrency(20);

        Student student2 = new Student();
        student2.setStudentEmail("student2@school.com");
        student2.setCurrency(10);

        Student savedStudent1 = new Student();
        savedStudent1.setStudentEmail("student1@school.com");
        savedStudent1.setCurrency(15);

        Student savedStudent2 = new Student();
        savedStudent2.setStudentEmail("student2@school.com");
        savedStudent2.setCurrency(7);

        when(studentRepository.findByStudentEmailIgnoreCase("student1@school.com")).thenReturn(student1);
        when(studentRepository.findByStudentEmailIgnoreCase("student2@school.com")).thenReturn(student2);
        when(studentRepository.save(student1)).thenReturn(savedStudent1);
        when(studentRepository.save(student2)).thenReturn(savedStudent2);

        List<Student> result = employeeMutationService.spendCurrency(List.of(request1, request2));

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals("student1@school.com", result.get(0).getStudentEmail());
        assertEquals(15, result.get(0).getCurrency());

        assertEquals("student2@school.com", result.get(1).getStudentEmail());
        assertEquals(7, result.get(1).getCurrency());

        assertEquals(15, student1.getCurrency());
        assertEquals(7, student2.getCurrency());

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("student1@school.com");
        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("student2@school.com");
        verify(studentRepository, times(1)).save(student1);
        verify(studentRepository, times(1)).save(student2);
        verifyNoMoreInteractions(studentRepository);
        verifyNoInteractions(employeeRepository);
    }

    @Test
    void spendCurrency_shouldReturnEmptyList_whenRequestsAreEmpty() {
        List<Student> result = employeeMutationService.spendCurrency(List.of());

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verifyNoInteractions(employeeRepository, studentRepository);
    }

    @Test
    void editSchool_shouldSetCurrencyToFiveForAllEmployeesInSchool() {
        Employee employee1 = new Employee();
        employee1.setEmployeeId("E1");
        employee1.setSchool("Test School");
        employee1.setCurrency(0);

        Employee employee2 = new Employee();
        employee2.setEmployeeId("E2");
        employee2.setSchool("Test School");
        employee2.setCurrency(12);

        when(employeeRepository.findBySchool("Test School")).thenReturn(List.of(employee1, employee2));
        when(employeeRepository.save(employee1)).thenReturn(employee1);
        when(employeeRepository.save(employee2)).thenReturn(employee2);

        List<Employee> result = employeeMutationService.editSchool("Test School");

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(5, result.get(0).getCurrency());
        assertEquals(5, result.get(1).getCurrency());

        assertEquals(5, employee1.getCurrency());
        assertEquals(5, employee2.getCurrency());

        verify(employeeRepository, times(1)).findBySchool("Test School");
        verify(employeeRepository, times(1)).save(employee1);
        verify(employeeRepository, times(1)).save(employee2);
        verifyNoMoreInteractions(employeeRepository);
        verifyNoInteractions(studentRepository);
    }

    @Test
    void editSchool_shouldReturnEmptyList_whenNoEmployeesFound() {
        when(employeeRepository.findBySchool("Empty School")).thenReturn(List.of());

        List<Employee> result = employeeMutationService.editSchool("Empty School");

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(employeeRepository, times(1)).findBySchool("Empty School");
        verify(employeeRepository, never()).save(any(Employee.class));
        verifyNoMoreInteractions(employeeRepository);
        verifyNoInteractions(studentRepository);
    }

    @Test
    void transferCurrency_shouldTransferCurrency_whenTeacherHasEnough() {
        CurrencyTransferRequest request = new CurrencyTransferRequest();
        request.setTeacherEmail("teacher@school.com");
        request.setStudentEmail("student@school.com");
        request.setCurrencyTransferred(5);

        Employee teacher = new Employee();
        teacher.setEmail("teacher@school.com");
        teacher.setCurrency(20);

        Student student = new Student();
        student.setStudentEmail("student@school.com");
        student.setCurrency(7);

        when(employeeRepository.findByEmailIgnoreCase("teacher@school.com")).thenReturn(teacher);
        when(studentRepository.findByStudentEmailIgnoreCase("student@school.com")).thenReturn(student);
        when(employeeRepository.save(teacher)).thenReturn(teacher);
        when(studentRepository.save(student)).thenReturn(student);

        assertDoesNotThrow(() -> employeeMutationService.transferCurrency(request));

        assertEquals(15, teacher.getCurrency());
        assertEquals(12, student.getCurrency());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase("teacher@school.com");
        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("student@school.com");
        verify(employeeRepository, times(1)).save(teacher);
        verify(studentRepository, times(1)).save(student);
        verifyNoMoreInteractions(employeeRepository, studentRepository);
    }

    @Test
    void transferCurrency_shouldThrowResourceNotFoundException_whenTeacherDoesNotHaveEnoughCurrency() {
        CurrencyTransferRequest request = new CurrencyTransferRequest();
        request.setTeacherEmail("teacher@school.com");
        request.setStudentEmail("student@school.com");
        request.setCurrencyTransferred(10);

        Employee teacher = new Employee();
        teacher.setEmail("teacher@school.com");
        teacher.setCurrency(5);

        Student student = new Student();
        student.setStudentEmail("student@school.com");
        student.setCurrency(7);

        when(employeeRepository.findByEmailIgnoreCase("teacher@school.com")).thenReturn(teacher);
        when(studentRepository.findByStudentEmailIgnoreCase("student@school.com")).thenReturn(student);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> employeeMutationService.transferCurrency(request)
        );

        assertEquals("You do not have enough currency to give", exception.getMessage());
        assertEquals(5, teacher.getCurrency());
        assertEquals(7, student.getCurrency());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase("teacher@school.com");
        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("student@school.com");
        verify(employeeRepository, never()).save(any(Employee.class));
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(employeeRepository, studentRepository);
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
}
