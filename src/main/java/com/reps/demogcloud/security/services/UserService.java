package com.reps.demogcloud.security.services;

import com.reps.demogcloud.security.models.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private BCryptPasswordEncoder bCryptPasswordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserModel foundUser = userRepository.findByUsername(username);

        if (foundUser != null) {
            List<GrantedAuthority> authorities = getUserAuthority(foundUser.getRoles());
            return buildUserForAuthentication(foundUser, authorities);
        } else {
            throw new UsernameNotFoundException("User not found with username: " + username);
        }
    }
    public UserModel findUserByUsername (String username) {
        return userRepository.findByUsername(username);
    }

    public void saveUser (UserModel userModel) {
        userModel.setPassword(bCryptPasswordEncoder.encode(userModel.getPassword()));
        RoleModel roleModel = roleRepository.findByRole("ADMIN");
        userModel.setRoles(new HashSet<>(Arrays.asList(roleModel)));
        userRepository.save(userModel);
    }

    public UserModel loadUserModelByUsername(String username) {
        // Implement the logic to load UserModel based on the username
        // For example, fetch it from a UserRepository
        return userRepository.findByUsername(username); // Assuming UserRepository has a method like findByUsername
    }

    private List<GrantedAuthority> getUserAuthority(Set<RoleModel> userRoles) {
        Set<GrantedAuthority> roles = new HashSet<>();
        for (RoleModel userRole : userRoles) {
            roles.add(new SimpleGrantedAuthority(userRole.getRole()
            ));
        }
        List<GrantedAuthority> grantedAuthorities = new ArrayList<>(roles);
        return grantedAuthorities;
    }

    private UserDetails buildUserForAuthentication(UserModel user, List<GrantedAuthority> authorities) {
        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), authorities);
    }


}
