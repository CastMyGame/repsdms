package com.reps.demogcloud.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import jakarta.mail.internet.InternetAddress;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

/** UserModel is the authority; Employee roles are a synchronized directory projection. */
@Service
@RequiredArgsConstructor
public class UserAdministrationService {
    private final UserRepository users;
    private final EmployeeRepository employees;
    private final StudentRepository students;
    private final UserContextService context;
    private final WorkflowAccessService access;
    private final com.reps.demogcloud.security.services.AccountAccessService accounts;

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.data.mongodb.core.MongoTemplate mongo;
    private Employee employee(String email){
        if(canonical==null)return employees.findByEmailIgnoreCase(email);
        var rows=mongo.find(canonical.query().addCriteria(org.springframework.data.mongodb.core.query.Criteria.where("email").regex(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess.exact(email))).limit(2),Employee.class);
        if(rows.size()>1)denied();return rows.isEmpty()?null:rows.get(0);
    }
    private void tenantSet(Class<?> type,String id,String field,Object value){
        if(mongo.updateFirst(canonical.id(id),new org.springframework.data.mongodb.core.query.Update().set(field,value),type).getMatchedCount()!=1)conflict();
    }
    private UserModel staff() {
        if(canonical!=null){canonical.staff();return canonical.account();}
        var user = users.findByUsername(context.getCurrentStaffEmail());
        if (user == null || hasRole(user.getRoles(), "STUDENT")) denied();
        return user;
    }
    public String requireSchoolAdmin(String requestedSchool) {
        if(canonical!=null){canonical.admin();if(requestedSchool!=null)canonical.label(requestedSchool);return canonical.school().getSchoolName();}
        access.requireAdmin();
        String school = staff().getSchool();
        if (requestedSchool != null && !same(school, requestedSchool)) denied();
        return school;
    }
    public List<UserModel> listUsers() {
        String school = requireSchoolAdmin(null);
        // getSchool() preserves older documents containing schoolName rather than school.
        return canonical==null?users.findAll().stream().filter(u -> same(school, u.getSchool())).toList():mongo.find(canonical.query(),UserModel.class);
    }
    public UserModel requireUser(String id) {
        if(canonical!=null){canonical.admin();return canonical.one(id,UserModel.class);}
        String school = requireSchoolAdmin(null);
        var user = users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
        if (!same(school, user.getSchool())) denied();
        return user;
    }
    private Employee linkedEmployee(UserModel user) {
        var employee = employee(user.getUsername());
        if (employee != null && (canonical!=null?(!canonical.owns(employee) || !canonical.owns(user)):(!same(employee.getSchool(), user.getSchool())
                || !same(employee.getEmail(), user.getUsername())))) denied();
        return employee;
    }
    private void notSelf(UserModel target) {
        if (same(target.getUsername(), staff().getUsername())) denied();
    }
    public UserModel updateUserRoles(String id, Set<RoleModel> requested) {
        var user = requireUser(id);
        notSelf(user);
        var roles = normalizedRoles(requested, false);
        var employee = linkedEmployee(user);
        boolean student = hasRole(user.getRoles(), "STUDENT");
        if (student) {
            var profile = students.findByStudentEmailIgnoreCase(user.getUsername());
            if (profile != null && (canonical!=null?!canonical.owns(profile):!same(profile.getSchool(), user.getSchool()))) denied();
        }
        if (student != hasRole(roles, "STUDENT") || (!student && employee == null)
                || (student && employee != null)) conflict();
        // Update the directory first, then the authoritative account. Never save stale enabled/password fields.
        if (employee != null) {if(canonical==null)employees.updateRolesInSchool(employee.getEmployeeId(), employee.getSchool(), roles);else tenantSet(Employee.class,employee.getEmployeeId(),"roles",roles);}
        if(canonical==null)users.updateRolesInSchool(user.getId(), user.getSchool(), roles);else tenantSet(UserModel.class,user.getId(),"roles",roles);
        user.setRoles(roles);
        return user;
    }
    public Employee updateEmployeeRoles(String id, Set<RoleModel> roles) {
        var employee = requireEmployeeAdmin(id);
        var user = employeeAccount(employee.getEmail());
        if (user == null || (canonical!=null?(!canonical.owns(user) || !canonical.owns(employee)):!same(user.getSchool(), employee.getSchool()))) conflict();
        var updated = updateUserRoles(user.getId(), roles);
        employee.setRoles(updated.getRoles());
        return employee;
    }
    public Employee requireEmployeeAdmin(String id) {
        if(canonical!=null){canonical.admin();return canonical.one(id,Employee.class);}
        String school = requireSchoolAdmin(null);
        var employee = employees.findById(id).orElseThrow(() -> new ResourceNotFoundException("Employee not found"));
        if (canonical==null && !same(school, employee.getSchool())) denied();
        return employee;
    }
    private UserModel employeeAccount(String email) {
        var user = users.findByUsername(email);
        return user != null || email == null ? user : users.findByUsername(email.toLowerCase(Locale.ROOT));
    }
    public void setEnabled(String id, boolean enabled) {
        var user = requireUser(id);
        notSelf(user);
        var employee = linkedEmployee(user);
        boolean studentAccount = hasRole(user.getRoles(), "STUDENT");
        if (studentAccount) {
            var student = students.findByStudentEmailIgnoreCase(user.getUsername());
            if (student != null && (canonical!=null?!canonical.owns(student):!same(student.getSchool(), user.getSchool()))) denied();
            if (employee != null || (enabled && (student == null || student.isArchived()))) conflict();
        }
        if (enabled) {
            if (!studentAccount && employee == null) conflict();
            normalizedRoles(user.getRoles(), false);
            // Repair the directory projection before granting account access.
            if (employee != null) {
                if(canonical==null)employees.updateRolesInSchool(employee.getEmployeeId(), employee.getSchool(), user.getRoles());else tenantSet(Employee.class,employee.getEmployeeId(),"roles",user.getRoles());
                if(canonical==null)employees.updateArchivedInSchool(employee.getEmployeeId(), employee.getSchool(), false);else tenantSet(Employee.class,employee.getEmployeeId(),"archived",false);
            }
            accounts.revoke(user.getId()); // Re-enabling must never revive an old ticket.
            if(canonical==null)users.updateEnabledInSchool(user.getId(), user.getSchool(), true);else tenantSet(UserModel.class,user.getId(),"enabled",true);
            user.setEnabled(true);
            if(!studentAccount && user.isPasswordSetupRequired()) accounts.createdStaff(user);
        } else {
            // Revoke login first; an archive failure must never keep the account enabled.
            if(canonical==null)users.updateEnabledInSchool(user.getId(), user.getSchool(), false);else tenantSet(UserModel.class,user.getId(),"enabled",false);
            accounts.revoke(user.getId());
            if (employee != null) if(canonical==null)employees.updateArchivedInSchool(employee.getEmployeeId(), employee.getSchool(), true);else tenantSet(Employee.class,employee.getEmployeeId(),"archived",true);
        }
    }
    public void deactivateEmployee(String id) {
        var employee = requireEmployeeAdmin(id);
        if (same(employee.getEmail(), staff().getUsername())) denied();
        var user = employeeAccount(employee.getEmail());
        if (user != null) {
            if (canonical!=null?(!canonical.owns(user) || !canonical.owns(employee)):!same(user.getSchool(), employee.getSchool())) denied();
            setEnabled(user.getId(), false);
        } else {
            if(canonical==null)employees.updateArchivedInSchool(id, employee.getSchool(), true);else tenantSet(Employee.class,id,"archived",true);
        }
    }
    public List<Employee> visibleEmployees(List<Employee> records) {
        var actor = staff();
        boolean directory = hasRole(actor.getRoles(), "ADMIN") || hasRole(actor.getRoles(), "GUIDANCE");
        return records.stream().filter(e -> (canonical!=null?canonical.owns(e):same(e.getSchool(), actor.getSchool()))
                && (directory || same(e.getEmail(), actor.getUsername()))).toList();
    }
    public void requireEmployeeRead(String email) {
        var actor = staff();
        var target = employee(email);
        if (target == null || (canonical!=null?!canonical.owns(target):!same(target.getSchool(), actor.getSchool()))
                || (!same(email, actor.getUsername()) && !hasRole(actor.getRoles(), "ADMIN")
                && !hasRole(actor.getRoles(), "GUIDANCE"))) denied();
    }
    public void requireEmployeeEdit(String email) {
        var actor = staff();
        var target = employee(email);
        if (target == null || target.isArchived() || (canonical!=null?!canonical.owns(target):!same(target.getSchool(), actor.getSchool()))
                || (!same(email, actor.getUsername()) && !hasRole(actor.getRoles(), "ADMIN"))) denied();
    }
    public void requireClassEdit(String email, ClassRequest request) {
        requireEmployeeEdit(email);
        if (request == null || request.getClassToUpdate() == null) throw new IllegalArgumentException("Class is required");
        var roster = request.getClassToUpdate().getClassRoster();
        if (roster != null) for (String studentEmail : roster) {
            if (studentEmail == null || studentEmail.isBlank()) throw new IllegalArgumentException("Student identifier is required");
            studentEmail = studentEmail.trim().toLowerCase(Locale.ROOT);
            var student = canonical==null?students.findByStudentEmailIgnoreCase(studentEmail):canonical.lookupStudent(studentEmail);
            if (student == null || student.isArchived() || (canonical!=null?!canonical.owns(student):!same(staff().getSchool(), student.getSchool()))) denied();
        }
    }
    public com.reps.demogcloud.models.student.Student prepareStudentCreation(com.reps.demogcloud.models.student.Student input) {
        var actor = staff();
        if (input == null) throw new IllegalArgumentException("Student is required");
        if (input.getSchool() != null && !input.getSchool().isBlank()){if(canonical==null){if(!same(input.getSchool(),actor.getSchool()))denied();}else canonical.label(input.getSchool());}
        if(canonical!=null)canonical.bindNew(input);
        input.setSchool(actor.getSchool());
        input.setGuidanceEmail(studentStaffReference(input.getGuidanceEmail(), actor.getSchool(), "GUIDANCE"));
        input.setAdminEmail(studentStaffReference(input.getAdminEmail(), actor.getSchool(), "ADMIN"));
        return input;
    }
    public String studentStaffReference(String email, String school, String role) {
        if (email == null || email.isBlank()) return null;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if(canonical!=null){var employee=canonical.employee(normalized);if(employee.getRoles()==null || !hasRole(employee.getRoles(),role))denied();return employee.getEmail();}
        var account = users.findByUsername(normalized);
        var employee = employees.findByEmailIgnoreCase(normalized);
        if (account == null || !account.isEnabled() || !same(account.getSchool(), school) || !hasRole(account.getRoles(), role)
                || employee == null || employee.isArchived() || !same(employee.getSchool(), school)) denied();
        return normalized;
    }
    public Employee prepareEmployee(Employee input) {
        String school = requireSchoolAdmin(input.getSchool());
        if (input.getEmployeeId() != null) denied();
        String email = input.getEmail() == null ? "" : input.getEmail().trim().toLowerCase(Locale.ROOT);
        try {
            InternetAddress address = new InternetAddress(email, true); address.validate();
            if (!email.equals(address.getAddress())) throw new IllegalArgumentException();
        }
        catch (Exception ex) { throw new IllegalArgumentException("Valid employee email is required"); }
        if (users.findByUsername(email) != null || employee(email) != null)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already registered");
        var result = new Employee();
        result.setEmail(email); result.setSchool(school);
        result.setFirstName(input.getFirstName()); result.setLastName(input.getLastName());
        result.setRoles(input.getRoles() == null || input.getRoles().isEmpty()
                ? Set.of(new RoleModel(null, "TEACHER")) : normalizedRoles(input.getRoles(), true));
        // Do not accept IDs, archived status, currency or class/roster state through onboarding.
        if(canonical!=null)canonical.bindNew(result);
        return result;
    }
    public List<Employee> prepareEmployees(List<Employee> inputs) {
        requireSchoolAdmin(null);
        List<Employee> prepared = new ArrayList<>(); Set<String> emails = new HashSet<>();
        for (var input : inputs) {
            var employee = prepareEmployee(input);
            if (!emails.add(employee.getEmail())) throw new IllegalArgumentException("Duplicate employee email");
            prepared.add(employee);
        }
        return prepared;
    }
    public void requireStaff() { staff(); }
    public void requireStudentAccess(String email) { requireStaff(); access.requireStudentAccess(email); }
    public void denyMaintenance() { denied(); }

    private Set<RoleModel> normalizedRoles(Set<RoleModel> requested, boolean staffOnly) {
        if (requested == null || requested.isEmpty()) throw new IllegalArgumentException("A supported role is required");
        Set<String> names = new TreeSet<>();
        for (var role : requested) {
            String name = role == null || role.getRole() == null ? "" : role.getRole().trim().toUpperCase(Locale.ROOT);
            if (!Set.of("ADMIN", "TEACHER", "GUIDANCE", "STUDENT").contains(name))
                throw new IllegalArgumentException("Unsupported role");
            names.add(name);
        }
        if (names.contains("STUDENT") && (staffOnly || names.size() != 1)) conflict();
        Set<RoleModel> normalized = new LinkedHashSet<>();
        for (String name : names) normalized.add(new RoleModel(null, name));
        return normalized;
    }
    private boolean hasRole(Set<RoleModel> roles, String name) {
        return roles != null && roles.stream().anyMatch(r -> r != null && same(name, r.getRole()));
    }
    private static boolean same(String a, String b) { return a != null && !a.isBlank() && b != null && a.equalsIgnoreCase(b); }
    private static void denied() { throw new AccessDeniedException("Access denied"); }
    private static void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT, "Account profiles require administrator review"); }
}
