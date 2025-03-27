package com.reps.demogcloud.data;

import com.reps.demogcloud.models.student.Student;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StudentRepository extends MongoRepository<Student,String> {
    List<Student> findByIsArchived (boolean bool);
    List<Student> findByLastName(String lastName);
    List<Student> findByParentEmail(String email);
    List<Student> findBySchool(String school);
    Student findByStudentEmailIgnoreCase(String email);
    Student findByStudentIdNumber (String id);

    List<Student> findByIsArchivedAndSchool(boolean b, String school);

    List<Student> findByIsArchivedAndLastNameAndSchool(Boolean bool,String lastName, String schoolName);

    List<Student> findBySpottersContainsIgnoreCase(String spotterEmail);

    boolean existsByStudentEmail(String studentEmail);
}
