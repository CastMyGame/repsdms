package com.reps.demogcloud.data;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EmployeeRepository extends EmployeeBalanceSafeRepository<Employee>, MongoRepository<Employee, String> {
    //Select everything from students where last name is equal to our first argument
    List<Employee> findByLastName(String lastName);
    Employee findByEmailIgnoreCase(String email);

    List<Employee> findByArchived(boolean bool);
    Employee findByEmployeeId (String employeeId);

    List<Employee> findByArchivedAndSchool(boolean bool, String school);

    List<Employee> findBySchool (String school);

    @org.springframework.data.mongodb.repository.Query("{'_id': ?0, 'school': ?1}")
    @org.springframework.data.mongodb.repository.Update("{'$set': {'roles': ?2}}")
    void updateRolesInSchool(String id, String school, java.util.Set<com.reps.demogcloud.security.models.RoleModel> roles);

    @org.springframework.data.mongodb.repository.Query("{'_id': ?0, 'school': ?1}")
    @org.springframework.data.mongodb.repository.Update("{'$set': {'archived': ?2}}")
    void updateArchivedInSchool(String id, String school, boolean archived);
}
