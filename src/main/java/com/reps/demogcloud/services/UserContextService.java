package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.EntityNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserContextService {

    private static final String STUDENT_ROLE = "STUDENT";
    private static final String TEACHER_ROLE = "TEACHER";
    private static final String GUIDANCE_ROLE = "GUIDANCE";
    private static final String ADMIN_ROLE = "ADMIN";

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

    public UserModel getCurrentUserModel() {
        String username = getRequiredAuthentication().getName();
        return userAccountService.loadUserModelByUsername(username);
    }

    public boolean hasRole(String role) {
        UserModel user = getCurrentUserModel();
        return user.getRoles() != null
                && user.getRoles().stream()
                .anyMatch(userRole -> role.equalsIgnoreCase(userRole.getRole()));
    }

    public boolean hasAnyRole(String... roles) {
        for (String role : roles) {
            if (hasRole(role)) {
                return true;
            }
        }
        return false;
    }

    public void requireAnyRole(String... roles) {
        if (!hasAnyRole(roles)) {
            throw new AccessDeniedException("Your account is not allowed to perform this action");
        }
    }

    public void requireCurrentStudent(String studentEmail) {
        if (!hasRole(STUDENT_ROLE)
                || !getCurrentUserEmail().equalsIgnoreCase(studentEmail)) {
            throw new AccessDeniedException("Students may access only their own records");
        }
    }

    public void requireStudentRecordAccess(String studentEmail) {
        if (hasRole(STUDENT_ROLE)) {
            requireCurrentStudent(studentEmail);
            return;
        }

        requireStaffAccessToStudent(studentEmail);
    }

    public void requireStaffAccessToStudent(String studentEmail) {
        requireAnyRole(TEACHER_ROLE, GUIDANCE_ROLE, ADMIN_ROLE);
        Student student = studentRepository.findByStudentEmailIgnoreCase(studentEmail);
        if (student == null || !getCurrentUserSchool().equalsIgnoreCase(student.getSchool())) {
            throw new AccessDeniedException("You may access only students in your school");
        }
    }

    public void requireTeacherDefaultAccess(String teacherEmail) {
        if (hasRole(TEACHER_ROLE) && getCurrentUserEmail().equalsIgnoreCase(teacherEmail)) {
            return;
        }

        if (hasRole(ADMIN_ROLE)) {
            Employee teacher = employeeRepository.findByEmailIgnoreCase(teacherEmail);
            if (teacher != null && getCurrentUserSchool().equalsIgnoreCase(teacher.getSchool())) {
                return;
            }
        }

        throw new AccessDeniedException("You may manage only your own assignment defaults");
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
