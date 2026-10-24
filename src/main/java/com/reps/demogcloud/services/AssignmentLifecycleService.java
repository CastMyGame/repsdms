package com.reps.demogcloud.services;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.models.assignments.*;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import lombok.*;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class AssignmentLifecycleService {
    private final WorkflowAccessService access;
    private final UserContextService users;
    private final AssignmentResolver resolver;
    private final PunishRepository cases;
    private final MongoTemplate mongo;
    private final RequiredNotificationService notifications;

    @Data public static class Command {
        private long version;
        private String actionId;
        private Map<String,AssignmentWork.Answer> answers;
        private String currentQuestionId;
        private String decision;
        private String feedback;
        private String templateId;
        private boolean confirmRestart;
    }
    public record View(String caseId, String caseStatus, boolean available, boolean recoveryRequired, AssignmentWork work,
                       List<String> originalRecord, Map<Date,List<String>> legacyAnswerHistory) {}
    private static ResponseStatusException conflict() { return new ResponseStatusException(HttpStatus.CONFLICT,"Assignment changed. Reload before editing; your draft has not been discarded."); }
    private static boolean text(String value) { return value!=null && !value.isBlank(); }
    private AssignmentWork work(Punishment p) {
        if(com.reps.demogcloud.models.enums.InfractionType.isSpecialCase(p.getInfractionName()))
            return AssignmentWork.unavailable("This case does not use a disciplinary assignment template");
        if(p.getAssignmentWork()!=null) return AssignmentWork.copy(p.getAssignmentWork(),AssignmentWork.class);
        if(!"OPEN".equals(p.getStatus())) return AssignmentWork.unavailable("Legacy case history is preserved. Staff must review this case before changing assignment work.");
        if(p.getMapIndex()!=0 || p.getAnswerHistory()!=null && !p.getAnswerHistory().isEmpty())
            return AssignmentWork.unavailable("Legacy progress/submissions need staff-confirmed recovery. Existing history is preserved.");
        if(!text(p.getAssignmentTemplateId())) return AssignmentWork.unavailable("No original template binding was saved. Staff must explicitly attach an assignment.");
        return AssignmentWork.assigned(resolver.pinned(p.getAssignmentTemplateId(),p.getSchool()));
    }
    public View view(String id) {
        var p=access.requireCase(id);
        AssignmentWork w;
        try { w=work(p); }
        catch(IllegalArgumentException missing) { w=AssignmentWork.unavailable(missing.getMessage()); }
        boolean available=w.getTemplate()!=null && !"UNAVAILABLE".equals(w.getState());
        if(available && (!Objects.equals(w.getTemplate().getId(),p.getAssignmentTemplateId()) || !resolver.belongs(w.getTemplate(),p.getSchool())))
            throw new org.springframework.security.access.AccessDeniedException("Access denied");
        // Submitted work is reviewed against its original snapshot, even if its catalog entry was retired.
        if(available && !Set.of("SUBMITTED","COMPLETED").contains(w.getState())) try { resolver.pinned(p.getAssignmentTemplateId(),p.getSchool()); }
        catch(IllegalArgumentException missing) { available=false; w.setUnavailableReason(missing.getMessage()); }
        return new View(id,p.getStatus(),available,p.getAssignmentWork()==null && (p.getMapIndex()!=0 || p.getAnswerHistory()!=null && !p.getAnswerHistory().isEmpty()),w,
                p.getInfractionDescription()==null?List.of():p.getInfractionDescription(),p.getAnswerHistory()==null?Map.of():p.getAnswerHistory());
    }
    private void staff(Punishment p) {
        access.requireStaff();
        // Same persisted participant/admin policy as case communications, independent of email gates.
        notifications.requireActionAccess(p,"CLOSED");
    }
    private static String digest(String operation,Command c) {
        try {
            var mapper=new com.fasterxml.jackson.databind.ObjectMapper().configure(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true);
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(List.of(operation,c))));
        } catch(Exception e) { throw new IllegalArgumentException("Invalid assignment command"); }
    }
    private static boolean repeated(AssignmentWork w,Command c,String digest) {
        if(w!=null && Objects.equals(w.getLastActionId(),c.getActionId())) {
            if(!Objects.equals(w.getLastActionDigest(),digest)) throw conflict();
            return true;
        }
        return false;
    }
    public View change(String id,String operation,Command c) {
        var p=access.requireCase(id);
        if("save".equals(operation) || "submit".equals(operation)) access.requireStudent(); else staff(p);
        if(c==null || c.getVersion()<0 || c.getActionId()==null || !c.getActionId().matches("[A-Za-z0-9_-]{16,80}"))
            throw new IllegalArgumentException("A valid assignment action identifier and version are required");
        if(c.getFeedback()!=null && c.getFeedback().length()>2000) throw new IllegalArgumentException("Feedback is too long");
        String fingerprint=digest(operation,c);
        if(repeated(p.getAssignmentWork(),c,fingerprint)) return view(id);
        if(p.isArchived() || "CLOSED".equals(p.getStatus())) throw conflict();
        AssignmentWork w;
        try { w=work(p); }
        catch(IllegalArgumentException unavailable) {
            if(!"bind".equals(operation)) throw unavailable;
            w=AssignmentWork.unavailable("Original assignment unavailable; explicit recovery required");
        }
        if(w.getVersion()!=c.getVersion()) throw conflict();
        String event=null;
        if("bind".equals(operation)) {
            if(com.reps.demogcloud.models.enums.InfractionType.isSpecialCase(p.getInfractionName())) throw conflict();
            if("SUBMITTED".equals(w.getState())) throw conflict();
            if(!c.isConfirmRestart()) throw new IllegalArgumentException("Explicit staff confirmation is required to attach/recover assignment work");
            if((p.getMapIndex()!=0 || p.getAnswerHistory()!=null && !p.getAnswerHistory().isEmpty() || "PENDING".equals(p.getStatus())) && !text(c.getFeedback()))
                throw new IllegalArgumentException("Explain why this legacy work needs recovery");
            var template=access.requireTemplate(c.getTemplateId());
            if(!template.isActive() || !resolver.belongs(template,p.getSchool())
                    || !template.getInfractionName().trim().equalsIgnoreCase(p.getInfractionName().trim())
                    || !String.valueOf(template.getLevel()).equals(p.getInfractionLevel()))
                throw new IllegalArgumentException("Choose an active template for this case's infraction and level");
            var replacement=AssignmentWork.assigned(template);
            replacement.setVersion(w.getVersion()); replacement.setSubmissions(new ArrayList<>(w.getSubmissions()));
            if(!w.getAnswers().isEmpty()) {
                var preserved=new AssignmentWork.Submission(); preserved.setId(c.getActionId()); preserved.setAnswers(w.getAnswers());
                preserved.setTemplate(w.getTemplate());
                preserved.setOutcome("RECOVERED_DRAFT"); preserved.setReviewedAt(Instant.now()); preserved.setReviewer(users.getCurrentStaffEmail());
                replacement.getSubmissions().add(preserved);
            }
            w=replacement; p.setAssignmentTemplateId(template.getId());
            w.setFeedback(c.getFeedback());
            if("PENDING".equals(p.getStatus())) { p.setStatus("OPEN"); w.setState("REJECTED"); event="REJECTED"; }
        } else {
            if(w.getTemplate()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,w.getUnavailableReason());
            if(!Objects.equals(w.getTemplate().getId(),p.getAssignmentTemplateId()) || !resolver.belongs(w.getTemplate(),p.getSchool()))
                throw new org.springframework.security.access.AccessDeniedException("Access denied");
            if(!"review".equals(operation)) resolver.pinned(p.getAssignmentTemplateId(),p.getSchool()); // Never substitute for an unavailable original.
            if("save".equals(operation) || "submit".equals(operation)) {
                if(!Set.of("ASSIGNED","IN_PROGRESS","REJECTED").contains(w.getState()) || !"OPEN".equals(p.getStatus())) throw conflict();
                validate(w.getTemplate(),c.getAnswers(),"submit".equals(operation));
                w.setAnswers(new LinkedHashMap<>(c.getAnswers()));
                if(text(c.getCurrentQuestionId()) && w.getTemplate().getQuestions().stream().noneMatch(q -> q.getId().equals(c.getCurrentQuestionId())))
                    throw new IllegalArgumentException("Unknown assignment question");
                w.setCurrentQuestionId(c.getCurrentQuestionId());
                if("submit".equals(operation)) {
                    var submission=new AssignmentWork.Submission(); submission.setId(c.getActionId()); submission.setSubmittedAt(Instant.now());
                    submission.setAnswers(AssignmentWork.copy(w,AssignmentWork.class).getAnswers());
                    submission.setTemplate(AssignmentWork.copy(w.getTemplate(),AssignmentTemplate.class));
                    w.getSubmissions().add(submission); w.setState("SUBMITTED"); p.setStatus("PENDING"); event="SUBMITTED";
                } else if(!"REJECTED".equals(w.getState())) w.setState("IN_PROGRESS");
            } else if("review".equals(operation)) {
                if(!"SUBMITTED".equals(w.getState()) || w.getSubmissions().isEmpty() || !"PENDING".equals(p.getStatus())) throw conflict();
                if(!Set.of("ACCEPT","REJECT").contains(Objects.toString(c.getDecision(),""))) throw new IllegalArgumentException("Choose ACCEPT or REJECT");
                if("REJECT".equals(c.getDecision()) && !text(c.getFeedback())) throw new IllegalArgumentException("Rejection feedback is required");
                if(c.getFeedback()!=null && c.getFeedback().length()>2000) throw new IllegalArgumentException("Feedback is too long");
                var submission=w.getSubmissions().get(w.getSubmissions().size()-1);
                submission.setReviewer(users.getCurrentStaffEmail()); submission.setReviewedAt(Instant.now()); submission.setFeedback(c.getFeedback());
                w.setFeedback(c.getFeedback());
                if("ACCEPT".equals(c.getDecision())) {
                    submission.setOutcome("ACCEPTED"); w.setState("COMPLETED"); p.setStatus("CLOSED");
                    p.setTimeClosed(java.time.LocalDate.now()); p.setClosedTimes(p.getClosedTimes()+1); event="CLOSED";
                } else {
                    submission.setOutcome("REJECTED"); w.setState("REJECTED"); p.setStatus("OPEN"); event="REJECTED";
                }
            } else throw new IllegalArgumentException("Unknown assignment action");
        }
        if(w.getSubmissions().size()>100) throw new IllegalArgumentException("Assignment history limit reached; contact school support");
        w.setVersion(w.getVersion()+1); w.setLastActionId(c.getActionId()); w.setLastActionDigest(fingerprint); w.setSavedAt(Instant.now());
        try {
            if(new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules().writeValueAsBytes(w).length>8_000_000)
                throw new IllegalArgumentException("Assignment history is too large; contact school support. Existing work is preserved.");
        } catch(com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid assignment work"); }
        p.setAssignmentWork(w);
        if(event!=null && notifications.isEnabled()) { notifications.prepare(p,event,c.getActionId(),null); notifications.captureContent(p); }
        var query=Query.query(Criteria.where("_id").is(id).and("revision").is(p.getRevision()));
        var update=new Update().set("assignmentWork",w).set("assignmentTemplateId",p.getAssignmentTemplateId())
                .set("status",p.getStatus()).set("timeClosed",p.getTimeClosed()).set("closedTimes",p.getClosedTimes())
                .set("revision",p.getRevision()==null?0:p.getRevision()+1);
        if(event!=null && notifications.isEnabled()) update.set("requiredNotifications",p.getRequiredNotifications())
                .set("originalAuthorEmail",p.getOriginalAuthorEmail()).set("responsibleStaffEmail",p.getResponsibleStaffEmail())
                .set("conversationId",p.getConversationId());
        var saved=mongo.findAndModify(query,update,FindAndModifyOptions.options().returnNew(true),Punishment.class);
        if(saved==null) {
            var latest=cases.findById(id).orElseThrow(AssignmentLifecycleService::conflict);
            if(!repeated(latest.getAssignmentWork(),c,fingerprint)) throw conflict();
        }
        return view(id);
    }

    public static void validate(AssignmentTemplate template,Map<String,AssignmentWork.Answer> answers,boolean submitting) {
        if(answers==null || answers.size()>200) throw new IllegalArgumentException("Invalid assignment answers");
        try {
            if(new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(answers).length>65536)
                throw new IllegalArgumentException("Assignment draft is too large");
        } catch(com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid assignment answers"); }
        var known=new HashSet<String>(); for(var q:template.getQuestions()) known.add(q.getId());
        if(!known.containsAll(answers.keySet())) throw new IllegalArgumentException("Unknown answer question");
        for(var q:template.getQuestions()) {
            String questionLabel="Question "+(template.getQuestions().indexOf(q)+1)+": ";
            var a=answers.get(q.getId());
            if(a==null) { if(submitting && q.isRequired()) throw new IllegalArgumentException(questionLabel+"complete the required answer"); continue; }
            if(a.getText()!=null && a.getText().length()>20000 || a.getRetryText()!=null && a.getRetryText().length()>20000)
                throw new IllegalArgumentException("Answer is too long");
            if(q.getType()==AssignmentTemplate.QuestionType.EXPLORATORY_OPEN) {
                int length=a.getText()==null?0:a.getText().trim().length();
                if(submitting && (q.isRequired() && length==0 || length>0 && (q.getMinLength()!=null && length<q.getMinLength() || q.getMaxLength()!=null && length>q.getMaxLength())))
                    throw new IllegalArgumentException(questionLabel+"a required written answer is missing or outside its length limits");
            } else {
                var ids=a.getOptionIds()==null?List.<String>of():a.getOptionIds();
                var options=q.getOptions()==null?List.<AssignmentTemplate.AnswerOption>of():q.getOptions();
                var valid=options.stream().map(AssignmentTemplate.AnswerOption::getId).toList();
                if(ids.size()>valid.size() || new HashSet<>(ids).size()!=ids.size() || !valid.containsAll(ids)
                        || q.getSelectionMode()!=AssignmentTemplate.SelectionMode.MULTIPLE && ids.size()>1)
                    throw new IllegalArgumentException("Invalid answer selection");
                if(submitting && q.isRequired() && ids.isEmpty()) throw new IllegalArgumentException(questionLabel+"choose a required answer");
                if(submitting && q.getType()==AssignmentTemplate.QuestionType.READING_MC && (q.isRequired() || !ids.isEmpty())) {
                    var correct=options.stream().filter(AssignmentTemplate.AnswerOption::isCorrect).map(AssignmentTemplate.AnswerOption::getId).collect(java.util.stream.Collectors.toSet());
                    boolean passed=q.getSelectionMode()!=AssignmentTemplate.SelectionMode.MULTIPLE ? ids.size()==1 && correct.contains(ids.get(0))
                            : q.getGradingMode()==AssignmentTemplate.GradingMode.ANY_CORRECT ? ids.stream().anyMatch(correct::contains) : !correct.isEmpty() && correct.equals(new HashSet<>(ids));
                    if(!passed && !retry(q,a)) throw new IllegalArgumentException(questionLabel+"complete the reading question or its required retry");
                }
            }
        }
    }
    private static boolean retry(AssignmentTemplate.TemplateQuestion q,AssignmentWork.Answer a) {
        var retry=q.getRetry();
        if(retry==null || !retry.isEnabled() || !text(retry.getTextToCopy()) || !text(a.getRetryText())) return false;
        // Preserve the existing student retry rule: case/punctuation-normalized word coverage.
        String punctuation="[.,/#!$%^&*;:{}=\\-_`~()]";
        var expected=Arrays.stream(retry.getTextToCopy().replaceAll(punctuation,"").toLowerCase(Locale.ROOT).trim().split("\\s+"))
                .filter(word -> !word.isBlank()).toList();
        var actual=new HashSet<>(Arrays.asList(a.getRetryText().replaceAll(punctuation,"").toLowerCase(Locale.ROOT).trim().split("\\s+")));
        int threshold=retry.getRequiredAccuracyPercent()==null?80:retry.getRequiredAccuracyPercent();
        return !expected.isEmpty() && threshold>=0 && threshold<=100 &&
                100.0*expected.stream().filter(actual::contains).count()/expected.size()>=threshold;
    }
}
