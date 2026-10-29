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
    private final com.reps.demogcloud.security.services.AccountAccessService accounts;

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    public EmployeeResponse createNewEmployee(Employee request) {
        if(canonical!=null){canonical.admin();canonical.label(request.getSchool());canonical.bindNew(request);}
        //Check it email exist in system
        // Staff establish their password through reset or use their verified Google login.

        Employee doesEmployeeExist = employeeRepository.findByEmailIgnoreCase(request.getEmail());
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername(request.getEmail().toLowerCase());
        // AuthService creates an undisclosed random password; setup is separate.
        authenticationRequest.setFirstName(request.getFirstName());
        authenticationRequest.setLastName(request.getLastName());
        authenticationRequest.setSchool(request.getSchool());
        authenticationRequest.setRoles(request.getRoles());
        if (doesEmployeeExist == null) {
            try {
                var user=authService.createEmployeeUser(authenticationRequest);
                if(canonical!=null)request.setCurrency(0);
                var employee=canonical==null?employeeRepository.save(request):employeeRepository.insert(request);
                accounts.createdStaff(user);
                return new EmployeeResponse("", employee);
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
