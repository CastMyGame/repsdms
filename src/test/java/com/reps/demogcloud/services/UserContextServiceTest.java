package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.EntityNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserContextServiceTest {

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private UserContextService userContextService;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getCurrentUserSchool_shouldReturnStudentSchool_whenCurrentUserIsStudent() {
        setAuthenticatedUser("student@test.com");

        UserModel userModel = buildUser("student@test.com", "STUDENT");
        Student student = mock(Student.class);

        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(student.getSchool()).thenReturn("Lincoln High");

        String result = userContextService.getCurrentUserSchool();

        assertEquals("Lincoln High", result);
        verify(studentRepository).findByStudentEmailIgnoreCase("student@test.com");
        verify(employeeRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void getCurrentUserSchool_shouldThrowException_whenStudentNotFound() {
        setAuthenticatedUser("student@test.com");

        UserModel userModel = buildUser("student@test.com", "STUDENT");

        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);

        EntityNotFoundException ex = assertThrows(
                EntityNotFoundException.class,
                () -> userContextService.getCurrentUserSchool()
        );

        assertEquals("Student not found with email: student@test.com", ex.getMessage());
        verify(studentRepository).findByStudentEmailIgnoreCase("student@test.com");
        verify(employeeRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void getCurrentUserSchool_shouldReturnEmployeeSchool_whenCurrentUserIsNotStudent() {
        setAuthenticatedUser("teacher@test.com");

        UserModel userModel = buildUser("teacher@test.com", "TEACHER");
        Employee employee = mock(Employee.class);

        when(userAccountService.loadUserModelByUsername("teacher@test.com")).thenReturn(userModel);
        when(employeeRepository.findByEmailIgnoreCase("teacher@test.com")).thenReturn(employee);
        when(employee.getSchool()).thenReturn("Roosevelt High");

        String result = userContextService.getCurrentUserSchool();

        assertEquals("Roosevelt High", result);
        verify(employeeRepository).findByEmailIgnoreCase("teacher@test.com");
        verify(studentRepository, never()).findByStudentEmailIgnoreCase(anyString());
    }

    @Test
    void getCurrentUserSchool_shouldThrowException_whenEmployeeNotFound() {
        setAuthenticatedUser("teacher@test.com");

        UserModel userModel = buildUser("teacher@test.com", "TEACHER");

        when(userAccountService.loadUserModelByUsername("teacher@test.com")).thenReturn(userModel);
        when(employeeRepository.findByEmailIgnoreCase("teacher@test.com")).thenReturn(null);

        EntityNotFoundException ex = assertThrows(
                EntityNotFoundException.class,
                () -> userContextService.getCurrentUserSchool()
        );

        assertEquals("Employee not found with email: teacher@test.com", ex.getMessage());
        verify(employeeRepository).findByEmailIgnoreCase("teacher@test.com");
        verify(studentRepository, never()).findByStudentEmailIgnoreCase(anyString());
    }

    @Test
    void getCurrentUserEmail_shouldReturnAuthenticatedUsername() {
        setAuthenticatedUser("user@test.com");

        String result = userContextService.getCurrentUserEmail();

        assertEquals("user@test.com", result);
    }

    @Test
    void isCurrentUserStudent_shouldReturnTrue_whenUserHasStudentRole() {
        setAuthenticatedUser("student@test.com");

        UserModel userModel = buildUser("student@test.com", "STUDENT");

        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);

        boolean result = userContextService.isCurrentUserStudent();

        assertTrue(result);
    }

    @Test
    void isCurrentUserStudent_shouldReturnTrue_whenUserHasStudentRoleDifferentCase() {
        setAuthenticatedUser("student@test.com");

        UserModel userModel = buildUser("student@test.com", "student");

        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);

        boolean result = userContextService.isCurrentUserStudent();

        assertTrue(result);
    }

    @Test
    void isCurrentUserStudent_shouldReturnFalse_whenUserDoesNotHaveStudentRole() {
        setAuthenticatedUser("teacher@test.com");

        UserModel userModel = buildUser("teacher@test.com", "TEACHER");

        when(userAccountService.loadUserModelByUsername("teacher@test.com")).thenReturn(userModel);

        boolean result = userContextService.isCurrentUserStudent();

        assertFalse(result);
    }

    @Test
    void isCurrentUserStudent_shouldReturnFalse_whenUserRolesAreNull() {
        setAuthenticatedUser("user@test.com");

        UserModel userModel = new UserModel();
        userModel.setUsername("user@test.com");
        userModel.setRoles(null);

        when(userAccountService.loadUserModelByUsername("user@test.com")).thenReturn(userModel);

        boolean result = userContextService.isCurrentUserStudent();

        assertFalse(result);
    }

    @Test
    void getCurrentUsername_shouldReturnAuthenticatedUsername() {
        setAuthenticatedUser("anotheruser@test.com");

        String result = userContextService.getCurrentUsername();

        assertEquals("anotheruser@test.com", result);
    }

    @Test
    void getSchoolForCurrentUser_shouldDelegateToGetCurrentUserSchool() {
        setAuthenticatedUser("student@test.com");

        UserModel userModel = buildUser("student@test.com", "STUDENT");
        Student student = mock(Student.class);

        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(student.getSchool()).thenReturn("Central High");

        String result = userContextService.getSchoolForCurrentUser();

        assertEquals("Central High", result);
    }

    @Test
    void isStudent_shouldDelegateToIsCurrentUserStudent() {
        setAuthenticatedUser("student@test.com");

        UserModel userModel = buildUser("student@test.com", "STUDENT");

        when(userAccountService.loadUserModelByUsername("student@test.com")).thenReturn(userModel);

        boolean result = userContextService.isStudent();

        assertTrue(result);
    }

    @Test
    void getCurrentUserEmail_shouldThrowException_whenAuthenticationIsMissing() {
        SecurityContextHolder.clearContext();

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> userContextService.getCurrentUserEmail()
        );

        assertEquals("No authenticated user found in security context", ex.getMessage());
    }

    @Test
    void getCurrentUserSchool_shouldThrowException_whenAuthenticationIsNotAuthenticated() {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken("user@test.com", "password");
        auth.setAuthenticated(false);
        SecurityContextHolder.getContext().setAuthentication(auth);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> userContextService.getCurrentUserSchool()
        );

        assertEquals("No authenticated user found in security context", ex.getMessage());
    }

    private void setAuthenticatedUser(String username) {
        TestingAuthenticationToken auth =
                new TestingAuthenticationToken(username, "password");
        auth.setAuthenticated(true);
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private UserModel buildUser(String username, String roleName) {
        RoleModel role = new RoleModel();
        role.setRole(roleName);

        UserModel user = new UserModel();
        user.setUsername(username);
        user.setRoles(Set.of(role));

        return user;
    }
}
