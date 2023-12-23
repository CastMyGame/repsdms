package com.reps.demogcloud.security.controllers;

import com.reps.demogcloud.security.config.TokenProvider;
import com.reps.demogcloud.security.models.AuthToken;
import com.reps.demogcloud.security.models.LoginUser;
import com.reps.demogcloud.security.models.UserDto;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@CrossOrigin(origins = "*", maxAge = 3600)
@RestController
@RequestMapping("/users")
public class UserController {

    @Autowired
    private AuthenticationManager authenticationManager;

    @Autowired
    private TokenProvider jwtTokenUtil;

    @Autowired
    private UserService userService;

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
}
