package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.services.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(
        origins = {
                "http://localhost:3000",
                "https://repsdiscipline.vercel.app",
                "https://repsdev.vercel.app"
        }
)
@RestController
@RequiredArgsConstructor
@RequestMapping("/users/v1")
public class UserControllers {

    private final UserAccountService userAccountService;
    private final UserRepository userRepository;

    //---------------------------GET Controllers-------------------------------------
    @GetMapping("/users")
    public ResponseEntity<List<UserModel>> getAllUsers() {
        List<UserModel> users = userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{role}")
    public ResponseEntity<List<UserModel>> getAllUsersByRole(@PathVariable String role) {
        List<UserModel> users = userRepository.findAll().stream()
                .filter(user -> {
                    Set<RoleModel> userRoles = user.getRoles();
                    if (userRoles != null) {
                        return userRoles.stream().anyMatch(roleModel -> roleModel.getRole().equals(role));
                    }
                    return false;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    //------------------------------POST Controllers---------------------------------
    @PostMapping("/users/create/{school}")
    public ResponseEntity<List<UserModel>> createNewUsers(@PathVariable String school) {
        List<UserModel> createdUsers = userAccountService.createUsersForSchool(school);
        return ResponseEntity.ok(createdUsers);
    }

    //----------------------------------PUT Controllers-----------------------------------
    @PutMapping("/users/{school}")
    public ResponseEntity<List<UserModel>> lowercaseThemAll(@PathVariable String school) {
        List<UserModel> users = userAccountService.lowerCaseThemAll(school);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{id}/roles")
    public ResponseEntity<UserModel> updateUsersRole(@PathVariable String id, @RequestBody Set<RoleModel> roles) {
        Optional<UserModel> optionalUser = userRepository.findById(id);

        if (optionalUser.isPresent()) {
            UserModel user = optionalUser.get();
            user.setRoles(roles);
            UserModel updatedUser = userRepository.save(user);
            return ResponseEntity.ok(updatedUser);
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    //-------------------------DELETE Controllers-----------------------------------
    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUserById(@PathVariable String id) {
        Optional<UserModel> optionalUser = userRepository.findById(id);

        if (optionalUser.isPresent()) {
            userRepository.deleteById(id);
            return ResponseEntity.ok("User with ID " + id + " has been deleted.");
        } else {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User with ID " + id + " not found.");
        }
    }
}