package com.reps.demogcloud.services;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.dto.*;
import java.util.List;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/** HTTP workflow authorization uses persisted identities, never request roles or school fields. */
@Service
@RequiredArgsConstructor
public class WorkflowAccessService {
    private final UserRepository users;
    private final EmployeeRepository employees;
    private final StudentRepository students;
    private final SchoolRepository schools;
    private final PunishRepository punishments;
    private final AssignmentTemplateRepository templates;
    private final UserContextService userContext;

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    public com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical(){return canonical;}
    public boolean sameTenant(Object record){return canonical!=null?canonical.owns(record):sameSchool(record instanceof com.reps.demogcloud.models.school.School school?school.getSchoolName():(String)new org.springframework.beans.BeanWrapperImpl(record).getPropertyValue("school"));}

    private UserModel actor() {
        if(canonical!=null)return canonical.account();
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) denied();
        var user = users.findByUsername(auth.getName());
        if (user == null || !user.isEnabled() || user.isPasswordSetupRequired() || !text(user.getSchool())
                || !same(user.getUsername(), auth.getName())) denied();
        return user;
    }

    private boolean role(UserModel user, String role) {
        return user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r != null && role.equalsIgnoreCase(r.getRole()));
    }

    public void requireStaff() { if(canonical!=null){canonical.staff();return;}userContext.getCurrentStaffEmail(); actor(); }

    public void requireAdmin() {
        if(canonical!=null){canonical.admin();return;}
        requireStaff();
        if (!role(actor(), "ADMIN")) denied();
    }

    public boolean hasCurrentRole(String name) { return role(actor(), name); }

    public boolean isStudentActor() { return role(actor(), "STUDENT"); }

    public void requireStudent() {
        var user = actor();
        if (!role(user, "STUDENT")) denied();
        requireStudentAccess(user.getUsername());
    }

    public void requireStudentAccess(String email) {
        if (!canReadStudent(email)) denied();
    }

    public boolean canReadStudent(String email) {
        if(canonical!=null){try{canonical.student(email);return true;}catch(AccessDeniedException forbidden){return false;}}
        var user = actor();
        var student = students.findByStudentEmailIgnoreCase(email);
        if (student == null || student.isArchived() || !same(student.getStudentEmail(), email)
                || !same(user.getSchool(), student.getSchool())) return false;
        if (role(user, "STUDENT")) return same(user.getUsername(), student.getStudentEmail());
        requireStaff();
        return userContext.hasIndependentStudentAccess(user.getUsername(), student);
    }

    public boolean canReadCase(Punishment punishment) {
        if(canonical!=null)return canonical.canReadRecord(punishment,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.PUNISHMENT);
        if (punishment == null) return false;
        var user = actor();
        var student = students.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
        if (student == null || !same(punishment.getSchool(), student.getSchool())
                || !same(user.getSchool(), student.getSchool())) return false;
        if (canReadStudent(student.getStudentEmail())) return true;
        if (role(user, "STUDENT")) return false;
        requireStaff();
        return same(user.getUsername(), punishment.getTeacherEmail())
                || same(user.getUsername(), punishment.getOriginalAuthorEmail())
                || same(user.getUsername(), punishment.getResponsibleStaffEmail());
    }

    public Punishment requireCase(String id) {
        if(canonical!=null)return canonical.record(id,Punishment.class,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.PUNISHMENT);
        var punishment = punishments.findById(id).orElseThrow(WorkflowAccessService::forbidden);
        if (!canReadCase(punishment)) denied();
        return punishment;
    }
    public boolean canReadOffice(com.reps.demogcloud.models.officeReferral.OfficeReferral referral) {
        if(canonical!=null)return canonical.canReadRecord(referral,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.OFFICE);
        if(referral==null) return false;
        var scope=new Punishment();scope.setStudentEmail(referral.getStudentEmail());scope.setSchool(referral.getSchool());
        scope.setTeacherEmail(referral.getTeacherEmail());scope.setOriginalAuthorEmail(referral.getOriginalAuthorEmail());
        scope.setResponsibleStaffEmail(referral.getResponsibleStaffEmail());
        return canReadCase(scope);
    }

    private boolean system(AssignmentTemplate template) {
        return template.isCreatedBySystem() && !text(template.getCreatedByUserId())
                && !text(template.getSchoolId()) && template.getScope() == AssignmentTemplate.Scope.SYSTEM_DEFAULT;
    }

    private boolean schoolMatches(String id, String name) {
        if (same(id, name)) return true;
        return schools.findBySchoolNameIgnoreCase(name)
                .map(s -> same(id, s.getSchoolIdNumber())).orElse(false);
    }

    private boolean templateInSchool(AssignmentTemplate template, String school) {
        if (text(template.getSchoolId())) return schoolMatches(template.getSchoolId(), school);
        // Legacy personal templates omitted schoolId; resolve ownership from trusted staff records.
        if (!text(template.getCreatedByUserId())) return false;
        var owner = employees.findByEmailIgnoreCase(template.getCreatedByUserId());
        return owner != null && !owner.isArchived() && same(owner.getSchool(), school);
    }

    public boolean canReadTemplate(AssignmentTemplate template) {
        if(canonical!=null)return canonical.templateRead(template);
        requireStaff();
        if (template == null) return false;
        var user = actor();
        return system(template) || (templateInSchool(template, user.getSchool())
                && (same(template.getCreatedByUserId(), user.getUsername()) || role(user, "ADMIN")
                || template.getVisibility() == AssignmentTemplate.Visibility.SCHOOL
                || template.getScope() == AssignmentTemplate.Scope.SCHOOL_DEFAULT));
    }

    public AssignmentTemplate requireTemplate(String id) {
        var template = canonical==null?templates.findById(id).orElseThrow(WorkflowAccessService::forbidden):canonical.template(id);
        if (!canReadTemplate(template)) denied();
        return template;
    }

    public void requireTemplateWrite(String id) {
        if(canonical!=null){canonical.templateWrite(requireTemplate(id));return;}
        var template = requireTemplate(id);
        if (system(template) || !templateInSchool(template, actor().getSchool())
                || (!same(template.getCreatedByUserId(), actor().getUsername()) && !role(actor(), "ADMIN"))) denied();
    }

    public void prepareTemplateCreation(AssignmentTemplate template) {
        if(canonical!=null){canonical.prepareTemplate(template);return;}
        requireStaff();
        var user = actor();
        if ((text(template.getCreatedByUserId()) && !same(template.getCreatedByUserId(), user.getUsername()))
                || (text(template.getSchoolId()) && !schoolMatches(template.getSchoolId(), user.getSchool()))
                || template.isCreatedBySystem()) denied();
        if (template.getScope() == AssignmentTemplate.Scope.SCHOOL_DEFAULT && !role(user, "ADMIN")) denied();
        template.setCreatedByUserId(user.getUsername());
        template.setSchoolId(user.getSchool());
        template.setCreatedBySystem(false);
        if (template.getScope() != AssignmentTemplate.Scope.SCHOOL_DEFAULT)
            template.setScope(AssignmentTemplate.Scope.TEACHER_DEFAULT);
    }

    public void requireCaseTemplate(String caseId, AssignmentTemplate template) {
        if(canonical!=null){var record=requireCase(caseId);if(template==null || !java.util.Objects.equals(record.getAssignmentTemplateId(),template.getId()) || !canonical.templateOwner(template))denied();return;}
        var punishment = requireCase(caseId);
        // An explicitly linked private template is readable by that case's authorized student/staff.
        if (template == null || (!system(template) && !templateInSchool(template, punishment.getSchool()))) denied();
    }

    public void requireOwnBinding(String teacherEmail) {
        requireStaff();
        if (!same(actor().getUsername(), teacherEmail)) denied();
    }

    public void requireBindingTemplate(String teacherEmail, String schoolId, String templateId) {
        requireOwnBinding(teacherEmail);
        if(canonical!=null){if(text(schoolId))canonical.label(schoolId);}else if (text(schoolId) && !schoolMatches(schoolId, actor().getSchool())) denied();
        requireTemplate(templateId);
    }

    public AdminOverviewDTO scopeAdminOverview(AdminOverviewDTO dto) {
        requireAdmin();
        if (dto == null || dto.getSchool() == null || !sameTenant(dto.getSchool())
                || dto.getTeacher() == null || !sameTenant(dto.getTeacher())) denied();
        dto.setPunishmentResponse(visibleStudents(dto.getPunishmentResponse()));
        dto.setWriteUpResponse(visibleStudents(dto.getWriteUpResponse()));
        dto.setShoutOutsResponse(visibleStudents(dto.getShoutOutsResponse()));
        dto.setTeachers(dto.getTeachers().stream().filter(e -> sameTenant(e)).toList());
        dto.setOfficeReferrals(dto.getOfficeReferrals().stream()
                .filter(r -> sameTenant(r) && canReadStudent(r.getStudentEmail())).toList());
        return dto;
    }

    public TeacherOverviewDTO scopeTeacherOverview(TeacherOverviewDTO dto) {
        requireStaff();
        if (dto == null || dto.getSchool() == null || !sameTenant(dto.getSchool())
                || dto.getTeacher() == null || !sameTenant(dto.getTeacher())) denied();
        dto.setPunishmentResponse(visibleStudents(dto.getPunishmentResponse()));
        dto.setWriteUpResponse(visibleStudents(dto.getWriteUpResponse()));
        dto.setShoutOutsResponse(visibleStudents(dto.getShoutOutsResponse()));
        dto.setOfficeReferrals(visibleStudents(dto.getOfficeReferrals()));
        return dto;
    }

    private List<TeacherDTO> visibleStudents(List<TeacherDTO> records) {
        return records.stream().filter(r -> canReadStudent(r.getStudentEmail())).toList();
    }

    public StudentOverviewDTO scopeStudentOverview(StudentOverviewDTO dto, String email) {
        if (dto == null || dto.getStudent() == null || !same(dto.getStudent().getStudentEmail(), email)
                || !sameTenant(dto.getStudent()) || dto.getSchool() == null
                || !sameTenant(dto.getSchool())) denied();
        dto.setPunishments(dto.getPunishments().stream()
                .filter(p -> same(email, p.getStudentEmail()) && sameTenant(p)).toList());
        dto.setOfficeReferrals(dto.getOfficeReferrals().stream()
                .filter(r -> same(email, r.getStudentEmail()) && sameTenant(r)).toList());
        return dto;
    }

    public boolean sameSchool(String school) { return canonical==null && same(actor().getSchool(), school); }

    /** Legacy assignments are global and have no tenant/owner; no school role may mutate them. */
    public void denyLegacyWrite() { denied(); }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static boolean same(String left, String right) {
        return text(left) && text(right) && left.equalsIgnoreCase(right);
    }
    private static AccessDeniedException forbidden() { return new AccessDeniedException("Access denied"); }
    private static void denied() { throw forbidden(); }
}
