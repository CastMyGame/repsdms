package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.EmployeeRequest;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.security.models.RoleModel;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmployeeService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    public Employee findByEmail(String email) throws Exception {
        Employee employeeRecord = employeeRepository.findByEmailIgnoreCase(email);

        if (employeeRecord == null) {
            throw new ResourceNotFoundException("That Employee does not exist");
        }
        logger.debug(String.valueOf(employeeRecord));
        System.out.println(employeeRecord);
        return employeeRecord;
    }
    public List<Employee> findByLastName(String lastName) throws Exception {
        List<Employee> fetchData = employeeRepository.findByLastName(lastName);
        List<Employee> employeeRecord = fetchData.stream()
                .filter(x-> !x.isArchived()) // Filter out punishments where isArchived is true
                .toList(); // Collect the filtered punishments into a list
        if (employeeRecord.isEmpty()) {
            throw new ResourceNotFoundException("That student does not exist");
        }
        logger.debug(String.valueOf(employeeRecord));
        System.out.println(employeeRecord);
        return employeeRecord;
    }

    public EmployeeResponse createNewEmployee (Employee request ) {
        System.out.println("service " +request);

        try {
            return new EmployeeResponse("", employeeRepository.save(request));
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            return new EmployeeResponse(e.getMessage(), null);
        }
    }

    public String deleteEmployee(String id) throws Exception {
        try {
            Optional<Employee> employeeOptional = employeeRepository.findById(id);

            if (employeeOptional.isPresent()) {
                Employee deletedEmployee = employeeOptional.get();
                employeeRepository.deleteById(id);

                return new StringBuilder()
                        .append(deletedEmployee.getFirstName())
                        .append(" ")
                        .append(deletedEmployee.getLastName())
                        .append(" has been deleted")
                        .toString();
            } else {
                throw new Exception("Employee with ID " + id + " does not exist");
            }
        } catch (Exception e) {
            throw new Exception("An error occurred while deleting the employee");
        }
    }

    public List<Employee> getAllEmployees() {
        return employeeRepository.findByIsArchived(false);
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
        LocalDateTime createdOn = LocalDateTime.now();
        existingRecord.setArchivedOn(createdOn);
        existingRecord.setArchivedBy(employeeId);
        return employeeRepository.save(existingRecord);
    }

    public Optional<List<Employee>> findAllByRole(String role) {
        List<Employee> allEmployees = employeeRepository.findAll();

        if (!allEmployees.isEmpty()) {
            List<Employee> employeesWithRole = allEmployees.stream()
                    .filter(employee -> {
                        Set<RoleModel> userRoles = employee.getRoles();
                        return userRoles != null && userRoles.stream()
                                .anyMatch(roleModel -> roleModel.getRole().equals(role));
                    })
                    .collect(Collectors.toList());

            return Optional.of(employeesWithRole);
        } else {
            return Optional.empty();
        }
    }

}