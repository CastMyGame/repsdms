package com.reps.demogcloud.security.controllers;


import com.fasterxml.jackson.databind.annotation.JsonAppend;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.AuthenticationResponse;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.services.UserService;
import com.reps.demogcloud.security.utils.JwtUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(
        origins = {
"http://localhost:3000"
        }
)

@RestController
public class AuthControllers {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtils jwtUtils;
    @Autowired
    UserService userService;
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AuthenticationManager authenticationManager;



    @GetMapping("/test")
    private  String testingToken(){
        return "I WORKS";
    }
    @PostMapping("/register")
    private ResponseEntity<?> registerUser(@RequestBody AuthenticationRequest authenticationRequest) {
        String username = authenticationRequest.getUsername();
        String password = authenticationRequest.getPassword();
        String firstName = authenticationRequest.getFirstName();
        String lastName = authenticationRequest.getLastName();
        String school = authenticationRequest.getSchoolName();

        UserModel userModel = new UserModel();
        userModel.setUsername(username);
        userModel.setFirstName(firstName);
        userModel.setLastName(lastName);
        userModel.setSchoolName(school);

        // Use BCryptPasswordEncoder to encode the provided password
        String encodedPassword = passwordEncoder.encode(password);
        userModel.setPassword(encodedPassword);

        try {
            userRepository.save(userModel);
            return ResponseEntity.ok(new AuthenticationResponse("Successfully Registered " + username,null));
        } catch (Exception e) {
            return ResponseEntity.ok(new AuthenticationResponse("Error During Registration of user: " + username,null));
        }
    }


    @PostMapping("/auth")
    private ResponseEntity<?> authenticateUser ( @RequestBody AuthenticationRequest authenticationRequest){
        String username = authenticationRequest.getUsername();

        String password = authenticationRequest.getPassword();
        try{
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username,password));
        }catch (Exception e){
            return ResponseEntity.ok(new AuthenticationResponse("Error Authenticating user: " + username,null));
        }
        UserDetails loadedUser = userService.loadUserByUsername(username);
        String generatedToken = jwtUtils.generateToken(loadedUser);

        // Fetch additional user-related details (e.g., UserModel) based on the username
        UserModel userModel = userService.loadUserModelByUsername(username);

        // Create a response object that includes the token and user details
        AuthenticationResponse response = new AuthenticationResponse(generatedToken, userModel);


        return ResponseEntity.ok(response);
    }


}
