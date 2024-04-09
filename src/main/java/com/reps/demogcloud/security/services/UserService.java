package com.reps.demogcloud.security.services;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.models.contactus.ContactUsRequest;
import com.reps.demogcloud.security.models.contactus.ContactUsResponse;
import com.reps.demogcloud.services.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class UserService implements UserDetailsService {

    @Autowired
    PasswordEncoder passwordEncoder;
    @Autowired
    EmployeeRepository employeeRepository;
    @Autowired
    UserRepository userRepository;
    @Autowired
    StudentRepository studentRepository;
    @Autowired
    EmailService emailService;


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

    public UserModel loadUserModelByUsername(String username) {
        // Implement the logic to load UserModel based on the username
        // For example, fetch it from a UserRepository
        return userRepository.findByUsername(username); // Assuming UserRepository has a method like findByUsername
    }

    public Object loadModelEmployeeModelByUsername(String username) {
        // Implement the logic to load UserModel based on the username
        // For example, fetch it from a UserRepository
        return employeeRepository.findByEmailIgnoreCase(username); // Assuming UserRepository has a method like findByUsername
    }

    public List<UserModel> createUsersForSchool(String school) {
        List<Student> schoolUsers = studentRepository.findBySchool(school);
        Set<RoleModel> studentRoles = new HashSet<>();
        RoleModel studentRole = new RoleModel();
        studentRole.setRole("STUDENT");
        studentRoles.add(studentRole);

        List<UserModel> createdUsers = new ArrayList<>();
        for (Student student : schoolUsers) {
            UserModel userExists = userRepository.findByUsername(student.getStudentEmail());
            if (userExists == null) {
                UserModel newUser = new UserModel();
                String password = "123abc";
                newUser.setUsername(student.getStudentEmail().toLowerCase());
                newUser.setSchoolName(school);
                newUser.setFirstName(student.getFirstName());
                newUser.setLastName(student.getLastName());
                newUser.setRoles(studentRoles);

                // Use BCryptPasswordEncoder to encode the provided password
                String encodedPassword = passwordEncoder.encode(password);
                newUser.setPassword(encodedPassword);

                userRepository.save(newUser);
                createdUsers.add(newUser);
            }
        }
        return createdUsers;

    }

    public ContactUsResponse contactUs(ContactUsRequest contactUsRequest) {
        emailService.sendContactUsMail(contactUsRequest);
        ContactUsResponse response = new ContactUsResponse();
        response.setRequest(contactUsRequest);
        response.setError("");

        return response;

    }

    public List<UserModel> lowerCaseThemAll(String school) {
        List<UserModel> users = userRepository.findBySchoolName(school);
        for (UserModel user : users) {
            user.setUsername(user.getUsername().toLowerCase());
            userRepository.save(user);
        }
        return users;
    }
}