package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
public class AuthService {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    public UserModel createEmployeeUser(AuthenticationRequest authenticationRequest) {
        if(canonical!=null){canonical.admin();canonical.label(authenticationRequest.getSchool());
            if(authenticationRequest.getRoles()==null || authenticationRequest.getRoles().isEmpty() || authenticationRequest.getRoles().stream().anyMatch(r->r==null || !Set.of("ADMIN","TEACHER","GUIDANCE").contains(r.getRole())))throw new IllegalArgumentException("Supported staff roles required");}
        if (userRepository.existsByUsername(authenticationRequest.getUsername().toLowerCase())) {
            throw new IllegalArgumentException("User with this email already exists.");
        }
        var account=newAccount(authenticationRequest);
        return canonical==null?userRepository.save(account):userRepository.insert(account);
    }

    /** Deterministic insert prevents concurrent student provisioning from creating duplicate accounts.
     * A retry can repair a missing profile, but never overwrites a password, role or membership. */
    public UserModel provisionStudent(AuthenticationRequest request) {
        if(canonical!=null){canonical.staff();canonical.label(request.getSchool());request.setRoles(Set.of(new RoleModel(null,"STUDENT")));}
        String id = studentProvisioningId(request.getUsername());
        var existing = userRepository.findByUsernameIgnoreCase(request.getUsername());
        if (existing == null) {
            var account = newAccount(request);
            account.setId(id);
            try { return userRepository.insert(account); }
            catch (org.springframework.dao.DuplicateKeyException race) {
                existing = userRepository.findById(id).orElse(null);
            }
        }
        if(canonical!=null && existing!=null && !canonical.owns(existing))throw new org.springframework.security.access.AccessDeniedException("Access denied");
        if (existing == null || !id.equals(existing.getId()) || !existing.isEnabled()
                || canonical==null && !request.getSchool().equalsIgnoreCase(existing.getSchool())
                || !request.getFirstName().equals(existing.getFirstName()) || !request.getLastName().equals(existing.getLastName())
                || existing.getRoles() == null || existing.getRoles().size() != 1
                || existing.getRoles().stream().noneMatch(r -> "STUDENT".equals(r.getRole())))
            throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                    "Account already exists; use existing-student enrollment or request administrator review");
        return existing;
    }

    public static String studentProvisioningId(String email) {
        return "student-" + java.util.UUID.nameUUIDFromBytes(email.trim().toLowerCase(java.util.Locale.ROOT)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private UserModel newAccount(AuthenticationRequest authenticationRequest) {
        String username = authenticationRequest.getUsername();
        String password = AccountAccessService.unusablePassword();
        String firstName = authenticationRequest.getFirstName();
        String lastName = authenticationRequest.getLastName();
        String school = authenticationRequest.getSchool();
        Set<RoleModel> roles = authenticationRequest.getRoles();

        UserModel userModel = new UserModel();
        userModel.setPasswordSetupRequired(true);
        userModel.setUsername(username);
        userModel.setFirstName(firstName);
        userModel.setLastName(lastName);
        userModel.setSchool(school);
        userModel.setRoles(roles);
        // Use BCryptPasswordEncoder to encode the provided password
        String encodedPassword = passwordEncoder.encode(password);
        userModel.setPassword(encodedPassword);

        if(canonical!=null)canonical.bindNew(userModel);
        return userModel;
    }
}
