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
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class UserAccountService {

    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EmailService emailService;

    public UserModel loadUserModelByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Object loadModelEmployeeModelByUsername(String username) {
        return employeeRepository.findByEmailIgnoreCase(username);
    }

    public List<UserModel> createUsersForSchool(String school) {
        List<Student> schoolUsers = studentRepository.findBySchool(school);

        Set<RoleModel> studentRoles = new HashSet<>();
        RoleModel studentRole = new RoleModel();
        studentRole.setRole("STUDENT");
        studentRoles.add(studentRole);

        List<UserModel> createdUsers = new ArrayList<>();
        for (Student student : schoolUsers) {
            String email = student.getStudentEmail() == null ? null : student.getStudentEmail().trim().toLowerCase();
            if (email == null || email.isBlank()) continue;

            UserModel userExists = userRepository.findByUsername(email);
            if (userExists == null) {
                UserModel newUser = new UserModel();
                String password = student.getLastName().toLowerCase() + student.getSchool().toLowerCase();

                newUser.setUsername(email);
                newUser.setSchool(school);
                newUser.setFirstName(student.getFirstName());
                newUser.setLastName(student.getLastName());
                newUser.setRoles(studentRoles);
                newUser.setPassword(passwordEncoder.encode(password));

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
        List<UserModel> users = userRepository.findBySchool(school);
        List<UserModel> updatedUsers = new ArrayList<>();

        for (UserModel user : users) {
            RoleModel student = new RoleModel();
            student.setRole("STUDENT");

            if (user.getRoles().contains(student)) {
                user.setPassword(passwordEncoder.encode(user.getUsername()));
                userRepository.save(user);
                updatedUsers.add(user);
            }
        }

        return updatedUsers;
    }
}
