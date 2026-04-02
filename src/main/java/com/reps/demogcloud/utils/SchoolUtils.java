package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.EntityNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchoolUtils {

    private final UserAccountService userAccountService;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    public String fetchSchoolName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || authentication.getPrincipal() == null || authentication.getName() == null) {
            throw new IllegalArgumentException("Authentication or principal is missing.");
        }

        UserModel userModel = userAccountService.loadUserModelByUsername(authentication.getName());

        if (userModel == null) {
            throw new EntityNotFoundException("User with username " + authentication.getName() + " not found.");
        }

        boolean isStudent = userModel.getRoles() != null
                && userModel.getRoles().stream()
                .filter(role -> role != null && role.getRole() != null)
                .anyMatch(role -> "STUDENT".equalsIgnoreCase(role.getRole()));

        if (isStudent) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(userModel.getUsername());
            if (student == null) {
                throw new EntityNotFoundException("Student with email " + userModel.getUsername() + " not found.");
            }
            return student.getSchool();
        }

        Employee employee = employeeRepository.findByEmailIgnoreCase(userModel.getUsername());
        if (employee == null) {
            throw new EntityNotFoundException("Employee with email " + userModel.getUsername() + " not found.");
        }
        return employee.getSchool();
    }
}