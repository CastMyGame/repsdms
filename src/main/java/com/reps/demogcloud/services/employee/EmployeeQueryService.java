package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeQueryService {
    private final CustomFilters customFilters;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final EmployeeRepository employeeRepository;
    private final SchoolRepository schoolRepository;

    public List<Employee> findAll() throws ResourceNotFoundException {
        return customFilters.FetchEmployeeDataByIsArchivedAndSchool(false);
    }

    public Optional<List<Employee>> findAllByRole(String role) {

        //Fetch Data
        List<Employee> allEmployees = customFilters.FetchEmployeeDataByIsArchivedAndSchool(false);

        if (!allEmployees.isEmpty()) {
            List<Employee> employeesWithRole = allEmployees.stream()
                    .filter(employee -> {
                        Set<RoleModel> userRoles = employee.getRoles();
                        return userRoles != null && userRoles.stream()
                                .anyMatch(roleModel -> roleModel.getRole().equals(role));
                    }).sorted(Comparator.comparing(Employee::getLastName)).collect(Collectors.toList());

            return Optional.of(employeesWithRole);
        } else {
            return Optional.empty();
        }
    }

    public Employee findByLoggedInEmployee() throws Exception {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var findMe = employeeRepository.findByEmailIgnoreCase(authentication.getName());

        if (findMe == null) {
            throw new Exception("No employee with that email exists");
        }

        return findMe;
    }

    public School getEmployeeSchool() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var findMe = employeeRepository.findByEmailIgnoreCase(authentication.getName());

        return schoolRepository.findSchoolBySchoolName(findMe.getSchool());
    }

    public Employee findByUserName(String email) {
        Employee employee = employeeRepository.findByEmailIgnoreCase(email);
        if (employee == null) {
            throw new ResourceNotFoundException("No employees with that email exist");

        }
        return employee;
    }
}
