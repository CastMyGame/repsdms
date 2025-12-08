package com.reps.demogcloud.controllers;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.models.assignments.Assignment;

import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.dto.AssignmentTemplateSummaryDTO;
import com.reps.demogcloud.models.punishment.Punishment;
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
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final PunishRepository punishRepository;

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

    @GetMapping("/templates/search")
    public ResponseEntity<List<AssignmentTemplateSummaryDTO>> searchTemplates(
            @RequestParam(required = false) String infractionName,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String creatorEmail,
            @RequestParam(required = false) Boolean createdBySystem,
            @RequestParam(required = false, name = "q") String textQuery
    ) {
        var results = assignmentService.searchTemplates(
                infractionName,
                level,
                creatorEmail,
                createdBySystem,
                textQuery
        );
        return ResponseEntity.ok(results);
    }

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

    @GetMapping("/templates/{id}")
    public ResponseEntity<AssignmentTemplate> getTemplateById(@PathVariable String id) throws Exception {
        var template = assignmentService.getTemplateById(id);
        return ResponseEntity.ok(template);
    }

    @GetMapping("/{id}/assignment-template")
    public ResponseEntity<AssignmentTemplate> getAssignmentTemplateForPunishment(@PathVariable String id) throws Exception {
        Punishment punishment = punishRepository.findById(id)
                .orElseThrow(() -> new Exception("Punishment not found: " + id));

        if (punishment.getAssignmentTemplateId() == null) {
            return ResponseEntity.notFound().build();
        }

        AssignmentTemplate template = assignmentService.getTemplateById(punishment.getAssignmentTemplateId());
        return ResponseEntity.ok(template);
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


    // Create a new template
    @PostMapping("/templates")
    public ResponseEntity<AssignmentTemplate> createTemplate(@RequestBody AssignmentTemplate template) {
        var created = assignmentService.createAssignmentTemplate(template);
        return ResponseEntity.ok(created); // or .status(HttpStatus.CREATED).body(created)
    }

    // Update an existing template
    @PutMapping("/templates/{id}")
    public ResponseEntity<AssignmentTemplate> updateTemplate(@PathVariable String id,
                                                             @RequestBody AssignmentTemplate template) throws Exception {
        var updated = assignmentService.updateAssignmentTemplate(id, template);
        return ResponseEntity.ok(updated);
    }

    // Delete a template by id
    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) throws Exception {
        assignmentService.deleteAssignmentTemplate(id);
        return ResponseEntity.noContent().build();
    }
}
