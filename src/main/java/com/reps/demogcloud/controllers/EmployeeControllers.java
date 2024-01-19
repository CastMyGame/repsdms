package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.services.EmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(
        origins = {
                "http://localhost:3000"
        }
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/employees/v1")
public class EmployeeControllers {



    @Autowired
    EmployeeService employeeService;
    @Autowired
    private EmployeeRepository employeeRepository;


    @GetMapping("/employees")
    private ResponseEntity<List<Employee>> getAllUsers(){
        List<Employee> employees =  employeeRepository.findAll();
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


    @DeleteMapping("/employee/{id}")
    public ResponseEntity<String> deleteEmployeeById(@PathVariable String id) {

        // Check if user exists
        Optional<Employee> optionalEmployee = employeeRepository.findById(id);

        if (optionalEmployee.isPresent()) {
            // If user exists, delete the user
            employeeRepository.deleteById(id);

            // Return confirmation message
            return ResponseEntity.ok("User with ID " + id + " has been deleted.");
        } else {
            // If user not found, return a 404 Not Found response
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with ID " + id + " not found.");
        }
    }


    @GetMapping("/employee/{role}")
    private ResponseEntity<List<Employee>> getAllEmployeesByRole(@PathVariable String role) {
        List<Employee> employees = employeeRepository.findAll().stream()
                .filter(employee -> {
                    Set<RoleModel> userRoles = employee.getRoles();
                    if (userRoles != null) {
                        return userRoles.stream().anyMatch(roleModel -> roleModel.getRole().equals(role));
                    }
                    return false; // Return false if userRoles is null
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(employees);
    }




}
