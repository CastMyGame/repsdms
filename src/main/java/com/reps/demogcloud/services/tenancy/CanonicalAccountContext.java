package com.reps.demogcloud.services.tenancy;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.security.models.*;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.util.*;

/** Opt-in context, intentionally not called by runtime routes/login/workers before coordinated A7 cutover.
 * Both password and Google login issue the existing REPS JWT/AccountPrincipal. Raw OIDC sessions are
 * deliberately not canonical actors: their subject is not the persisted, versioned REPS identity. */
@Service
@RequiredArgsConstructor
public class CanonicalAccountContext {
    private final UserRepository users;
    private final EmployeeRepository employees;
    private final StudentRepository students;
    private final SchoolRepository schools;

    public record Actor(String accountId, String profileId, String tenantSchoolId, Set<String> roles) {
        public Actor { roles=Set.copyOf(roles); }
    }

    public Actor requireCurrent() {
        try { return resolve(); }
        catch (IncorrectResultSizeDataAccessException ambiguous) { throw denied(); }
    }

    private Actor resolve() {
        var auth=SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken
                || !(auth.getPrincipal() instanceof AccountPrincipal principal)
                || !principal.isEnabled() || !principal.isAccountNonLocked()) throw denied();
        String username=principal.getUsername();
        if (username == null || username.isBlank()) throw denied();
        UserModel account=users.findByUsername(username);
        if (account == null || !username.equals(account.getUsername()) || !valid(account.getId())
                || !account.isEnabled() || account.isPasswordSetupRequired()
                || principal.credentialVersion != account.getCredentialVersion()) throw denied();
        return membership(account);
    }

    /** Validate immutable recorded IDs for internal jobs; not authentication or student-access authority. */
    public Actor requireRecorded(String accountId,String tenantSchoolId,String profileId) {
        if(!valid(accountId) || !valid(tenantSchoolId) || !valid(profileId))throw denied();
        try {
            var account=users.findById(accountId).orElseThrow(CanonicalAccountContext::denied);
            var actor=membership(account);
            if(!actor.accountId().equals(accountId) || !actor.tenantSchoolId().equals(tenantSchoolId) || !actor.profileId().equals(profileId))throw denied();
            return actor;
        } catch(IncorrectResultSizeDataAccessException ambiguous) { throw denied(); }
    }
    public Actor requireProvisioned(UserModel account) {
        try {
            if(account==null || !valid(account.getId()))throw denied();
            var stored=users.findById(account.getId()).orElseThrow(CanonicalAccountContext::denied);
            if(!Objects.equals(account.getUsername(),stored.getUsername()) || !Objects.equals(account.getTenantSchoolId(),stored.getTenantSchoolId()))throw denied();
            return membership(stored,true);
        } catch(IncorrectResultSizeDataAccessException ambiguous){throw denied();}
    }
    private Actor membership(UserModel account) {return membership(account,false);}
    private Actor membership(UserModel account,boolean allowPending) {
        if(account==null)throw denied();
        String username=account.getUsername();
        if(username==null || username.isBlank() || !valid(account.getId()) || !account.isEnabled() || !allowPending && account.isPasswordSetupRequired())throw denied();
        Set<String> roles=roles(account.getRoles());
        boolean student=roles.equals(Set.of("STUDENT"));
        if (!student && !Set.of("TEACHER","GUIDANCE","ADMIN").containsAll(roles)) throw denied();
        var employee=employees.findByEmailIgnoreCase(username);
        var child=students.findByStudentEmailIgnoreCase(username);
        String profileId, profileOwner;
        if (student) {
            if (child == null || employee != null || child.isArchived() || !sameEmail(username,child.getStudentEmail())) throw denied();
            profileId=child.getStudentIdNumber();profileOwner=child.getTenantSchoolId();
        } else {
            if (employee == null || child != null || employee.isArchived() || !sameEmail(username,employee.getEmail())
                    || !roles.equals(roles(employee.getRoles()))) throw denied();
            profileId=employee.getEmployeeId();profileOwner=employee.getTenantSchoolId();
        }
        String owner=account.getTenantSchoolId();
        if (!valid(profileId) || !valid(owner) || !valid(profileOwner) || !owner.equals(profileOwner)) throw denied();
        var school=schools.findById(owner).orElseThrow(CanonicalAccountContext::denied);
        if (!owner.equals(school.getSchoolIdNumber())) throw denied();
        // No display-name lookup/comparison, fallback, inferred ownership or request fields.
        return new Actor(account.getId(),profileId,owner,roles);
    }

    private static Set<String> roles(Set<RoleModel> input) {
        if (input == null || input.isEmpty()) throw denied();
        var result=new HashSet<String>();
        for (var role:input) {
            if (role == null || role.getRole() == null || role.getRole().isBlank()
                    || !role.getRole().equals(role.getRole().trim())) throw denied();
            result.add(role.getRole().toUpperCase(Locale.ROOT));
        }
        return result;
    }
    private static boolean valid(String id) { return id != null && !id.isBlank() && id.equals(id.trim()); }
    private static boolean sameEmail(String a,String b) { return b != null && a.equalsIgnoreCase(b); }
    private static AccessDeniedException denied() { return new AccessDeniedException("Canonical account membership is unavailable"); }
}
