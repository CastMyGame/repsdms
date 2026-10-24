package com.reps.demogcloud.services;

import com.reps.demogcloud.data.AssignmentRepository;
import com.reps.demogcloud.data.AssignmentTemplateBindingRepository;
import com.reps.demogcloud.data.AssignmentTemplateRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.assignments.Assignment;
import com.reps.demogcloud.models.assignments.AssignmentConverter;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.assignments.AssignmentTemplateBinding;
import com.reps.demogcloud.models.dto.AssignmentTemplateSummaryDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class AssignmentService {

    private final AssignmentRepository assignmentRepository;
    private final AssignmentTemplateRepository assignmentTemplateRepository;
    private final AssignmentTemplateBindingRepository bindingRepository;
    private final StudentRepository studentRepository;
    private final PunishRepository punishRepository;
    private final AssignmentResolver resolver;

    public AssignmentTemplate resolveForCase(String teacher, String school, String infraction, int level) {
        return resolver.resolve(teacher,school,infraction,level);
    }

    // -------- LEGACY METHODS (still operate on old Assignment model) --------

    public List<Assignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    public Assignment createNewAssignment(Assignment assignment) {
        return assignmentRepository.save(assignment);
    }

    public Assignment deleteAssignment(String assignmentName) {
        return assignmentRepository.deleteByInfractionName(assignmentName);
    }

    public Assignment updateNewAssignment(Assignment assignment, String id) throws Exception {
        Assignment existingAssignment = assignmentRepository.findById(id)
                .orElseThrow(() -> new Exception("Assignment with ID " + id + " not found"));

        existingAssignment.setInfractionName(assignment.getInfractionName());
        existingAssignment.setLevel(assignment.getLevel());
        existingAssignment.setQuestions(assignment.getQuestions());

        return assignmentRepository.save(existingAssignment);
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
        if (template.getId() != null) {
            template.setId(null);
        }

        Instant now = Instant.now();
        template.setCreatedAt(now);
        template.setUpdatedAt(now);

        if (template.getCreatedByUserId() != null && !template.getCreatedByUserId().isBlank()) {
            // Teacher / user created
            template.setCreatedBySystem(false);
            if (template.getScope() == null) {
                template.setScope(AssignmentTemplate.Scope.TEACHER_DEFAULT);
            }
        } else {
            // No user ID → treat as system
            template.setCreatedBySystem(true);
            if (template.getScope() == null) {
                template.setScope(AssignmentTemplate.Scope.SYSTEM_DEFAULT);
            }
        }

        return assignmentTemplateRepository.save(template);
    }

    public AssignmentTemplate updateAssignmentTemplate(String id, AssignmentTemplate updated) throws Exception {
        AssignmentTemplate existing = assignmentTemplateRepository.findById(id)
                .orElseThrow(() -> new Exception("AssignmentTemplate with ID " + id + " not found"));

        existing.setInfractionName(updated.getInfractionName());
        existing.setLevel(updated.getLevel());
        existing.setQuestions(updated.getQuestions());
        existing.setUpdatedAt(Instant.now());

        return assignmentTemplateRepository.save(existing);
    }

    public void deleteAssignmentTemplate(String id) throws Exception {
        AssignmentTemplate existing = assignmentTemplateRepository.findById(id)
                .orElseThrow(() -> new Exception("AssignmentTemplate with ID " + id + " not found"));

        assignmentTemplateRepository.delete(existing);
    }

    public AssignmentTemplate resolveTemplateForInfraction(String teacherEmail, String schoolId, String infractionName, int level) {
        return resolveForCase(teacherEmail, schoolId, infractionName, level);
    }

    public AssignmentTemplate resolveTemplateForPunishment(Punishment punishment) {
        if (punishment.getAssignmentTemplateId()==null)
            throw new IllegalArgumentException("Assignment unavailable; staff must explicitly bind a template to this case");
        AssignmentTemplate pinned=resolver.pinned(punishment.getAssignmentTemplateId(),punishment.getSchool());
        if(punishment.getAssignmentWork()!=null && punishment.getAssignmentWork().getTemplate()==null)
            throw new IllegalArgumentException("Assignment unavailable; staff must review this case");
        return punishment.getAssignmentWork()!=null && punishment.getAssignmentWork().getTemplate()!=null
                ? punishment.getAssignmentWork().getTemplate() : pinned;
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
            return List.of();
        }

        String infractionFilter = infractionName != null ? infractionName.trim().toLowerCase() : null;
        String creatorFilter = creatorEmail != null ? creatorEmail.trim().toLowerCase() : null;
        String textFilter = textQuery != null ? textQuery.trim().toLowerCase() : null;

        List<AssignmentTemplate> filtered = all.stream()
                .filter(t -> {
                    if (infractionFilter != null) {
                        if (t.getInfractionName() == null ||
                                !t.getInfractionName().trim().toLowerCase().equals(infractionFilter)) {
                            return false;
                        }
                    }

                    if (level != null && t.getLevel() != level) {
                        return false;
                    }

                    if (creatorFilter != null) {
                        String creator = t.getCreatedByUserId();
                        if (creator == null || !creator.trim().toLowerCase().equals(creatorFilter)) {
                            return false;
                        }
                    }

                    if (createdBySystem != null) {
                        if (t.isCreatedBySystem() != createdBySystem) {
                            return false;
                        }
                    }

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
            if (template.getName() != null &&
                    template.getName().toLowerCase().contains(textFilter)) {
                return true;
            }

            if (StringUtils.hasText(q.getTitle()) &&
                    q.getTitle().toLowerCase().contains(textFilter)) {
                return true;
            }

            if (StringUtils.hasText(q.getPrompt()) &&
                    q.getPrompt().toLowerCase().contains(textFilter)) {
                return true;
            }

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
            AssignmentTemplate.TemplateQuestion q = t.getQuestions().get(0);
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
                t.getName(),
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

    public AssignmentTemplate buildAssignmentForPunishment(String punishmentId) throws Exception {
        Punishment punishment = punishRepository.findById(punishmentId)
                .orElseThrow(() -> new Exception("Punishment not found: " + punishmentId));

        return resolveTemplateForPunishment(punishment);
    }
}
