package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.services.AuthService;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmployeeService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final EmployeeRepository employeeRepository;
    private final AuthService authService;

    private final CustomFilters customFilters;


    public EmployeeService(EmployeeRepository employeeRepository, AuthService authService, CustomFilters customFilters) {
        this.employeeRepository = employeeRepository;
        this.authService = authService;
        this.customFilters = customFilters;
    }


    public List<Employee> findAll() throws ResourceNotFoundException {
        return customFilters.FetchEmployeeDataByIsArchivedAndSchool(false);


    }


    public EmployeeResponse createNewEmployee(Employee request) {
        Set<RoleModel> roles = new HashSet<>();
        RoleModel teacher = new RoleModel();
        teacher.setRole("TEACHER");
        roles.add(teacher);
        //Check it email exist in system
        Employee doesEmployeeExist = employeeRepository.findByEmailIgnoreCase(request.getEmail());
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername(request.getEmail().toLowerCase());
        authenticationRequest.setPassword("123abc");
        authenticationRequest.setFirstName(request.getFirstName());
        authenticationRequest.setLastName(request.getLastName());
        authenticationRequest.setSchoolName(request.getSchool());
        authenticationRequest.setRoles(roles);
        if (doesEmployeeExist == null) {
            try {
                authService.createEmployeeUser(authenticationRequest);
                return new EmployeeResponse("", employeeRepository.save(request));
            } catch (IllegalArgumentException e) {
                logger.error(e.getMessage());
                return new EmployeeResponse(e.getMessage(), null);
            }

        } else {
            return new EmployeeResponse("Error: Email Already Registered In System", null);

        }
    }

    public void deleteEmployee(String id) throws Exception {
        try {
            Optional<Employee> employeeOptional = employeeRepository.findById(id);

            if (employeeOptional.isPresent()) {
                employeeRepository.deleteById(id);

            } else {
                throw new Exception("Employee with ID " + id + " does not exist");
            }
        } catch (Exception e) {
            throw new Exception("An error occurred while deleting the employee");
        }
    }

    public List<Employee> getAllEmployees() {
        return customFilters.FetchEmployeeDataByIsArchivedAndSchool(false);
    }

    private Employee ensureEmployeeExists(Employee employee) {
        return null;
    }

    public Employee findByEmployeeId(String employeeId) throws ResourceNotFoundException {
        var findMe = employeeRepository.findByEmployeeId(employeeId);

        if (findMe == null) {
            throw new ResourceNotFoundException("No employees with that ID exist");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }

    public List<Employee> findAllEmployeeIsArchived(boolean bool) throws ResourceNotFoundException {
        List<Employee> archivedRecords = employeeRepository.findByIsArchived(bool);
        if (archivedRecords.isEmpty()) {
            throw new ResourceNotFoundException("No Archived Records exist in employees table");
        }
        return archivedRecords;
    }


    public Employee archiveRecord(String employeeId) {
        //Check for existing record
        Employee existingRecord = findByEmployeeId(employeeId);
        //Updated Record
        existingRecord.setArchived(true);
        LocalDate createdOn = LocalDate.now();
        existingRecord.setArchivedOn(createdOn);
        existingRecord.setArchivedBy(employeeId);
        return employeeRepository.save(existingRecord);
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

}
