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

    private UserModel actor() {
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

    public void requireStaff() { userContext.getCurrentStaffEmail(); actor(); }

    public void requireAdmin() {
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
        var user = actor();
        var student = students.findByStudentEmailIgnoreCase(email);
        if (student == null || student.isArchived() || !same(student.getStudentEmail(), email)
                || !same(user.getSchool(), student.getSchool())) return false;
        if (role(user, "STUDENT")) return same(user.getUsername(), student.getStudentEmail());
        requireStaff();
        return userContext.hasIndependentStudentAccess(user.getUsername(), student);
    }

    public boolean canReadCase(Punishment punishment) {
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
        var punishment = punishments.findById(id).orElseThrow(WorkflowAccessService::forbidden);
        if (!canReadCase(punishment)) denied();
        return punishment;
    }
    public boolean canReadOffice(com.reps.demogcloud.models.officeReferral.OfficeReferral referral) {
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
        requireStaff();
        if (template == null) return false;
        var user = actor();
        return system(template) || (templateInSchool(template, user.getSchool())
                && (same(template.getCreatedByUserId(), user.getUsername()) || role(user, "ADMIN")
                || template.getVisibility() == AssignmentTemplate.Visibility.SCHOOL
                || template.getScope() == AssignmentTemplate.Scope.SCHOOL_DEFAULT));
    }

    public AssignmentTemplate requireTemplate(String id) {
        var template = templates.findById(id).orElseThrow(WorkflowAccessService::forbidden);
        if (!canReadTemplate(template)) denied();
        return template;
    }

    public void requireTemplateWrite(String id) {
        var template = requireTemplate(id);
        if (system(template) || !templateInSchool(template, actor().getSchool())
                || (!same(template.getCreatedByUserId(), actor().getUsername()) && !role(actor(), "ADMIN"))) denied();
    }

    public void prepareTemplateCreation(AssignmentTemplate template) {
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
        if (text(schoolId) && !schoolMatches(schoolId, actor().getSchool())) denied();
        requireTemplate(templateId);
    }

    public AdminOverviewDTO scopeAdminOverview(AdminOverviewDTO dto) {
        requireAdmin();
        if (dto == null || dto.getSchool() == null || !sameSchool(dto.getSchool().getSchoolName())
                || dto.getTeacher() == null || !sameSchool(dto.getTeacher().getSchool())) denied();
        dto.setPunishmentResponse(visibleStudents(dto.getPunishmentResponse()));
        dto.setWriteUpResponse(visibleStudents(dto.getWriteUpResponse()));
        dto.setShoutOutsResponse(visibleStudents(dto.getShoutOutsResponse()));
        dto.setTeachers(dto.getTeachers().stream().filter(e -> sameSchool(e.getSchool())).toList());
        dto.setOfficeReferrals(dto.getOfficeReferrals().stream()
                .filter(r -> sameSchool(r.getSchool()) && canReadStudent(r.getStudentEmail())).toList());
        return dto;
    }

    public TeacherOverviewDTO scopeTeacherOverview(TeacherOverviewDTO dto) {
        requireStaff();
        if (dto == null || dto.getSchool() == null || !sameSchool(dto.getSchool().getSchoolName())
                || dto.getTeacher() == null || !sameSchool(dto.getTeacher().getSchool())) denied();
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
                || !sameSchool(dto.getStudent().getSchool()) || dto.getSchool() == null
                || !sameSchool(dto.getSchool().getSchoolName())) denied();
        dto.setPunishments(dto.getPunishments().stream()
                .filter(p -> same(email, p.getStudentEmail()) && sameSchool(p.getSchool())).toList());
        dto.setOfficeReferrals(dto.getOfficeReferrals().stream()
                .filter(r -> same(email, r.getStudentEmail()) && sameSchool(r.getSchool())).toList());
        return dto;
    }

    public boolean sameSchool(String school) { return same(actor().getSchool(), school); }

    /** Legacy assignments are global and have no tenant/owner; no school role may mutate them. */
    public void denyLegacyWrite() { denied(); }

    private static boolean text(String value) { return value != null && !value.isBlank(); }
    private static boolean same(String left, String right) {
        return text(left) && text(right) && left.equalsIgnoreCase(right);
    }
    private static AccessDeniedException forbidden() { return new AccessDeniedException("Access denied"); }
    private static void denied() { throw forbidden(); }
}
