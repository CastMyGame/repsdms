package com.reps.demogcloud.services.tenancy;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.security.models.UserModel;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;

/** Real-service canonical access. Never resolves a tenant by display name. */
@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="reps.tenancy.domain-preparation-enabled",havingValue="true")
public class CanonicalWorkflowAccess {
    private final CanonicalAccountContext accounts;
    private final CanonicalCoreWorkflows core;
    private final MongoTemplate mongo;
    private final Environment env;
    private final HistoricalCaseAccess historical;
    @org.springframework.beans.factory.annotation.Autowired(required=false) private CanonicalEmailOwnership emailOwnership;
    @jakarta.annotation.PostConstruct public void isolated(){if(!Arrays.asList(env.getActiveProfiles()).contains("tenant-isolated-verification"))throw new IllegalStateException("Canonical workflow integration requires isolated verification");}
    public CanonicalAccountContext.Actor provisioned(UserModel user){isolated();return accounts.requireProvisioned(user);}
    public CanonicalAccountContext.Actor actor(){isolated();var worker=emailOwnership==null?null:emailOwnership.recordedActor();return worker==null?accounts.requireCurrent():worker;}
    public String tenant(){return actor().tenantSchoolId();}
    public Query query(){return Query.query(Criteria.where("tenantSchoolId").is(tenant()));}
    public Query id(String id){return query().addCriteria(Criteria.where("_id").is(id));}
    public boolean role(String role){return actor().roles().contains(role);}
    public void staff(){if(role("STUDENT"))throw denied();}
    public void admin(){staff();if(!role("ADMIN"))throw denied();}
    public UserModel account(){var actor=actor();var user=mongo.findOne(id(actor.accountId()),UserModel.class);if(user==null)throw denied();return user;}
    public String email(){return account().getUsername();}
    public School school(){return mongo.findById(tenant(),School.class);}
    /** Label validation only: callers must still select targets by canonical ownership. */
    public void label(String label){var school=school();if(label==null || school==null || !label.equals(school.getSchoolIdNumber()) && !label.equalsIgnoreCase(school.getSchoolName()) && !label.equalsIgnoreCase(Objects.toString(account().getSchool(),"")))throw denied();}
    public boolean owns(Object value){
        if(value==null)return false;
        if(value instanceof School school)return tenant().equals(school.getSchoolIdNumber());
        var bean=new BeanWrapperImpl(value);return bean.isReadableProperty("tenantSchoolId") && tenant().equals(bean.getPropertyValue("tenantSchoolId"));
    }
    public <T>T one(String id,Class<T> type){T row=mongo.findOne(id(id),type);if(row==null)throw denied();return row;}
    public Student lookupStudent(String email){
        var rows=mongo.find(query().addCriteria(Criteria.where("studentEmail").regex(exact(email))).limit(2),Student.class);
        if(rows.size()!=1)throw denied();return rows.get(0);
    }
    public Student student(String email){return core.readStudent(lookupStudent(email).getStudentIdNumber());}
    public Employee employee(String email){
        staff();var rows=mongo.find(query().addCriteria(Criteria.where("email").regex(exact(email))).limit(2),Employee.class);
        if(rows.size()!=1)throw denied();var employee=rows.get(0);
        var users=mongo.find(query().addCriteria(Criteria.where("username").regex(exact(email))).limit(2),UserModel.class);
        if(users.size()!=1)throw denied();accounts.requireRecorded(users.get(0).getId(),tenant(),employee.getEmployeeId());return employee;
    }
    public boolean independent(String email,Student student){
        if(!owns(student) || student.isArchived())return false;
        try{
            Employee employee=employee(email);var users=mongo.find(query().addCriteria(Criteria.where("username").regex(exact(email))).limit(2),UserModel.class);
            if(users.size()!=1)return false;var who=accounts.requireRecorded(users.get(0).getId(),tenant(),employee.getEmployeeId());
            if(who.roles().contains("ADMIN"))return true;
            if(who.roles().contains("GUIDANCE") && same(email,student.getGuidanceEmail()))return true;
            return who.roles().contains("TEACHER") && employee.getClasses()!=null && employee.getClasses().stream().filter(Objects::nonNull)
                    .filter(c->c.getClassRoster()!=null).flatMap(c->c.getClassRoster().stream()).anyMatch(e->same(e,student.getStudentEmail()));
        }catch(AccessDeniedException failure){return false;}
    }
    public <T>T record(String id,Class<T> type,CanonicalCoreWorkflows.Kind kind){core.readAccessibleRecord(kind,id);return one(id,type);}
    public boolean canReadRecord(Object entity,CanonicalCoreWorkflows.Kind kind){
        if(!owns(entity))return false;
        try{core.readAccessibleRecord(kind,Objects.toString(new BeanWrapperImpl(entity).getPropertyValue("id"),""));return true;}
        catch(AccessDeniedException failure){return false;}
    }
    public void prepare(CanonicalCoreWorkflows.Kind kind,Student student){core.prepareCreations(List.of(new CanonicalCoreWorkflows.Draft(kind,student.getStudentIdNumber(),null)));CanonicalAuthorizationFence.student(mongo,student,tenant());}
    /** Live actions never inherit the immutable historical-snapshot grant. */
    public void action(Object entity, String event) {
        if (!owns(entity)) throw denied();
        var bean = new BeanWrapperImpl(entity);
        Student student = student(Objects.toString(bean.getPropertyValue("studentEmail"), ""));
        CanonicalAuthorizationFence.student(mongo,student,tenant());
        if (role("STUDENT")) {
            if (!Set.of("SUBMITTED", "COMPLETED").contains(event)) throw denied();
            return;
        }
        staff();
        if (!independent(email(), student)) throw denied();
        if (role("ADMIN")) return;
        if (entity instanceof com.reps.demogcloud.models.officeReferral.OfficeReferral
                && !Set.of("CREATED", "STATUS").contains(event)) throw denied();
        if (role("GUIDANCE") && same(email(), student.getGuidanceEmail())) return;
        boolean participant = false;
        for (String field : List.of("teacherEmail", "originalAuthorEmail", "responsibleStaffEmail")) {
            if (bean.isReadableProperty(field) && same(email(), Objects.toString(bean.getPropertyValue(field), ""))) participant = true;
        }
        if (entity instanceof com.reps.demogcloud.models.guidance.GuidanceReferral && !participant) throw denied();
        if (!participant && (student.getSpotters() == null || student.getSpotters().stream().noneMatch(e -> same(e, email())))) throw denied();
    }
    public <T>T atomic(java.util.function.Supplier<T> body){
        var hello=mongo.executeCommand(new org.bson.Document("hello",1));
        if((!hello.containsKey("setName") && !"isdbgrid".equals(hello.getString("msg"))) || !hello.containsKey("logicalSessionTimeoutMinutes"))throw new IllegalStateException("Canonical workflow writes require MongoDB transactions");
        var options=com.mongodb.TransactionOptions.builder().readConcern(com.mongodb.ReadConcern.SNAPSHOT).writeConcern(com.mongodb.WriteConcern.MAJORITY).build();
        try{return new org.springframework.transaction.support.TransactionTemplate(new org.springframework.data.mongodb.MongoTransactionManager(mongo.getMongoDatabaseFactory(),options)).execute(status->{CanonicalAuthorizationFence.actor(mongo,actor());return body.get();});}catch(RuntimeException failure){throw CanonicalAuthorizationFence.failure(failure);}
    }
    public AssignmentTemplate template(String id){
        var global=new Criteria().andOperator(Criteria.where("tenantSchoolId").exists(false),Criteria.where("createdBySystem").is(true),Criteria.where("scope").is("SYSTEM_DEFAULT"));
        var row=mongo.findOne(Query.query(new Criteria().andOperator(Criteria.where("_id").is(id),new Criteria().orOperator(Criteria.where("tenantSchoolId").is(tenant()),global))),AssignmentTemplate.class);
        if(!templateOwner(row))throw denied();return row;
    }
    public org.springframework.data.mongodb.core.aggregation.AggregationOperation studentLookup(String alias){
        String owner=tenant();return context->new org.bson.Document("$lookup",new org.bson.Document("from","students").append("let",new org.bson.Document("email","$studentEmail"))
                .append("pipeline",List.of(new org.bson.Document("$match",new org.bson.Document("tenantSchoolId",owner).append("$expr",new org.bson.Document("$eq",List.of("$studentEmail","$$email")))))).append("as",alias));
    }
    public boolean global(AssignmentTemplate template){return template.getTenantSchoolId()==null && template.isCreatedBySystem()
            && template.getScope()==AssignmentTemplate.Scope.SYSTEM_DEFAULT && empty(template.getSchoolId()) && empty(template.getCreatedByUserId());}
    public boolean templateOwner(AssignmentTemplate template){return template!=null && (global(template) || owns(template));}
    public boolean templateRead(AssignmentTemplate template){
        staff();return templateOwner(template) && (global(template) || role("ADMIN") || same(email(),template.getCreatedByUserId())
                || template.getVisibility()==AssignmentTemplate.Visibility.SCHOOL || template.getScope()==AssignmentTemplate.Scope.SCHOOL_DEFAULT);
    }
    public void templateWrite(AssignmentTemplate template){staff();if(global(template) || !owns(template) || !role("ADMIN") && !same(email(),template.getCreatedByUserId()))throw denied();}
    public void prepareTemplate(AssignmentTemplate template){
        staff();if(template.isCreatedBySystem() || template.getTenantSchoolId()!=null && !owns(template)
                || !empty(template.getCreatedByUserId()) && !same(email(),template.getCreatedByUserId()))throw denied();
        if(!empty(template.getSchoolId()))label(template.getSchoolId());
        if(template.getScope()==AssignmentTemplate.Scope.SCHOOL_DEFAULT)admin();else template.setScope(AssignmentTemplate.Scope.TEACHER_DEFAULT);
        template.setTenantSchoolId(tenant());template.setSchoolId(school().getSchoolName());template.setCreatedByUserId(email());template.setCreatedBySystem(false);
    }
    public void bindNew(Object entity){var bean=new BeanWrapperImpl(entity);Object owner=bean.getPropertyValue("tenantSchoolId");if(owner!=null && !tenant().equals(owner))throw denied();bean.setPropertyValue("tenantSchoolId",tenant());historical.captureInitial(entity);}
    public static Pattern exact(String email){if(email==null || email.isBlank())throw denied();return Pattern.compile("^"+Pattern.quote(email.trim())+"$",Pattern.CASE_INSENSITIVE);}
    private static boolean empty(String value){return value==null || value.isBlank();}
    private static boolean same(String a,String b){return a!=null && b!=null && a.equalsIgnoreCase(b);}
    private static AccessDeniedException denied(){return new AccessDeniedException("Canonical workflow access denied");}
}
