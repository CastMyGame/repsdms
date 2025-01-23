package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.guidance.Guidance;
import com.reps.demogcloud.models.guidance.GuidanceResponse;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.models.student.UpdateSpottersRequest;
import com.reps.demogcloud.services.StudentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.mail.MessagingException;
import java.io.IOException;
import java.util.List;

@CrossOrigin(origins = {
"http//localhost:3000",
        "https://repsdiscipline.vercel.app",
        "https://repsdev.vercel.app"})

@RestController
@RequiredArgsConstructor
@RequestMapping("/student/v1")
public class StudentController {

    private StudentService studentService;


    @Autowired
    public StudentController(StudentService studentService) {
        this.studentService = studentService;
    }

    //---------------------------------GET Controllers--------------------------------
    @GetMapping("/")
    public ResponseEntity<?> getHome() {
        return new ResponseEntity<>(HttpStatus.OK);
    }

    @GetMapping("/studentid/{studentId}")
    public ResponseEntity<Student> getStudentByIdNumber(@PathVariable String studentId) {
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
    public ResponseEntity<Student> getStudentByEmail (@PathVariable String email) throws Exception {
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

    @GetMapping("/detentionList/{school}")
    public ResponseEntity<List<PunishmentDTO>> getDetentionList(@PathVariable String school) {
        List<PunishmentDTO> response = studentService.getDetentionList(school);

        return ResponseEntity
                .accepted()
                .body(response);
    }

    @GetMapping("/issList/{school}")
    public ResponseEntity<List<PunishmentDTO>> getIssList(@PathVariable String school) {
        List<PunishmentDTO> response = studentService.getIssList(school);


        return ResponseEntity
                .accepted()
                .body(response);
    }

    @GetMapping("/findBySpotter/{spotterEmail}")
    public ResponseEntity<List<Student>> getBySpotter(@PathVariable String spotterEmail) {
        List<Student> response = studentService.findBySpotter(spotterEmail);


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
    public ResponseEntity<List<Student>> transferPoints (@RequestBody String givingStudentEmail,
                                                         @RequestParam String receivingStudentEmail,
                                                         @RequestParam Integer pointsTransferred) {
        List<Student> response = studentService.transferPoints(givingStudentEmail,
                receivingStudentEmail,
                pointsTransferred);
        return ResponseEntity
                .accepted()
                .body(response);
    }

    @PostMapping("/getByEmailList")
    public ResponseEntity<List<Student>> getStudentByEmail (@RequestBody List<String> email) throws Exception {
        var message = studentService.findByStudentEmailList(email);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    // Endpoint to add time to a student's time bank
    @PostMapping("/{studentEmail}/add-time")
    public ResponseEntity<Student> addTimeToStudent(
            @PathVariable String studentEmail,
            @RequestParam int hours,
            @RequestParam int minutes) {

        // Call the service to add time to the student's timeBank
        Student updatedStudent = studentService.addTimeToStudent(studentEmail, hours, minutes);

        return ResponseEntity.ok(updatedStudent);
    }
    //----------------------------PUT Controllers--------------------------------
    @PutMapping("/assignSchool")
    public ResponseEntity<List<Student>> massAssignSchool() {
        List<Student> response = studentService.massAssignForSchool();

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

    @PutMapping("/notes/{id}")
    public ResponseEntity<Student> updateGuidance(@PathVariable String id, @RequestBody ThreadEvent event) throws MessagingException, IOException, InterruptedException {
        var message = studentService.updateStudentNotes(id,event);

        return ResponseEntity
                .accepted()
                .body(message);
    }

    @PutMapping("/addAsSpotter")
    public ResponseEntity<List<Student>> addAsSpotter(@RequestBody UpdateSpottersRequest request) {
        var student = studentService.addAsSpotter(request);

        return ResponseEntity.accepted().body(student);
    }

    @PutMapping("/removeAsSpotter")
    public ResponseEntity<List<Student>> deleteSpotters(@RequestBody UpdateSpottersRequest request) {
        var student = studentService.deleteSpotters(request);

        return ResponseEntity.accepted().body(student);
    }



    //---------------------------DELETE Controllers--------------------------
    @DeleteMapping("/delete")
    public ResponseEntity<String> deleteStudent (@RequestBody StudentRequest studentRequest) throws Exception {
        var delete = studentService.deleteStudent(studentRequest);
        return ResponseEntity
                .accepted()
                .body(delete);
    }



    @PutMapping("/remove-spotter/{email}")
    public ResponseEntity<Student> removeSpotterByEmail(@PathVariable String email,@RequestBody Student student) {
        Student response = studentService.removeSpotterByEmail(email,student);

        return ResponseEntity.accepted().body(response);
    }


}
