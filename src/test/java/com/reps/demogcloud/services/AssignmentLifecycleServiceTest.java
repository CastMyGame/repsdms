package com.reps.demogcloud.services;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.models.assignments.*;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.services.email.RequiredNotificationService;
import org.junit.jupiter.api.*;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Mock Mongo CAS, not a claim of real MongoDB execution. */
class AssignmentLifecycleServiceTest {
    WorkflowAccessService access=mock(WorkflowAccessService.class);
    UserContextService users=mock(UserContextService.class);
    AssignmentResolver resolver=mock(AssignmentResolver.class);
    PunishRepository repository=mock(PunishRepository.class);
    MongoTemplate mongo=mock(MongoTemplate.class);
    RequiredNotificationService notifications=mock(RequiredNotificationService.class);
    AssignmentLifecycleService service=new AssignmentLifecycleService(access,users,resolver,repository,mongo,notifications);
    Punishment stored;
    AssignmentTemplate template;
    @BeforeEach void setup() {
        template=template();stored=new Punishment();stored.setPunishmentId("case");stored.setStatus("OPEN");stored.setSchool("School");
        stored.setStudentEmail("student@example.test");stored.setTeacherEmail("teacher@example.test");stored.setInfractionName("Horseplay");stored.setInfractionLevel("1");
        stored.setRevision(0L);stored.setInfractionDescription(new ArrayList<>(List.of("Original behavior")));
        stored.setAssignmentTemplateId("original");stored.setAssignmentWork(AssignmentWork.assigned(template));
        when(access.requireCase("case")).thenAnswer(i -> snapshot());
        when(access.requireTemplate("original")).thenReturn(template);
        when(resolver.belongs(any(AssignmentTemplate.class),eq("School"))).thenReturn(true);
        when(resolver.pinned("original","School")).thenReturn(template);
        when(users.getCurrentStaffEmail()).thenReturn("teacher@example.test");
        when(repository.findById("case")).thenAnswer(i -> Optional.of(snapshot()));
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Punishment.class))).thenAnswer(i -> {
            Query query=i.getArgument(0);Update update=i.getArgument(1);
            assertEquals("case",query.getQueryObject().get("_id"));
            if(!Objects.equals(query.getQueryObject().get("revision"),stored.getRevision())) return null;
            var fields=(org.bson.Document)update.getUpdateObject().get("$set");
            stored.setAssignmentWork(AssignmentWork.copy((AssignmentWork)fields.get("assignmentWork"),AssignmentWork.class));
            stored.setAssignmentTemplateId(fields.getString("assignmentTemplateId"));stored.setStatus(fields.getString("status"));
            stored.setRevision(((Number)fields.get("revision")).longValue());stored.setClosedTimes((Integer)fields.get("closedTimes"));
            return snapshot();
        });
    }
    Punishment snapshot() { var copy=new Punishment();org.springframework.beans.BeanUtils.copyProperties(stored,copy);
        if(stored.getAssignmentWork()!=null) copy.setAssignmentWork(AssignmentWork.copy(stored.getAssignmentWork(),AssignmentWork.class));return copy; }
    static AssignmentTemplate template() {
        var t=new AssignmentTemplate();t.setId("original");t.setInfractionName("Horseplay");t.setLevel(1);t.setCreatedBySystem(true);
        var q=new AssignmentTemplate.TemplateQuestion();q.setId("q1");q.setType(AssignmentTemplate.QuestionType.EXPLORATORY_OPEN);q.setRequired(true);q.setPrompt("What happened?");q.setMinLength(3);
        t.setQuestions(new ArrayList<>(List.of(q)));return t;
    }
    AssignmentLifecycleService.Command command(long version,String answer) {
        var c=new AssignmentLifecycleService.Command();c.setVersion(version);c.setActionId(UUID.randomUUID().toString());c.setCurrentQuestionId("q1");
        var a=new AssignmentWork.Answer();a.setText(answer);c.setAnswers(new LinkedHashMap<>(Map.of("q1",a)));return c;
    }
    @Test void answersAndResumeLocationAreSavedInOneCompareAndSet() {
        service.change("case","save",command(0,"A saved answer"));
        assertEquals("A saved answer",stored.getAssignmentWork().getAnswers().get("q1").getText());
        assertEquals("q1",stored.getAssignmentWork().getCurrentQuestionId());assertEquals(1,stored.getAssignmentWork().getVersion());
        assertEquals(List.of("Original behavior"),stored.getInfractionDescription());verify(repository,never()).save(any());
    }
    @Test void simulatedRestartReadsDurableDraftInsteadOfReResolving() {
        service.change("case","save",command(0,"Retained"));
        var restarted=new AssignmentLifecycleService(access,users,resolver,repository,mongo,notifications);
        assertEquals("Retained",restarted.view("case").work().getAnswers().get("q1").getText());
    }
    @Test void exactRetryDoesNotSaveOrSendAgain() {
        var c=command(0,"Retained");service.change("case","save",c);service.change("case","save",c);
        assertEquals(1,stored.getAssignmentWork().getVersion());verify(mongo,times(1)).findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Punishment.class));
    }
    @Test void actionIdCannotBeReusedForDifferentAnswers() {
        var c=command(0,"Original");service.change("case","save",c);c.getAnswers().get("q1").setText("Changed");
        assertEquals(409,assertThrows(ResponseStatusException.class,()->service.change("case","save",c)).getStatusCode().value());
    }
    @Test void staleConcurrentSaveIsAConflictAndDoesNotEraseTheWinner() {
        service.change("case","save",command(0,"Winner"));
        assertThrows(ResponseStatusException.class,()->service.change("case","save",command(0,"Loser")));
        assertEquals("Winner",stored.getAssignmentWork().getAnswers().get("q1").getText());
    }
    @Test void databaseRaceAfterReadIsAConflict() {
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Punishment.class))).thenReturn(null);
        assertThrows(ResponseStatusException.class,()->service.change("case","save",command(0,"Draft")));
        assertTrue(stored.getAssignmentWork().getAnswers().isEmpty());
    }
    @Test void failedPersistenceDoesNotClaimSuccessOrMutateSavedWork() {
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Punishment.class))).thenThrow(new org.springframework.dao.DataAccessResourceFailureException("synthetic"));
        assertThrows(org.springframework.dao.DataAccessException.class,()->service.change("case","save",command(0,"Draft")));
        assertTrue(stored.getAssignmentWork().getAnswers().isEmpty());
    }
    @Test void emptyAndTooShortRequiredAnswersCannotSubmit() {
        for(var answer:List.of("","  ","ab")) assertThrows(IllegalArgumentException.class,()->service.change("case","submit",command(0,answer)));
        assertEquals("OPEN",stored.getStatus());verifyNoInteractions(mongo);
    }
    @Test void requiredMissingQuestionAndUnknownAnswerAreRejected() {
        var c=command(0,"Valid");c.setAnswers(Map.of());assertThrows(IllegalArgumentException.class,()->service.change("case","submit",c));
        c.setAnswers(Map.of("forged",new AssignmentWork.Answer()));assertThrows(IllegalArgumentException.class,()->service.change("case","save",c));
    }
    @Test void submitRejectReviseResubmitAcceptPreservesOriginalAndEverySubmission() {
        service.change("case","submit",command(0,"First response"));
        assertEquals("PENDING",stored.getStatus());
        var reject=command(1,"unused");reject.setDecision("REJECT");reject.setFeedback("Please explain the impact");service.change("case","review",reject);
        assertEquals("REJECTED",stored.getAssignmentWork().getState());assertEquals("OPEN",stored.getStatus());
        service.change("case","submit",command(2,"A revised response"));
        var accept=command(3,"unused");accept.setDecision("ACCEPT");service.change("case","review",accept);service.change("case","review",accept);
        assertEquals("CLOSED",stored.getStatus());assertEquals(1,stored.getClosedTimes());
        var history=stored.getAssignmentWork().getSubmissions();assertEquals(2,history.size());
        assertEquals("First response",history.get(0).getAnswers().get("q1").getText());assertEquals("REJECTED",history.get(0).getOutcome());
        assertEquals("ACCEPTED",history.get(1).getOutcome());assertEquals(List.of("Original behavior"),stored.getInfractionDescription());
        verify(access,times(3)).requireStaff();
    }
    @Test void reviewRequiresSubmissionAndRejectionFeedback() {
        var review=command(0,"unused");review.setDecision("ACCEPT");assertThrows(ResponseStatusException.class,()->service.change("case","review",review));
        service.change("case","submit",command(0,"Response"));review.setVersion(1);review.setDecision("REJECT");assertThrows(IllegalArgumentException.class,()->service.change("case","review",review));
    }
    @Test void templateEditsCannotChangeTheAssignedQuestions() {
        template.getQuestions().get(0).setPrompt("Edited prompt");
        assertEquals("What happened?",service.view("case").work().getTemplate().getQuestions().get(0).getPrompt());
    }
    @Test void missingOrInactiveOriginalCannotTriggerReplacement() {
        when(resolver.pinned("original","School")).thenThrow(new IllegalArgumentException("Assigned template unavailable"));
        assertFalse(service.view("case").available());assertThrows(IllegalArgumentException.class,()->service.change("case","save",command(0,"Saved")));
        verify(resolver,never()).resolve(any(),any(),any(),anyInt());
    }
    @Test void legacyIndexCannotBeTreatedAsRecoverableAnswers() {
        stored.setAssignmentWork(null);stored.setMapIndex(2);var view=service.view("case");
        assertFalse(view.available());assertTrue(view.recoveryRequired());verifyNoInteractions(mongo);
    }
    @Test void legacyUnboundCaseIsNotAutomaticallyAssigned() {
        stored.setAssignmentWork(null);stored.setAssignmentTemplateId(null);assertFalse(service.view("case").available());verifyNoInteractions(mongo);
    }
    @Test void explicitLegacyRecoveryPreservesDescriptionsAndHistory() {
        stored.setAssignmentWork(null);stored.setMapIndex(2);stored.setAnswerHistory(new Date(),List.of("Historical answer"));
        var before=stored.getAnswerHistory();var c=command(0,"unused");c.setTemplateId("original");c.setConfirmRestart(true);c.setFeedback("Identity and intended assignment confirmed");
        service.change("case","bind",c);assertNotNull(stored.getAssignmentWork());assertEquals(before,stored.getAnswerHistory());assertEquals(List.of("Original behavior"),stored.getInfractionDescription());
    }
    @Test void recoveryRequiresExplicitConfirmation() {
        stored.setAssignmentWork(AssignmentWork.unavailable("Missing"));var c=command(0,"unused");c.setTemplateId("original");
        assertThrows(IllegalArgumentException.class,()->service.change("case","bind",c));verifyNoInteractions(mongo);
    }
    @Test void legacyMissingOriginalCanBeExplicitlyRecoveredWithoutChangingTheCatalog() {
        stored.setAssignmentWork(null);stored.setAssignmentTemplateId("removed");
        when(resolver.pinned("removed","School")).thenThrow(new IllegalArgumentException("Original removed"));
        var c=command(0,"unused");c.setTemplateId("original");c.setConfirmRestart(true);
        service.change("case","bind",c);
        assertEquals("original",stored.getAssignmentTemplateId());
        assertEquals("ASSIGNED",stored.getAssignmentWork().getState());
        assertEquals(List.of("Original behavior"),stored.getInfractionDescription());
    }
    @Test void submittedWorkRemainsReviewableFromItsSnapshotAfterCatalogRetirement() {
        service.change("case","submit",command(0,"An original response"));
        when(resolver.pinned("original","School")).thenThrow(new IllegalArgumentException("Catalog entry retired"));
        assertTrue(service.view("case").available());
        var review=command(1,"unused");review.setDecision("ACCEPT");service.change("case","review",review);
        assertEquals("CLOSED",stored.getStatus());assertEquals("What happened?",stored.getAssignmentWork().getSubmissions().get(0).getTemplate().getQuestions().get(0).getPrompt());
    }
    @Test void caseAndNotificationIntentShareOneAtomicUpdate() {
        when(notifications.isEnabled()).thenReturn(true);
        doAnswer(i -> { Punishment p=i.getArgument(0);var intent=new com.reps.demogcloud.models.email.RequiredNotification();intent.setEvent("SUBMITTED");p.setRequiredNotifications(new ArrayList<>(List.of(intent)));return true; })
                .when(notifications).prepare(any(),eq("SUBMITTED"),anyString(),isNull());
        service.change("case","submit",command(0,"Full response"));
        var captured=org.mockito.ArgumentCaptor.forClass(Update.class);verify(mongo).findAndModify(any(Query.class),captured.capture(),any(FindAndModifyOptions.class),eq(Punishment.class));
        assertTrue(((org.bson.Document)captured.getValue().getUpdateObject().get("$set")).containsKey("requiredNotifications"));
        verify(notifications).captureContent(any(Punishment.class));
    }
    @Test void readingRequiresRealOptionIdsOrAnAcceptableRetry() {
        var q=template.getQuestions().get(0);q.setType(AssignmentTemplate.QuestionType.READING_MC);
        q.setOptions(List.of(new AssignmentTemplate.AnswerOption("yes","Correct",true),new AssignmentTemplate.AnswerOption("no","Wrong",false)));
        var a=new AssignmentWork.Answer();a.setOptionIds(List.of("no"));
        assertThrows(IllegalArgumentException.class,()->AssignmentLifecycleService.validate(template,Map.of("q1",a),true));
        a.setOptionIds(List.of("yes"));assertDoesNotThrow(()->AssignmentLifecycleService.validate(template,Map.of("q1",a),true));
        a.setOptionIds(List.of("true"));assertThrows(IllegalArgumentException.class,()->AssignmentLifecycleService.validate(template,Map.of("q1",a),true));
        var retry=new AssignmentTemplate.RetryConfig();retry.setEnabled(true);retry.setTextToCopy("A respectful response");q.setRetry(retry);a.setOptionIds(List.of("no"));a.setRetryText("A respectful response");
        assertDoesNotThrow(()->AssignmentLifecycleService.validate(template,Map.of("q1",a),true));
    }
    @Test void singleChoiceKeepsLegacyAnyCorrectOptionBehaviorAndMultipleChoiceHonorsGradingMode() {
        var q=template.getQuestions().get(0);q.setType(AssignmentTemplate.QuestionType.READING_MC);
        q.setOptions(List.of(new AssignmentTemplate.AnswerOption("a","First correct",true),new AssignmentTemplate.AnswerOption("b","Second correct",true)));
        q.setSelectionMode(AssignmentTemplate.SelectionMode.SINGLE);q.setGradingMode(AssignmentTemplate.GradingMode.ALL_CORRECT);
        var answer=new AssignmentWork.Answer();answer.setOptionIds(List.of("a"));
        assertDoesNotThrow(()->AssignmentLifecycleService.validate(template,Map.of("q1",answer),true));
        q.setSelectionMode(AssignmentTemplate.SelectionMode.MULTIPLE);
        assertThrows(IllegalArgumentException.class,()->AssignmentLifecycleService.validate(template,Map.of("q1",answer),true));
        answer.setOptionIds(List.of("a","b"));assertDoesNotThrow(()->AssignmentLifecycleService.validate(template,Map.of("q1",answer),true));
        answer.setOptionIds(List.of("b"));q.setGradingMode(AssignmentTemplate.GradingMode.ANY_CORRECT);
        assertDoesNotThrow(()->AssignmentLifecycleService.validate(template,Map.of("q1",answer),true));
    }
}
