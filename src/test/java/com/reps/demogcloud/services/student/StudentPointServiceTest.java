package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.student.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentPointServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentPointService studentPointService;

    @Test
    void addPoints_shouldAddPointsAndSaveStudent() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setPoints(10);

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(studentRepository.save(student)).thenReturn(student);

        Student result = studentPointService.addPoints("student@test.com", 5);

        assertNotNull(result);
        assertEquals(15, result.getPoints());

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("student@test.com");
        verify(studentRepository, times(1)).save(student);
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void addPoints_shouldThrowWhenStudentNotFound() {
        when(studentRepository.findByStudentEmailIgnoreCase("missing@test.com")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentPointService.addPoints("missing@test.com", 5)
        );

        assertEquals("Student can not be found", exception.getMessage());

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("missing@test.com");
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void deletePoints_shouldSubtractPointsAndSaveStudent() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setPoints(20);

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(studentRepository.save(student)).thenReturn(student);

        Student result = studentPointService.deletePoints("student@test.com", 5);

        assertNotNull(result);
        assertEquals(15, result.getPoints());

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("student@test.com");
        verify(studentRepository, times(1)).save(student);
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void deletePoints_shouldThrowWhenStudentNotFound() {
        when(studentRepository.findByStudentEmailIgnoreCase("missing@test.com")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentPointService.deletePoints("missing@test.com", 5)
        );

        assertEquals("Student can not be found", exception.getMessage());

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("missing@test.com");
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void deletePoints_shouldThrowWhenNotEnoughPoints() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setPoints(3);

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentPointService.deletePoints("student@test.com", 5)
        );

        assertEquals("You do not have enough points to redeem this", exception.getMessage());

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("student@test.com");
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void transferPoints_shouldTransferPointsBetweenStudentsAndSaveBoth() {
        Student givingStudent = new Student();
        givingStudent.setStudentEmail("giver@test.com");
        givingStudent.setPoints(20);

        Student receivingStudent = new Student();
        receivingStudent.setStudentEmail("receiver@test.com");
        receivingStudent.setPoints(5);

        when(studentRepository.findByStudentEmailIgnoreCase("giver@test.com")).thenReturn(givingStudent);
        when(studentRepository.findByStudentEmailIgnoreCase("receiver@test.com")).thenReturn(receivingStudent);
        when(studentRepository.save(givingStudent)).thenReturn(givingStudent);
        when(studentRepository.save(receivingStudent)).thenReturn(receivingStudent);

        List<Student> result = studentPointService.transferPoints("giver@test.com", "receiver@test.com", 7);

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(13, givingStudent.getPoints());
        assertEquals(12, receivingStudent.getPoints());
        assertSame(givingStudent, result.get(0));
        assertSame(receivingStudent, result.get(1));

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("giver@test.com");
        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("receiver@test.com");
        verify(studentRepository, times(1)).save(givingStudent);
        verify(studentRepository, times(1)).save(receivingStudent);
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void transferPoints_shouldThrowWhenGivingStudentNotFound() {
        when(studentRepository.findByStudentEmailIgnoreCase("giver@test.com")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentPointService.transferPoints("giver@test.com", "receiver@test.com", 5)
        );

        assertEquals("Giving student can not be found", exception.getMessage());

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("giver@test.com");
        verify(studentRepository, never()).findByStudentEmailIgnoreCase("receiver@test.com");
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void transferPoints_shouldThrowWhenReceivingStudentNotFound() {
        Student givingStudent = new Student();
        givingStudent.setStudentEmail("giver@test.com");
        givingStudent.setPoints(20);

        when(studentRepository.findByStudentEmailIgnoreCase("giver@test.com")).thenReturn(givingStudent);
        when(studentRepository.findByStudentEmailIgnoreCase("receiver@test.com")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentPointService.transferPoints("giver@test.com", "receiver@test.com", 5)
        );

        assertEquals("Receiving student can not be found", exception.getMessage());

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("giver@test.com");
        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("receiver@test.com");
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void transferPoints_shouldThrowWhenGivingStudentDoesNotHaveEnoughPoints() {
        Student givingStudent = new Student();
        givingStudent.setStudentEmail("giver@test.com");
        givingStudent.setPoints(2);

        Student receivingStudent = new Student();
        receivingStudent.setStudentEmail("receiver@test.com");
        receivingStudent.setPoints(5);

        when(studentRepository.findByStudentEmailIgnoreCase("giver@test.com")).thenReturn(givingStudent);
        when(studentRepository.findByStudentEmailIgnoreCase("receiver@test.com")).thenReturn(receivingStudent);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentPointService.transferPoints("giver@test.com", "receiver@test.com", 5)
        );

        assertEquals("You do not have enough points to give", exception.getMessage());

        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("giver@test.com");
        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("receiver@test.com");
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(studentRepository);
    }
}