package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.EntityNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserAccountService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;


import java.util.HashSet;
import java.util.List;


import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SchoolUtilsTest {

    @Mock
    private UserAccountService userAccountService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private SchoolUtils schoolUtils;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void fetchSchoolName_shouldReturnStudentSchool_whenUserHasStudentRole() {
        String email = "student@test.com";

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "student@test.com",
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserModel userModel = new UserModel();
        userModel.setUsername(email);
        userModel.setRoles(new HashSet<>(List.of(buildRole("STUDENT"))));

        Student student = new Student();
        student.setStudentEmail(email);
        student.setSchool("Test Student School");

        when(userAccountService.loadUserModelByUsername(email)).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase(email)).thenReturn(student);

        String result = schoolUtils.fetchSchoolName();

        assertEquals("Test Student School", result);
        verify(userAccountService).loadUserModelByUsername(email);
        verify(studentRepository).findByStudentEmailIgnoreCase(email);
        verify(employeeRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void fetchSchoolName_shouldReturnEmployeeSchool_whenUserIsNotStudent() {
        String email = "teacher@test.com";

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        "teacher@test.com",
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserModel userModel = new UserModel();
        userModel.setUsername(email);
        userModel.setRoles(new HashSet<>(List.of(buildRole("TEACHER"))));

        Employee employee = new Employee();
        employee.setEmail(email);
        employee.setSchool("Test Employee School");

        when(userAccountService.loadUserModelByUsername(email)).thenReturn(userModel);
        when(employeeRepository.findByEmailIgnoreCase(email)).thenReturn(employee);

        String result = schoolUtils.fetchSchoolName();

        assertEquals("Test Employee School", result);
        verify(userAccountService).loadUserModelByUsername(email);
        verify(employeeRepository).findByEmailIgnoreCase(email);
        verify(studentRepository, never()).findByStudentEmailIgnoreCase(anyString());
    }

    @Test
    void fetchSchoolName_shouldThrow_whenAuthenticationIsNull() {
        SecurityContextHolder.clearContext();

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> schoolUtils.fetchSchoolName()
        );

        assertEquals("Authentication or principal is missing.", ex.getMessage());
        verifyNoInteractions(userAccountService, studentRepository, employeeRepository);
    }

    @Test
    void fetchSchoolName_shouldThrow_whenPrincipalIsNull() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        null,
                        "password",
                        List.of()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> schoolUtils.fetchSchoolName()
        );

        assertEquals("Authentication or principal is missing.", ex.getMessage());
        verifyNoInteractions(userAccountService, studentRepository, employeeRepository);
    }

    @Test
    void fetchSchoolName_shouldThrow_whenUserModelIsNull() {
        String email = "missing@test.com";

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        "password",
                        List.of()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        when(userAccountService.loadUserModelByUsername(email)).thenReturn(null);

        EntityNotFoundException ex = assertThrows(
                EntityNotFoundException.class,
                () -> schoolUtils.fetchSchoolName()
        );

        assertEquals("User with username " + email + " not found.", ex.getMessage());
        verify(userAccountService).loadUserModelByUsername(email);
        verifyNoInteractions(studentRepository, employeeRepository);
    }

    @Test
    void fetchSchoolName_shouldThrow_whenStudentNotFound() {
        String email = "student@test.com";

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserModel userModel = new UserModel();
        userModel.setUsername(email);
        userModel.setRoles(new HashSet<>(List.of(buildRole("STUDENT"))));

        when(userAccountService.loadUserModelByUsername(email)).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase(email)).thenReturn(null);

        EntityNotFoundException ex = assertThrows(
                EntityNotFoundException.class,
                () -> schoolUtils.fetchSchoolName()
        );

        assertEquals("Student with email " + email + " not found.", ex.getMessage());
        verify(studentRepository).findByStudentEmailIgnoreCase(email);
        verify(employeeRepository, never()).findByEmailIgnoreCase(anyString());
    }

    @Test
    void fetchSchoolName_shouldThrow_whenEmployeeNotFound() {
        String email = "teacher@test.com";

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_TEACHER"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserModel userModel = new UserModel();
        userModel.setUsername(email);
        userModel.setRoles(new HashSet<>(List.of(buildRole("TEACHER"))));

        when(userAccountService.loadUserModelByUsername(email)).thenReturn(userModel);
        when(employeeRepository.findByEmailIgnoreCase(email)).thenReturn(null);

        EntityNotFoundException ex = assertThrows(
                EntityNotFoundException.class,
                () -> schoolUtils.fetchSchoolName()
        );

        assertEquals("Employee with email " + email + " not found.", ex.getMessage());
        verify(employeeRepository).findByEmailIgnoreCase(email);
        verify(studentRepository, never()).findByStudentEmailIgnoreCase(anyString());
    }

    @Test
    void fetchSchoolName_shouldTreatNullRolesAsEmployeePath() {
        String email = "admin@test.com";

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        "password",
                        List.of()
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserModel userModel = new UserModel();
        userModel.setUsername(email);
        userModel.setRoles(null);

        Employee employee = new Employee();
        employee.setEmail(email);
        employee.setSchool("Admin School");

        when(userAccountService.loadUserModelByUsername(email)).thenReturn(userModel);
        when(employeeRepository.findByEmailIgnoreCase(email)).thenReturn(employee);

        String result = schoolUtils.fetchSchoolName();

        assertEquals("Admin School", result);
        verify(employeeRepository).findByEmailIgnoreCase(email);
    }

    @Test
    void fetchSchoolName_shouldMatchStudentRole_caseInsensitively() {
        String email = "student@test.com";

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        email,
                        "password",
                        List.of(new SimpleGrantedAuthority("ROLE_STUDENT"))
                );
        SecurityContextHolder.getContext().setAuthentication(authentication);

        UserModel userModel = new UserModel();
        userModel.setUsername(email);
        userModel.setRoles(new HashSet<>(List.of(buildRole("student"))));

        Student student = new Student();
        student.setStudentEmail(email);
        student.setSchool("Case Test School");

        when(userAccountService.loadUserModelByUsername(email)).thenReturn(userModel);
        when(studentRepository.findByStudentEmailIgnoreCase(email)).thenReturn(student);

        String result = schoolUtils.fetchSchoolName();

        assertEquals("Case Test School", result);
        verify(studentRepository).findByStudentEmailIgnoreCase(email);
    }

    private com.reps.demogcloud.security.models.RoleModel buildRole(String roleName) {
        com.reps.demogcloud.security.models.RoleModel role =
                new com.reps.demogcloud.security.models.RoleModel();
        role.setRole(roleName);
        return role;
    }
}