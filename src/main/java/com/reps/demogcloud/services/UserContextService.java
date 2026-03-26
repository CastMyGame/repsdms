package com.reps.demogcloud.services;

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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserContextService {
    private final UserAccountService userAccountService;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    public String getCurrentUserSchool() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserModel user = userAccountService.loadUserModelByUsername(auth.getName());

        if (user.getRoles().stream().anyMatch(r -> r.getRole().equals("STUDENT"))) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(user.getUsername());
            if (student == null) {
                throw new EntityNotFoundException("Student not found with email: " + user.getUsername());
            }
            return student.getSchool();
        } else {
            Employee employee = employeeRepository.findByEmailIgnoreCase(user.getUsername());
            if (employee == null) {
                throw new EntityNotFoundException("Employee not found with email: " + user.getUsername());
            }
            return employee.getSchool();
        }
    }

    public String getCurrentUserEmail() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public boolean isCurrentUserStudent() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        UserModel user = userAccountService.loadUserModelByUsername(auth.getName());
        return user.getRoles().stream().anyMatch(role -> "STUDENT".equals(role.getRole()));
    }

    public String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    public String getSchoolForCurrentUser() {
        // Same as getCurrentUserSchool — can remove if redundant
        return getCurrentUserSchool();
    }

    public boolean isStudent() {
        return isCurrentUserStudent();
    }
}
