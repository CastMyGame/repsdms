package com.reps.demogcloud.security.controllers;


import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import javax.management.relation.Role;
import java.util.HashSet;
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



    @Autowired
    UserService userService;
    @Autowired
    private UserRepository userRepository;

    //---------------------------GET Controllers-------------------------------------
    @GetMapping("/users")
    private  ResponseEntity<List<UserModel>> getAllUsers(){
        List<UserModel> users =  userRepository.findAll();
        return ResponseEntity.ok(users);
    }

    @GetMapping("/users/{role}")
    private ResponseEntity<List<UserModel>> getAllUsersByRole(@PathVariable String role) {
        List<UserModel> users = userRepository.findAll().stream()
                .filter(user -> {
                    Set<RoleModel> userRoles = user.getRoles();
                    if (userRoles != null) {
                        return userRoles.stream().anyMatch(roleModel -> roleModel.getRole().equals(role));
                    }
                    return false; // Return false if userRoles is null
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(users);
    }

    //------------------------------POST Controllers---------------------------------
    @PostMapping("/users/create/{school}")
    private ResponseEntity<List<UserModel>> createNewUsers(@PathVariable String school){
        List<UserModel> createdUsers = userService.createUsersForSchool(school);
        return ResponseEntity.ok(createdUsers);
    }

    //----------------------------------PUT Controllers-----------------------------------
    @PutMapping("/users/{school}")
    private ResponseEntity<List<UserModel>> lowercaseThemAll(@PathVariable String school) {
        List<UserModel> users = userService.lowerCaseThemAll(school);
        return ResponseEntity.ok(users);
    }

    @PutMapping("/users/{id}/roles")
    private ResponseEntity<UserModel> updateUsersRole(@PathVariable String id, @RequestBody Set<RoleModel> roles) {
        Optional<UserModel> optionalUser = userRepository.findById(id);

        if (optionalUser.isPresent()) {
            UserModel user = optionalUser.get();

            // Update the role of the user
            user.setRoles(roles);  // Assuming UserModel has a setter method for roles of type Set<RoleModel>

            // Save the updated user back to the repository
            UserModel updatedUser = userRepository.save(user);

            // Return a response entity with the updated user and a success status
            return ResponseEntity.ok(updatedUser);
        } else {
            // If user not found, return a 404 Not Found response
            return ResponseEntity.notFound().build();
        }
    }

    //-------------------------DELETE Controllers-----------------------------------
    @DeleteMapping("/users/{id}")
    public ResponseEntity<String> deleteUserById(@PathVariable String id) {

        // Check if user exists
        Optional<UserModel> optionalUser = userRepository.findById(id);

        if (optionalUser.isPresent()) {
            // If user exists, delete the user
            userRepository.deleteById(id);

            // Return confirmation message
            return ResponseEntity.ok("User with ID " + id + " has been deleted.");
        } else {
            // If user not found, return a 404 Not Found response
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("User with ID " + id + " not found.");
        }
    }

}

