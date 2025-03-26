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
        String username = authenticationRequest.getUsername();
        String password = authenticationRequest.getPassword();
        String firstName = authenticationRequest.getFirstName();
        String lastName = authenticationRequest.getLastName();
        String school = authenticationRequest.getSchoolName();
        Set<RoleModel> roles = authenticationRequest.getRoles();

        UserModel userModel = new UserModel();
        userModel.setUsername(username);
        userModel.setFirstName(firstName);
        userModel.setLastName(lastName);
        userModel.setSchoolName(school);
        userModel.setRoles(roles);
        // Use BCryptPasswordEncoder to encode the provided password
        String encodedPassword = passwordEncoder.encode(password);
        userModel.setPassword(encodedPassword);

        return userRepository.save(userModel);
    }
}
