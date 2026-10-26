package com.reps.demogcloud.services.email;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.email.RequiredNotification;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.guidance.GuidanceReferral;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.UserContextService;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.*;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import org.springframework.mail.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;

/** Embedded Mongo obligations: no second write/queue, no provider calls on an action's request thread. */
@Service
@lombok.RequiredArgsConstructor
public class RequiredNotificationService {
    private final MongoTemplate mongo;
    private final Environment env;
    private final StudentRepository students;
    private final UserContextService users;
    private final EmailRoutingService router;
    private final EmailTemplateBuilderService templates;
    private static final Map<String,Class<?>> TYPES = Map.of("punishment",Punishment.class,"office",OfficeReferral.class,"guidance",GuidanceReferral.class);
    private static final Map<String,String> COLLECTIONS = Map.of("punishment","Punishments","office","OfficeReferrals","guidance","Guidance");

    public boolean isEnabled() {
        return Boolean.parseBoolean(env.getProperty("reps.email.notifications.enabled","false")) && router.isPhase1RoutingEnabled();
    }
    private String kind(Object entity) {
        if(entity instanceof Punishment) return "punishment"; if(entity instanceof OfficeReferral) return "office";
        if(entity instanceof GuidanceReferral) return "guidance"; throw new IllegalArgumentException("Unsupported case type");
    }
    private String text(Object entity,String property) { return Objects.toString(new BeanWrapperImpl(entity).getPropertyValue(property),""); }
    private void set(Object entity,String property,Object value) { new BeanWrapperImpl(entity).setPropertyValue(property,value); }
    @SuppressWarnings("unchecked") private List<RequiredNotification> intents(Object entity) {
        Object value=new BeanWrapperImpl(entity).getPropertyValue("requiredNotifications");
        if(value==null) { value=new ArrayList<RequiredNotification>(); set(entity,"requiredNotifications",value); }
        return (List<RequiredNotification>)value;
    }
    public String actionId() {
        var attributes=RequestContextHolder.getRequestAttributes();
        String value=attributes instanceof ServletRequestAttributes request ? request.getRequest().getHeader("X-REPS-Action-Id") : null;
        if(value==null || !value.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("A stable X-REPS-Action-Id UUID is required");
        try { return UUID.fromString(value).toString(); } catch(Exception ex) { throw new IllegalArgumentException("Invalid action id"); }
    }
    private String stable(String value) { return UUID.nameUUIDFromBytes(value.getBytes(StandardCharsets.UTF_8)).toString(); }
    public String creationId(String kind,String studentEmail,Object item) {
        String actor=users.getCurrentStaffEmail();
        try {
            String content=new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(item);
            return stable(kind+":"+actor.toLowerCase(Locale.ROOT)+":"+studentEmail.toLowerCase(Locale.ROOT)+":"+actionId()+":"+content);
        } catch(com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid action item"); }
    }
    public <T> T existingCreation(String id,Class<T> type) {
        T existing=mongo.findById(id,type); if(existing!=null) requireActionAccess(existing,"CREATED"); return existing;
    }
    public PunishmentResponse response(Punishment punishment) {
        var response=new PunishmentResponse(); response.setPunishment(punishment);
        response.setStudentToEmail(punishment.getStudentEmail());
        response.setTeacherToEmail(punishment.getOriginalAuthorEmail()==null?punishment.getTeacherEmail():punishment.getOriginalAuthorEmail());
        response.setSubject(punishment.getConversationId()!=null && punishment.getConversationId().length()>=8
                ? "REPS case "+punishment.getConversationId().substring(0,8)+" update" : "REPS case update");
        try {
            Student student=students.findByStudentEmailIgnoreCase(punishment.getStudentEmail());
            if(student!=null && Objects.equals(student.getSchool(),punishment.getSchool())) response.setParentToEmail(student.getParentEmail());
        } catch(org.springframework.dao.DataAccessException ignored) { /* Optional display metadata cannot invalidate the accepted action. */ }
        response.setMessage("Action saved. Required email status is tracked separately."); return response;
    }
    public boolean prepare(Object entity,String event) { return prepare(entity,event,actionId(),null); }
    public String currentStaff() { return users.getCurrentStaffEmail(); }
    public void requireActionAccess(Object entity,String event) {
        Student student=students.findByStudentEmailIgnoreCase(text(entity,"studentEmail"));
        if(student==null || !Objects.equals(student.getSchool(),text(entity,"school"))) throw new AccessDeniedException("Case school mismatch");
        if(users.isCurrentUserStudent()) {
            if(!Set.of("SUBMITTED","COMPLETED").contains(event) || !student.getStudentEmail().equalsIgnoreCase(users.getCurrentUserEmail()))
                throw new AccessDeniedException("Students may submit only their own work");
        } else authorizedViewer(entity);
    }
    public boolean hasAction(Object entity,String event) {
        requireActionAccess(entity,event); String action=actionId();
        return intents(entity).stream().anyMatch(n->event.equals(n.getEvent()) && action.equals(n.getActionId()));
    }
    public Punishment previousSubmission(String studentEmail,String infractionName) {
        Query query=Query.query(Criteria.where("studentEmail").is(studentEmail).and("infractionName").is(infractionName)
                .and("requiredNotifications").elemMatch(Criteria.where("actionId").is(actionId()).and("event").in("SUBMITTED","COMPLETED")));
        Punishment previous=mongo.findOne(query,Punishment.class);
        if(previous!=null) requireActionAccess(previous,"COMPLETED"); return previous;
    }
    public boolean prepare(Object entity,String event,String action,String body) {
        return prepareInternal(entity,event,action,body,false);
    }
    private boolean prepareInternal(Object entity,String event,String action,String body,boolean currency) {
        if(!currency && !isEnabled()) return true;
        String author=text(entity,"originalAuthorEmail"); if(author.isBlank()) author=text(entity,"teacherEmail");
        Student student=students.findByStudentEmailIgnoreCase(text(entity,"studentEmail"));
        if(student==null || !Objects.equals(student.getSchool(),text(entity,"school"))) throw new AccessDeniedException("Case school and student must match");
        String owner=text(entity,"responsibleStaffEmail");
        if(owner.isBlank()) owner=entity instanceof OfficeReferral ? student.getAdminEmail()
                : entity instanceof GuidanceReferral ? text(entity,"guidanceEmail") : author;
        if(owner==null || owner.isBlank()) owner=author;
        boolean background=event.startsWith("REMINDER_");
        boolean system=background || users.isCurrentUserStudent(); String actor=null;
        if(system) {
            if(!background && (!Set.of("SUBMITTED","COMPLETED").contains(event) || !student.getStudentEmail().equalsIgnoreCase(users.getCurrentUserEmail())))
                throw new AccessDeniedException("Students may submit only their own work");
        } else {
            actor=users.getCurrentStaffEmail(); authorize(actor,author,owner,student);
        }
        String id=stable(kind(entity)+":"+text(entity,"id")+":"+event+":"+action);
        if(intents(entity).stream().anyMatch(n->id.equals(n.getNotificationId()))) return false;
        set(entity,"originalAuthorEmail",author); set(entity,"responsibleStaffEmail",owner);
        if(text(entity,"conversationId").isBlank()) set(entity,"conversationId",stable(kind(entity)+":"+text(entity,"id")));
        var intent=new RequiredNotification(); intent.setNotificationId(id); intent.setActionId(action); intent.setEvent(event);
        intent.setActor(actor); intent.setSystem(system); intent.setBody(body); intent.setRfcMessageId("<"+id+"@notifications.repsdiscipline.com>");
        intents(entity).add(intent); return true;
    }
    /** Currency recognitions always retain their obligation; dispatch still requires all existing email gates. */
    public Object saveCurrencyCreated(Object entity) {
        prepareInternal(entity,"CREATED",actionId(),null,true);
        captureContent(entity);
        return mongo.insert(entity);
    }
    public Object saveCreated(Object entity) {
        prepare(entity,"CREATED");
        captureContent(entity);
        try { return mongo.insert(entity); }
        catch(org.springframework.dao.DuplicateKeyException duplicate) {
            Object existing=existingCreation(text(entity,"id"),entity.getClass());
            if(existing!=null) return existing; throw duplicate;
        }
    }
    public void reminder(Punishment punishment,String type) {
        if(!Set.of("ISS","DETENTION").contains(type)) throw new IllegalArgumentException("Unsupported reminder");
        if(prepare(punishment,"REMINDER_"+type,"REMINDER_"+type+":"+LocalDate.now(),null)) { captureContent(punishment); mongo.save(punishment); }
    }
    public void captureContent(Object entity) {
        List<RequiredNotification> list=intents(entity); if(list.isEmpty()) return;
        var wrapper=new BeanWrapperImpl(entity); Map<String,String> content=new LinkedHashMap<>();
        for(String field:List.of("infractionName","infractionLevel","status","archivedExplanation"))
            if(wrapper.isReadableProperty(field)) content.put(field,Objects.toString(wrapper.getPropertyValue(field),""));
        String descriptions=entity instanceof Punishment?"infractionDescription":"referralDescription";
        Object value=wrapper.getPropertyValue(descriptions);
        if(value instanceof List<?> entries && !entries.isEmpty()) content.put("description",Objects.toString(entries.get(0),""));
        if(wrapper.isReadableProperty("answerHistory") && wrapper.getPropertyValue("answerHistory") instanceof Map<?,?> history && !history.isEmpty())
            content.put("feedback",history.entrySet().stream().max(java.util.Comparator.comparing(entry->((Date)entry.getKey())))
                    .map(entry->Objects.toString(entry.getValue(),"")).orElse(""));
        if(entity instanceof OfficeReferral office && office.getReferralCode()!=null) content.put("infractionName",Objects.toString(office.getReferralCode().getCodeName(),"School referral"));
        if(entity instanceof Punishment punishment && punishment.getAssignmentWork()!=null
                && punishment.getAssignmentWork().getFeedback()!=null)
            content.put("feedback",punishment.getAssignmentWork().getFeedback());
        if(entity instanceof Punishment p && p.getAssignmentWork()!=null) content.put("assignmentState",p.getAssignmentWork().getState());
        if(entity instanceof OfficeReferral && "CLOSED".equals(list.get(list.size()-1).getEvent()) && value instanceof List<?> entries && entries.size()>1)
            content.put("closureComment",Objects.toString(entries.get(entries.size()-1),""));
        list.get(list.size()-1).setContent(content);
    }
    private String render(Object entity,RequiredNotification notification,Student student) {
        if(notification.getBody()!=null) return notification.getBody(); // Persisted guidance resource HTML is constructed/escaped server-side.
        String contact=notification.getActor()==null?text(entity,"originalAuthorEmail"):notification.getActor();
        return templates.buildCaseMessage(kind(entity),notification.getEvent(),
                notification.getContent()==null?Map.of():notification.getContent(),
                Objects.toString(student.getFirstName(),""),Objects.toString(student.getLastName(),""),contact,student.getPreferredLanguage());
    }
    private void authorize(String actor,String author,String owner,Student student) {
        users.requireStaffInStudentSchool(actor,student);
        // Explicit actor validation also works off the HTTP thread; never impersonate a login in SecurityContext.
        users.requireRecordedCaseCommunicationAccess(actor,author,owner,student);
    }
    private Object load(String kind,String id) {
        Class<?> type=TYPES.get(kind); if(type==null) throw new IllegalArgumentException("Unsupported case type");
        Object entity=mongo.findById(id,type); if(entity==null) throw new com.reps.demogcloud.exceptions.ResourceNotFoundException("Case not found"); return entity;
    }
    private Student authorizedViewer(Object entity) {
        Student student=students.findByStudentEmailIgnoreCase(text(entity,"studentEmail"));
        if(student==null || !Objects.equals(student.getSchool(),text(entity,"school"))) throw new AccessDeniedException("Case school mismatch");
        authorize(users.getCurrentStaffEmail(),text(entity,"originalAuthorEmail").isBlank()?text(entity,"teacherEmail"):text(entity,"originalAuthorEmail"),
                text(entity,"responsibleStaffEmail"),student); return student;
    }
    public Map<String,Object> statuses(String kind,String id) {
        if(!isEnabled()) return Map.of("available",false,"notifications",List.of());
        Object entity=load(kind,id); Student student=authorizedViewer(entity);
        List<Map<String,Object>> rows=new ArrayList<>();
        for(var n:intents(entity)) {
            Map<String,Object> row=new LinkedHashMap<>(); row.put("id",n.getNotificationId()); row.put("event",n.getEvent()); row.put("state",n.getState());
            row.put("actor",Objects.toString(n.getActor(),"SYSTEM")); row.put("attempts",n.getAttempts()); row.put("retrySafe",n.isRetrySafe());
            row.put("reason",Objects.toString(n.getReason(),"")); row.put("recipientOutcomes",n.getRecipientOutcomes()==null?Map.of():n.getRecipientOutcomes()); rows.add(row);
            row.put("provider",Objects.toString(n.getProvider(),"")); row.put("providerMessageId",Objects.toString(n.getProviderMessageId(),""));
            row.put("rfcMessageId",Objects.toString(n.getRfcMessageId(),"")); row.put("gmailThreadId",Objects.toString(n.getGmailThreadId(),""));
        }
        return Map.of("available",true,"caseId",text(entity,"id"),"kind",kind,"conversationId",text(entity,"conversationId"),
                "studentName",Objects.toString(student.getFirstName(),"")+" "+Objects.toString(student.getLastName(),""),"notifications",rows);
    }
    public Map<String,Object> listStatuses() {
        if(!isEnabled()) return Map.of("available",false,"cases",List.of());
        String school=users.getCurrentUserSchool(); users.getCurrentStaffEmail(); List<Map<String,Object>> cases=new ArrayList<>();
        for(var entry:TYPES.entrySet()) {
            Query query=Query.query(Criteria.where("school").is(school).and("requiredNotifications.0").exists(true));
            for(Object entity:mongo.find(query,entry.getValue())) {
                try { cases.add(statuses(entry.getKey(),text(entity,"id"))); } catch(AccessDeniedException ignored) { }
            }
        }
        return Map.of("available",true,"cases",cases);
    }
    public void retry(String kind,String id,String notificationId) {
        requireEnabled(); Object entity=load(kind,id); authorizedViewer(entity);
        var n=intents(entity).stream().filter(value->notificationId.equals(value.getNotificationId())).findFirst().orElseThrow();
        if(!n.isRetrySafe() || !Set.of("FAILED","RECONNECT_REQUIRED").contains(n.getState()))
            throw new IllegalStateException("This outcome cannot be retried safely; request manual resolution");
        update(kind,id,n.getNotificationId(),n.getState(),n.getAttemptId(),new Update().set("requiredNotifications.$.state","PENDING")
                .set("requiredNotifications.$.retrySafe",false).set("requiredNotifications.$.reason","STAFF_RETRY"));
    }
    public void resolve(String kind,String id,String notificationId,String note) {
        requireEnabled(); Object entity=load(kind,id); Student student=authorizedViewer(entity);
        users.requireAdministratorInSchool(student.getSchool());
        if(note==null || note.isBlank() || note.length()>500) throw new IllegalArgumentException("A resolution explanation is required (maximum 500 characters)");
        var n=intents(entity).stream().filter(value->notificationId.equals(value.getNotificationId())).findFirst().orElseThrow();
        if(!Set.of("UNCERTAIN","FAILED","RECONNECT_REQUIRED").contains(n.getState())) throw new IllegalStateException("Only unresolved outcomes may be resolved");
        update(kind,id,n.getNotificationId(),n.getState(),n.getAttemptId(),new Update().set("requiredNotifications.$.state","RESOLVED")
                .set("requiredNotifications.$.resolvedBy",users.getCurrentStaffEmail()).set("requiredNotifications.$.resolution",note));
    }
    private void requireEnabled() { if(!isEnabled()) throw new IllegalStateException("Managed notifications are disabled"); }
    public void deleteLegacyCase(String kind,String id) {
        Object entity=load(kind,id); authorizedViewer(entity);
        if(!intents(entity).isEmpty()) throw new IllegalStateException("Cases with retained notification obligations must be archived, not deleted");
        Object revision=new BeanWrapperImpl(entity).getPropertyValue("revision");
        Query query=Query.query(Criteria.where("_id").is(id).and("revision").is(revision).and("requiredNotifications.0").exists(false));
        if(mongo.remove(query,COLLECTIONS.get(kind)).getDeletedCount()!=1)
            throw new IllegalStateException("Case changed; reload before deleting");
    }
    private Query notificationQuery(String caseId,String id,String state,String attempt) {
        Criteria match=Criteria.where("notificationId").is(id).and("state").is(state);
        if(attempt!=null) match=match.and("attemptId").is(attempt);
        return Query.query(Criteria.where("_id").is(caseId).and("requiredNotifications").elemMatch(match));
    }
    private void update(String kind,String caseId,String id,String state,String attempt,Update update) {
        var result=mongo.updateFirst(notificationQuery(caseId,id,state,attempt),update.inc("revision",1),COLLECTIONS.get(kind));
        if(result.getMatchedCount()!=1) throw new IllegalStateException("Notification changed; reload its status");
    }
    /** No lease expiry can prove that a provider did not accept a message. */
    @Scheduled(fixedDelayString="${reps.email.notifications.poll-ms:30000}")
    public void dispatchPending() {
        if(!isEnabled() || !Boolean.parseBoolean(env.getProperty("reps.email.notifications.dispatch-enabled","false"))) return;
        for(String kind:TYPES.keySet()) {
            Query query=Query.query(Criteria.where("requiredNotifications.state").in("PENDING","DISPATCHING")).limit(25);
            for(Object entity:mongo.find(query,TYPES.get(kind))) {
                for(var intent:List.copyOf(intents(entity))) {
                    if("DISPATCHING".equals(intent.getState())) {
                        if(intent.getClaimedAt()!=null && intent.getClaimedAt().isBefore(Instant.now().minusSeconds(300))) {
                            try { update(kind,text(entity,"id"),intent.getNotificationId(),"DISPATCHING",intent.getAttemptId(),new Update()
                                    .set("requiredNotifications.$.state","UNCERTAIN").set("requiredNotifications.$.retrySafe",false)
                                    .set("requiredNotifications.$.reason","WORKER_INTERRUPTED")); } catch(IllegalStateException ignored) { }
                        }
                    } else if("PENDING".equals(intent.getState())) dispatchOne(kind,text(entity,"id"),intent.getNotificationId());
                }
            }
        }
    }
    public void dispatchOne(String kind,String caseId,String notificationId) {
        requireEnabled();
        if(!Boolean.parseBoolean(env.getProperty("reps.email.notifications.dispatch-enabled","false"))) return;
        String attempt=UUID.randomUUID().toString();
        Update claim=new Update().set("requiredNotifications.$.state","DISPATCHING").set("requiredNotifications.$.attemptId",attempt)
                .set("requiredNotifications.$.claimedAt",Date.from(Instant.now())).inc("requiredNotifications.$.attempts",1).inc("revision",1);
        Object claimed=mongo.findAndModify(notificationQuery(caseId,notificationId,"PENDING",null),claim,
                FindAndModifyOptions.options().returnNew(true),TYPES.get(kind));
        if(claimed==null) return;
        RequiredNotification intent=intents(claimed).stream().filter(n->notificationId.equals(n.getNotificationId())).findFirst().orElseThrow();
        boolean attempted=false;
        try {
            Student student=students.findByStudentEmailIgnoreCase(text(claimed,"studentEmail"));
            if(student==null || student.isArchived() || !Objects.equals(student.getSchool(),text(claimed,"school"))) throw new AccessDeniedException("Student is unavailable");
            String author=text(claimed,"originalAuthorEmail"), owner=text(claimed,"responsibleStaffEmail");
            if(!intent.isSystem()) authorize(intent.getActor(),author,owner,student);
            String actor=intent.isSystem()?null:users.getVerifiedStaffEmail(intent.getActor());
            String sendingIdentity=actor;
            if(intent.isSystem()) {
                try { users.requireStaffInStudentSchool(author,student); sendingIdentity=users.getVerifiedStaffEmail(author); }
                catch(AccessDeniedException ignored) { /* System SMTP can use its configured default Reply-To. */ }
            }
            List<String> cc=new ArrayList<>(); List<String> bcc=new ArrayList<>();
            cc.add(address(student.getStudentEmail()));
            for(String staff:Arrays.asList(author,owner,actor,student.getAdminEmail(),student.getGuidanceEmail())) addStaff(cc,staff,student);
            if(student.getSpotters()!=null) for(String staff:student.getSpotters()) { List<String> valid=new ArrayList<>(); addStaff(valid,staff,student); for(String email:valid) if(!cc.contains(email) && !bcc.contains(email)) bcc.add(email); }
            String to=kind.equals("guidance")?address(student.getStudentEmail()):address(student.getParentEmail());
            cc.removeIf(email->email.equalsIgnoreCase(to)); bcc.removeIf(email->email.equalsIgnoreCase(to));
            var recipients=new TreeSet<String>(); recipients.add(to); recipients.addAll(cc); recipients.addAll(bcc);
            intent.setRecipients(new ArrayList<>(recipients));
            String subject="REPS case "+text(claimed,"conversationId").substring(0,8)+" — "+templates.buildManagedSubject(kind,intent.getContent()==null?Map.of():intent.getContent(),student.getPreferredLanguage());
            String parent=null,thread=null;
            for(var previous:intents(claimed)) {
                if("ACCEPTED".equals(previous.getState()) && Objects.equals(previous.getRecipients(),new ArrayList<>(recipients))) {
                    parent=previous.getRfcMessageId();
                    thread=Objects.equals(actor,previous.getActor()) && Objects.equals(subject,previous.getSubject()) && "GMAIL".equals(previous.getProvider())?previous.getGmailThreadId():null;
                }
            }
            String body;
            try { body=render(claimed,intent,student); }
            catch(IllegalArgumentException | IllegalStateException invalid) { throw new ContentFailure(); }
            try { EmailContent.requireRendered(subject,body); }
            catch(IllegalArgumentException invalid) { throw new ContentFailure(); }
            Update metadata=new Update().set("requiredNotifications.$.recipients",new ArrayList<>(recipients)).set("requiredNotifications.$.subject",subject)
                    .set("requiredNotifications.$.inReplyTo",parent).set("requiredNotifications.$.references",parent);
            update(kind,caseId,notificationId,"DISPATCHING",attempt,metadata);
            attempted=true;
            Map<String,String> receipt=router.sendRequiredNotification(to,cc,subject,body,
                    bcc,sendingIdentity,intent.isSystem(),intent.getRfcMessageId(),parent,thread);
            update(kind,caseId,notificationId,"DISPATCHING",attempt,new Update().set("requiredNotifications.$.state","ACCEPTED")
                    .set("requiredNotifications.$.provider",receipt.get("provider")).set("requiredNotifications.$.providerMessageId",receipt.get("providerMessageId"))
                    .set("requiredNotifications.$.gmailThreadId",receipt.get("gmailThreadId")).set("requiredNotifications.$.reason","PROVIDER_ACCEPTED")
                    .set("requiredNotifications.$.retrySafe",false));
        } catch(Exception failure) {
            String message=Objects.toString(failure.getMessage(),"");
            String state=!attempted?"FAILED":message.startsWith("GMAIL_RECONNECT_REQUIRED")||message.startsWith("GMAIL_CONNECTION_UNKNOWN")?"RECONNECT_REQUIRED":
                    message.startsWith("GMAIL_SEND_REJECTED")||message.startsWith("SMTP_POLICY_INVALID")||failure instanceof MailAuthenticationException||failure instanceof MailPreparationException?"FAILED":"UNCERTAIN";
            Map<String,String> outcomes=partialOutcomes(failure);
            if(outcomes.containsValue("ACCEPTED")) state="UNCERTAIN";
            else if(intent.getRecipients()!=null && !outcomes.isEmpty() && outcomes.keySet().containsAll(intent.getRecipients())) state="FAILED";
            boolean safe=!"UNCERTAIN".equals(state);
            String reason = failure instanceof ContentFailure ? "CONTENT_OR_LINK_CONFIGURATION_INVALID" : failure instanceof RecipientFailure ? "REQUIRED_RECIPIENT_INVALID" : attempted ? "PROVIDER_"+state : "AUTHORIZATION_OR_RECIPIENT_VALIDATION";
            Update failureUpdate = new Update().set("requiredNotifications.$.state",state)
                    .set("requiredNotifications.$.retrySafe",safe).set("requiredNotifications.$.recipientOutcomes",outcomes);
            if (failure instanceof com.reps.demogcloud.security.services.GoogleMailService.SendFailure gmail) {
                reason = gmail.diagnostic(); failureUpdate.set("requiredNotifications.$.provider","GMAIL");
            }
            failureUpdate.set("requiredNotifications.$.reason",reason);
            try { update(kind,caseId,notificationId,"DISPATCHING",attempt,failureUpdate); }
            catch(Exception ignored) { /* Claim remains recoverable as UNCERTAIN. Never resend here. */ }
        }
    }
    private static class ContentFailure extends IllegalArgumentException {}
    private static class RecipientFailure extends IllegalArgumentException {}
    private String address(String email) {
        try {
            if(email==null || email.isBlank() || email.contains("\r") || email.contains("\n")) throw new RecipientFailure();
            InternetAddress parsed=new InternetAddress(email,true); parsed.validate();
            if(!email.equals(parsed.getAddress())) throw new RecipientFailure();
            return email.toLowerCase(Locale.ROOT);
        } catch(Exception invalid) { throw new RecipientFailure(); }
    }
    private void addStaff(List<String> recipients,String staff,Student student) {
        if(staff==null || staff.isBlank()) return;
        try { users.requireStaffInStudentSchool(staff,student); String verified=address(users.getVerifiedStaffEmail(staff)); if(!recipients.contains(verified)) recipients.add(verified); }
        catch(Exception ignored) { /* A removed/unauthorized participant is never copied from prior mail. */ }
    }
    private Map<String,String> partialOutcomes(Throwable error) {
        Map<String,String> result=new LinkedHashMap<>();
        if(error instanceof MailSendException send) for(Exception nested:send.getMessageExceptions()) result.putAll(partialOutcomes(nested));
        if(error instanceof SendFailedException send) {
            if(send.getValidSentAddresses()!=null) for(Address address:send.getValidSentAddresses()) result.put(address.toString(),"ACCEPTED");
            if(send.getValidUnsentAddresses()!=null) for(Address address:send.getValidUnsentAddresses()) result.put(address.toString(),"NOT_SENT");
            if(send.getInvalidAddresses()!=null) for(Address address:send.getInvalidAddresses()) result.put(address.toString(),"INVALID");
        }
        return result;
    }
}
