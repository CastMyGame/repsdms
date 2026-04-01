package com.reps.demogcloud.controllers;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.models.student.CurrencySpendRequest;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.services.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@CrossOrigin(
        origins = {
                "http://localhost:3000/",
                "https://repsdiscipline.vercel.app",
                "https://repsdev.vercel.app"
        }
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/employees/v1")
public class EmployeeController {

    private final EmployeeService employeeService;
    private final EmployeeRepository employeeRepository;

    @GetMapping("/employees")
    public ResponseEntity<List<Employee>> getAllUsers() {
        List<Employee> employees = employeeService.findAll();
        return ResponseEntity.ok(employees);
    }

    @GetMapping("/employees/email/{email}")
    public ResponseEntity<Employee> getUserById(@PathVariable String email) {
        Employee employee = employeeService.findByUserName(email);
        return ResponseEntity.ok(employee);
    }

    @GetMapping("/employees/{role}")
    public ResponseEntity<List<Employee>> getAllEmployeesByRole(@PathVariable String role) {
        Optional<List<Employee>> employeesOptional = employeeService.findAllByRole(role);

        if (employeesOptional.isEmpty() || employeesOptional.get().isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }

        return ResponseEntity.ok(employeesOptional.get());
    }

    @PostMapping("/employees")
    public ResponseEntity<EmployeeResponse> createEmployee(@RequestBody Employee employee) {
        EmployeeResponse response = employeeService.createNewEmployee(employee);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/employees/list")
    public ResponseEntity<List<EmployeeResponse>> createEmployeeList(@RequestBody List<Employee> employeeList) {
        List<EmployeeResponse> response = employeeService.createNewEmployeeList(employeeList);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/employees/{id}/roles")
    public ResponseEntity<Employee> updateEmployeesRole(@PathVariable String id, @RequestBody Set<RoleModel> roles) {
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);

        if (optionalEmployee.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Employee employee = optionalEmployee.get();
        employee.setRoles(roles);
        Employee updatedEmployee = employeeRepository.save(employee);

        return ResponseEntity.ok(updatedEmployee);
    }

    @PutMapping("/currency/spend")
    public ResponseEntity<List<Student>> spendCurrency(@RequestBody List<CurrencySpendRequest> requests) {
        List<Student> response = employeeService.spendCurrency(requests);
        return ResponseEntity.accepted().body(response);
    }

    @PutMapping("/{school}")
    public ResponseEntity<List<Employee>> editSchool(@PathVariable String school) {
        List<Employee> updated = employeeService.editSchool(school);

        if (updated == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/updateClass/{teacherEmail}")
    public ResponseEntity<Employee> updateClassRoster(@PathVariable String teacherEmail, @RequestBody ClassRequest request) {
        Employee updated = employeeService.addOrUpdateClassToEmployee(teacherEmail, request);

        if (updated == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(updated);
    }

    @PutMapping("/updateAll")
    public ResponseEntity<List<Employee>> updateAllEmployees() {
        List<Employee> updated = employeeService.updateAllEmployees();

        if (updated == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/employees/{id}")
    public ResponseEntity<String> deleteEmployeeById(@PathVariable String id) {
        try {
            employeeService.deleteEmployee(id);
            return ResponseEntity.ok("Employee with ID " + id + " has been deleted.");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Employee with ID " + id + " not found.");
        }
    }

    @PostMapping("/deleteClass/{teacherEmail}")
    public ResponseEntity<Employee> deleteClassRoster(@PathVariable String teacherEmail, @RequestBody ClassRequest request) {
        Employee updated = employeeService.removeClassFromEmployee(teacherEmail, request);

        if (updated == null) {
            return ResponseEntity.badRequest().build();
        }

        return ResponseEntity.ok(updated);
    }
}
