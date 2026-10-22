package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserAccountService;
import com.reps.demogcloud.services.UserAdministrationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Set;

@CrossOrigin(origins = {"http://localhost:3000", "https://repsdiscipline.vercel.app", "https://repsdev.vercel.app"})
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/v1")
public class UserControllers {
    private final UserAccountService userAccountService;
    private final UserAdministrationService administration;

    @GetMapping("/users")
    public ResponseEntity<List<UserModel>> getAllUsers() {
        return ResponseEntity.ok(administration.listUsers());
    }
    @GetMapping("/users/{role}")
    public ResponseEntity<List<UserModel>> getAllUsersByRole(@PathVariable String role) {
        return ResponseEntity.ok(administration.listUsers().stream().filter(u -> u.getRoles() != null
                && u.getRoles().stream().anyMatch(r -> r != null && role.equalsIgnoreCase(r.getRole()))).toList());
    }
    @PostMapping("/users/create/{school}")
    public ResponseEntity<List<UserModel>> createNewUsers(@PathVariable String school) {
        return ResponseEntity.ok(userAccountService.createUsersForSchool(school));
    }
    @PutMapping("/users/{school}")
    public ResponseEntity<List<UserModel>> lowercaseThemAll(@PathVariable String school) {
        administration.denyMaintenance();
        return ResponseEntity.status(403).build();
    }
    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserModel> updateUsersRole(@PathVariable String id, @RequestBody Set<RoleModel> roles) {
        return ResponseEntity.ok(administration.updateUserRoles(id, roles));
    }
    @PutMapping("/users/{id}/enabled")
    public ResponseEntity<Void> setEnabled(@PathVariable String id, @RequestBody ActivationRequest request) {
        if (request.enabled() == null) throw new IllegalArgumentException("enabled is required");
        administration.setEnabled(id, request.enabled());
        return ResponseEntity.noContent().build();
    }
    public record ActivationRequest(Boolean enabled) { }

    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUserById(@PathVariable String id) {
        administration.setEnabled(id, false);
        return ResponseEntity.ok("Account deactivated; records retained.");
    }
}
