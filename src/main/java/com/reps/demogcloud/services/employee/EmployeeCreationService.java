package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeCreationService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final EmployeeRepository employeeRepository;
    private final AuthService authService;

    public EmployeeResponse createNewEmployee(Employee request) {
        //Check it email exist in system
        String emailPrefix = request.getEmail().split("@")[0];

        Employee doesEmployeeExist = employeeRepository.findByEmailIgnoreCase(request.getEmail());
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername(request.getEmail().toLowerCase());
        authenticationRequest.setPassword(emailPrefix);
        authenticationRequest.setFirstName(request.getFirstName());
        authenticationRequest.setLastName(request.getLastName());
        authenticationRequest.setSchool(request.getSchool());
        authenticationRequest.setRoles(request.getRoles());
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

    public List<EmployeeResponse> createNewEmployeeList(List<Employee> request) {
        List<EmployeeResponse> response = new ArrayList<>();

        for (Employee employee : request) {
            response.add(createNewEmployee(employee));
        }

        return response;
    }
}
