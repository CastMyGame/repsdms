package com.reps.demogcloud.services.tenancy;

import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.UserModel;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.*;

/** Immutable, explicitly scoped historical facts; never returns a mutable case or live student data. */
@Service @RequiredArgsConstructor
@ConditionalOnProperty(name="reps.tenancy.domain-preparation-enabled",havingValue="true")
public class HistoricalCaseAccess {
    private static final Map<String,String> COLLECTIONS=Map.of("punishment","Punishments","office","OfficeReferrals","guidance","Guidance");
    private final CanonicalAccountContext accounts;
    private final MongoTemplate mongo;
    private final Environment env;
    @jakarta.annotation.PostConstruct public void isolated(){if(!Arrays.asList(env.getActiveProfiles()).contains("tenant-isolated-verification"))throw new IllegalStateException("Historical tenant access requires isolated verification");}
    public record View(String caseId,String kind,String capturedAt,String timeCreated,String studentName,String infraction,List<String> initialDescription){}
    public List<View> list(){
        isolated();var actor=accounts.requireCurrent();if(actor.roles().contains("STUDENT"))throw denied();var result=new ArrayList<View>();
        for(var entry:COLLECTIONS.entrySet())for(var row:mongo.find(query(actor).limit(501),Document.class,entry.getValue())){
            if(result.size()>=500)throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.CONFLICT,"Historical scope exceeds the reviewed result limit");
            result.add(view(row,entry.getKey(),actor));
        }
        return result;
    }
    public View get(String kind,String id){
        isolated();var actor=accounts.requireCurrent();if(actor.roles().contains("STUDENT") || !COLLECTIONS.containsKey(kind))throw denied();
        var row=mongo.findOne(query(actor).addCriteria(Criteria.where("_id").is(id)),Document.class,COLLECTIONS.get(kind));
        if(row==null)throw denied();return view(row,kind,actor);
    }
    private Query query(CanonicalAccountContext.Actor actor){
        var query=Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("historicalAccess").elemMatch(
                Criteria.where("accountId").is(actor.accountId()).and("profileId").is(actor.profileId()).and("tenantSchoolId").is(actor.tenantSchoolId())));
        query.fields().include("tenantSchoolId").include("historicalAccess");return query;
    }
    private View view(Document row,String kind,CanonicalAccountContext.Actor actor){
        try{return validatedView(row,kind,actor);}catch(RuntimeException invalid){throw denied();}
    }
    private View validatedView(Document row,String kind,CanonicalAccountContext.Actor actor){
        if(!actor.tenantSchoolId().equals(row.get("tenantSchoolId")) || !(row.get("historicalAccess") instanceof List<?> entries))throw denied();
        var matches=entries.stream().filter(Document.class::isInstance).map(Document.class::cast)
                .filter(e->actor.accountId().equals(e.get("accountId")) && actor.profileId().equals(e.get("profileId"))).toList();
        if(matches.size()!=1)throw denied();Document entry=matches.get(0);
        if(!Integer.valueOf(1).equals(entry.get("formatVersion")) || !actor.tenantSchoolId().equals(entry.get("tenantSchoolId"))
                || !Set.of("AUTHOR","ASSIGNEE").contains(entry.getString("basis")) || !Objects.equals(row.get("_id"),entry.get("caseId"))
                || !kind.equals(entry.get("kind")) || !(entry.get("capturedAt") instanceof Date when))throw denied();
        Object descriptions=entry.get("initialDescription");if(!(descriptions instanceof List<?> list) || list.size()>20 || list.stream().anyMatch(v->!(v instanceof String s) || s.length()>10000))throw denied();
        return new View(row.getString("_id"),kind,when.toInstant().toString(),entry.getString("timeCreated"),entry.getString("studentName"),entry.getString("infraction"),list.stream().map(String.class::cast).toList());
    }
    /** Called only on fresh server-generated cases after ordinary creation authorization. No legacy adoption. */
    public void captureInitial(Object entity){
        isolated();var actor=accounts.requireCurrent();var bean=new BeanWrapperImpl(entity);if(!bean.isReadableProperty("historicalAccess"))return;
        if(!actor.tenantSchoolId().equals(bean.getPropertyValue("tenantSchoolId")))throw denied();
        Object prior=bean.getPropertyValue("historicalAccess");if(prior instanceof List<?> list && !list.isEmpty())throw denied();
        String id=Objects.toString(bean.getPropertyValue("id"),"");if(id.isBlank() || mongo.exists(Query.query(Criteria.where("_id").is(id)),entity.getClass()))throw denied();
        String email=Objects.toString(bean.getPropertyValue("studentEmail"),"");
        var students=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("studentEmail").regex(CanonicalWorkflowAccess.exact(email))).limit(2),Student.class);
        if(students.size()!=1)throw denied();Student student=students.get(0);
        String kind=entity instanceof com.reps.demogcloud.models.punishment.Punishment?"punishment":entity instanceof com.reps.demogcloud.models.officeReferral.OfficeReferral?"office":"guidance";
        Object descriptions=bean.getPropertyValue(kind.equals("punishment")?"infractionDescription":"referralDescription");
        if(!(descriptions instanceof List<?> values) || values.size()>20 || values.stream().anyMatch(v->!(v instanceof String text) || text.length()>10000))throw new IllegalArgumentException("A bounded initial case description is required");
        List<String> initial=values.stream().map(String.class::cast).toList();
        String infraction=bean.isReadableProperty("infractionName")?Objects.toString(bean.getPropertyValue("infractionName"),""):"Office referral";
        var entries=new ArrayList<Document>();
        if(!actor.roles().contains("STUDENT"))entries.add(entry(actor,"AUTHOR",id,kind,student,infraction,initial,bean));
        String assigned=kind.equals("office")?student.getAdminEmail():kind.equals("guidance")?student.getGuidanceEmail():null;
        if(assigned!=null && !assigned.isBlank()){
            var users=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("username").regex(CanonicalWorkflowAccess.exact(assigned))).limit(2),UserModel.class);
            var profiles=mongo.find(Query.query(Criteria.where("tenantSchoolId").is(actor.tenantSchoolId()).and("email").regex(CanonicalWorkflowAccess.exact(assigned))).limit(2),Employee.class);
            if(users.size()!=1 || profiles.size()!=1)throw denied();var assignee=accounts.requireRecorded(users.get(0).getId(),actor.tenantSchoolId(),profiles.get(0).getEmployeeId());
            if(!assignee.roles().contains(kind.equals("office")?"ADMIN":"GUIDANCE"))throw denied();
            if(entries.stream().noneMatch(e->assignee.accountId().equals(e.get("accountId"))))entries.add(entry(assignee,"ASSIGNEE",id,kind,student,infraction,initial,bean));
        }
        bean.setPropertyValue("historicalAccess",entries);
    }
    private Document entry(CanonicalAccountContext.Actor actor,String basis,String id,String kind,Student student,String infraction,List<String> descriptions,BeanWrapperImpl bean){
        return new Document("formatVersion",1).append("accountId",actor.accountId()).append("profileId",actor.profileId()).append("tenantSchoolId",actor.tenantSchoolId())
                .append("basis",basis).append("caseId",id).append("kind",kind).append("capturedAt",Date.from(Instant.now()))
                .append("timeCreated",Objects.toString(bean.getPropertyValue("timeCreated"),"")).append("studentName",Objects.toString(student.getFirstName(),"")+" "+Objects.toString(student.getLastName(),""))
                .append("infraction",infraction).append("initialDescription",descriptions);
    }
    private static AccessDeniedException denied(){return new AccessDeniedException("Historical case access denied");}
}
