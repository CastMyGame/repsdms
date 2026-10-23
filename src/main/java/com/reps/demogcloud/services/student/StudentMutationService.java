package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.*;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.dao.DuplicateKeyException;
import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class StudentMutationService {
    private final StudentRepository studentRepository;
    private final AuthService authService;
    private final UserRepository userRepository;
    private final StudentQueryService studentQueryService;

    public StudentResponse createNewStudent(Student input) {
        if (input == null || input.getStudentEmail() == null || input.getStudentEmail().isBlank()
                || input.getFirstName() == null || input.getFirstName().isBlank()
                || input.getLastName() == null || input.getLastName().isBlank()
                || input.getSchool() == null || input.getSchool().isBlank())
            throw new IllegalArgumentException("Student identifier, name and school are required");
        String email = input.getStudentEmail().trim().toLowerCase(Locale.ROOT);
        var existing = studentRepository.findByStudentEmailIgnoreCase(email);
        if (existing != null) return compatible(existing, input);
        var request = new AuthenticationRequest();
        request.setUsername(email); request.setFirstName(input.getFirstName().trim()); request.setLastName(input.getLastName().trim());
        request.setSchool(input.getSchool()); request.setRoles(Set.of(new RoleModel(null, "STUDENT")));
        authService.provisionStudent(request);
        // Never accept caller IDs, archived state, points, currency, notes or staff relationships.
        var student = new Student();
        student.setStudentIdNumber(AuthService.studentProvisioningId(email)); student.setStudentEmail(email);
        student.setFirstName(request.getFirstName()); student.setLastName(request.getLastName()); student.setSchool(request.getSchool());
        student.setGrade(input.getGrade()); student.setAddress(input.getAddress()); student.setParentEmail(input.getParentEmail());
        // The HTTP boundary verifies these persisted same-school staff references.
        student.setGuidanceEmail(input.getGuidanceEmail()); student.setAdminEmail(input.getAdminEmail());
        student.setParentPhoneNumber(input.getParentPhoneNumber()); student.setStudentPhoneNumber(input.getStudentPhoneNumber());
        student.setPreferredLanguage(input.getPreferredLanguage()); student.setPoints(0); student.setCurrency(0);
        try { return new StudentResponse("", studentRepository.insert(student)); }
        catch (DuplicateKeyException race) {
            return compatible(studentRepository.findByStudentEmailIgnoreCase(email), input);
        }
    }

    private StudentResponse compatible(Student existing, Student input) {
        if (existing == null || existing.isArchived() || !input.getSchool().equalsIgnoreCase(existing.getSchool())
                || !input.getFirstName().trim().equalsIgnoreCase(existing.getFirstName())
                || !input.getLastName().trim().equalsIgnoreCase(existing.getLastName()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Student already exists; use existing-student enrollment or request administrator review");
        // Existing profile/account state is never overwritten by a creation retry.
        return new StudentResponse("", existing);
    }

    public String deleteStudent(StudentRequest request) throws Exception {
        try { studentRepository.delete(request.getStudent()); }
        catch (Exception e) { throw new Exception("That student does not exist"); }
        return request.getStudent().getFirstName() + " " + request.getStudent().getLastName() + " has been deleted";
    }
    public Student archiveRecord(String studentId) {
        var record = studentQueryService.findByStudentId(studentId);
        record.setArchived(true); record.setArchivedOn(LocalDate.now()); record.setArchivedBy(studentId);
        return studentRepository.save(record);
    }
}
