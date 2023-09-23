package com.reps.demogcloud.security.controllers;


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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthControllers {

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
    private ResponseEntity<?> registerUser(@RequestBody AuthenticationRequest authenticationRequest){
        String username = authenticationRequest.getUsername();
        String password = authenticationRequest.getPassword();
        UserModel userModel = new UserModel();
        userModel.setUsername(username);
        userModel.setPassword(password);
        try{
            userRepository.save(userModel);

        }catch (Exception e){
            ResponseEntity.ok(new AuthenticationResponse("Error During Registration of user: " + username));
        }
        return ResponseEntity.ok(new AuthenticationResponse("Successfully Registered " + username));
    }

    @PostMapping("/auth")
    private ResponseEntity<?> authenticateUser ( @RequestBody AuthenticationRequest authenticationRequest){
        String username = authenticationRequest.getUsername();
        String password = authenticationRequest.getPassword();
        try{
            authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(username,password));
        }catch (Exception e){
            return ResponseEntity.ok(new AuthenticationResponse("Error Authenticating user: " + username));
        }
        UserDetails loadedUser = userService.loadUserByUsername(username);
       String generatedToken = jwtUtils.generateToken(loadedUser);

        return ResponseEntity.ok(new AuthenticationResponse(generatedToken));
    }


}
