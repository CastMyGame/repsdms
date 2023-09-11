package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.services.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin
@RestController
@RequiredArgsConstructor
@RequestMapping("/student/v1")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @GetMapping("/")
    public ResponseEntity<?> getHome() {
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/studentid/{studentId}")
    public ResponseEntity<Student> getStudentByIdNumber(@PathVariable String studentId) throws Exception {
        var message = studentService.requestStudentIdNumber(studentId);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/lastname/{lastName}")
    public ResponseEntity<List<Student>> getStudentByLastName (@PathVariable String lastName) throws Exception {
        var message = studentService.requestStudentLastName(lastName);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<Student> getStudentByEmail (@PathVariable  String email) throws Exception {
        var message = studentService.requestStudentEmail(email);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteStudent (@RequestBody StudentRequest studentRequest) throws Exception {
        var delete = studentService.deleteStudent(studentRequest);
        return ResponseEntity
                .accepted()
                .body(delete);
    }

//    @PutMapping("/edit")
//    public ResponseEntity<Student> editInfraction (@RequestBody Student student) {
//        var edit = studentService.createNewStudent(student);
//        return ResponseEntity
//                .accepted()
//                .body(edit);
//    }

    @PostMapping("/newStudent")
    public ResponseEntity<StudentResponse> createStudent (@RequestBody Student studentRequest) {
        StudentResponse studentResponse = studentService.createNewStudent(studentRequest);
        return studentResponse.getStudent() == null
                ? new ResponseEntity<>(studentResponse, HttpStatus.BAD_REQUEST)
                : new ResponseEntity<>(studentResponse, HttpStatus.CREATED);
    }

    @GetMapping("/allStudents")
    public ResponseEntity<List<Student>> findAllStudents () {
        var findAll = studentService.getAllStudents();

        return ResponseEntity
                .accepted()
                .body(findAll);
    }
}
