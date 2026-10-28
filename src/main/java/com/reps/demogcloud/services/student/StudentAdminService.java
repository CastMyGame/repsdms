package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.util.*;

@Service @RequiredArgsConstructor
public class StudentAdminService {
    private final SchoolRepository schoolRepository;
    private final StudentRepository studentRepository;
    private final com.reps.demogcloud.utils.StudentUtils studentUtils;
    private final WorkflowAccessService access;
    private final UserContextService users;
    private final MongoTemplate mongo;
    private final UserAdministrationService administration;

    private com.reps.demogcloud.services.tenancy.CanonicalDomainAccess canonical;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setCanonicalDomainAccess(com.reps.demogcloud.services.tenancy.CanonicalDomainAccess value){canonical=value;}
    private Student staffStudent(String email) {
        if(canonical!=null)return canonical.student(email,true);
        access.requireStaff(); access.requireStudentAccess(email);
        return studentRepository.findByStudentEmailIgnoreCase(email);
    }
    private Query active(Student student) {
        return Query.query(Criteria.where("_id").is(student.getStudentIdNumber())
                .and(canonical==null?"school":"tenantSchoolId").is(canonical==null?student.getSchool():canonical.actor().tenantSchoolId()).and("archived").ne(true));
    }
    private Student modify(Query query, Update update) {
        Student result = mongo.findAndModify(query, update, FindAndModifyOptions.options().returnNew(true), Student.class);
        if (result == null) throw new ResponseStatusException(HttpStatus.CONFLICT,
                "Student changed; refresh before retrying. Earlier changes in a batch may already be saved.");
        return result;
    }
    public Student addTimeToStudent(String email, int hours, int minutes) {
        Student student = staffStudent(email);
        if (hours < 0 || hours > 1000 || minutes < 0 || minutes > 59 || hours + minutes == 0)
            throw new IllegalArgumentException("Positive detention time required (minutes 0-59)");
        Student.TimeBank old = student.getTimeBank();
        int total = Math.addExact(old == null ? 0 : Math.addExact(Math.multiplyExact(old.getHours(),60),old.getMinutes()), hours*60+minutes);
        return modify(active(student).addCriteria(Criteria.where("timeBank").is(old)),
                new Update().set("timeBank",new Student.TimeBank(total/60,total%60)));
    }
    public List<Student> updateStudents(List<Student> inputs) {
        if(canonical!=null)canonical.disconnected(); // Canonical profile field/relationship changes require 4B-2.
        access.requireStaff();
        if (inputs == null || inputs.isEmpty() || inputs.size() > 200) throw new IllegalArgumentException("Invalid student batch");
        List<Student> records = new ArrayList<>(); Set<String> ids = new HashSet<>();
        for (Student input : inputs) {
            if (input == null || input.getStudentIdNumber() == null || !ids.add(input.getStudentIdNumber()))
                throw new IllegalArgumentException("Unique student IDs required");
            Student old = studentRepository.findById(input.getStudentIdNumber()).orElseThrow(() -> new IllegalArgumentException("Student not found"));
            if (!access.sameSchool(old.getSchool())) throw new org.springframework.security.access.AccessDeniedException("Access denied");
            staffStudent(old.getStudentEmail());
            if ((input.getCurrency()!=null && !Objects.equals(input.getCurrency(),old.getCurrency()))
                    || (input.getPoints()!=null && !Objects.equals(input.getPoints(),old.getPoints()))
                    || (input.getSchool()!=null && !Objects.equals(input.getSchool(),old.getSchool()))
                    || (input.getStudentEmail()!=null && !input.getStudentEmail().equalsIgnoreCase(old.getStudentEmail()))
                    || input.isArchived() || changed(input.getSpotters(),old.getSpotters())
                    || changed(input.getNotesArray(),old.getNotesArray()) || changed(input.getTimeBank(),old.getTimeBank())
                    || changed(input.getArchivedBy(),old.getArchivedBy()) || changed(input.getArchivedOn(),old.getArchivedOn())
                    || changed(input.getArchivedExplanation(),old.getArchivedExplanation()))
                throw new IllegalArgumentException("Protected student fields require their authorized workflow");
            if (changed(input.getGuidanceEmail(),old.getGuidanceEmail()) || changed(input.getAdminEmail(),old.getAdminEmail())) {
                access.requireAdmin();
                if(input.getGuidanceEmail()!=null) administration.studentStaffReference(input.getGuidanceEmail(),old.getSchool(),"GUIDANCE");
                if(input.getAdminEmail()!=null) administration.studentStaffReference(input.getAdminEmail(),old.getSchool(),"ADMIN");
            }
            if (input.getProfileRevision()!=old.getProfileRevision()) conflict();
            records.add(old);
        }
        List<Student> results = new ArrayList<>();
        for (int i=0;i<inputs.size();i++) {
            Student input=inputs.get(i), old=records.get(i); Update update=new Update();
            set(update,"firstName",input.getFirstName());set(update,"lastName",input.getLastName());
            set(update,"parentEmail",input.getParentEmail());set(update,"address",input.getAddress());set(update,"grade",input.getGrade());
            set(update,"parentPhoneNumber",input.getParentPhoneNumber());set(update,"studentPhoneNumber",input.getStudentPhoneNumber());
            set(update,"stateStudentId",input.getStateStudentId());
            set(update,"guidanceEmail",input.getGuidanceEmail()==null?null:input.getGuidanceEmail().trim().toLowerCase(Locale.ROOT));
            set(update,"adminEmail",input.getAdminEmail()==null?null:input.getAdminEmail().trim().toLowerCase(Locale.ROOT));
            Criteria revision=old.getProfileRevision()==0
                    ? new Criteria().orOperator(Criteria.where("profileRevision").is(0),Criteria.where("profileRevision").exists(false))
                    : Criteria.where("profileRevision").is(old.getProfileRevision());
            results.add(modify(active(old).addCriteria(revision), update.inc("profileRevision",1)));
        }
        return results;
    }
    private boolean changed(Object input,Object old) { return input!=null && !Objects.equals(input,old); }
    private void set(Update update,String name,Object value) { if(value!=null)update.set(name,value); }
    private void conflict() { throw new ResponseStatusException(HttpStatus.CONFLICT,"Student profile changed; refresh before retrying"); }

    public Student updateStudentNotes(String id,ThreadEvent input) {
        Student student;
        if(canonical!=null)student=canonical.studentId(id);
        else {
            access.requireStaff();student=studentRepository.findById(id).orElseThrow(() -> new org.springframework.security.access.AccessDeniedException("Access denied"));
            if(!access.sameSchool(student.getSchool()))throw new org.springframework.security.access.AccessDeniedException("Access denied");
            staffStudent(student.getStudentEmail());
        }
        if(input==null || input.getContent()==null || input.getContent().isBlank() || input.getContent().length()>10000
                || input.getEvent()==null || input.getEvent().length()>100)throw new IllegalArgumentException("Valid note required");
        ThreadEvent event=new ThreadEvent();event.setCreatedBy(canonical==null?users.getCurrentStaffEmail():canonical.email(true));event.setDate(LocalDate.now());
        event.setEvent(input.getEvent());event.setContent(input.getContent());
        var update=new Update().push("notesArray",event);if(canonical!=null)update.inc("profileRevision",1);return modify(active(student),update);
    }
    public Optional<School> getStudentSchool() {
        if(canonical!=null){var actor=canonical.actor();if(!actor.roles().contains("STUDENT"))throw new org.springframework.security.access.AccessDeniedException("Access denied");return schoolRepository.findById(actor.tenantSchoolId());}
        access.requireStudent();
        Student student=studentRepository.findByStudentEmailIgnoreCase(users.getCurrentUsername());
        return schoolRepository.findBySchoolNameIgnoreCase(student.getSchool());
    }
    public List<Student> massAssignForSchool() {
        throw new ResponseStatusException(HttpStatus.GONE,"Legacy global maintenance is retired");
    }
}
