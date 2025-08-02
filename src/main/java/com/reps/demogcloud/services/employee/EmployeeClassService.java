package com.reps.demogcloud.services.employee;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.employee.Employee;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmployeeClassService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final EmployeeRepository employeeRepository;

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
}
