package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.models.employee.CurrencyTransferRequest;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.CurrencySpendRequest;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.services.AuthService;
import lombok.extern.slf4j.Slf4j;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
public class EmployeeService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final EmployeeRepository employeeRepository;
    private final AuthService authService;
    private final CustomFilters customFilters;
    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;


    public EmployeeService(EmployeeRepository employeeRepository, AuthService authService, CustomFilters customFilters, SchoolRepository schoolRepository, StudentRepository studentRepository) {
        this.employeeRepository = employeeRepository;
        this.authService = authService;
        this.customFilters = customFilters;
        this.schoolRepository = schoolRepository;
        this.studentRepository = studentRepository;
    }


    public List<Employee> findAll() throws ResourceNotFoundException {
        return customFilters.FetchEmployeeDataByIsArchivedAndSchool(false);


    }


    public EmployeeResponse createNewEmployee(Employee request) {
//        Set<RoleModel> roles = new HashSet<>();
//        RoleModel teacher = new RoleModel();
//        teacher.setRole(request.getRoles());
//        roles.add(teacher);
        //Check it email exist in system
        Employee doesEmployeeExist = employeeRepository.findByEmailIgnoreCase(request.getEmail());
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername(request.getEmail().toLowerCase());
        authenticationRequest.setPassword(request.getLastName().toLowerCase() + request.getSchool().toLowerCase());
        authenticationRequest.setFirstName(request.getFirstName());
        authenticationRequest.setLastName(request.getLastName());
        authenticationRequest.setSchoolName(request.getSchool());
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

    public List<EmployeeResponse> createNewEmployeeList (List<Employee> request) {
        List<EmployeeResponse> response = new ArrayList<>();

        for(Employee employee : request) {
            response.add(createNewEmployee(employee));
        }

        return response;
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

    public List<Student> spendCurrency(List<CurrencySpendRequest> requests) {
        List<Student> spenders = new ArrayList<>();
        for(CurrencySpendRequest request : requests) {
            Student spender = studentRepository.findByStudentEmailIgnoreCase(request.getStudentEmail());
            spender.setCurrency(spender.getCurrency() - request.getCurrencyTransferred());
            spenders.add(studentRepository.save(spender));
        }
        return spenders;
    }

    public List<Employee> editSchool(String schoolName, String update) {
        List<Employee> employees = employeeRepository.findBySchool(schoolName);
        List<Employee> updated = new ArrayList<>();
        for (Employee employee : employees) {
            employee.setCurrency(5);
            employeeRepository.save(employee);
            updated.add(employee);
        }

        return updated;
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

    public void transferCurrency(CurrencyTransferRequest request) {
            Employee teacher = employeeRepository.findByEmailIgnoreCase(request.getTeacherEmail());
            Student student = studentRepository.findByStudentEmailIgnoreCase(request.getStudentEmail());
            if (teacher.getCurrency() < request.getCurrencyTransferred()) {
                throw new ResourceNotFoundException("You do not have enough currency to give");
            }
            teacher.setCurrency(teacher.getCurrency() - request.getCurrencyTransferred());
            employeeRepository.save(teacher);
        student.setCurrency(student.getCurrency() + request.getCurrencyTransferred());
        studentRepository.save(student);
    }

    public Employee findByUserName(String email) {
        Employee employee = employeeRepository.findByEmailIgnoreCase(email);
        if(employee == null){
            throw new ResourceNotFoundException("No employees with that email exist");

        }
        return employee;
    }

    public Employee addOrUpdateClassToEmployee(String teacherEmail, ClassRequest newClass) throws NullPointerException {
        // Fetch the employee by ID
        Employee teacher = employeeRepository.findByEmailIgnoreCase(teacherEmail);

        if (teacher != null) {
            // Initialize the classes list if it's null
            if (teacher.getClasses() == null) {
                teacher.setClasses(new ArrayList<>());
            }
            // Check if the class already exists based on class name
            Optional<Employee.ClassRoster> existingClassOpt = teacher.getClasses().stream()
                    .filter(classRoster -> classRoster.getClassName().equalsIgnoreCase(newClass.getClassToUpdate().getClassName()))
                    .findFirst();

            if (existingClassOpt.isPresent()) {
                // Update existing class details
                Employee.ClassRoster existingClass = existingClassOpt.get();
                existingClass.setClassRoster(newClass.getClassToUpdate().getClassRoster());
                existingClass.setPunishmentsThisWeek(newClass.getClassToUpdate().getPunishmentsThisWeek());
            } else {
                // Add new class to the list
                teacher.getClasses().add(newClass.getClassToUpdate());
            }

            // Save the updated employee back to the database
            return employeeRepository.save(teacher);
        } else {
            // Throw a custom exception if the employee does not exist
            throw new ResourceNotFoundException("Teacher with email " + teacherEmail + " not found");
        }
    }

    public Employee removeClassFromEmployee(String teacherEmail, ClassRequest classToDelete) throws ResourceNotFoundException {
        // Fetch the employee by email
        Employee teacher = employeeRepository.findByEmailIgnoreCase(teacherEmail);

        if (teacher != null) {
            // Check if the classes list is initialized and contains the specified class
            if (teacher.getClasses() != null) {
                boolean classRemoved = teacher.getClasses().removeIf(classRoster ->
                        classRoster.getClassName().equalsIgnoreCase(classToDelete.getClassToUpdate().getClassName())
                );

                if (classRemoved) {
                    // Save the updated employee object back to the database if the class was removed
                    return employeeRepository.save(teacher);
                } else {
                    throw new ResourceNotFoundException("Class with name " + classToDelete.getClassToUpdate().getClassName() + " not found for teacher " + teacherEmail);
                }
            } else {
                throw new ResourceNotFoundException("Teacher " + teacherEmail + " has no classes to remove.");
            }
        } else {
            throw new ResourceNotFoundException("Teacher with email " + teacherEmail + " not found.");
        }
    }

    public List<Employee> updateAllEmployees() {
        List<Employee> employees = employeeRepository.findAll();
        List<Employee> updatedEmployees = new ArrayList<>();
        for(Employee teacher : employees) {
            if (teacher.getClasses() == null) {
                Employee.ClassRoster roster = new Employee.ClassRoster();
                List<String> classRoster = new ArrayList<>();
                roster.setClassName("");
                roster.setClassPeriod("");
                roster.setPunishmentsThisWeek(0);
                roster.setClassRoster(classRoster);
                List<Employee.ClassRoster> emptyRoster = new ArrayList<>();
                emptyRoster.add(roster);
                teacher.setClasses(emptyRoster);
                employeeRepository.save(teacher);
                updatedEmployees.add(teacher);
            }
        }
        return updatedEmployees;
    }
}


