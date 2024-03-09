package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.punishment.Punishment;
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

@CrossOrigin(origins = {
"http//localhost:3000",
"http://localhost:3000/"})

@RestController
@RequiredArgsConstructor
@RequestMapping("/student/v1")
public class StudentController {

    @Autowired
    private StudentService studentService;

    //---------------------------------GET Controllers--------------------------------
    @GetMapping("/")
    public ResponseEntity<?> getHome() {
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/studentid/{studentId}")
    public ResponseEntity<Student> getStudentByIdNumber(@PathVariable String studentId) throws Exception {
        var message = studentService.findByStudentId(studentId);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/allStudents")
    public ResponseEntity<List<Student>> findAllStudents () {
        var findAll = studentService.getAllStudents(false);

        return ResponseEntity
                .accepted()
                .body(findAll);
    }

    @GetMapping("/archived")
    public ResponseEntity<List<Student>> getAllArchived() {
        List<Student> message = studentService.getAllStudents(true);
        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/lastname/{lastName}")
    public ResponseEntity<List<Student>> getStudentByLastName (@PathVariable String lastName) throws Exception {
        var message = studentService.findByStudentLastName(lastName);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/email/{email}")
    public ResponseEntity<Student> getStudentByEmail (@PathVariable  String email) throws Exception {
        var message = studentService.findByStudentEmail(email);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/parentEmail/{parentEmail}")
    public ResponseEntity<List<Student>> getStudentByParentEmail (@PathVariable  String parentEmail) throws Exception {
        var message = studentService.findStudentByParentEmail(parentEmail);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @GetMapping("/detentionList")
    public ResponseEntity<List<Student>> getDetentionList() {
        List<Student> response = studentService.getDetentionList();

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @GetMapping("/issList")
    public ResponseEntity<List<Punishment>> getIssList() {
        List<Punishment> response = studentService.getIssList();

        return ResponseEntity
                .accepted()
                .body(response);
    }

    //-----------------------------POST Controllers-----------------------------------
    @PostMapping("/newStudent")
    public ResponseEntity<StudentResponse> createStudent (@RequestBody Student studentRequest) {
        StudentResponse studentResponse = studentService.createNewStudent(studentRequest);
        return studentResponse.getStudent() == null
                ? new ResponseEntity<>(studentResponse, HttpStatus.BAD_REQUEST)
                : new ResponseEntity<>(studentResponse, HttpStatus.CREATED);
    }

    @PostMapping("/addStudents")
    public ResponseEntity<List<Student>> addAllStudents(@RequestBody List<Student> students) {
        for(Student student: students) {
            studentService.createNewStudent(student);
        }
        return ResponseEntity
                .accepted()
                .body(students);
    }
    // Points Controllers
    @PostMapping("/points/add")
    public ResponseEntity<Student> addPoints (@RequestParam String studentEmail, @RequestParam Integer points) {
        Student response = studentService.addPoints(studentEmail, points);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PostMapping("/points/delete")
    public ResponseEntity<Student> deletePoints (@RequestParam String studentEmail,@RequestParam Integer points) {
        Student response = studentService.deletePoints(studentEmail, points);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PostMapping("/points/transfer")
    public ResponseEntity<List<Student>> transferPoints (@RequestParam String givingStudentEmail,
                                                         @RequestParam String receivingStudentEmail,
                                                         @RequestParam Integer pointsTransferred) {
        List<Student> response = studentService.transferPoints(givingStudentEmail,
                receivingStudentEmail,
                pointsTransferred);
        return ResponseEntity
                .accepted()
                .body(response);
    }
    //----------------------------PUT Controllers--------------------------------
    @PutMapping("/assignSchool")
    public ResponseEntity<List<Student>> massAssignSchool(@RequestParam boolean isArchived) {
        List<Student> response = studentService.massAssignForSchool(isArchived);

        return ResponseEntity
                .accepted()
                .body(response);
    }
    @PutMapping("/archived/{studentId}")
    public ResponseEntity<Student> archivedDeleted(@PathVariable String studentId) {
        Student response = studentService.archiveRecord(studentId);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    //---------------------------DELETE Controllers--------------------------
    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteStudent (@RequestBody StudentRequest studentRequest) throws Exception {
        var delete = studentService.deleteStudent(studentRequest);
        return ResponseEntity
                .accepted()
                .body(delete);
    }

}
