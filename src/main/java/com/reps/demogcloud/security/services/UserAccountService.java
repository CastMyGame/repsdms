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

    @org.springframework.beans.factory.annotation.Autowired(required=false) private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private org.springframework.data.mongodb.core.MongoTemplate mongo;
    private final PasswordEncoder passwordEncoder;
    private final EmployeeRepository employeeRepository;
    private final UserRepository userRepository;
    private final StudentRepository studentRepository;
    private final EmailService emailService;
    private final com.reps.demogcloud.services.UserAdministrationService administration;

    public UserModel loadUserModelByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public Object loadModelEmployeeModelByUsername(String username) {
        return employeeRepository.findByEmailIgnoreCase(username);
    }

    public List<UserModel> createUsersForSchool(String school) {
        school = administration.requireSchoolAdmin(school);
        List<Student> schoolUsers = canonical==null?studentRepository.findBySchool(school):mongo.find(canonical.query(),Student.class);
        final String verifiedSchool = school;
        if (schoolUsers.stream().anyMatch(s -> canonical==null?s.getSchool() == null || !verifiedSchool.equalsIgnoreCase(s.getSchool()):!canonical.owns(s)))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");

        Set<RoleModel> studentRoles = new HashSet<>();
        RoleModel studentRole = new RoleModel();
        studentRole.setRole("STUDENT");
        studentRoles.add(studentRole);

        List<UserModel> createdUsers = new ArrayList<>();
        for (Student student : schoolUsers) {
            if (student.isArchived()) continue;
            String email = student.getStudentEmail() == null ? null : student.getStudentEmail().trim().toLowerCase();
            if (email == null || email.isBlank()) continue;

            UserModel userExists = userRepository.findByUsernameIgnoreCase(email);
            if (userExists == null) {
                UserModel newUser = new UserModel();
                String password = AccountAccessService.unusablePassword();
                newUser.setPasswordSetupRequired(true);

                newUser.setUsername(email);
                newUser.setId(AuthService.studentProvisioningId(email));
                newUser.setSchool(school);
                if(canonical!=null)canonical.bindNew(newUser);
                newUser.setFirstName(student.getFirstName());
                newUser.setLastName(student.getLastName());
                newUser.setRoles(studentRoles);
                newUser.setPassword(passwordEncoder.encode(password));

                try { createdUsers.add(userRepository.insert(newUser)); }
                catch (org.springframework.dao.DuplicateKeyException concurrentProvisioning) {
                    // Another request owns this deterministic identity. Never save over it.
                    var saved = userRepository.findByUsernameIgnoreCase(email);
                    if (saved == null || (canonical==null?!school.equalsIgnoreCase(saved.getSchool()):!canonical.owns(saved)))
                        throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,
                                "Student account provisioning requires administrator review");
                }
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
        throw new org.springframework.security.access.AccessDeniedException("Legacy password maintenance is disabled");
    }
}
