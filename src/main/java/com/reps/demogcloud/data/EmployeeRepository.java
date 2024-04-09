package com.reps.demogcloud.data;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends MongoRepository<Employee, String> {
    //Select everything from students where last name is equal to our first argument
    List<Employee> findByLastName(String lastName);
    Employee findByEmailIgnoreCase(String email);
    List<Employee> findByIsArchived (boolean bool);
    Employee findByEmployeeId (String employeeId);

    List<Employee> findByIsArchivedAndSchool(boolean bool, String school);
}
