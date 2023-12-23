package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.config.TokenProvider;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.repository.UserRepository;
import com.reps.demogcloud.security.services.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/users/v1")
@RequiredArgsConstructor
public class UserController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenProvider jwtTokenUtil;

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    /**
     * Generates a token for the given user credentials.
     *
     * @param loginUser The user's login credentials.
     * @return A response entity containing the generated token.
     * @throws AuthenticationException if authentication fails.
     */
    @PostMapping("/authenticate")
    public ResponseEntity<?> generateToken(@RequestBody LoginUser loginUser) throws AuthenticationException {
        final Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(
                        loginUser.getUsername(),
                        loginUser.getPassword()
                )
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final String token = jwtTokenUtil.generateToken(authentication);
        return ResponseEntity.ok(new AuthToken(token));
    }

    /**
     * Saves a new user.
     *
     * @param user The user to be saved.
     * @return The saved user.
     */
    @PostMapping("/register")
    public UserModel saveUser(@RequestBody UserDto user){
        return userService.save(user);
    }

    /**
     * Returns a message that can only be accessed by users with the 'ADMIN' role.
     *
     * @return A message that can only be accessed by admins.
     */
    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/adminping")
    public String adminPing(){
        return "Only Admins Can Read This";
    }

    /**
     * Returns a message that can be accessed by any user.
     *
     * @return A message that can be accessed by any user.
     */
    @PreAuthorize("hasRole('STUDENT')")
    @GetMapping("/userping")
    public String userPing(){
        return "Any User Can Read This";
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/create/user")
    public UserModel createUser(@RequestBody UserDto user){
        return userService.createUser(user);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/find/all")
    public List<UserModel> getAllList(){
        return userService.findAll();
    }

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping("/find/by/username")
    public UserModel getAllList(@RequestParam String username){
        return userService.findOne(username);
    }

    @GetMapping("/users")
    private  ResponseEntity<List<UserModel>> getAllUsers(){
        List<UserModel> users =  userRepository.findAll();
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
}
