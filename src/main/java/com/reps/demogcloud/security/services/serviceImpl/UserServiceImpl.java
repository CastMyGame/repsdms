package com.reps.demogcloud.security.services.serviceImpl;

import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserDto;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.repository.UserRepository;
import com.reps.demogcloud.security.services.RoleService;
import com.reps.demogcloud.security.services.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service(value = "userService")
public class UserServiceImpl implements UserDetailsService, UserService {

    @Autowired
    private RoleService roleService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private BCryptPasswordEncoder bcryptEncoder;

    // Load user by username
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        UserModel user = userRepository.findByUsername(username);
        if(user == null){
            throw new UsernameNotFoundException("Invalid username or password.");
        }
        return new org.springframework.security.core.userdetails.User(user.getUsername(), user.getPassword(), getAuthority(user));
    }

    // Get user authorities
    private Set<SimpleGrantedAuthority> getAuthority(UserModel user) {
        Set<SimpleGrantedAuthority> authorities = new HashSet<>();
        user.getRoles().forEach(role -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getRole()));
        });
        return authorities;
    }

    // Find all users
    public List<UserModel> findAll() {
        List<UserModel> list = new ArrayList<>();
        userRepository.findAll().iterator().forEachRemaining(list::add);
        return list;
    }

    // Find user by username
    @Override
    public UserModel findOne(String username) {
        return userRepository.findByUsername(username);
    }

    // Save user
    @Override
    public UserModel save(UserDto user) {

        UserModel nUser = user.getUserFromDto();
        nUser.setPassword(bcryptEncoder.encode(user.getPassword()));

        // Set default role as USER
        RoleModel role = roleService.findByRole("STUDENT");
        Set<RoleModel> roleSet = new HashSet<>();
        roleSet.add(role);

        // If email domain is admin.edu, add ADMIN role
        if(nUser.getUsername().split("@")[1].equals("admin.edu")){
            role = roleService.findByRole("ADMIN");
            roleSet.add(role);
        }

        nUser.setRoles(roleSet);
        return userRepository.save(nUser);
    }

    @Override
    public UserModel createUser(UserDto user) {
        UserModel nUser = user.getUserFromDto();
        nUser.setPassword(bcryptEncoder.encode(user.getPassword()));

        RoleModel studentRole = roleService.findByRole("STUDENT");
        RoleModel adminRole = roleService.findByRole("ADMIN");

        Set<RoleModel> roleSet = new HashSet<>();
        if (studentRole != null) {
            roleSet.add(studentRole);
        }
        if (adminRole != null) {
            roleSet.add(adminRole);
        }

        nUser.setRoles(roleSet);
        return userRepository.save(nUser);
    }
}
