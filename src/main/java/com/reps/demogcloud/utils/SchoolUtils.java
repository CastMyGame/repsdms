package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.EntityNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SchoolUtils {
    private final UserService userService;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    public String fetchSchoolName() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserModel userModel = userService.loadUserModelByUsername(authentication.getName());
        if (authentication.getPrincipal() == null) {
            throw new IllegalArgumentException("Authentication or principal is missing.");
        }

        if (userModel.getRoles().stream().anyMatch(role -> "STUDENT".equals(role.getRole()))) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(userModel.getUsername());
            if (student == null) {
                throw new EntityNotFoundException("Student with email " + userModel.getUsername() + " not found.");
            }
            return student.getSchool();
        } else {
            Employee employee = employeeRepository.findByEmailIgnoreCase(userModel.getUsername());
            if (employee == null) {
                throw new EntityNotFoundException("Employee with email " + userModel.getUsername() + " not found.");
            }
            return employee.getSchool();
        }
    }
}
