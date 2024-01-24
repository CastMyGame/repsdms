package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.employee.EmployeeResponse;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.services.AuthService;
import com.reps.demogcloud.services.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@CrossOrigin(
        origins = {
                "http://localhost:3000"
        }
)
@RestController
@RequestMapping("/employees/v1")
public class EmployeeControllers {

    private final EmployeeService employeeService;

    private final EmployeeRepository employeeRepository;

    public EmployeeControllers(EmployeeService employeeService, EmployeeRepository employeeRepository) {
        this.employeeService = employeeService;
        this.employeeRepository = employeeRepository;
    }


    @GetMapping("/employees")
    private ResponseEntity<List<Employee>> getAllUsers(){
        List<Employee> employees =  employeeRepository.findAll();
        return ResponseEntity.ok(employees);
    }

    @PostMapping("/employees")
    private ResponseEntity<EmployeeResponse> createEmployee(@RequestBody Employee employee){
        System.out.println("controller " +employee);

        EmployeeResponse employees =  employeeService.createNewEmployee(employee);
        return ResponseEntity.ok(employees);
    }




    @PutMapping("/employees/{id}/roles")
    private ResponseEntity<Employee> updateEmployeesRole(@PathVariable String id, @RequestBody Set<RoleModel> roles) {
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);

        if (optionalEmployee.isPresent()) {
            Employee employee = optionalEmployee.get();

            // Update the role of the user
            employee.setRoles(roles);  // Assuming UserModel has a setter method for roles of type Set<RoleModel>

            // Save the updated user back to the repository
            Employee updatedEmployee = employeeRepository.save(employee);

            // Return a response entity with the updated user and a success status
            return ResponseEntity.ok(updatedEmployee);
        } else {
            // If user not found, return a 404 Not Found response
            return ResponseEntity.notFound().build();
        }
    }


    @DeleteMapping("/employees/{id}")
    public ResponseEntity<String> deleteEmployeeById(@PathVariable String id) {
        try {
            String employeeToDelete = employeeService.deleteEmployee(id);

            // If the service method executed successfully, return a 200 OK response
            return ResponseEntity.ok("Employee with ID " + id + " has been deleted.");
        } catch (Exception e) {
            // If an exception occurred, handle it and return a 404 Not Found response
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Employee with ID " + id + " not found.");
        }
    }



    @GetMapping("/employees/{role}")
    private ResponseEntity<List<Employee>> getAllEmployeesByRole(@PathVariable String role) {
        Optional<List<Employee>> employeesOptional = employeeService.findAllByRole(role);

        if (employeesOptional.isPresent()) {
            List<Employee> employees = employeesOptional.get();
            if(employees.isEmpty()){
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());

            }else{
                return ResponseEntity.ok(employees);

            }
        } else {
            // If no employees with the specified role are found, return a 404 Not Found response
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Collections.emptyList());
        }
    }







}
