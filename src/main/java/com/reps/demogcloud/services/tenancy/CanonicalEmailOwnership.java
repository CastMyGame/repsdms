package com.reps.demogcloud.services.tenancy;

import com.reps.demogcloud.models.email.RequiredNotification;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import org.bson.Document;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.regex.Pattern;

/** Isolated request-side preparation. Worker-to-router/provider ownership remains disconnected. */
@Service @lombok.RequiredArgsConstructor
@ConditionalOnProperty(name="reps.tenancy.email-preparation-enabled",havingValue="true")
public class CanonicalEmailOwnership {
    private final CanonicalAccountContext accounts;
    private final CanonicalCoreWorkflows core;
    private final MongoTemplate mongo;
    private final Environment env;
    private record Recorded(CanonicalAccountContext.Actor actor,long credentialVersion){}
    private final ThreadLocal<Recorded> recorded = new ThreadLocal<>();
    public CanonicalAccountContext.Actor recordedActor(){
        var scope=recorded.get();if(scope==null)return null;var actor=scope.actor();
        var current=accounts.requireRecorded(actor.accountId(),actor.tenantSchoolId(),actor.profileId());
        var user=mongo.findById(actor.accountId(),UserModel.class);if(user==null || user.getCredentialVersion()!=scope.credentialVersion())throw denied();return current;
    }
    public <T>T withWorker(Object entity,RequiredNotification intent,java.util.function.Supplier<T> work){
        isolatedOnly();if(recorded.get()!=null)throw denied();
        var bean=new BeanWrapperImpl(entity);
        if(intent.getTenantSchoolId()==null || !intent.getTenantSchoolId().equals(bean.getPropertyValue("tenantSchoolId")))throw denied();
        var actor=accounts.requireRecorded(intent.getActorAccountId(),intent.getTenantSchoolId(),intent.getActorProfileId());
        var user=mongo.findOne(Query.query(Criteria.where("_id").is(actor.accountId()).and("tenantSchoolId").is(actor.tenantSchoolId())),UserModel.class);
        if(user==null || intent.getActorCredentialVersion()==null || intent.getActorCredentialVersion()!=user.getCredentialVersion()
            || !intent.isSystem() && (intent.getActor()==null || !user.getUsername().equalsIgnoreCase(intent.getActor())))throw denied();
        if(!actor.roles().contains("STUDENT")){
            var profiles=mongo.find(Query.query(Criteria.where("_id").is(actor.profileId()).and("tenantSchoolId").is(actor.tenantSchoolId())),com.reps.demogcloud.models.employee.Employee.class);
            if(profiles.size()!=1)throw denied();var staff=profiles.get(0);
            String childEmail=Objects.toString(bean.getPropertyValue("studentEmail"),"");
            var children=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("studentEmail").regex(Pattern.compile("^"+Pattern.quote(childEmail)+"$",Pattern.CASE_INSENSITIVE))).limit(2),Student.class);
            if(children.size()!=1 || children.get(0).isArchived())throw denied();var child=children.get(0);
            boolean admin=actor.roles().contains("ADMIN");
            boolean guide=actor.roles().contains("GUIDANCE") && user.getUsername().equalsIgnoreCase(child.getGuidanceEmail());
            boolean roster=actor.roles().contains("TEACHER") && staff.getClasses()!=null && staff.getClasses().stream().filter(Objects::nonNull).filter(c->c.getClassRoster()!=null).flatMap(c->c.getClassRoster().stream()).anyMatch(childEmail::equalsIgnoreCase);
            if(!admin && !guide && !roster)throw denied();
            boolean participant=false;
            for(String field:List.of("teacherEmail","originalAuthorEmail","responsibleStaffEmail"))if(bean.isReadableProperty(field) && user.getUsername().equalsIgnoreCase(Objects.toString(bean.getPropertyValue(field),"")))participant=true;
            if(!admin && !guide && !participant && (entity instanceof com.reps.demogcloud.models.guidance.GuidanceReferral || child.getSpotters()==null || child.getSpotters().stream().noneMatch(user.getUsername()::equalsIgnoreCase)))throw denied();
        }
        recorded.set(new Recorded(actor,intent.getActorCredentialVersion()));
        try {workerStudent(entity,intent);return work.get();}finally{recorded.remove();}
    }
    public Student workerStudent(Object entity,RequiredNotification intent){
        var actor=recordedActor();if(actor==null || !actor.tenantSchoolId().equals(intent.getTenantSchoolId()))throw denied();
        var bean=new BeanWrapperImpl(entity);if(!actor.tenantSchoolId().equals(bean.getPropertyValue("tenantSchoolId")))throw denied();
        String email=Objects.toString(bean.getPropertyValue("studentEmail"),"");
        var children=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("studentEmail").regex(Pattern.compile("^"+Pattern.quote(email)+"$",Pattern.CASE_INSENSITIVE))).limit(2),Student.class);
        if(children.size()!=1 || children.get(0).isArchived())throw denied();var child=children.get(0);
        var users=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("username").regex(Pattern.compile("^"+Pattern.quote(email)+"$",Pattern.CASE_INSENSITIVE))).limit(2),UserModel.class);
        if(users.size()!=1)throw denied();var student=accounts.requireProvisioned(users.get(0));
        if(!actor.tenantSchoolId().equals(student.tenantSchoolId()) || !child.getStudentIdNumber().equals(student.profileId()))throw denied();
        if(!student.roles().equals(Set.of("STUDENT")))throw denied();
        if(actor.roles().contains("STUDENT")){if(!intent.isSystem() || !actor.accountId().equals(student.accountId()) || !Set.of("SUBMITTED","COMPLETED").contains(intent.getEvent()))throw denied();}
        return child;
    }

    @jakarta.annotation.PostConstruct public void isolatedOnly(){if(!Arrays.asList(env.getActiveProfiles()).contains("tenant-isolated-verification"))throw new IllegalStateException("Canonical email preparation requires isolated verification; worker cutover is not implemented");}
    public CanonicalAccountContext.Actor actor(){isolatedOnly();var worker=recordedActor();return worker==null?accounts.requireCurrent():worker;}
    public String email(boolean staffOnly){
        var actor=actor();if(staffOnly && actor.roles().contains("STUDENT"))throw denied();
        var user=mongo.findOne(Query.query(Criteria.where("_id").is(actor.accountId()).and("tenantSchoolId").is(actor.tenantSchoolId())),UserModel.class);
        if(user==null)throw denied();return user.getUsername();
    }
    public Query grant(String username){
        var actor=actor();String current=email(true);
        if(username==null || !current.equalsIgnoreCase(username.trim()))throw denied();
        String key=current.trim().toLowerCase(Locale.ROOT);
        Query projection=Query.query(Criteria.where("_id").is(key));projection.fields().include("ownerAccountId").include("ownerProfileId").include("tenantSchoolId");
        Document existing=mongo.findOne(projection,Document.class,"oauth2_authorized_clients");
        if(existing!=null && (!actor.accountId().equals(existing.get("ownerAccountId")) || !actor.profileId().equals(existing.get("ownerProfileId")) || !actor.tenantSchoolId().equals(existing.get("tenantSchoolId"))))throw unavailable();
        // Equality fields also become server-derived metadata on an initial upsert. Existing legacy
        // grants cannot be adopted this way: their _id is already occupied and metadata was checked.
        return Query.query(Criteria.where("_id").is(key).and("ownerAccountId").is(actor.accountId()).and("ownerProfileId").is(actor.profileId()).and("tenantSchoolId").is(actor.tenantSchoolId()));
    }
    public Student caseAccess(Object entity,String event,boolean staffOnly){
        var actor=actor();if(staffOnly && actor.roles().contains("STUDENT"))throw denied();
        var bean=new BeanWrapperImpl(entity);
        if(!actor.tenantSchoolId().equals(bean.getPropertyValue("tenantSchoolId")))throw denied();
        String studentEmail=Objects.toString(bean.getPropertyValue("studentEmail"),"");
        var rows=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("studentEmail").regex(Pattern.compile("^"+Pattern.quote(studentEmail)+"$",Pattern.CASE_INSENSITIVE))).limit(2),Student.class);
        if(rows.size()!=1)throw denied();Student student="STATUS".equals(event) && actor.roles().contains("ADMIN")?rows.get(0):core.readStudent(rows.get(0).getStudentIdNumber());
        var kind=entity instanceof com.reps.demogcloud.models.punishment.Punishment?CanonicalCoreWorkflows.Kind.PUNISHMENT:
                entity instanceof com.reps.demogcloud.models.officeReferral.OfficeReferral?CanonicalCoreWorkflows.Kind.OFFICE:
                entity instanceof com.reps.demogcloud.models.guidance.GuidanceReferral?CanonicalCoreWorkflows.Kind.GUIDANCE:null;
        if(kind==null)throw denied();
        String id=Objects.toString(bean.getPropertyValue("id"),"");
        if(mongo.exists(Query.query(Criteria.where("_id").is(id)),entity.getClass())){var stored="STATUS".equals(event)?core.readAccessibleRecord(kind,id):core.readRecord(kind,id);if(!student.getStudentEmail().equalsIgnoreCase(stored.getString("studentEmail")))throw denied();}
        else core.prepareCreations(List.of(new CanonicalCoreWorkflows.Draft(kind,student.getStudentIdNumber(),null)));
        if(actor.roles().contains("STUDENT") && !Set.of("SUBMITTED","COMPLETED").contains(event))throw denied();
        return student;
    }
    public Query caseQuery(String id){return Query.query(Criteria.where("_id").is(id).and("tenantSchoolId").is(actor().tenantSchoolId()));}
    public void bind(RequiredNotification intent){var actor=actor();intent.setTenantSchoolId(actor.tenantSchoolId());intent.setActorAccountId(actor.accountId());intent.setActorProfileId(actor.profileId());var user=mongo.findById(actor.accountId(),UserModel.class);if(user==null)throw denied();intent.setActorCredentialVersion(user.getCredentialVersion());}
    public void requireIntent(RequiredNotification intent){
        var actor=actor();if(!actor.tenantSchoolId().equals(intent.getTenantSchoolId()) || intent.getActorAccountId()==null || intent.getActorProfileId()==null)throw unavailable();
        accounts.requireRecorded(intent.getActorAccountId(),intent.getTenantSchoolId(),intent.getActorProfileId());
    }
    public void requireAdmin(){if(!actor().roles().contains("ADMIN"))throw denied();}
    public void disconnectedWorker(){throw unavailable();}
    private static AccessDeniedException denied(){return new AccessDeniedException("Canonical communication access denied");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Canonical communication ownership requires reviewed preparation");}
}
