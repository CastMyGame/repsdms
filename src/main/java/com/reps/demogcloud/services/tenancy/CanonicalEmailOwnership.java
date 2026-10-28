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
    @jakarta.annotation.PostConstruct public void isolatedOnly(){if(!Arrays.asList(env.getActiveProfiles()).contains("tenant-isolated-verification"))throw new IllegalStateException("Canonical email preparation requires isolated verification; worker cutover is not implemented");}
    public CanonicalAccountContext.Actor actor(){isolatedOnly();return accounts.requireCurrent();}
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
        if(rows.size()!=1)throw denied();Student student=core.readStudent(rows.get(0).getStudentIdNumber());
        var kind=entity instanceof com.reps.demogcloud.models.punishment.Punishment?CanonicalCoreWorkflows.Kind.PUNISHMENT:
                entity instanceof com.reps.demogcloud.models.officeReferral.OfficeReferral?CanonicalCoreWorkflows.Kind.OFFICE:
                entity instanceof com.reps.demogcloud.models.guidance.GuidanceReferral?CanonicalCoreWorkflows.Kind.GUIDANCE:null;
        if(kind==null)throw denied();
        String id=Objects.toString(bean.getPropertyValue("id"),"");
        if(mongo.exists(Query.query(Criteria.where("_id").is(id)),entity.getClass()))core.readRecord(kind,id);
        else core.prepareCreations(List.of(new CanonicalCoreWorkflows.Draft(kind,student.getStudentIdNumber(),null)));
        if(actor.roles().contains("STUDENT") && !Set.of("SUBMITTED","COMPLETED").contains(event))throw denied();
        return student;
    }
    public Query caseQuery(String id){return Query.query(Criteria.where("_id").is(id).and("tenantSchoolId").is(actor().tenantSchoolId()));}
    public void bind(RequiredNotification intent){var actor=actor();intent.setTenantSchoolId(actor.tenantSchoolId());intent.setActorAccountId(actor.accountId());intent.setActorProfileId(actor.profileId());}
    public void requireIntent(RequiredNotification intent){
        var actor=actor();if(!actor.tenantSchoolId().equals(intent.getTenantSchoolId()) || intent.getActorAccountId()==null || intent.getActorProfileId()==null)throw unavailable();
        accounts.requireRecorded(intent.getActorAccountId(),intent.getTenantSchoolId(),intent.getActorProfileId());
    }
    public void requireAdmin(){if(!actor().roles().contains("ADMIN"))throw denied();}
    public void disconnectedWorker(){throw unavailable();}
    private static AccessDeniedException denied(){return new AccessDeniedException("Canonical communication access denied");}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Canonical communication ownership requires reviewed preparation");}
}
