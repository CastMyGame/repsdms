package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.EntityNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserContextService {

    private static final String STUDENT_ROLE = "STUDENT";

    private final UserAccountService userAccountService;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    public String getCurrentUserSchool() {
        UserModel user = getCurrentUserModel();
        String email = user.getUsername();

        if (hasStudentRole(user)) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(email);
            if (student == null) {
                throw new EntityNotFoundException("Student not found with email: " + email);
            }
            return student.getSchool();
        }

        Employee employee = employeeRepository.findByEmailIgnoreCase(email);
        if (employee == null) {
            throw new EntityNotFoundException("Employee not found with email: " + email);
        }
        return employee.getSchool();
    }

    public String getCurrentUserEmail() {
        return getRequiredAuthentication().getName();
    }

    public boolean isCurrentUserStudent() {
        return hasStudentRole(getCurrentUserModel());
    }

    public String getCurrentUsername() {
        return getRequiredAuthentication().getName();
    }

    public String getSchoolForCurrentUser() {
        return getCurrentUserSchool();
    }

    public boolean isStudent() {
        return isCurrentUserStudent();
    }

    private UserModel getCurrentUserModel() {
        String username = getRequiredAuthentication().getName();
        return userAccountService.loadUserModelByUsername(username);
    }

    private boolean hasStudentRole(UserModel user) {
        return user.getRoles() != null
                && user.getRoles().stream()
                .anyMatch(role -> STUDENT_ROLE.equalsIgnoreCase(role.getRole()));
    }

    private Authentication getRequiredAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("No authenticated user found in security context");
        }

        return authentication;
    }
}
