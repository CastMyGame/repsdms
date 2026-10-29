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

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.data.mongodb.core.MongoTemplate mongo;
    private List<AssignmentTemplate> scopedTemplates(){
        canonical.staff();var global=new org.springframework.data.mongodb.core.query.Criteria().andOperator(org.springframework.data.mongodb.core.query.Criteria.where("tenantSchoolId").exists(false),org.springframework.data.mongodb.core.query.Criteria.where("createdBySystem").is(true),org.springframework.data.mongodb.core.query.Criteria.where("scope").is("SYSTEM_DEFAULT"));
        return mongo.find(org.springframework.data.mongodb.core.query.Query.query(new org.springframework.data.mongodb.core.query.Criteria().orOperator(org.springframework.data.mongodb.core.query.Criteria.where("tenantSchoolId").is(canonical.tenant()),global)),AssignmentTemplate.class).stream().filter(canonical::templateRead).toList();
    }
    private void ownedTeacher(String teacher){canonical.staff();if(teacher==null || !canonical.email().equalsIgnoreCase(teacher))throw new org.springframework.security.access.AccessDeniedException("Access denied");}
    private List<AssignmentTemplateBinding> bindings(String teacher,String infraction,int level){
        var legacy=org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("tenantSchoolId").exists(false).and("teacherEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(teacher)).and("infractionName").is(infraction).and("level").is(level).and("active").ne(false));
        if(mongo.exists(legacy,AssignmentTemplateBinding.class))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Legacy binding ownership requires reviewed resolution");
        return mongo.find(canonical.query().addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("teacherEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(teacher)).and("infractionName").is(infraction).and("level").is(level).and("active").is(true)),AssignmentTemplateBinding.class);
    }
    private void bindingFence(){if(mongo.updateFirst(canonical.id(canonical.actor().profileId()),new org.springframework.data.mongodb.core.query.Update().inc("bindingRevision",1),com.reps.demogcloud.models.employee.Employee.class).getMatchedCount()!=1)throw new org.springframework.dao.OptimisticLockingFailureException("Binding owner changed");}
    public AssignmentTemplate resolveForCase(String teacher, String school, String infraction, int level) {
        return resolver.resolve(teacher,school,infraction,level);
    }

    // -------- LEGACY METHODS (still operate on old Assignment model) --------

    public List<Assignment> getAllAssignments() {
        return assignmentRepository.findAll();
    }

    public Assignment createNewAssignment(Assignment assignment) {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy assignment mutation is unavailable");
        return assignmentRepository.save(assignment);
    }

    public Assignment deleteAssignment(String assignmentName) {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy assignment mutation is unavailable");
        return assignmentRepository.deleteByInfractionName(assignmentName);
    }

    public Assignment updateNewAssignment(Assignment assignment, String id) throws Exception {
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy assignment mutation is unavailable");
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
        if(canonical!=null)throw new org.springframework.security.access.AccessDeniedException("Legacy assignment mutation is unavailable");
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
        return canonical==null?assignmentTemplateRepository.findAll():scopedTemplates();
    }

    public AssignmentTemplate getTemplateById(String id) throws Exception {
        return canonical==null?assignmentTemplateRepository.findById(id).orElseThrow(() -> new Exception("AssignmentTemplate with ID " + id + " not found")):canonical.template(id);
    }

    public List<AssignmentTemplate> getTemplatesByInfractionAndLevel(String infractionName, int level) {
        return canonical==null?assignmentTemplateRepository.findByInfractionNameAndLevel(infractionName, level):scopedTemplates().stream().filter(t->java.util.Objects.equals(t.getInfractionName(),infractionName) && t.getLevel()==level).toList();
    }

    public AssignmentTemplate createAssignmentTemplate(AssignmentTemplate template) {
        if(canonical!=null)canonical.prepareTemplate(template);
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
        AssignmentTemplate existing = canonical==null?assignmentTemplateRepository.findById(id).orElseThrow(() -> new Exception("AssignmentTemplate with ID " + id + " not found")):canonical.template(id);
        if(canonical!=null)canonical.templateWrite(existing);

        existing.setInfractionName(updated.getInfractionName());
        existing.setLevel(updated.getLevel());
        existing.setQuestions(updated.getQuestions());
        existing.setUpdatedAt(Instant.now());

        return assignmentTemplateRepository.save(existing);
    }

    public void deleteAssignmentTemplate(String id) throws Exception {
        AssignmentTemplate existing = canonical==null?assignmentTemplateRepository.findById(id).orElseThrow(() -> new Exception("AssignmentTemplate with ID " + id + " not found")):canonical.template(id);
        if(canonical!=null)canonical.templateWrite(existing);

        if(canonical!=null){existing.setActive(false);existing.setUpdatedAt(Instant.now());assignmentTemplateRepository.save(existing);return;}
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

        if(canonical!=null){
            ownedTeacher(teacherEmail);if(schoolId!=null && !schoolId.isBlank())canonical.label(schoolId);
            var selected=canonical.template(assignmentTemplateId);if(!canonical.templateRead(selected) || !selected.isActive())throw new org.springframework.security.access.AccessDeniedException("Access denied");
            return canonical.atomic(()->{
                bindingFence();var previous=bindings(teacherEmail,infractionName,level);
                if(previous.size()>1)throw new org.springframework.dao.OptimisticLockingFailureException("Conflicting bindings require review");
                if(previous.size()==1 && assignmentTemplateId.equals(previous.get(0).getAssignmentTemplateId()))return previous.get(0);
                for(var old:previous){old.setActive(false);old.setUpdatedAt(Instant.now());bindingRepository.save(old);}
                var created=new AssignmentTemplateBinding();created.setTeacherEmail(canonical.email());created.setSchoolId(canonical.school().getSchoolName());created.setInfractionName(infractionName);created.setLevel(level);created.setAssignmentTemplateId(assignmentTemplateId);created.setActive(true);created.setCreatedAt(Instant.now());created.setUpdatedAt(Instant.now());canonical.bindNew(created);return bindingRepository.save(created);
            });
        }
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
        if(canonical!=null){ownedTeacher(teacherEmail);canonical.atomic(()->{bindingFence();for(var old:bindings(teacherEmail,infractionName,level)){old.setActive(false);old.setUpdatedAt(Instant.now());bindingRepository.save(old);}return null;});return;}
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
        if(canonical!=null){ownedTeacher(teacherEmail);return mongo.find(canonical.query().addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("teacherEmail").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(teacherEmail)).and("active").is(true)),AssignmentTemplateBinding.class);}return bindingRepository.findByTeacherEmailAndActiveTrue(teacherEmail);
    }

    public List<AssignmentTemplateBinding> getActiveBindingsForTemplate(String templateId) {
        if(canonical!=null){canonical.templateWrite(canonical.template(templateId));return mongo.find(canonical.query().addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("assignmentTemplateId").is(templateId).and("active").is(true)),AssignmentTemplateBinding.class);}return bindingRepository.findByAssignmentTemplateIdAndActiveTrue(templateId);
    }

    public List<AssignmentTemplateSummaryDTO> searchTemplates(
            String infractionName,
            Integer level,
            String creatorEmail,
            Boolean createdBySystem,
            String textQuery
    ) {
        List<AssignmentTemplate> all = canonical==null?assignmentTemplateRepository.findAll():scopedTemplates();

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
        Punishment punishment = canonical==null?punishRepository.findById(punishmentId).orElseThrow(() -> new Exception("Punishment not found: " + punishmentId)):canonical.record(punishmentId,Punishment.class,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.PUNISHMENT);

        return resolveTemplateForPunishment(punishment);
    }
}
