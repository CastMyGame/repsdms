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

    public UserModel createEmployeeUser(AuthenticationRequest authenticationRequest) {
        if (userRepository.existsByUsername(authenticationRequest.getUsername().toLowerCase())) {
            throw new IllegalArgumentException("User with this email already exists.");
        }
        return userRepository.save(newAccount(authenticationRequest));
    }

    /** Deterministic insert prevents concurrent student provisioning from creating duplicate accounts.
     * A retry can repair a missing profile, but never overwrites a password, role or membership. */
    public UserModel provisionStudent(AuthenticationRequest request) {
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
        if (existing == null || !id.equals(existing.getId()) || !existing.isEnabled()
                || !request.getSchool().equalsIgnoreCase(existing.getSchool())
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

        return userModel;
    }
}
