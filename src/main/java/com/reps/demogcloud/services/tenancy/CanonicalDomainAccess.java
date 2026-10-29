package com.reps.demogcloud.services.tenancy;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.employee.ClassRequest;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.guidance.GuidanceReferral;
import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent;
import com.reps.demogcloud.security.models.UserModel;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.*;
import java.util.function.Supplier;
import java.util.regex.Pattern;

/** Explicitly disabled real-service integration. No currency receipts, providers or ID-only route cutover. */
@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="reps.tenancy.domain-preparation-enabled",havingValue="true")
public class CanonicalDomainAccess {
    private final CanonicalAccountContext context;
    private final CanonicalCoreWorkflows core;
    private final MongoTemplate mongo;
    private final org.springframework.core.env.Environment environment;
    @jakarta.annotation.PostConstruct public void requireIsolatedVerificationProfile(){
        if(!Arrays.asList(environment.getActiveProfiles()).contains("tenant-isolated-verification"))
            throw new IllegalStateException("Canonical domain preparation is restricted to the isolated verification profile until coordinated cutover");
    }
    @org.springframework.beans.factory.annotation.Autowired(required=false) private HistoricalCaseAccess historical;
    public void ownNew(Object entity){new org.springframework.beans.BeanWrapperImpl(entity).setPropertyValue("tenantSchoolId",actor().tenantSchoolId());if(historical!=null)historical.captureInitial(entity);}
    public CanonicalAccountContext.Actor actor(){return context.requireCurrent();}
    public String email(boolean staff){var actor=actor();if(staff && actor.roles().contains("STUDENT"))throw denied();
        var account=mongo.findOne(owned(actor.accountId(),actor),UserModel.class);if(account==null)throw denied();return account.getUsername();}
    public Student student(String email,boolean staff){
        var actor=actor();if(staff && actor.roles().contains("STUDENT"))throw denied();
        var matches=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("studentEmail").regex(exact(email))).limit(2),Student.class);
        if(matches.size()!=1)throw denied();var student=core.readStudent(matches.get(0).getStudentIdNumber());CanonicalAuthorizationFence.student(mongo,student,actor.tenantSchoolId());return student;
    }
    public Student studentId(String id){if(actor().roles().contains("STUDENT"))throw denied();return core.readStudent(id);}
    public Employee roster(String email,ClassRequest request){
        var actor=actor();var teachers=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("email").regex(exact(email))).limit(2),Employee.class);
        if(teachers.size()!=1 || request==null || request.getClassToUpdate()==null)throw denied();
        var ids=new ArrayList<String>();var emails=request.getClassToUpdate().getClassRoster();
        if(emails!=null)for(String item:emails){var rows=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("studentEmail").regex(exact(item))).limit(2),Student.class);if(rows.size()!=1)throw denied();ids.add(rows.get(0).getStudentIdNumber());}
        core.validateRoster(teachers.get(0).getEmployeeId(),ids);
        for(String id:ids)CanonicalAuthorizationFence.student(mongo,mongo.findOne(owned(id,actor),Student.class),actor.tenantSchoolId());
        var teacherAccounts=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("username").regex(exact(teachers.get(0).getEmail()))).limit(2),UserModel.class);
        if(teacherAccounts.size()!=1)throw denied();
        if(org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive() && mongo.updateFirst(owned(teacherAccounts.get(0).getId(),actor).addCriteria(Criteria.where("enabled").ne(false)),new Update().inc("authorizationFence",1L),UserModel.class).getMatchedCount()!=1)throw denied();
        return teachers.get(0);
    }
    public GuidanceReferral guidance(String id,boolean manage){
        Document raw=core.readRecord(CanonicalCoreWorkflows.Kind.GUIDANCE,id);
        var record=mongo.getConverter().read(GuidanceReferral.class,raw);record.setPersisted(true);
        if(manage){var actor=actor();var student=student(record.getStudentEmail(),true);
            if(!actor.roles().contains("ADMIN") && (!actor.roles().contains("GUIDANCE") || !same(email(true),student.getGuidanceEmail())))throw denied();}
        return record;
    }
    public List<GuidanceReferral> guidanceList(String status){
        if(actor().roles().contains("STUDENT"))throw denied();
        var query=Query.query(Criteria.where("tenantSchoolId").is(actor().tenantSchoolId()).and("archived").ne(true));
        if(status!=null)query.addCriteria(Criteria.where("status").is(status));
        var result=new ArrayList<GuidanceReferral>();for(var row:mongo.find(query,GuidanceReferral.class))try{result.add(guidance(row.getGuidanceId(),false));}catch(AccessDeniedException unauthorized){}return result;
    }
    public CanonicalCoreWorkflows.CreationOwner prepareGuidance(Student student){return core.prepareCreations(List.of(new CanonicalCoreWorkflows.Draft(CanonicalCoreWorkflows.Kind.GUIDANCE,student.getStudentIdNumber(),null))).get(0);}
    public Document linkedPunishment(String id){return core.readRecord(CanonicalCoreWorkflows.Kind.PUNISHMENT,id);}
    public List<TrackedBehaviorEvent> behaviorHistory(List<String> emails){
        var selected=new ArrayList<String>();for(String email:emails)selected.add(student(email,false).getStudentEmail());
        return mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor().tenantSchoolId()).and("studentEmail").in(selected))
                .with(org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"timeCreated")),TrackedBehaviorEvent.class);
    }
    public <T>T transaction(Supplier<T> work){
        Document hello=mongo.executeCommand(new Document("hello",1));
        if((!hello.containsKey("setName") && !"isdbgrid".equals(hello.getString("msg"))) || !hello.containsKey("logicalSessionTimeoutMinutes"))throw unavailable();
        var options=com.mongodb.TransactionOptions.builder().readConcern(com.mongodb.ReadConcern.SNAPSHOT).writeConcern(com.mongodb.WriteConcern.MAJORITY).build();
        try{return new TransactionTemplate(new MongoTransactionManager(mongo.getMongoDatabaseFactory(),options)).execute(s->{CanonicalAuthorizationFence.actor(mongo,actor());return work.get();});}catch(RuntimeException failure){throw CanonicalAuthorizationFence.failure(failure);}
    }
    public void disconnected(){throw unavailable();}
    private static ResponseStatusException unavailable(){return new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Canonical workflow dependency is not ready");}
    private static Query owned(String id,CanonicalAccountContext.Actor actor){return Query.query(Criteria.where("_id").is(id).and("tenantSchoolId").is(actor.tenantSchoolId()));}
    private static Pattern exact(String email){if(email==null || email.isBlank())throw denied();return Pattern.compile("^"+Pattern.quote(email.trim())+"$",Pattern.CASE_INSENSITIVE);}
    private static boolean same(String a,String b){return a!=null && b!=null && a.equalsIgnoreCase(b);}
    private static AccessDeniedException denied(){return new AccessDeniedException("Canonical domain access denied");}
}
