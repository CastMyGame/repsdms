package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.*;
import com.reps.demogcloud.services.UserContextService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@Service @RequiredArgsConstructor
public class StudentSpotterService {
    private final StudentRepository studentRepository;
    private final UserContextService users;
    private final MongoTemplate mongo;

    private com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalWorkflowAccess(com.reps.demogcloud.services.tenancy.CanonicalWorkflowAccess value){canonical=value;}
    private Student student(String email){return canonical==null?studentRepository.findByStudentEmailIgnoreCase(email):canonical.student(email);}
    public List<Student> addAsSpotter(UpdateSpottersRequest request) { return change(request, true); }
    public List<Student> deleteSpotters(UpdateSpottersRequest request) { return change(request, false); }

    private List<Student> change(UpdateSpottersRequest request, boolean add) {
        String actor = users.getCurrentStaffEmail();
        boolean admin = users.isCurrentStaffAdministrator();
        if (request == null || request.getStudentEmail() == null || request.getStudentEmail().isEmpty()
                || request.getStudentEmail().size() > 200 || request.getSpotters() == null
                || request.getSpotters().isEmpty() || request.getSpotters().size() > 200)
            throw new IllegalArgumentException("Select students and staff");
        List<String> staff = request.getSpotters().stream().map(this::normalize).distinct().toList();
        if (!admin && (staff.size() != 1 || !actor.equalsIgnoreCase(staff.get(0)))) denied();
        List<Student> targets = request.getStudentEmail().stream().map(this::normalize).distinct()
                .map(this::student).toList();
        // Validate the entire batch before updates. Spotter status itself is never authority.
        for (Student student : targets) {
            users.requireStaffInStudentSchool(actor, student);
            if (student.isArchived()) denied();
            for (String email : staff) {
                if (add) {
                    users.requireStaffInStudentSchool(email, student);
                    if (!users.hasIndependentStudentAccess(email, student)) denied();
                } else if (!admin && !users.hasIndependentStudentAccess(actor, student)) denied();
            }
        }
        List<Student> results = new ArrayList<>();
        for (Student student : targets) {
            Query query = Query.query(Criteria.where("_id").is(student.getStudentIdNumber())
                    .and(canonical==null?"school":"tenantSchoolId").is(canonical==null?student.getSchool():canonical.tenant()).and("archived").ne(true));
            Update update = new Update();
            if (add) update.addToSet("spotters").each(staff.toArray());
            else {
                // Also remove historical mixed-case spellings without replacing the whole array.
                List<String> removals = new ArrayList<>(staff);
                if (student.getSpotters() != null) student.getSpotters().stream()
                        .filter(e -> e != null && staff.stream().anyMatch(e::equalsIgnoreCase)).forEach(removals::add);
                update.pullAll("spotters", removals.toArray());
            }
            Student result = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Student.class);
            if (result == null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Student changed; refresh and retry. Some earlier spotter changes may already be saved; retry is safe.");
            results.add(result);
        }
        return results;
    }

    public Student removeSpotterByEmail(String email, Student input) {
        if (input == null || input.getStudentIdNumber() == null) throw new IllegalArgumentException("Student ID required");
        Student student = canonical==null?studentRepository.findByStudentIdNumber(input.getStudentIdNumber()):canonical.one(input.getStudentIdNumber(),Student.class);
        if (student == null) { denied(); }
        users.requireStaffInStudentSchool(users.getCurrentStaffEmail(), student);
        UpdateSpottersRequest request = new UpdateSpottersRequest();
        request.setStudentEmail(List.of(student.getStudentEmail())); request.setSpotters(List.of(email));
        return deleteSpotters(request).get(0);
    }

    public List<Student> findBySpotter(String email) {
        String actor = users.getCurrentStaffEmail();
        String target = normalize(email);
        if (!actor.equalsIgnoreCase(target) && !users.isCurrentStaffAdministrator()) denied();
        String verified = users.getVerifiedStaffEmail(target);
        return (canonical==null?studentRepository.findBySpottersContainsIgnoreCase(verified):mongo.find(canonical.query(),Student.class)).stream()
                .filter(s -> s.getSpotters()!=null && s.getSpotters().stream().anyMatch(verified::equalsIgnoreCase))
                .filter(s -> users.hasIndependentStudentAccess(actor, s) && users.hasIndependentStudentAccess(verified, s)).toList();
    }
    private String normalize(String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Identifier required");
        return value.trim().toLowerCase(Locale.ROOT);
    }
    private static void denied() { throw new AccessDeniedException("Access denied"); }
}
