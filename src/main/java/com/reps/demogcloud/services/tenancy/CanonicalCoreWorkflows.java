package com.reps.demogcloud.services.tenancy;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.security.models.UserModel;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.Pattern;

/** Disabled preparation adapter. No production controller/worker invokes it. No legacy name fallback.
 * Case factories/assignment state transitions and cross-document commit coordination are NOT replaced here. */
@Service
@ConditionalOnProperty(name="reps.tenancy.core-preparation-enabled",havingValue="true")
@RequiredArgsConstructor
public class CanonicalCoreWorkflows {
    private static final String OWNER="tenantSchoolId";
    private final CanonicalAccountContext context;
    private final MongoTemplate mongo;

    public enum Kind {
        PUNISHMENT("Punishments"), OFFICE("OfficeReferrals"), GUIDANCE("Guidance"), BEHAVIOR("TrackedBehaviorEvents");
        final String collection;
        Kind(String collection){this.collection=collection;}
    }
    /** Identity assertions are never authority. New adapters must not forward legacy identity fields. */
    public record Claims(String actorAccountId,String tenantSchoolId,String school) {}
    public record Draft(Kind kind,String studentId,Claims claims) {}
    /** Metadata only, not a saved case or a reusable authorization capability. */
    public record CreationOwner(Kind kind,String tenantSchoolId,String actorAccountId,String actorProfileId,
                                String actorEmail,String studentId,String studentEmail) {}
    private record Scope(CanonicalAccountContext.Actor actor,Student student,Employee staff) {}

    public Student readStudent(String id) { return authorized(context.requireCurrent(),id,false).student(); }

    private Scope authorized(CanonicalAccountContext.Actor actor,String id,boolean staffOnly) {
        Student student=ownedStudent(actor,id);
        if(actor.roles().contains("STUDENT")) {
            if(staffOnly || !actor.profileId().equals(student.getStudentIdNumber())) throw denied();
            return new Scope(actor,student,null);
        }
        Employee staff=staff(actor);
        if(!independent(actor,staff,student)) throw denied();
        return new Scope(actor,student,staff);
    }
    private Student ownedStudent(CanonicalAccountContext.Actor actor,String id) {
        if(!text(id)) throw denied();
        Student student=mongo.findOne(owned(id,actor).addCriteria(Criteria.where("archived").ne(true)),Student.class);
        if(student==null || !actor.tenantSchoolId().equals(student.getTenantSchoolId()) || !text(student.getStudentEmail())) throw denied();
        var accounts=mongo.find(Query.query(Criteria.where(OWNER).is(actor.tenantSchoolId()).and("username")
                .regex(exactEmail(student.getStudentEmail()))).limit(2),UserModel.class);
        if(accounts.size()!=1 || !text(accounts.get(0).getId()) || !accounts.get(0).isEnabled() || accounts.get(0).getRoles()==null
                || accounts.get(0).getRoles().size()!=1 || accounts.get(0).getRoles().stream().anyMatch(r->r==null || !"STUDENT".equalsIgnoreCase(r.getRole()))) throw denied();
        return student;
    }
    private Employee staff(CanonicalAccountContext.Actor actor) {
        if(actor.roles().contains("STUDENT")) throw denied();
        Employee staff=mongo.findOne(owned(actor.profileId(),actor).addCriteria(Criteria.where("archived").ne(true)),Employee.class);
        if(staff==null || !text(staff.getEmail())) throw denied();
        return staff;
    }
    private boolean independent(CanonicalAccountContext.Actor actor,Employee staff,Student student) {
        if(actor.roles().contains("ADMIN")) return true;
        if(actor.roles().contains("GUIDANCE") && same(staff.getEmail(),student.getGuidanceEmail())) return true;
        return actor.roles().contains("TEACHER") && staff.getClasses()!=null && staff.getClasses().stream().filter(Objects::nonNull)
                .filter(c->c.getClassRoster()!=null).flatMap(c->c.getClassRoster().stream()).anyMatch(e->same(e,student.getStudentEmail()));
    }

    public Document readRecord(Kind kind,String id) {
        var actor=context.requireCurrent();
        return record(actor,kind,id);
    }
    public Document readAccessibleRecord(Kind kind,String id){return record(context.requireCurrent(),kind,id,true);}
    private Document record(CanonicalAccountContext.Actor actor,Kind kind,String id){return record(actor,kind,id,false);}
    private Document record(CanonicalAccountContext.Actor actor,Kind kind,String id,boolean allowArchived) {
        if(kind==null || !text(id)) throw denied();
        var query=owned(id,actor);if(!allowArchived)query.addCriteria(Criteria.where("archived").ne(true));
        var record=mongo.findOne(query,Document.class,kind.collection);
        if(record==null || !(record.get("studentEmail") instanceof String email) || !text(email)) throw denied();
        var students=mongo.find(Query.query(Criteria.where(OWNER).is(actor.tenantSchoolId()).and("studentEmail").regex(exactEmail(email))).limit(2),Student.class);
        if(students.size()!=1) throw denied();
        var scope=allowArchived && actor.roles().contains("ADMIN")?new Scope(actor,students.get(0),staff(actor)):authorized(actor,students.get(0).getStudentIdNumber(),false);
        if(kind==Kind.GUIDANCE) {
            // Guidance notes stay limited to counselors/admins and independently related teacher authors.
            if(actor.roles().contains("STUDENT")) throw denied();
            if(!actor.roles().contains("ADMIN") && !assignedCounselor(scope)
                    && !(actor.roles().contains("TEACHER") && (same(scope.staff().getEmail(),string(record,"teacherEmail"))
                    || same(scope.staff().getEmail(),string(record,"originalAuthorEmail"))))) throw denied();
        }
        if(kind==Kind.GUIDANCE && text(string(record,"linkToPunishment"))) {
            Document linked=record(actor,Kind.PUNISHMENT,string(record,"linkToPunishment"),allowArchived);
            if(!same(email,string(linked,"studentEmail"))) throw denied();
        }
        return record;
    }
    private static boolean assignedCounselor(Scope scope) {
        return scope.actor().roles().contains("GUIDANCE") && same(scope.staff().getEmail(),scope.student().getGuidanceEmail());
    }

    /** Reusable guarded lookup for assignment adapters, not assignment progress/approval execution. */
    public Document readAssignmentCase(String id) {
        var actor=context.requireCurrent();var record=record(actor,Kind.PUNISHMENT,id);
        Object pinned=record.get("assignmentTemplateId");
        if(!(pinned instanceof String templateId) || !text(templateId)) throw denied();
        if(record.get("assignmentWork")!=null && !(record.get("assignmentWork") instanceof Document)) throw denied();
        Document work=(Document)record.get("assignmentWork");
        if(work!=null && work.get("template")!=null && !(work.get("template") instanceof Document)) throw denied();
        if(work!=null && work.get("template") instanceof Document snapshot) {
            Object snapshotId=snapshot.getOrDefault("id",snapshot.get("_id"));
            if(!templateId.equals(snapshotId)) throw denied();
            requireTemplateOwner(snapshot,actor);
            return record; // Immutable case snapshot remains authoritative if the catalog later disappears.
        }
        Criteria global=new Criteria().andOperator(Criteria.where("createdBySystem").is(true),Criteria.where("scope").is("SYSTEM_DEFAULT"),
                Criteria.where(OWNER).exists(false),new Criteria().orOperator(Criteria.where("createdByUserId").is(null),Criteria.where("createdByUserId").is("")),
                new Criteria().orOperator(Criteria.where("schoolId").is(null),Criteria.where("schoolId").is("")));
        Document template=mongo.findOne(Query.query(Criteria.where("_id").is(templateId)).addCriteria(new Criteria()
                .orOperator(Criteria.where(OWNER).is(actor.tenantSchoolId()),global)),Document.class,"assignment_templates");
        if(template==null) throw denied();
        requireTemplateOwner(template,actor);
        return record;
    }
    private void requireTemplateOwner(Document template,CanonicalAccountContext.Actor actor) {
        if(template.get("createdBySystem")!=null && !(template.get("createdBySystem") instanceof Boolean)) throw denied();
        boolean global=Boolean.TRUE.equals(template.get("createdBySystem")) && "SYSTEM_DEFAULT".equals(string(template,"scope"))
                && !text(string(template,"createdByUserId")) && !text(string(template,"schoolId")) && !template.containsKey(OWNER);
        if(!global && (!actor.tenantSchoolId().equals(template.get(OWNER)) || Boolean.TRUE.equals(template.get("createdBySystem")))) throw denied();
    }

    /** Validate the complete roster before a future revision-CAS adapter writes any class changes. */
    public List<String> validateRoster(String employeeId,List<String> studentIds) {
        var actor=context.requireCurrent();staff(actor);
        if(!actor.roles().contains("ADMIN") && (!actor.roles().contains("TEACHER") || !actor.profileId().equals(employeeId))) throw denied();
        Employee teacher=mongo.findOne(owned(employeeId,actor).addCriteria(Criteria.where("archived").ne(true)),Employee.class);
        if(teacher==null || !text(teacher.getEmployeeId()) || !text(teacher.getEmail()) || teacher.getRoles()==null
                || teacher.getRoles().stream().noneMatch(r->r!=null && "TEACHER".equalsIgnoreCase(r.getRole()))) throw denied();
        var accounts=mongo.find(Query.query(Criteria.where(OWNER).is(actor.tenantSchoolId()).and("username").regex(exactEmail(teacher.getEmail()))).limit(2),UserModel.class);
        if(accounts.size()!=1 || !text(accounts.get(0).getId()) || !accounts.get(0).isEnabled() || !roles(accounts.get(0)).equals(teacher.getRoles().stream()
                .map(r->{if(r==null || r.getRole()==null)throw denied();return r.getRole().toUpperCase(Locale.ROOT);}).collect(java.util.stream.Collectors.toSet()))) throw denied();
        if(studentIds==null || studentIds.size()>200 || studentIds.stream().distinct().count()!=studentIds.size()) throw new IllegalArgumentException("Invalid roster");
        var result=new ArrayList<String>();
        for(String id:studentIds) result.add(ownedStudent(actor,id).getStudentEmail());
        return List.copyOf(result);
    }

    public List<CreationOwner> prepareCreations(List<Draft> drafts) {
        var actor=context.requireCurrent();
        if(drafts==null || drafts.isEmpty() || drafts.size()>200) throw new IllegalArgumentException("Select 1-200 records");
        var result=new ArrayList<CreationOwner>();
        for(var draft:drafts) {
            if(draft==null || draft.kind()==null) throw new IllegalArgumentException("Record type required");
            if(draft.claims()!=null && (draft.claims().actorAccountId()!=null || draft.claims().tenantSchoolId()!=null || draft.claims().school()!=null)) throw denied();
            var scope=authorized(actor,draft.studentId(),draft.kind()!=Kind.GUIDANCE);
            if(draft.kind()==Kind.OFFICE) requireStaffReference(actor,scope.student().getAdminEmail(),"ADMIN");
            if(draft.kind()==Kind.GUIDANCE && text(scope.student().getGuidanceEmail())) requireStaffReference(actor,scope.student().getGuidanceEmail(),"GUIDANCE");
            String email=scope.staff()==null?scope.student().getStudentEmail():scope.staff().getEmail();
            result.add(new CreationOwner(draft.kind(),actor.tenantSchoolId(),actor.accountId(),actor.profileId(),email,
                    scope.student().getStudentIdNumber(),scope.student().getStudentEmail()));
        }
        return List.copyOf(result);
    }
    private void requireStaffReference(CanonicalAccountContext.Actor actor,String email,String role) {
        if(!text(email))throw denied();
        var users=mongo.find(Query.query(Criteria.where(OWNER).is(actor.tenantSchoolId()).and("username").regex(exactEmail(email))).limit(2),UserModel.class);
        var profiles=mongo.find(Query.query(Criteria.where(OWNER).is(actor.tenantSchoolId()).and("email").regex(exactEmail(email)).and("archived").ne(true)).limit(2),Employee.class);
        if(users.size()!=1 || profiles.size()!=1 || !text(users.get(0).getId()) || !text(profiles.get(0).getEmployeeId())
                || !users.get(0).isEnabled() || users.get(0).isPasswordSetupRequired()
                || !roles(users.get(0)).contains(role) || profiles.get(0).getRoles()==null
                || !roles(users.get(0)).equals(profiles.get(0).getRoles().stream().map(r->{if(r==null || r.getRole()==null)throw denied();return r.getRole().toUpperCase(Locale.ROOT);}).collect(java.util.stream.Collectors.toSet()))) throw denied();
    }

    public Student appendStudentNote(String id,long expectedRevision,String content) {
        var scope=authorized(context.requireCurrent(),id,true);
        if(content==null || content.isBlank() || content.length()>10000) throw new IllegalArgumentException("Valid note required");
        var note=new ThreadEvent();note.setContent(content);note.setEvent("NOTE");note.setDate(LocalDate.now());note.setCreatedBy(scope.staff().getEmail());
        return mutate(scope,expectedRevision,new Update().push("notesArray",note));
    }
    public Student changeOwnSpotter(String id,long expectedRevision,boolean add) {
        var scope=authorized(context.requireCurrent(),id,true); // Never inspect spotters as permission evidence.
        var update=add?new Update().addToSet("spotters",scope.staff().getEmail()):new Update().pull("spotters",scope.staff().getEmail());
        return mutate(scope,expectedRevision,update);
    }
    private Student mutate(Scope scope,long expectedRevision,Update update) {
        if(expectedRevision<0 || expectedRevision!=scope.student().getProfileRevision()) throw conflict();
        Criteria revision=expectedRevision==0?new Criteria().orOperator(Criteria.where("profileRevision").is(0),Criteria.where("profileRevision").exists(false))
                :Criteria.where("profileRevision").is(expectedRevision);
        Student saved=mongo.findAndModify(owned(scope.student().getStudentIdNumber(),scope.actor()).addCriteria(Criteria.where("archived").ne(true)).addCriteria(revision),
                update.inc("profileRevision",1),FindAndModifyOptions.options().returnNew(true),Student.class);
        if(saved==null) throw conflict();
        return saved;
    }
    private static Query owned(String id,CanonicalAccountContext.Actor actor){return Query.query(Criteria.where("_id").is(id).and(OWNER).is(actor.tenantSchoolId()));}
    private static Pattern exactEmail(String email){return Pattern.compile("^"+Pattern.quote(email)+"$",Pattern.CASE_INSENSITIVE);}
    private static boolean text(String value){return value!=null && !value.isBlank();}
    private static boolean same(String a,String b){return a!=null && b!=null && a.equalsIgnoreCase(b);}
    private static Set<String> roles(UserModel account){
        if(account.getRoles()==null || account.getRoles().isEmpty())throw denied();
        var result=new HashSet<String>();for(var r:account.getRoles()){if(r==null || r.getRole()==null)throw denied();result.add(r.getRole().toUpperCase(Locale.ROOT));}
        if(!Set.of("TEACHER","GUIDANCE","ADMIN").containsAll(result))throw denied();return result;
    }
    private static String string(Document document,String field){Object value=document.get(field);if(value!=null && !(value instanceof String))throw denied();return (String)value;}
    private static AccessDeniedException denied(){return new AccessDeniedException("Canonical workflow access denied");}
    private static OptimisticLockingFailureException conflict(){return new OptimisticLockingFailureException("Student changed; reload before retrying");}
}
