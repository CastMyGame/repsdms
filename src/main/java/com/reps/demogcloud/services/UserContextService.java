package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.EntityNotFoundException;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import jakarta.mail.internet.InternetAddress;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserContextService {

    private static final String STUDENT_ROLE = "STUDENT";

    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EmployeeRepository employeeRepository;

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    public String getCurrentUserSchool() {
        if(canonical!=null)return canonical.school().getSchoolName();
        UserModel user = getCurrentUserModel();
        String email = user.getUsername();

        if (hasStudentRole(user)) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(email);
            if (student == null) {
                throw new EntityNotFoundException("Student not found with email: " + email);
            }
            return student.getSchool();
        }

        Employee employee = employeeRepository.findByEmailIgnoreCase(email);
        if (employee == null) {
            throw new EntityNotFoundException("Employee not found with email: " + email);
        }
        return employee.getSchool();
    }

    public String getCurrentUserEmail() {
        if(canonical!=null)return canonical.email();
        return getRequiredAuthentication().getName();
    }

    public boolean isCurrentUserStudent() {
        return hasStudentRole(getCurrentUserModel());
    }

    public String getCurrentUsername() {
        if(canonical!=null)return canonical.email();
        return getRequiredAuthentication().getName();
    }

    public String getSchoolForCurrentUser() {
        return getCurrentUserSchool();
    }

    public boolean isStudent() {
        return isCurrentUserStudent();
    }

    /** Resolve a sender from enabled school staff records, never from a request's From field. */
    public String getCurrentStaffEmail() {
        return getVerifiedStaffEmail(getRequiredAuthentication().getName());
    }

    public String getVerifiedStaffEmail(String email) {
        if(canonical!=null)return canonical.employee(email).getEmail();
        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("A verified staff sender is required");
        }
        UserModel user = userRepository.findByUsername(email);
        Employee employee = employeeRepository.findByEmailIgnoreCase(email);
        boolean staff = user != null && user.isEnabled() && !user.isPasswordSetupRequired() && !hasRole(user, "STUDENT") && user.getRoles() != null
                && user.getRoles().stream().anyMatch(role -> role != null
                && ("TEACHER".equalsIgnoreCase(role.getRole())
                || "GUIDANCE".equalsIgnoreCase(role.getRole())
                || "ADMIN".equalsIgnoreCase(role.getRole())));
        if (!staff || employee == null || employee.isArchived()
                || employee.getEmail() == null || !employee.getEmail().equalsIgnoreCase(user.getUsername())
                || employee.getSchool() == null || employee.getSchool().isBlank() || user.getSchool() == null || user.getSchool().isBlank()
                || !employee.getSchool().equalsIgnoreCase(user.getSchool())) {
            throw new AccessDeniedException("A verified staff sender is required");
        }
        try {
            InternetAddress address = new InternetAddress(employee.getEmail(), true);
            address.validate();
            if (!employee.getEmail().equals(address.getAddress())) {
                throw new IllegalArgumentException();
            }
        } catch (Exception exception) {
            throw new AccessDeniedException("Invalid staff email address");
        }
        return employee.getEmail();
    }

    /** Spotters are subscriptions, never proof of student access. Recheck persisted relationships. */
    public boolean hasIndependentStudentAccess(String email, Student student) {
        if(canonical!=null)return canonical.independent(email,student);
        try {
            requireStaffInStudentSchool(email, student);
            if (student.isArchived()) return false;
            UserModel user = userRepository.findByUsername(email);
            if (hasRole(user, "ADMIN")) return true;
            if (hasRole(user, "GUIDANCE") && email.equalsIgnoreCase(student.getGuidanceEmail())) return true;
            Employee employee = employeeRepository.findByEmailIgnoreCase(email);
            return hasRole(user, "TEACHER") && employee.getClasses() != null
                    && employee.getClasses().stream().filter(c -> c != null && c.getClassRoster() != null)
                    .flatMap(c -> c.getClassRoster().stream()).anyMatch(student.getStudentEmail()::equalsIgnoreCase);
        } catch (AccessDeniedException denied) { return false; }
    }

    private boolean hasAuthorizedStudentSubscription(String email, Student student) {
        if (!hasIndependentStudentAccess(email, student)) return false;
        UserModel user = userRepository.findByUsername(email);
        return (hasRole(user, "GUIDANCE") && email.equalsIgnoreCase(student.getGuidanceEmail()))
                || (student.getSpotters() != null && student.getSpotters().stream().anyMatch(email::equalsIgnoreCase));
    }

    public boolean isCurrentStaffAdministrator() {
        getCurrentStaffEmail();
        return hasRole(getCurrentUserModel(), "ADMIN");
    }

    private boolean hasRole(UserModel user, String role) {
        return user != null && user.getRoles() != null && user.getRoles().stream()
                .anyMatch(r -> r != null && role.equalsIgnoreCase(r.getRole()));
    }

    public void requireStaffInStudentSchool(String staffEmail, Student student) {
        if(canonical!=null){canonical.employee(staffEmail);if(!canonical.owns(student))throw new AccessDeniedException("Access denied");return;}
        getVerifiedStaffEmail(staffEmail);
        Employee employee = employeeRepository.findByEmailIgnoreCase(staffEmail);
        if (student == null || student.getSchool() == null
                || !student.getSchool().equalsIgnoreCase(employee.getSchool())) {
            throw new AccessDeniedException("Communication is limited to the student's school");
        }
    }

    public void requireCaseCommunicationAccess(String originalTeacher, Student student) {
        if(canonical!=null){if(!canonical.independent(canonical.email(),student))throw new AccessDeniedException("Access denied");return;}
        String actor = getCurrentStaffEmail();
        requireStaffInStudentSchool(actor, student);
        UserModel user = getCurrentUserModel();
        boolean admin = user.getRoles().stream().anyMatch(role -> role != null
                && "ADMIN".equalsIgnoreCase(role.getRole()));
        boolean participant = actor.equalsIgnoreCase(originalTeacher)
                || hasAuthorizedStudentSubscription(actor, student);
        if (!admin && !participant) {
            throw new AccessDeniedException("Staff must be an authorized participant in this case");
        }
    }

    public void requireRecordedCaseCommunicationAccess(String actor, String originalTeacher, String owner, Student student) {
        if(canonical!=null){
            if(!canonical.independent(actor,student))throw new AccessDeniedException("Access denied");
            var employee=canonical.employee(actor);
            boolean admin=employee.getRoles().stream().anyMatch(r->r!=null && "ADMIN".equalsIgnoreCase(r.getRole()));
            boolean guide=employee.getRoles().stream().anyMatch(r->r!=null && "GUIDANCE".equalsIgnoreCase(r.getRole())) && actor.equalsIgnoreCase(student.getGuidanceEmail());
            if(!admin && !guide && !actor.equalsIgnoreCase(originalTeacher) && !actor.equalsIgnoreCase(owner) && (student.getSpotters()==null || student.getSpotters().stream().noneMatch(actor::equalsIgnoreCase)))throw new AccessDeniedException("Access denied");return;
        }
        requireStaffInStudentSchool(actor, student);
        UserModel user = userRepository.findByUsername(actor);
        boolean admin = user.getRoles().stream().anyMatch(role -> role != null && "ADMIN".equalsIgnoreCase(role.getRole()));
        boolean participant = actor.equalsIgnoreCase(originalTeacher) || actor.equalsIgnoreCase(owner)
                || hasAuthorizedStudentSubscription(actor, student);
        if (!admin && !participant) throw new AccessDeniedException("Staff must be an authorized case participant");
    }

    /** Guidance notes are not a general student-record or spotter permission. */
    public boolean canReadGuidance(com.reps.demogcloud.models.guidance.GuidanceReferral record) {
        if(canonical!=null)return canonical.canReadRecord(record,com.reps.demogcloud.services.tenancy.CanonicalCoreWorkflows.Kind.GUIDANCE);
        return canReadGuidanceFor(getCurrentStaffEmail(), record);
    }

    private boolean canReadGuidanceFor(String actor, com.reps.demogcloud.models.guidance.GuidanceReferral record) {
        if (record == null || record.getStudentEmail() == null || record.getStudentEmail().isBlank()) return false;
        getVerifiedStaffEmail(actor);
        Student student = studentRepository.findByStudentEmailIgnoreCase(record.getStudentEmail());
        UserModel account = userRepository.findByUsername(actor);
        if (student == null || student.getStudentEmail() == null || !record.getStudentEmail().equalsIgnoreCase(student.getStudentEmail())
                || record.getSchool() == null || record.getSchool().isBlank() || student.getSchool() == null || student.getSchool().isBlank()
                || !record.getSchool().equalsIgnoreCase(student.getSchool())
                || !record.getSchool().equalsIgnoreCase(account.getSchool())) return false;
        if (hasRole(account,"ADMIN")) return true;
        if (!hasIndependentStudentAccess(actor,student)) return false;
        return (hasRole(account,"GUIDANCE") && actor.equalsIgnoreCase(student.getGuidanceEmail()))
                || (hasRole(account,"TEACHER") && (actor.equalsIgnoreCase(record.getTeacherEmail())
                || actor.equalsIgnoreCase(record.getOriginalAuthorEmail())));
    }

    public void requireGuidanceAccess(com.reps.demogcloud.models.guidance.GuidanceReferral record, boolean manage) {
        if(canonical!=null){if(!canReadGuidance(record))throw new AccessDeniedException("Access denied");if(manage && !canonical.role("ADMIN") && (!canonical.role("GUIDANCE") || !canonical.email().equalsIgnoreCase(canonical.student(record.getStudentEmail()).getGuidanceEmail())))throw new AccessDeniedException("Access denied");return;}
        String actor = getCurrentStaffEmail();
        requireRecordedGuidanceAccess(actor, record);
        if (manage && !hasRole(userRepository.findByUsername(actor),"ADMIN")) {
            Student student = studentRepository.findByStudentEmailIgnoreCase(record.getStudentEmail());
            if (!hasRole(userRepository.findByUsername(actor),"GUIDANCE") || !actor.equalsIgnoreCase(student.getGuidanceEmail()))
                throw new AccessDeniedException("Access denied");
        }
    }

    public void requireRecordedGuidanceAccess(String actor, com.reps.demogcloud.models.guidance.GuidanceReferral record) {
        if(canonical!=null){
            var student=canonical.lookupStudent(record.getStudentEmail());
            if(!canonical.owns(record) || !canonical.independent(actor,student))throw new AccessDeniedException("Access denied");
            var staff=canonical.employee(actor);
            boolean admin=staff.getRoles().stream().anyMatch(r->r!=null && "ADMIN".equalsIgnoreCase(r.getRole()));
            boolean guide=staff.getRoles().stream().anyMatch(r->r!=null && "GUIDANCE".equalsIgnoreCase(r.getRole())) && actor.equalsIgnoreCase(student.getGuidanceEmail());
            if(!admin && !guide && !actor.equalsIgnoreCase(record.getTeacherEmail()) && !actor.equalsIgnoreCase(record.getOriginalAuthorEmail()))throw new AccessDeniedException("Access denied");return;
        }
        if (!canReadGuidanceFor(actor,record)) throw new AccessDeniedException("Access denied");
    }

    public void requireAdministratorInSchool(String school) {
        if(canonical!=null){canonical.admin();canonical.label(school);return;}
        String actor = getCurrentStaffEmail();
        UserModel user = userRepository.findByUsername(actor);
        if (!school.equalsIgnoreCase(user.getSchool()) || user.getRoles().stream()
                .noneMatch(role -> role != null && "ADMIN".equalsIgnoreCase(role.getRole())))
            throw new AccessDeniedException("A school administrator must record manual resolution");
    }

    private UserModel getCurrentUserModel() {
        if(canonical!=null)return canonical.account();
        String username = getRequiredAuthentication().getName();
        return userRepository.findByUsername(username);
    }

    private boolean hasStudentRole(UserModel user) {
        return user.getRoles() != null
                && user.getRoles().stream()
                .anyMatch(role -> STUDENT_ROLE.equalsIgnoreCase(role.getRole()));
    }

    private Authentication getRequiredAuthentication() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new IllegalStateException("No authenticated user found in security context");
        }

        return authentication;
    }
}
