package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.assignments.Assignment;

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

    private AssignmentService assignmentService;

    @Autowired
    public assignmentController(AssignmentService assignmentService) {
        this.assignmentService = assignmentService;
    }

    //-----------------------GET Controllers----------------------------
    @GetMapping("/")
    public ResponseEntity<List<Assignment>> getAllQuestions() throws Exception{
        var message = assignmentService.getAllAssignments();
        return ResponseEntity
                .accepted()
                .body(message);
    }

    //------------------------POST Controllers-----------------------------
    @PostMapping("/")
    public ResponseEntity<Assignment> createNewAssignment(@RequestBody Assignment assignment) throws Exception {
        System.out.println(assignment);
        var message = assignmentService.createNewAssignment(assignment);
        return ResponseEntity
                .accepted()
                .body(message);
    }

    //----------------------------PUT Controllers----------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<Assignment> updateAssignment(@RequestBody Assignment assignment,@PathVariable String id) throws Exception {
        System.out.println(assignment);
        var message = assignmentService.updateNewAssignment(assignment,id);
        return ResponseEntity
                .accepted()
                .body(message);
    }


    //-------------------------DELETE Controllers--------------------------------
    @DeleteMapping("/delete/{assignmentName}")
    public ResponseEntity<Assignment> deleteAssignmentByName(@PathVariable String assignmentName) throws Exception {
        var message = assignmentService.deleteAssignment(assignmentName);
        return ResponseEntity
                .accepted()
                .body(message);
    }
}
