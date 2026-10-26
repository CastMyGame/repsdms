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
    private final com.reps.demogcloud.services.WorkflowAccessService access;
    private final com.reps.demogcloud.services.UserAdministrationService administration;
    private final com.reps.demogcloud.services.UserContextService context;
    private final org.springframework.data.mongodb.core.MongoTemplate mongo;

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

    public String deleteStudent(StudentRequest request) {
        throw new ResponseStatusException(HttpStatus.GONE, "Destructive deletion is retired; an administrator may archive the student");
    }
    public Student archiveRecord(String studentId) {
        access.requireAdmin();
        Student student=studentRepository.findByStudentIdNumber(studentId);
        if(student==null || !access.sameSchool(student.getSchool()))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        UserModel account=userRepository.findByUsernameIgnoreCase(student.getStudentEmail());
        if(account!=null) {
            if(!access.sameSchool(account.getSchool()) || account.getRoles()==null
                    || account.getRoles().size()!=1 || account.getRoles().stream().noneMatch(r -> r!=null && "STUDENT".equalsIgnoreCase(r.getRole())))
                throw new org.springframework.security.access.AccessDeniedException("Access denied");
            // Existing deactivation revokes account tickets/sessions first. Fail closed on a later archive failure.
            administration.setEnabled(account.getId(),false);
        }
        var query=org.springframework.data.mongodb.core.query.Query.query(org.springframework.data.mongodb.core.query.Criteria.where("_id")
                .is(studentId).and("school").is(student.getSchool()));
        var update=new org.springframework.data.mongodb.core.query.Update().set("archived",true);
        if(!student.isArchived())update.set("archivedOn",LocalDate.now()).set("archivedBy",context.getCurrentStaffEmail());
        Student result=mongo.findAndModify(query,update,org.springframework.data.mongodb.core.FindAndModifyOptions.options().returnNew(true),Student.class);
        if(result==null)throw new ResponseStatusException(HttpStatus.CONFLICT,"Account access disabled; student archive needs retry");
        return result;
    }
}
