package com.reps.demogcloud.services;

import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.models.employee.CurrencyTransferRequest;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.CurrencySpendRequest;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.employee.EmployeeClassService;
import com.reps.demogcloud.services.employee.EmployeeCreationService;
import com.reps.demogcloud.services.employee.EmployeeMutationService;
import com.reps.demogcloud.services.employee.EmployeeQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeService {
    private final EmployeeCreationService employeeCreationService;
    private final EmployeeQueryService employeeQueryService;
    private final EmployeeMutationService employeeMutationService;
    private final EmployeeClassService employeeClassService;

    public EmployeeResponse createNewEmployee(Employee request) {
        return employeeCreationService.createNewEmployee(request);
    }

    public List<EmployeeResponse> createNewEmployeeList(List<Employee> request) {
        return employeeCreationService.createNewEmployeeList(request);
    }

    public List<Employee> findAll() throws ResourceNotFoundException {
        return employeeQueryService.findAll();
    }

    public Employee findByLoggedInEmployee() throws Exception {
        return employeeQueryService.findByLoggedInEmployee();
    }

    public Optional<School> getEmployeeSchool() {
        return employeeQueryService.getEmployeeSchool();
    }

    public Optional<List<Employee>> findAllByRole(String role) {
        return employeeQueryService.findAllByRole(role);
    }

    public Employee findByUserName(String email) {
        return employeeQueryService.findByUserName(email);
    }

    public void deleteEmployee(String id) throws Exception {
        employeeMutationService.deleteEmployee(id);
    }

    public List<Student> spendCurrency(List<CurrencySpendRequest> requests) {
        return employeeMutationService.spendCurrency(requests);
    }

    public List<Employee> editSchool(String school) {
        return employeeMutationService.editSchool(school);
    }

    public void transferCurrency(CurrencyTransferRequest request) {
        employeeMutationService.transferCurrency(request);
    }

    public List<Employee> updateAllEmployees() {
        return employeeMutationService.updateAllEmployees();
    }

    public Employee addOrUpdateClassToEmployee(String teacherEmail, ClassRequest newClass) throws NullPointerException {
        return employeeClassService.addOrUpdateClassToEmployee(teacherEmail, newClass);
    }

    public Employee removeClassFromEmployee(String teacherEmail, ClassRequest classToDelete) throws ResourceNotFoundException {
        return employeeClassService.removeClassFromEmployee(teacherEmail, classToDelete);
    }
}


