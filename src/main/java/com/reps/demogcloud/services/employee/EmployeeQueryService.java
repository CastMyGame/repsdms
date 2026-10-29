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

    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.data.mongodb.core.MongoTemplate mongo;
    private Employee ownedEmployee(String email){
        canonical.staff();var rows=mongo.find(canonical.query().addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("email").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(email))).limit(2),Employee.class);
        if(rows.size()!=1 || (!canonical.role("ADMIN") && !canonical.role("GUIDANCE") && !canonical.email().equalsIgnoreCase(email)))throw new org.springframework.security.access.AccessDeniedException("Access denied");return rows.get(0);
    }
    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    public List<Employee> findAll() throws ResourceNotFoundException {
        return fetchEmployeeDataByArchivedAndSchool(false);
    }

    public Optional<List<Employee>> findAllByRole(String role) {

        //Fetch Data
        List<Employee> allEmployees = fetchEmployeeDataByArchivedAndSchool(false);

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
        var findMe = canonical==null?employeeRepository.findByEmailIgnoreCase(authentication.getName()):ownedEmployee(authentication.getName());

        if (findMe == null) {
            throw new Exception("No employee with that email exists");
        }

        return findMe;
    }

    public Optional<School> getEmployeeSchool() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        var findMe = canonical==null?employeeRepository.findByEmailIgnoreCase(authentication.getName()):ownedEmployee(authentication.getName());

        return canonical==null?schoolRepository.findBySchoolNameIgnoreCase(findMe.getSchool()):Optional.of(canonical.school());
    }

    public Employee findByUserName(String email) {
        Employee employee = canonical==null?employeeRepository.findByEmailIgnoreCase(email):ownedEmployee(email);
        if (employee == null) {
            throw new ResourceNotFoundException("No employees with that email exist");

        }
        return employee;
    }

    public List<Employee> fetchEmployeeDataByArchivedAndSchool(boolean bool) throws ResourceNotFoundException {
        List<Employee> archivedRecords = canonical==null?employeeRepository.findByArchivedAndSchool(bool, schoolUtils.fetchSchoolName()):mongo.find(canonical.query().addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("archived").is(bool)),Employee.class).stream().filter(e->canonical.role("ADMIN") || canonical.role("GUIDANCE") || canonical.email().equalsIgnoreCase(e.getEmail())).toList();
        if (archivedRecords.isEmpty()) {
            return new ArrayList<>();
        }
        return archivedRecords;
    }
}
