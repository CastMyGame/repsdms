package com.reps.demogcloud.services;

import com.reps.demogcloud.data.AssignmentRepository;
import com.reps.demogcloud.data.AssignmentTemplateBindingRepository;
import com.reps.demogcloud.data.AssignmentTemplateRepository;
import com.reps.demogcloud.models.assignments.Assignment;

import java.time.Instant;
import java.util.ArrayList;
import java.util.NoSuchElementException;

import com.reps.demogcloud.models.assignments.AssignmentConverter;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.assignments.AssignmentTemplateBinding;
import com.reps.demogcloud.models.dto.AssignmentTemplateSummaryDTO;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
@Slf4j
@AllArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentTemplateRepository assignmentTemplateRepository;
    private final AssignmentTemplateBindingRepository bindingRepository;

    // -------- LEGACY METHODS (still operate on old Assignment model) --------
    public List<Assignment> getAllAssignments() {
        List<Assignment> assignments = new ArrayList<>();
        try {
            assignments = assignmentRepository.findAll();
        } catch (Exception e) {
            e.printStackTrace();
        }
       return assignments;
    }
    public  Assignment createNewAssignment(Assignment assignments){
        return assignmentRepository.save(assignments);
    }
    public  Assignment deleteAssignment(String assignmentName){
        return assignmentRepository.deleteByInfractionName(assignmentName);
    }
    public Assignment updateNewAssignment(Assignment assignment, String id) throws Exception {
        try {
            Assignment existingAssignment = assignmentRepository.findById(id).orElseThrow();

            // Update fields of existingAssignment with the values from the provided assignment
            existingAssignment.setInfractionName(assignment.getInfractionName());
            existingAssignment.setLevel(assignment.getLevel());
            existingAssignment.setQuestions(assignment.getQuestions());

            // Update other fields as needed

            // Save the updated assignment
            return assignmentRepository.save(existingAssignment);

        } catch (NoSuchElementException e) {
            // Handle the case when the assignment with the given id is not found
            // You can throw a custom exception or return null depending on your requirements
            throw new Exception("Assignment with ID " + id + " not found");
        }
    }
    // -------- NEW METHOD: MIGRATE LEGACY -> TEMPLATES --------

    /**
     * One-time migration: read all legacy Assignment documents
     * and write corresponding AssignmentTemplate documents
     * into the 'assignment_templates' collection.
     *
     * Returns the count migrated.
     */
    public int migrateLegacyAssignmentsToTemplates() {
        List<Assignment> legacyAssignments = assignmentRepository.findAll();
        log.info("Found {} legacy assignments to migrate", legacyAssignments.size());

        int migrated = 0;
        for (Assignment legacy : legacyAssignments) {
            AssignmentTemplate template = AssignmentConverter.fromLegacy(legacy);
            assignmentTemplateRepository.save(template);
            migrated++;
            log.info("Migrated legacy assignment {} -> template {}",
                    legacy.getAssignmentId(), template.getId());
        }

        log.info("Finished migrating {} legacy assignments to templates", migrated);
        return migrated;
    }

    // ================= V2: TEMPLATE METHODS (canonical going forward) =================

    public List<AssignmentTemplate> getAllTemplates() {
        return assignmentTemplateRepository.findAll();
    }

    public AssignmentTemplate getTemplateById(String id) throws Exception {
        return assignmentTemplateRepository.findById(id)
                .orElseThrow(() -> new Exception("AssignmentTemplate with ID " + id + " not found"));
    }

    public List<AssignmentTemplate> getTemplatesByInfractionAndLevel(String infractionName, int level) {
        return assignmentTemplateRepository.findByInfractionNameAndLevel(infractionName, level);
    }

    public AssignmentTemplate createAssignmentTemplate(AssignmentTemplate template) {
        // new templates will generally be teacher- or system-created
        if (template.getId() != null) {
            // Let Mongo generate an ID instead of trusting client if you prefer
            template.setId(null);
        }
        Instant now = Instant.now();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);

        // For now default to system-created; later we’ll set createdByUserId & createdBySystem=false
        if (!template.isCreatedBySystem() && template.getCreatedByUserId() == null) {
            template.setCreatedBySystem(true);
        }

        return assignmentTemplateRepository.save(template);
    }

    public AssignmentTemplate updateAssignmentTemplate(String id, AssignmentTemplate updated) throws Exception {
        AssignmentTemplate existing = assignmentTemplateRepository.findById(id)
                .orElseThrow(() -> new Exception("AssignmentTemplate with ID " + id + " not found"));

        // Fields you want to allow updating:
        existing.setInfractionName(updated.getInfractionName());
        existing.setLevel(updated.getLevel());
        existing.setQuestions(updated.getQuestions());
        // you might NOT want to update createdBySystem / createdByUserId here
        existing.setUpdatedAt(Instant.now());

        return assignmentTemplateRepository.save(existing);
    }

    public void deleteAssignmentTemplate(String id) throws Exception {
        AssignmentTemplate existing = assignmentTemplateRepository.findById(id)
                .orElseThrow(() -> new Exception("AssignmentTemplate with ID " + id + " not found"));

        assignmentTemplateRepository.delete(existing);
    }

    public AssignmentTemplate resolveTemplateForInfraction(
            String teacherEmail,
            String schoolId,
            String infractionName,
            int level
    ) throws Exception {

        // 1) Teacher-specific binding
        if (teacherEmail != null) {
            var bindingOpt = bindingRepository
                    .findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                            teacherEmail, infractionName, level
                    );

            if (bindingOpt.isPresent()) {
                String templateId = bindingOpt.get().getAssignmentTemplateId();
                return assignmentTemplateRepository.findById(templateId)
                        .orElseThrow(() -> new Exception(
                                "Binding exists but template " + templateId + " not found"));
            }
        }

        // 2) School-wide fallback
        if (schoolId != null) {
            var bindingOpt = bindingRepository
                    .findFirstByTeacherEmailIsNullAndSchoolIdAndInfractionNameAndLevelAndActiveTrue(
                            schoolId, infractionName, level
                    );

            if (bindingOpt.isPresent()) {
                String templateId = bindingOpt.get().getAssignmentTemplateId();
                return assignmentTemplateRepository.findById(templateId)
                        .orElseThrow(() -> new Exception(
                                "School binding exists but template " + templateId + " not found"));
            }
        }

        // 3) System default fallback
        var systemTemplates =
                assignmentTemplateRepository
                        .findByInfractionNameAndLevelAndScopeAndActiveTrue(
                                infractionName,
                                level,
                                AssignmentTemplate.Scope.SYSTEM_DEFAULT
                        );

        if (!systemTemplates.isEmpty()) {
            return systemTemplates.get(0);
        }

        throw new Exception(
                "No AssignmentTemplate found for infraction=" + infractionName + ", level=" + level);
    }

    // ================= BINDINGS: TEACHER DEFAULTS & SHARING =================

    public AssignmentTemplateBinding setTeacherDefaultTemplate(
            String teacherEmail,
            String schoolId,
            String infractionName,
            int level,
            String assignmentTemplateId
    ) throws Exception {

        // Ensure the template exists
        AssignmentTemplate template = assignmentTemplateRepository.findById(assignmentTemplateId)
                .orElseThrow(() -> new Exception("AssignmentTemplate with ID " + assignmentTemplateId + " not found"));

        Instant now = Instant.now();

        // Deactivate existing binding for this teacher/infraction/level
        bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                teacherEmail, infractionName, level
        ).ifPresent(existing -> {
            existing.setActive(false);
            existing.setUpdatedAt(now);
            bindingRepository.save(existing);
        });

        // Create the new binding
        AssignmentTemplateBinding binding = new AssignmentTemplateBinding();
        binding.setTeacherEmail(teacherEmail);
        binding.setSchoolId(schoolId);
        binding.setInfractionName(infractionName);
        binding.setLevel(level);
        binding.setAssignmentTemplateId(template.getId());
        binding.setActive(true);
        binding.setCreatedAt(now);
        binding.setUpdatedAt(now);

        return bindingRepository.save(binding);
    }

    public void clearTeacherDefaultTemplate(
            String teacherEmail,
            String infractionName,
            int level
    ) {
        Instant now = Instant.now();

        bindingRepository.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
                teacherEmail, infractionName, level
        ).ifPresent(existing -> {
            existing.setActive(false);
            existing.setUpdatedAt(now);
            bindingRepository.save(existing);
        });
    }

    public List<AssignmentTemplateBinding> getActiveBindingsForTeacher(String teacherEmail) {
        return bindingRepository.findByTeacherEmailAndActiveTrue(teacherEmail);
    }

    public List<AssignmentTemplateBinding> getActiveBindingsForTemplate(String templateId) {
        return bindingRepository.findByAssignmentTemplateIdAndActiveTrue(templateId);
    }

    public List<AssignmentTemplateSummaryDTO> searchTemplates(
            String infractionName,
            Integer level,
            String creatorEmail,
            Boolean createdBySystem,
            String textQuery
    ) {
        List<AssignmentTemplate> all = assignmentTemplateRepository.findAll();

        if (all.isEmpty()) {
            // No templates at all → just return empty list of DTOs
            return List.of();
        }

        String infractionFilter = infractionName != null ? infractionName.trim().toLowerCase() : null;
        String creatorFilter = creatorEmail != null ? creatorEmail.trim().toLowerCase() : null;
        String textFilter = textQuery != null ? textQuery.trim().toLowerCase() : null;

        List<AssignmentTemplate> filtered = all.stream()
                .filter(t -> {
                    // infractionName filter
                    if (infractionFilter != null) {
                        if (t.getInfractionName() == null ||
                                !t.getInfractionName().trim().toLowerCase().equals(infractionFilter)) {
                            return false;
                        }
                    }

                    // level filter
                    if (level != null && t.getLevel() != level) {
                        return false;
                    }

                    // creator email filter (using createdByUserId as email)
                    if (creatorFilter != null) {
                        String creator = t.getCreatedByUserId();
                        if (creator == null || !creator.trim().toLowerCase().equals(creatorFilter)) {
                            return false;
                        }
                    }

                    // createdBySystem filter
                    if (createdBySystem != null) {
                        if (t.isCreatedBySystem() != createdBySystem) {
                            return false;
                        }
                    }

                    // text query filter: look in title/body/prompt of any question
                    if (textFilter != null) {
                        return matchesTextQuery(t, textFilter);
                    }

                    return true;
                })
                .toList();

        return filtered.stream()
                .map(this::toSummaryDTO)
                .toList();
    }

    private boolean matchesTextQuery(AssignmentTemplate template, String textFilter) {
        if (template.getQuestions() == null || template.getQuestions().isEmpty()) {
            return false;
        }

        for (AssignmentTemplate.TemplateQuestion q : template.getQuestions()) {
            // Check title
            if (StringUtils.hasText(q.getTitle()) &&
                    q.getTitle().toLowerCase().contains(textFilter)) {
                return true;
            }
            // Check prompt
            if (StringUtils.hasText(q.getPrompt()) &&
                    q.getPrompt().toLowerCase().contains(textFilter)) {
                return true;
            }
            // Check passage body
            if (StringUtils.hasText(q.getPassageBody()) &&
                    q.getPassageBody().toLowerCase().contains(textFilter)) {
                return true;
            }
        }

        return false;
    }

    private AssignmentTemplateSummaryDTO toSummaryDTO(AssignmentTemplate t) {

        String preview = null;
        if (t.getQuestions() != null && !t.getQuestions().isEmpty()) {
            var q = t.getQuestions().get(0);
            if (q.getPrompt() != null && !q.getPrompt().isBlank()) {
                preview = q.getPrompt();
            } else if (q.getTitle() != null && !q.getTitle().isBlank()) {
                preview = q.getTitle();
            } else if (q.getPassageBody() != null && !q.getPassageBody().isBlank()) {
                preview = q.getPassageBody().substring(0, Math.min(80, q.getPassageBody().length()))
                        + (q.getPassageBody().length() > 80 ? "..." : "");
            }
        }

        return new AssignmentTemplateSummaryDTO(
                t.getId(),
                t.getInfractionName(),
                t.getLevel(),
                t.isCreatedBySystem(),
                t.getCreatedByUserId(),
                (t.getQuestions() != null ? t.getQuestions().size() : 0),
                preview,
                t.getCreatedAt(),
                t.getUpdatedAt()
        );
    }
}
