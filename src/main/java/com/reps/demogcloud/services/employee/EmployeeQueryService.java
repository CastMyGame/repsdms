package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.utils.SchoolUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeQueryService {

    private final EmployeeRepository employeeRepository;
    private final SchoolRepository schoolRepository;
    private final SchoolUtils schoolUtils;

    public List<Employee> findAll() throws ResourceNotFoundException {
        return FetchEmployeeDataByArchivedAndSchool(false);
    }

    public Optional<List<Employee>> findAllByRole(String role) {

        //Fetch Data
        List<Employee> allEmployees = FetchEmployeeDataByArchivedAndSchool(false);

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

    public Optional<School> getEmployeeSchool() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var findMe = employeeRepository.findByEmailIgnoreCase(authentication.getName());

        return schoolRepository.findBySchoolNameIgnoreCase(findMe.getSchool());
    }

    public Employee findByUserName(String email) {
        Employee employee = employeeRepository.findByEmailIgnoreCase(email);
        if (employee == null) {
            throw new ResourceNotFoundException("No employees with that email exist");

        }
        return employee;
    }

    public List<Employee> FetchEmployeeDataByArchivedAndSchool(boolean bool) throws ResourceNotFoundException {
        List<Employee> archivedRecords = employeeRepository.findByArchivedAndSchool(bool, schoolUtils.fetchSchoolName());
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }
}
