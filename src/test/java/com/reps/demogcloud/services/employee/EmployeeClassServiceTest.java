package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.employee.Employee;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmployeeClassServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeeClassService employeeClassService;

    @Test
    void addOrUpdateClassToEmployee_shouldInitializeClassesAndAddNewClass_whenTeacherClassesAreNull() {
        String teacherEmail = "teacher@school.com";

        Employee teacher = new Employee();
        teacher.setEmail(teacherEmail);
        teacher.setClasses(null);

        Employee.ClassRoster newRoster = new Employee.ClassRoster();
        newRoster.setClassName("Math");
        newRoster.setClassPeriod("1");
        newRoster.setClassRoster(List.of("student1@school.com", "student2@school.com"));
        newRoster.setPunishmentsThisWeek(2);

        ClassRequest request = new ClassRequest();
        request.setClassToUpdate(newRoster);

        when(employeeRepository.findByEmailIgnoreCase(teacherEmail)).thenReturn(teacher);
        when(employeeRepository.save(teacher)).thenReturn(teacher);

        Employee result = employeeClassService.addOrUpdateClassToEmployee(teacherEmail, request);

        assertNotNull(result);
        assertNotNull(result.getClasses());
        assertEquals(1, result.getClasses().size());
        assertEquals("Math", result.getClasses().get(0).getClassName());
        assertEquals("1", result.getClasses().get(0).getClassPeriod());
        assertEquals(2, result.getClasses().get(0).getPunishmentsThisWeek());
        assertEquals(2, result.getClasses().get(0).getClassRoster().size());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase(teacherEmail);
        verify(employeeRepository, times(1)).save(teacher);
        verifyNoMoreInteractions(employeeRepository);
    }

    @Test
    void addOrUpdateClassToEmployee_shouldAddNewClass_whenTeacherExistsAndClassDoesNotExist() {
        String teacherEmail = "teacher@school.com";

        Employee.ClassRoster existingRoster = new Employee.ClassRoster();
        existingRoster.setClassName("Science");
        existingRoster.setClassPeriod("2");
        existingRoster.setClassRoster(new ArrayList<>());
        existingRoster.setPunishmentsThisWeek(0);

        Employee teacher = new Employee();
        teacher.setEmail(teacherEmail);
        teacher.setClasses(new ArrayList<>(List.of(existingRoster)));

        Employee.ClassRoster newRoster = new Employee.ClassRoster();
        newRoster.setClassName("Math");
        newRoster.setClassPeriod("1");
        newRoster.setClassRoster(List.of("student1@school.com"));
        newRoster.setPunishmentsThisWeek(1);

        ClassRequest request = new ClassRequest();
        request.setClassToUpdate(newRoster);

        when(employeeRepository.findByEmailIgnoreCase(teacherEmail)).thenReturn(teacher);
        when(employeeRepository.save(teacher)).thenReturn(teacher);

        Employee result = employeeClassService.addOrUpdateClassToEmployee(teacherEmail, request);

        assertNotNull(result);
        assertEquals(2, result.getClasses().size());
        assertEquals("Science", result.getClasses().get(0).getClassName());
        assertEquals("Math", result.getClasses().get(1).getClassName());
        assertEquals("1", result.getClasses().get(1).getClassPeriod());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase(teacherEmail);
        verify(employeeRepository, times(1)).save(teacher);
        verifyNoMoreInteractions(employeeRepository);
    }

    @Test
    void addOrUpdateClassToEmployee_shouldUpdateExistingClass_whenClassAlreadyExists() {
        String teacherEmail = "teacher@school.com";

        Employee.ClassRoster existingRoster = new Employee.ClassRoster();
        existingRoster.setClassName("Math");
        existingRoster.setClassPeriod("1");
        existingRoster.setClassRoster(new ArrayList<>(List.of("oldStudent@school.com")));
        existingRoster.setPunishmentsThisWeek(0);

        Employee teacher = new Employee();
        teacher.setEmail(teacherEmail);
        teacher.setClasses(new ArrayList<>(List.of(existingRoster)));

        Employee.ClassRoster updatedRoster = new Employee.ClassRoster();
        updatedRoster.setClassName("Math");
        updatedRoster.setClassPeriod("9");
        updatedRoster.setClassRoster(List.of("newStudent1@school.com", "newStudent2@school.com"));
        updatedRoster.setPunishmentsThisWeek(4);

        ClassRequest request = new ClassRequest();
        request.setClassToUpdate(updatedRoster);

        when(employeeRepository.findByEmailIgnoreCase(teacherEmail)).thenReturn(teacher);
        when(employeeRepository.save(teacher)).thenReturn(teacher);

        Employee result = employeeClassService.addOrUpdateClassToEmployee(teacherEmail, request);

        assertNotNull(result);
        assertEquals(1, result.getClasses().size());

        Employee.ClassRoster savedRoster = result.getClasses().get(0);
        assertEquals("Math", savedRoster.getClassName());
        assertEquals("1", savedRoster.getClassPeriod());
        assertEquals(4, savedRoster.getPunishmentsThisWeek());
        assertEquals(2, savedRoster.getClassRoster().size());
        assertEquals("newStudent1@school.com", savedRoster.getClassRoster().get(0));
        assertEquals("newStudent2@school.com", savedRoster.getClassRoster().get(1));

        verify(employeeRepository, times(1)).findByEmailIgnoreCase(teacherEmail);
        verify(employeeRepository, times(1)).save(teacher);
        verifyNoMoreInteractions(employeeRepository);
    }

    @Test
    void addOrUpdateClassToEmployee_shouldThrowResourceNotFoundException_whenTeacherDoesNotExist() {
        String teacherEmail = "missing@school.com";

        Employee.ClassRoster roster = new Employee.ClassRoster();
        roster.setClassName("Math");

        ClassRequest request = new ClassRequest();
        request.setClassToUpdate(roster);

        when(employeeRepository.findByEmailIgnoreCase(teacherEmail)).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> employeeClassService.addOrUpdateClassToEmployee(teacherEmail, request)
        );

        assertEquals("Teacher with email missing@school.com not found", exception.getMessage());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase(teacherEmail);
        verify(employeeRepository, never()).save(any(Employee.class));
        verifyNoMoreInteractions(employeeRepository);
    }

    @Test
    void removeClassFromEmployee_shouldRemoveClassAndSave_whenClassExists() throws ResourceNotFoundException {
        String teacherEmail = "teacher@school.com";

        Employee.ClassRoster math = new Employee.ClassRoster();
        math.setClassName("Math");
        math.setClassPeriod("1");

        Employee.ClassRoster science = new Employee.ClassRoster();
        science.setClassName("Science");
        science.setClassPeriod("2");

        Employee teacher = new Employee();
        teacher.setEmail(teacherEmail);
        teacher.setClasses(new ArrayList<>(List.of(math, science)));

        Employee.ClassRoster classToDelete = new Employee.ClassRoster();
        classToDelete.setClassName("Math");

        ClassRequest request = new ClassRequest();
        request.setClassToUpdate(classToDelete);

        when(employeeRepository.findByEmailIgnoreCase(teacherEmail)).thenReturn(teacher);
        when(employeeRepository.save(teacher)).thenReturn(teacher);

        Employee result = employeeClassService.removeClassFromEmployee(teacherEmail, request);

        assertNotNull(result);
        assertEquals(1, result.getClasses().size());
        assertEquals("Science", result.getClasses().get(0).getClassName());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase(teacherEmail);
        verify(employeeRepository, times(1)).save(teacher);
        verifyNoMoreInteractions(employeeRepository);
    }

    @Test
    void removeClassFromEmployee_shouldThrowResourceNotFoundException_whenTeacherHasNoClasses() {
        String teacherEmail = "teacher@school.com";

        Employee teacher = new Employee();
        teacher.setEmail(teacherEmail);
        teacher.setClasses(null);

        Employee.ClassRoster classToDelete = new Employee.ClassRoster();
        classToDelete.setClassName("Math");

        ClassRequest request = new ClassRequest();
        request.setClassToUpdate(classToDelete);

        when(employeeRepository.findByEmailIgnoreCase(teacherEmail)).thenReturn(teacher);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> employeeClassService.removeClassFromEmployee(teacherEmail, request)
        );

        assertEquals("Teacher teacher@school.com has no classes to remove.", exception.getMessage());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase(teacherEmail);
        verify(employeeRepository, never()).save(any(Employee.class));
        verifyNoMoreInteractions(employeeRepository);
    }

    @Test
    void removeClassFromEmployee_shouldThrowResourceNotFoundException_whenClassDoesNotExist() {
        String teacherEmail = "teacher@school.com";

        Employee.ClassRoster science = new Employee.ClassRoster();
        science.setClassName("Science");
        science.setClassPeriod("2");

        Employee teacher = new Employee();
        teacher.setEmail(teacherEmail);
        teacher.setClasses(new ArrayList<>(List.of(science)));

        Employee.ClassRoster classToDelete = new Employee.ClassRoster();
        classToDelete.setClassName("Math");

        ClassRequest request = new ClassRequest();
        request.setClassToUpdate(classToDelete);

        when(employeeRepository.findByEmailIgnoreCase(teacherEmail)).thenReturn(teacher);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> employeeClassService.removeClassFromEmployee(teacherEmail, request)
        );

        assertEquals("Class with name Math not found for teacher teacher@school.com", exception.getMessage());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase(teacherEmail);
        verify(employeeRepository, never()).save(any(Employee.class));
        verifyNoMoreInteractions(employeeRepository);
    }

    @Test
    void removeClassFromEmployee_shouldThrowResourceNotFoundException_whenTeacherDoesNotExist() {
        String teacherEmail = "missing@school.com";

        Employee.ClassRoster classToDelete = new Employee.ClassRoster();
        classToDelete.setClassName("Math");

        ClassRequest request = new ClassRequest();
        request.setClassToUpdate(classToDelete);

        when(employeeRepository.findByEmailIgnoreCase(teacherEmail)).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> employeeClassService.removeClassFromEmployee(teacherEmail, request)
        );

        assertEquals("Teacher with email missing@school.com not found.", exception.getMessage());

        verify(employeeRepository, times(1)).findByEmailIgnoreCase(teacherEmail);
        verify(employeeRepository, never()).save(any(Employee.class));
        verifyNoMoreInteractions(employeeRepository);
    }
}
