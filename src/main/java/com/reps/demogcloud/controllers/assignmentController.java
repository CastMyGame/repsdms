package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.assignments.Assignment;
import com.reps.demogcloud.models.student.Student;

import com.reps.demogcloud.services.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = {
"http//localhost:3000",
"http://localhost:3000/"})

@RestController
@RequiredArgsConstructor
@RequestMapping("/assignments/v1")
public class assignmentController {

    @Autowired
  private AssignmentService assignmentService;


    @GetMapping("/")
    public ResponseEntity<List<Assignment>> getAllQuestions() throws Exception {
        var message = assignmentService.getAllAssignments();
        return ResponseEntity
                .accepted()
                .body(message);
    }


    @PostMapping("/")
    public ResponseEntity<Assignment> createNewAssignment(@RequestBody Assignment assignment) throws Exception {
        System.out.println(assignment);
        var message = assignmentService.createNewAssignment(assignment);
        return ResponseEntity
                .accepted()
                .body(message);
    }


    @DeleteMapping("/delete/{assignmentName}")
    public ResponseEntity<Assignment> deleteAssignmentByName(@PathVariable String assignmentName) throws Exception {
        var message = assignmentService.deleteAssignment(assignmentName);
        return ResponseEntity
                .accepted()
                .body(message);
    }
}
