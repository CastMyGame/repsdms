package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.services.AuthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentMutationServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private AuthService authService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private StudentQueryService studentQueryService;

    @InjectMocks
    private StudentMutationService studentMutationService;

    @Test
    void createNewStudent_shouldThrow_whenStudentEmailAlreadyExists() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");

        when(studentRepository.existsByStudentEmail("student@test.com")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentMutationService.createNewStudent(student)
        );

        assertEquals("A student with this email already exists.", exception.getMessage());

        verify(studentRepository, times(1)).existsByStudentEmail("student@test.com");
        verify(userRepository, never()).existsByUsername(anyString());
        verify(authService, never()).createEmployeeUser(any(AuthenticationRequest.class));
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(studentRepository, userRepository, authService, studentQueryService);
    }

    @Test
    void createNewStudent_shouldThrow_whenUserEmailAlreadyExists() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");

        when(studentRepository.existsByStudentEmail("student@test.com")).thenReturn(false);
        when(userRepository.existsByUsername("student@test.com")).thenReturn(true);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> studentMutationService.createNewStudent(student)
        );

        assertEquals("A user with this email has already been registered.", exception.getMessage());

        verify(studentRepository, times(1)).existsByStudentEmail("student@test.com");
        verify(userRepository, times(1)).existsByUsername("student@test.com");
        verify(authService, never()).createEmployeeUser(any(AuthenticationRequest.class));
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(studentRepository, userRepository, authService, studentQueryService);
    }

    @Test
    void createNewStudent_shouldCreateStudentAndReturnResponse_whenValid() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setSchool("Burke High");

        Student savedStudent = new Student();
        savedStudent.setStudentEmail("student@test.com");
        savedStudent.setFirstName("John");
        savedStudent.setLastName("Doe");
        savedStudent.setSchool("Burke High");
        savedStudent.setPoints(0);

        when(studentRepository.existsByStudentEmail("student@test.com")).thenReturn(false);
        when(userRepository.existsByUsername("student@test.com")).thenReturn(false);
        when(studentRepository.save(student)).thenReturn(savedStudent);

        StudentResponse result = studentMutationService.createNewStudent(student);

        assertNotNull(result);
        assertEquals("", result.getError());
        assertNotNull(result.getStudent());
        assertEquals("student@test.com", result.getStudent().getStudentEmail());
        assertEquals(0, student.getPoints());

        ArgumentCaptor<AuthenticationRequest> authCaptor = ArgumentCaptor.forClass(AuthenticationRequest.class);
        verify(authService, times(1)).createEmployeeUser(authCaptor.capture());

        AuthenticationRequest authRequest = authCaptor.getValue();
        assertEquals("student@test.com", authRequest.getUsername());
        assertEquals("student@test.com", authRequest.getPassword());
        assertEquals("John", authRequest.getFirstName());
        assertEquals("Doe", authRequest.getLastName());
        assertEquals("Burke High", authRequest.getSchool());

        Set<RoleModel> roles = authRequest.getRoles();
        assertNotNull(roles);
        assertEquals(1, roles.size());
        assertEquals("STUDENT", roles.iterator().next().getRole());

        verify(studentRepository, times(1)).existsByStudentEmail("student@test.com");
        verify(userRepository, times(1)).existsByUsername("student@test.com");
        verify(studentRepository, times(1)).save(student);
        verifyNoMoreInteractions(studentRepository, userRepository, authService, studentQueryService);
    }

    @Test
    void createNewStudent_shouldReturnErrorResponse_whenAuthServiceThrowsIllegalArgumentException() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setSchool("Burke High");

        when(studentRepository.existsByStudentEmail("student@test.com")).thenReturn(false);
        when(userRepository.existsByUsername("student@test.com")).thenReturn(false);
        doThrow(new IllegalArgumentException("Auth creation failed"))
                .when(authService).createEmployeeUser(any(AuthenticationRequest.class));

        StudentResponse result = studentMutationService.createNewStudent(student);

        assertNotNull(result);
        assertEquals("Auth creation failed", result.getError());
        assertNull(result.getStudent());
        assertEquals(0, student.getPoints());

        verify(studentRepository, times(1)).existsByStudentEmail("student@test.com");
        verify(userRepository, times(1)).existsByUsername("student@test.com");
        verify(authService, times(1)).createEmployeeUser(any(AuthenticationRequest.class));
        verify(studentRepository, never()).save(any(Student.class));
        verifyNoMoreInteractions(studentRepository, userRepository, authService, studentQueryService);
    }

    @Test
    void deleteStudent_shouldDeleteAndReturnMessage_whenStudentExists() throws Exception {
        Student student = new Student();
        student.setFirstName("John");
        student.setLastName("Doe");

        StudentRequest request = new StudentRequest();
        request.setStudent(student);

        doNothing().when(studentRepository).delete(student);

        String result = studentMutationService.deleteStudent(request);

        assertEquals("John Doe has been deleted", result);

        verify(studentRepository, times(1)).delete(student);
        verifyNoMoreInteractions(studentRepository, authService, userRepository, studentQueryService);
    }

    @Test
    void deleteStudent_shouldThrowException_whenDeleteFails() {
        Student student = new Student();
        student.setFirstName("John");
        student.setLastName("Doe");

        StudentRequest request = new StudentRequest();
        request.setStudent(student);

        doThrow(new RuntimeException("delete failed")).when(studentRepository).delete(student);

        Exception exception = assertThrows(
                Exception.class,
                () -> studentMutationService.deleteStudent(request)
        );

        assertEquals("That student does not exist", exception.getMessage());

        verify(studentRepository, times(1)).delete(student);
        verifyNoMoreInteractions(studentRepository, authService, userRepository, studentQueryService);
    }

    @Test
    void archiveRecord_shouldArchiveStudentAndSave() {
        Student existingStudent = new Student();
        existingStudent.setStudentEmail("student@test.com");
        existingStudent.setArchived(false);

        when(studentQueryService.findByStudentId("S-1")).thenReturn(existingStudent);
        when(studentRepository.save(existingStudent)).thenReturn(existingStudent);

        Student result = studentMutationService.archiveRecord("S-1");

        assertNotNull(result);
        assertTrue(result.isArchived());
        assertEquals("S-1", result.getArchivedBy());
        assertEquals(LocalDate.now(), result.getArchivedOn());

        verify(studentQueryService, times(1)).findByStudentId("S-1");
        verify(studentRepository, times(1)).save(existingStudent);
        verifyNoMoreInteractions(studentRepository, authService, userRepository, studentQueryService);
    }
}
