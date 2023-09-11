package com.reps.demogcloud.data;

import com.reps.demogcloud.models.student.Student;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRepository extends MongoRepository<Student,String> {
    Student findByStudentIdNumber (String id);
    //Select everything from students where last name is equal to our first argument
    List<Student> findByLastName(String lastName);
    Student findByStudentEmail(String email);
}
