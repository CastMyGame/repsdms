package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.CurrencyTransferRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.CurrencySpendRequest;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.services.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeMutationService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final EmployeeRepository employeeRepository;
    private final StudentRepository studentRepository;

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

    public List<Student> spendCurrency(List<CurrencySpendRequest> requests) {
        List<Student> spenders = new ArrayList<>();
        for (CurrencySpendRequest request : requests) {
            Student spender = studentRepository.findByStudentEmailIgnoreCase(request.getStudentEmail());
            spender.setCurrency(spender.getCurrency() - request.getCurrencyTransferred());
            spenders.add(studentRepository.save(spender));
        }
        return spenders;
    }

    public List<Employee> editSchool(String schoolName) {
        List<Employee> employees = employeeRepository.findBySchool(schoolName);
        List<Employee> updated = new ArrayList<>();
        for (Employee employee : employees) {
            employee.setCurrency(5);
            employeeRepository.save(employee);
            updated.add(employee);
        }

        return updated;
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

    public List<Employee> updateAllEmployees() {
        List<Employee> employees = employeeRepository.findAll();
        List<Employee> updatedEmployees = new ArrayList<>();
        for (Employee teacher : employees) {
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
