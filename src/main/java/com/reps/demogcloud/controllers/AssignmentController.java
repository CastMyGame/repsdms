package com.reps.demogcloud.controllers;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.models.assignments.Assignment;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.dto.AssignmentTemplateSummaryDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.services.AssignmentService;
import com.reps.demogcloud.services.WorkflowAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = {
        "http://localhost:3000",
        "https://repsdiscipline.vercel.app",
        "https://repsdev.vercel.app"
})
@RestController
@RequiredArgsConstructor
@RequestMapping("/assignments/v1")
public class AssignmentController {

    private final AssignmentService assignmentService;
    private final PunishRepository punishRepository;
    private final WorkflowAccessService access;

    //-----------------------GET Controllers----------------------------

    @GetMapping("/")
    public ResponseEntity<List<Assignment>> getAllQuestions() throws Exception {
        access.requireStaff();
        List<Assignment> assignments = assignmentService.getAllAssignments();
        return ResponseEntity.accepted().body(assignments);
    }

    @GetMapping("/templates/for-punishment/{punishmentId}")
    public ResponseEntity<AssignmentTemplate> getTemplateForPunishment(
            @PathVariable String punishmentId
    ) throws Exception {
        access.requireCase(punishmentId);
        AssignmentTemplate template = assignmentService.buildAssignmentForPunishment(punishmentId);
        access.requireCaseTemplate(punishmentId, template);
        return ResponseEntity.ok(template);
    }

    @GetMapping("/templates/search")
    public ResponseEntity<List<AssignmentTemplateSummaryDTO>> searchTemplates(
            @RequestParam(required = false) String infractionName,
            @RequestParam(required = false) Integer level,
            @RequestParam(required = false) String creatorEmail,
            @RequestParam(required = false) Boolean createdBySystem,
            @RequestParam(required = false, name = "q") String textQuery
    ) {
        access.requireStaff();
        List<AssignmentTemplateSummaryDTO> results = assignmentService.searchTemplates(
                infractionName,
                level,
                creatorEmail,
                createdBySystem,
                textQuery
        );
        var visibleIds = assignmentService.getAllTemplates().stream().filter(access::canReadTemplate)
                .map(AssignmentTemplate::getId).collect(java.util.stream.Collectors.toSet());
        return ResponseEntity.ok(results.stream().filter(t -> visibleIds.contains(t.getId())).toList());
    }

    @GetMapping("/templates")
    public ResponseEntity<List<AssignmentTemplate>> getAllTemplates() {
        access.requireStaff();
        List<AssignmentTemplate> templates = assignmentService.getAllTemplates().stream()
                .filter(access::canReadTemplate).toList();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/templates/by-infraction")
    public ResponseEntity<List<AssignmentTemplate>> getTemplatesByInfractionAndLevel(
            @RequestParam String infractionName,
            @RequestParam int level
    ) {
        access.requireStaff();
        List<AssignmentTemplate> templates =
                assignmentService.getTemplatesByInfractionAndLevel(infractionName, level).stream()
                        .filter(access::canReadTemplate).toList();
        return ResponseEntity.ok(templates);
    }

    @GetMapping("/templates/{id}")
    public ResponseEntity<AssignmentTemplate> getTemplateById(@PathVariable String id) throws Exception {
        access.requireTemplate(id);
        AssignmentTemplate template = assignmentService.getTemplateById(id);
        return ResponseEntity.ok(template);
    }

    @GetMapping("/{id}/assignment-template")
    public ResponseEntity<AssignmentTemplate> getAssignmentTemplateForPunishment(
            @PathVariable String id
    ) throws Exception {
        access.requireCase(id);
        Punishment punishment = punishRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Punishment not found: " + id));

        if (punishment.getAssignmentTemplateId() == null) {
            return ResponseEntity.notFound().build();
        }

        AssignmentTemplate template = assignmentService.getTemplateById(punishment.getAssignmentTemplateId());
        access.requireCaseTemplate(id, template);
        return ResponseEntity.ok(template);
    }

    // Make sure to migrate this as the main and phase out the old get assignments
    @GetMapping("/by-punishment/{punishmentId}")
    public ResponseEntity<AssignmentTemplate> getAssignmentForPunishment(
            @PathVariable String punishmentId
    ) throws Exception {
        access.requireCase(punishmentId);
        AssignmentTemplate assignment = assignmentService.buildAssignmentForPunishment(punishmentId);
        access.requireCaseTemplate(punishmentId, assignment);
        return ResponseEntity.ok(assignment);
    }

    //------------------------POST Controllers-----------------------------

    @PostMapping("/")
    public ResponseEntity<Assignment> createNewAssignment(@RequestBody Assignment assignment) throws Exception {
        access.denyLegacyWrite();
        Assignment createdAssignment = assignmentService.createNewAssignment(assignment);
        return ResponseEntity.accepted().body(createdAssignment);
    }

    @PostMapping("/migrate-legacy")
    public ResponseEntity<String> migrateLegacyAssignments() {
        access.denyLegacyWrite();
        int migrated = assignmentService.migrateLegacyAssignmentsToTemplates();
        String message = "Migrated " + migrated + " legacy assignments to templates.";
        return ResponseEntity.ok(message);
    }

    @PostMapping("/templates")
    public ResponseEntity<AssignmentTemplate> createTemplate(@RequestBody AssignmentTemplate template) {
        access.prepareTemplateCreation(template);
        AssignmentTemplate created = assignmentService.createAssignmentTemplate(template);
        return ResponseEntity.ok(created);
    }

    //----------------------------PUT Controllers----------------------------------

    @PutMapping("/{id}")
    public ResponseEntity<Assignment> updateAssignment(
            @RequestBody Assignment assignment,
            @PathVariable String id
    ) throws Exception {
        access.denyLegacyWrite();
        Assignment updatedAssignment = assignmentService.updateNewAssignment(assignment, id);
        return ResponseEntity.accepted().body(updatedAssignment);
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<AssignmentTemplate> updateTemplate(
            @PathVariable String id,
            @RequestBody AssignmentTemplate template
    ) throws Exception {
        access.requireTemplateWrite(id);
        AssignmentTemplate updated = assignmentService.updateAssignmentTemplate(id, template);
        return ResponseEntity.ok(updated);
    }

    //-------------------------DELETE Controllers--------------------------------

    @DeleteMapping("/delete/{assignmentName}")
    public ResponseEntity<Assignment> deleteAssignmentByName(@PathVariable String assignmentName) throws Exception {
        access.denyLegacyWrite();
        Assignment deletedAssignment = assignmentService.deleteAssignment(assignmentName);
        return ResponseEntity.accepted().body(deletedAssignment);
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) throws Exception {
        access.requireTemplateWrite(id);
        assignmentService.deleteAssignmentTemplate(id);
        return ResponseEntity.noContent().build();
    }
}