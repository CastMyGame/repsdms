package com.reps.demogcloud.controllers;

import com.reps.demogcloud.models.assignments.AssignmentTemplateBinding;
import com.reps.demogcloud.models.dto.ClearTeacherDefaultBindingRequest;
import com.reps.demogcloud.models.dto.SetTeacherDefaultBindingRequest;
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
@RequestMapping("/assignments/v1/bindings")
public class AssignmentBindingController {

    private final AssignmentService assignmentService;

    /**
     * Set or change the default template for a teacher + infraction + level.
     *
     * This is what the frontend calls when a teacher clicks:
     * "Use this assignment for my Level X [InfractionName]".
     */
    @PostMapping("/teacher-default")
    public ResponseEntity<AssignmentTemplateBinding> setTeacherDefault(
            @RequestBody SetTeacherDefaultBindingRequest request
    ) throws Exception {

        AssignmentTemplateBinding binding = assignmentService.setTeacherDefaultTemplate(
                request.getTeacherEmail(),
                request.getSchoolId(),
                request.getInfractionName(),
                request.getLevel(),
                request.getAssignmentTemplateId()
        );

        return ResponseEntity.ok(binding);
    }

    /**
     * Clear a teacher's default template for a given infraction + level.
     * After this, the resolver will fall back to school/system defaults.
     */
    @DeleteMapping("/teacher-default")
    public ResponseEntity<Void> clearTeacherDefault(
            @RequestBody ClearTeacherDefaultBindingRequest request
    ) {
        assignmentService.clearTeacherDefaultTemplate(
                request.getTeacherEmail(),
                request.getInfractionName(),
                request.getLevel()
        );
        return ResponseEntity.noContent().build();
    }

    /**
     * List all active bindings for a teacher.
     * Useful for "My Defaults" in the UI.
     */
    @GetMapping("/teacher-default")
    public ResponseEntity<List<AssignmentTemplateBinding>> getTeacherDefaults(
            @RequestParam String teacherEmail
    ) {
        List<AssignmentTemplateBinding> bindings =
                assignmentService.getActiveBindingsForTeacher(teacherEmail);

        return ResponseEntity.ok(bindings);
    }

    /**
     * List all active bindings that reference a given template.
     * Useful for diagnostics / "who is using this assignment?"
     */
    @GetMapping("/by-template/{templateId}")
    public ResponseEntity<List<AssignmentTemplateBinding>> getBindingsForTemplate(
            @PathVariable String templateId
    ) {
        List<AssignmentTemplateBinding> bindings =
                assignmentService.getActiveBindingsForTemplate(templateId);

        return ResponseEntity.ok(bindings);
    }
}
