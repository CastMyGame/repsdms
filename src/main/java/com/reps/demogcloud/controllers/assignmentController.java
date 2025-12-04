package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.assignments.Assignment;

import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.services.AssignmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = {
"http//localhost:3000",
        "https://repsdiscipline.vercel.app",
        "https://repsdev.vercel.app"})

@RestController
@RequiredArgsConstructor
@RequestMapping("/assignments/v1")
public class assignmentController {

    private final AssignmentService assignmentService;

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
        var message = assignmentService.createNewAssignment(assignment);
        return ResponseEntity
                .accepted()
                .body(message);
    }

    //----------------------------PUT Controllers----------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<Assignment> updateAssignment(@RequestBody Assignment assignment,@PathVariable String id) throws Exception {
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

    // ------------------------- MIGRATION ENDPOINT ------------------------------

    /**
     * One-shot endpoint to migrate all legacy assignments into
     * the new assignment_templates collection.
     *
     * You can hit this from Bruno: POST /assignments/v1/migrate-legacy
     */
    @PostMapping("/migrate-legacy")
    public ResponseEntity<String> migrateLegacyAssignments() {
        int migrated = assignmentService.migrateLegacyAssignmentsToTemplates();
        String msg = "Migrated " + migrated + " legacy assignments to templates.";
        return ResponseEntity.ok(msg);
    }

    // ------------------------- (OPTIONAL) NEW TEMPLATE READ ENDPOINTS -----------

    @GetMapping("/templates")
    public ResponseEntity<List<AssignmentTemplate>> getAllTemplates() {
        var templates = assignmentService.getAllTemplates();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/templates/by-infraction")
    public ResponseEntity<List<AssignmentTemplate>> getTemplatesByInfractionAndLevel(
            @RequestParam String infractionName,
            @RequestParam int level
    ) {
        var templates = assignmentService.getTemplatesByInfractionAndLevel(infractionName, level);
        return ResponseEntity.ok(templates);
    }
}
