package com.reps.demogcloud.controllers;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.models.assignments.Assignment;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.dto.AssignmentTemplateSummaryDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.services.AssignmentService;
import com.reps.demogcloud.services.UserContextService;
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
    private final UserContextService userContextService;

    //-----------------------GET Controllers----------------------------

    @GetMapping("/")
    public ResponseEntity<List<Assignment>> getAllQuestions() throws Exception {
        List<Assignment> assignments = assignmentService.getAllAssignments();
        return ResponseEntity.accepted().body(assignments);
    }

    @GetMapping("/templates/for-punishment/{punishmentId}")
    public ResponseEntity<AssignmentTemplate> getTemplateForPunishment(
            @PathVariable String punishmentId
    ) throws Exception {
        authorizePunishmentAccess(punishmentId);
        AssignmentTemplate template = assignmentService.buildAssignmentForPunishment(punishmentId);
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
        List<AssignmentTemplateSummaryDTO> results = assignmentService.searchTemplates(
                infractionName,
                level,
                creatorEmail,
                createdBySystem,
                textQuery
        );
        if (creatorEmail != null
                && !creatorEmail.equalsIgnoreCase(userContextService.getCurrentUserEmail())
                && !userContextService.hasRole("ADMIN")) {
            throw new org.springframework.security.access.AccessDeniedException("Cannot search another teacher's private templates");
        }
        return ResponseEntity.ok(results.stream().filter(this::canViewTemplateSummary).toList());
    }

    @GetMapping("/templates")
    public ResponseEntity<List<AssignmentTemplate>> getAllTemplates() {
        List<AssignmentTemplate> templates = assignmentService.getAllTemplates();
        return ResponseEntity.ok(templates.stream().filter(this::canViewTemplate).toList());
    }

    @GetMapping("/templates/by-infraction")
    public ResponseEntity<List<AssignmentTemplate>> getTemplatesByInfractionAndLevel(
            @RequestParam String infractionName,
            @RequestParam int level
    ) {
        List<AssignmentTemplate> templates =
                assignmentService.getTemplatesByInfractionAndLevel(infractionName, level);
        return ResponseEntity.ok(templates.stream().filter(this::canViewTemplate).toList());
    }

    @GetMapping("/templates/{id}")
    public ResponseEntity<AssignmentTemplate> getTemplateById(@PathVariable String id) throws Exception {
        AssignmentTemplate template = assignmentService.getTemplateById(id);
        requireTemplateVisible(template);
        return ResponseEntity.ok(template);
    }

    @GetMapping("/{id}/assignment-template")
    public ResponseEntity<AssignmentTemplate> getAssignmentTemplateForPunishment(
            @PathVariable String id
    ) throws Exception {
        Punishment punishment = punishRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Punishment not found: " + id));
        userContextService.requireStudentRecordAccess(punishment.getStudentEmail());

        if (punishment.getAssignmentTemplateId() == null) {
            return ResponseEntity.notFound().build();
        }

        AssignmentTemplate template = assignmentService.getTemplateById(punishment.getAssignmentTemplateId());
        return ResponseEntity.ok(template);
    }

    // Make sure to migrate this as the main and phase out the old get assignments
    @GetMapping("/by-punishment/{punishmentId}")
    public ResponseEntity<AssignmentTemplate> getAssignmentForPunishment(
            @PathVariable String punishmentId
    ) throws Exception {
        authorizePunishmentAccess(punishmentId);
        AssignmentTemplate assignment = assignmentService.buildAssignmentForPunishment(punishmentId);
        return ResponseEntity.ok(assignment);
    }

    //------------------------POST Controllers-----------------------------

    @PostMapping("/")
    public ResponseEntity<Assignment> createNewAssignment(@RequestBody Assignment assignment) throws Exception {
        userContextService.requireAnyRole("ADMIN");
        Assignment createdAssignment = assignmentService.createNewAssignment(assignment);
        return ResponseEntity.accepted().body(createdAssignment);
    }

    @PostMapping("/migrate-legacy")
    public ResponseEntity<String> migrateLegacyAssignments() {
        userContextService.requireAnyRole("ADMIN");
        int migrated = assignmentService.migrateLegacyAssignmentsToTemplates();
        String message = "Migrated " + migrated + " legacy assignments to templates.";
        return ResponseEntity.ok(message);
    }

    @PostMapping("/templates")
    public ResponseEntity<AssignmentTemplate> createTemplate(@RequestBody AssignmentTemplate template) {
        if (!userContextService.hasRole("ADMIN")) {
            template.setCreatedBySystem(false);
            template.setCreatedByUserId(userContextService.getCurrentUserEmail());
            template.setSchoolId(userContextService.getCurrentUserSchool());
            template.setScope(AssignmentTemplate.Scope.TEACHER_DEFAULT);
        }
        AssignmentTemplate created = assignmentService.createAssignmentTemplate(template);
        return ResponseEntity.ok(created);
    }

    //----------------------------PUT Controllers----------------------------------

    @PutMapping("/{id}")
    public ResponseEntity<Assignment> updateAssignment(
            @RequestBody Assignment assignment,
            @PathVariable String id
    ) throws Exception {
        userContextService.requireAnyRole("ADMIN");
        Assignment updatedAssignment = assignmentService.updateNewAssignment(assignment, id);
        return ResponseEntity.accepted().body(updatedAssignment);
    }

    @PutMapping("/templates/{id}")
    public ResponseEntity<AssignmentTemplate> updateTemplate(
            @PathVariable String id,
            @RequestBody AssignmentTemplate template
    ) throws Exception {
        AssignmentTemplate existing = assignmentService.getTemplateById(id);
        requireTemplateManageable(existing);
        AssignmentTemplate updated = assignmentService.updateAssignmentTemplate(id, template);
        return ResponseEntity.ok(updated);
    }

    //-------------------------DELETE Controllers--------------------------------

    @DeleteMapping("/delete/{assignmentName}")
    public ResponseEntity<Assignment> deleteAssignmentByName(@PathVariable String assignmentName) throws Exception {
        userContextService.requireAnyRole("ADMIN");
        Assignment deletedAssignment = assignmentService.deleteAssignment(assignmentName);
        return ResponseEntity.accepted().body(deletedAssignment);
    }

    @DeleteMapping("/templates/{id}")
    public ResponseEntity<Void> deleteTemplate(@PathVariable String id) throws Exception {
        AssignmentTemplate existing = assignmentService.getTemplateById(id);
        requireTemplateManageable(existing);
        assignmentService.deleteAssignmentTemplate(id);
        return ResponseEntity.noContent().build();
    }

    private void authorizePunishmentAccess(String punishmentId) {
        Punishment punishment = punishRepository.findById(punishmentId)
                .orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Assignment not found"));
        userContextService.requireStudentRecordAccess(punishment.getStudentEmail());
    }

    private boolean canViewTemplateSummary(AssignmentTemplateSummaryDTO template) {
        return userContextService.hasRole("ADMIN")
                || template.isCreatedBySystem()
                || userContextService.getCurrentUserEmail().equalsIgnoreCase(template.getCreatedByUserId());
    }

    private boolean canViewTemplate(AssignmentTemplate template) {
        return userContextService.hasRole("ADMIN")
                || template.isCreatedBySystem()
                || userContextService.getCurrentUserEmail().equalsIgnoreCase(template.getCreatedByUserId())
                || (template.getVisibility() == AssignmentTemplate.Visibility.SCHOOL
                && template.getSchoolId() != null
                && template.getSchoolId().equalsIgnoreCase(userContextService.getCurrentUserSchool()));
    }

    private void requireTemplateVisible(AssignmentTemplate template) {
        if (!canViewTemplate(template)) {
            throw new org.springframework.security.access.AccessDeniedException("Template is private");
        }
    }

    private void requireTemplateManageable(AssignmentTemplate template) {
        if (template.isCreatedBySystem()) {
            userContextService.requireAnyRole("ADMIN");
            return;
        }
        if (!userContextService.hasRole("ADMIN")
                && !userContextService.getCurrentUserEmail().equalsIgnoreCase(template.getCreatedByUserId())) {
            throw new org.springframework.security.access.AccessDeniedException("You may manage only your own templates");
        }
    }
}
