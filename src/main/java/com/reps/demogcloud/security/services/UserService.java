package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.parameters.P;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserModel foundUser = userRepository.findByUsername(username);

        if (foundUser == null) {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }

        String name = foundUser.getUsername();
        String hashedPassword = foundUser.getPassword(); // The stored hashed password

        // You should use BCryptPasswordEncoder to encode the raw password provided by the user
        // and compare it with the stored hashed password
        // Example:
        // BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        // boolean passwordMatches = passwordEncoder.matches(rawPassword, hashedPassword);

        // Here, we're returning a basic UserDetails with no roles/authorities.
        // In practice, you should load roles/authorities from your database based on the user's profile.
        return new User(name, hashedPassword, new ArrayList<>());
    }

}
