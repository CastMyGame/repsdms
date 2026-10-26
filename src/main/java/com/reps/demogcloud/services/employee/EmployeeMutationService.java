package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.CurrencyTransferRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.CurrencySpendRequest;
import com.reps.demogcloud.models.student.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeMutationService {

    private final EmployeeRepository employeeRepository;
    private final StudentRepository studentRepository;
    private final com.reps.demogcloud.services.currency.CurrencyService currency;

    public void deleteEmployee(String id) throws Exception {
        Optional<Employee> employeeOptional = employeeRepository.findById(id);

        if (employeeOptional.isPresent()) {
            employeeRepository.deleteById(id);
        } else {
            throw new Exception("Employee with ID " + id + " does not exist");
        }
    }

    public List<Student> spendCurrency(List<CurrencySpendRequest> requests) {
        return currency.spend(requests);
    }

    public List<Employee> editSchool(String school) {
        return currency.resetSchool(school);
    }

    public void transferCurrency(CurrencyTransferRequest request) {
        throw new IllegalStateException("Direct currency transfer is unsupported; create an atomic recognition batch");
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
