package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.data.filters.CustomFilters;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.security.models.AuthenticationRequest;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.security.models.UserRepository;
import com.reps.demogcloud.security.services.AuthService;
import com.reps.demogcloud.utils.StudentUtils;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StudentMutationService {

    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    private final CustomFilters customFilters;
    private final StudentUtils studentUtils;
    private final StudentRepository studentRepository;
    private final PunishRepository punishRepository;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final StudentQueryService studentQueryService;

    public StudentResponse createNewStudent(Student studentRequest) {
        // Check if a student with the same email already exists
        if (studentRepository.existsByStudentEmail(studentRequest.getStudentEmail().toLowerCase())) {
            throw new IllegalArgumentException("A student with this email already exists.");
        }
        // Check if a user with the same email (username) already exists
        if (userRepository.existsByUsername(studentRequest.getStudentEmail().toLowerCase())) {
            throw new IllegalArgumentException("A user with this email has already been registered.");
        }
        Set<RoleModel> roles = new HashSet<>();
        RoleModel student = new RoleModel();
        student.setRole("STUDENT");
        roles.add(student);
        AuthenticationRequest authenticationRequest = new AuthenticationRequest();
        authenticationRequest.setUsername(studentRequest.getStudentEmail().toLowerCase());
        authenticationRequest.setPassword(studentRequest.getStudentEmail().toLowerCase());
        authenticationRequest.setFirstName(studentRequest.getFirstName());
        authenticationRequest.setLastName(studentRequest.getLastName());
        authenticationRequest.setSchoolName(studentRequest.getSchool());
        authenticationRequest.setRoles(roles);
        studentRequest.setPoints(0);
        try {
            authService.createEmployeeUser(authenticationRequest);
            return new StudentResponse("", studentRepository.save(studentRequest));
        } catch (IllegalArgumentException e) {
            logger.error(e.getMessage());
            return new StudentResponse(e.getMessage(), null);
        }
    }

    public String deleteStudent(StudentRequest studentRequest) throws Exception {
        try {
            studentRepository.delete(studentRequest.getStudent());
        } catch (Exception e) {
            throw new Exception("That student does not exist");
        }
        return studentRequest.getStudent().getFirstName() +
                " " +
                studentRequest.getStudent().getLastName() +
                " has been deleted";
    }

    public Student archiveRecord(String studentId) {
        //Check for existing record
        Student existingRecord = studentQueryService.findByStudentId(studentId);
        //Updated Record
        existingRecord.setArchived(true);
        LocalDate createdOn = LocalDate.now();
        existingRecord.setArchivedOn(createdOn);
        existingRecord.setArchivedBy(studentId);
        return studentRepository.save(existingRecord);
    }
}
