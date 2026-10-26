package com.reps.demogcloud.services.email;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.email.RequiredNotification;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.guidance.GuidanceReferral;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.punishment.PunishmentClosureServiceImpl;
import com.reps.demogcloud.utils.PunishmentUtils;
import com.mongodb.client.result.UpdateResult;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.context.request.*;
import org.springframework.mail.MailSendException;
import jakarta.mail.*;
import jakarta.mail.internet.InternetAddress;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Stateful mocked persistence plus real Spring Mongo mapping. This is NOT a MongoDB/restart integration test. */
class RequiredNotificationServiceTest {
    private final MongoTemplate mongo=mock(MongoTemplate.class);
    private final StudentRepository students=mock(StudentRepository.class);
    private final UserContextService users=mock(UserContextService.class);
    private final EmailRoutingService router=mock(EmailRoutingService.class);
    private final EmailTemplateBuilderService templates=mock(EmailTemplateBuilderService.class, invocation ->
            invocation.getMethod().getReturnType()==String.class ? "<p>Synthetic school message</p>" : RETURNS_DEFAULTS.answer(invocation));
    private final MockEnvironment env=new MockEnvironment();
    private final AtomicReference<Document> database=new AtomicReference<>();
    private final Object lock=new Object();
    private MappingMongoConverter converter;
    private RequiredNotificationService service;
    private Student student;
    private static final String ID="case-id", TEACHER="teacher@example.test", ADMIN="admin@example.test";
    @BeforeEach void setup() throws Exception {
        env.withProperty("reps.email.notifications.enabled","true").withProperty("reps.email.notifications.dispatch-enabled","true");
        when(router.isPhase1RoutingEnabled()).thenReturn(true);
        when(users.getCurrentStaffEmail()).thenReturn(TEACHER);
        when(users.getCurrentUserSchool()).thenReturn("School");
        when(users.getVerifiedStaffEmail(anyString())).thenAnswer(i->i.getArgument(0));
        student=new Student(); student.setStudentEmail("student@example.test"); student.setParentEmail("guardian@example.test");
        student.setSchool("School"); student.setAdminEmail(ADMIN); student.setSpotters(List.of("spotter@example.test"));
        when(students.findByStudentEmailIgnoreCase(student.getStudentEmail())).thenReturn(student);
        var context=new MongoMappingContext(); var conversions=MongoCustomConversions.create(adapter->{});
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder()); context.afterPropertiesSet();
        converter=new MappingMongoConverter(NoOpDbRefResolver.INSTANCE,context); converter.setCustomConversions(conversions); converter.afterPropertiesSet();
        service=new RequiredNotificationService(mongo,env,students,users,router,templates);
        when(templates.buildManagedSubject(anyString(),anyMap(),any())).thenReturn("School update");
        action();
        when(mongo.insert(any(Punishment.class))).thenAnswer(i->{ synchronized(lock) {
            var value=(Punishment)i.getArgument(0); value.setRevision(0L); database.set(write(value)); new CasePersistenceCallbacks().onAfterSave(value,database.get(),"Punishments"); return value;
        }});
        when(mongo.save(any(Punishment.class))).thenAnswer(i->{ synchronized(lock) {
            var value=(Punishment)i.getArgument(0); value.setRevision(value.getRevision()==null?0L:value.getRevision()+1); database.set(write(value)); new CasePersistenceCallbacks().onAfterSave(value,database.get(),"Punishments"); return value;
        }});
        when(mongo.findById(anyString(),eq(Punishment.class))).thenAnswer(i->{ synchronized(lock) { return database.get()!=null && i.getArgument(0).equals(database.get().getString("_id"))?read():null; }});
        when(mongo.findOne(any(Query.class),eq(Punishment.class))).thenAnswer(i->{ synchronized(lock) { return match(i.getArgument(0))?read():null; }});
        when(mongo.find(any(Query.class),any(Class.class))).thenAnswer(i->{ synchronized(lock) {
            return i.getArgument(1)==Punishment.class && match(i.getArgument(0))?List.of(read()):List.of();
        }});
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Punishment.class))).thenAnswer(i->{ synchronized(lock) {
            Query q=i.getArgument(0); if(!match(q)) return null; Document before=Document.parse(database.get().toJson()); apply(q,i.getArgument(1));
            if(!((FindAndModifyOptions)i.getArgument(2)).isReturnNew()) { var after=database.get(); database.set(before); Punishment result=read(); database.set(after); return result; } return read();
        }});
        when(mongo.updateFirst(any(Query.class),any(Update.class),eq("Punishments"))).thenAnswer(i->{ synchronized(lock) {
            boolean matches=match(i.getArgument(0)); if(matches) apply(i.getArgument(0),i.getArgument(1)); return UpdateResult.acknowledged(matches?1:0,matches?1L:0L,null);
        }});
        when(router.sendRequiredNotification(anyString(),anyList(),anyString(),anyString(),anyList(),any(),anyBoolean(),anyString(),any(),any()))
                .thenReturn(Map.of("provider","SMTP","providerMessageId","synthetic-provider-id","gmailThreadId",""));
    }
    @AfterEach void clean() { RequestContextHolder.resetRequestAttributes(); }
    private void action() { var request=new MockHttpServletRequest(); request.addHeader("X-REPS-Action-Id",UUID.randomUUID().toString()); RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request)); }
    private Document write(Object value) { var doc=new Document(); converter.write(value,doc); return doc; }
    private Punishment read() { if(database.get()==null) return null; var value=converter.read(Punishment.class,Document.parse(database.get().toJson())); new CasePersistenceCallbacks().onAfterConvert(value,database.get(),"Punishments"); return value; }
    private Punishment fresh() {
        var p=new Punishment(); p.setPunishmentId(ID); p.setStudentEmail(student.getStudentEmail()); p.setTeacherEmail(TEACHER); p.setSchool("School");
        p.setStatus("OPEN"); p.setInfractionDescription(new ArrayList<>(List.of("synthetic description"))); p.setInfractionName("Synthetic"); return p;
    }
    private Punishment create() { var p=fresh(); service.saveCreated(p); return read(); }
    @Test void assignmentSnapshotAndRequiredIntentRoundTripInTheSameCaseDocument() {
        var p=fresh();var template=new com.reps.demogcloud.models.assignments.AssignmentTemplate();template.setId("snapshot");
        var q=new com.reps.demogcloud.models.assignments.AssignmentTemplate.TemplateQuestion();q.setType(com.reps.demogcloud.models.assignments.AssignmentTemplate.QuestionType.EXPLORATORY_OPEN);q.setPrompt("Synthetic reflection");
        template.setQuestions(List.of(q));p.setAssignmentTemplateId("snapshot");p.setAssignmentWork(com.reps.demogcloud.models.assignments.AssignmentWork.assigned(template));
        service.saveCreated(p);var recovered=read();
        assertEquals("ASSIGNED",recovered.getAssignmentWork().getState());assertEquals("Synthetic reflection",recovered.getAssignmentWork().getTemplate().getQuestions().get(0).getPrompt());
        assertEquals(1,recovered.getRequiredNotifications().size());assertEquals("snapshot",recovered.getAssignmentTemplateId());
        verify(mongo,times(1)).insert(any(Punishment.class));
    }
    @SuppressWarnings("unchecked") private List<Document> notifications() { return (List<Document>)(List<?>)database.get().getList("requiredNotifications",Document.class); }
    private int selected(Query query) {
        Object value=query.getQueryObject().get("requiredNotifications"); if(!(value instanceof Document doc)) return -1;
        Document needed=doc.get("$elemMatch",Document.class); if(needed==null) return -1;
        for(int index=0;index<notifications().size();index++) {
            Document n=notifications().get(index); boolean matches=needed.entrySet().stream().allMatch(e->Objects.equals(n.get(e.getKey()),e.getValue()));
            if(matches) return index;
        } return -1;
    }
    private boolean match(Query query) {
        if(database.get()==null) return false;
        for(var entry:query.getQueryObject().entrySet()) {
            if(entry.getKey().equals("requiredNotifications")) { if(selected(query)<0) return false; }
            else if(entry.getKey().equals("requiredNotifications.state")) {
                List<?> states=((Document)entry.getValue()).getList("$in",Object.class);
                if(notifications().stream().noneMatch(n->states.contains(n.getString("state")))) return false;
            } else if(entry.getKey().equals("requiredNotifications.0")) { if(notifications().isEmpty()) return false; }
            else if(!Objects.equals(database.get().get(entry.getKey()),entry.getValue())) return false;
        } return true;
    }
    private void apply(Query query,Update update) {
        int selected=selected(query);
        for(var operation:update.getUpdateObject().entrySet()) for(var entry:((Document)operation.getValue()).entrySet()) {
            Document target=database.get(); String key=entry.getKey();
            if(key.startsWith("requiredNotifications.$.")) { target=notifications().get(selected); key=key.substring("requiredNotifications.$.".length()); }
            if(operation.getKey().equals("$inc")) target.put(key,((Number)target.getOrDefault(key,0L)).longValue()+((Number)entry.getValue()).longValue());
            else if(operation.getKey().equals("$unset")) target.remove(key); else target.put(key,entry.getValue());
        }
    }
    private RequiredNotification last() { var p=read(); return p.getRequiredNotifications().get(p.getRequiredNotifications().size()-1); }
    private void dispatchLast() { service.dispatchOne("punishment",ID,last().getNotificationId()); }
    private void realContent() {
        var translations=mock(com.reps.demogcloud.services.translation.TranslationService.class);
        when(translations.translate(anyString(),eq("en"),eq("es"))).thenAnswer(i->i.getArgument(0));
        var builder=new EmailTemplateBuilderService(translations);
        org.springframework.test.util.ReflectionTestUtils.setField(builder,"accountBaseUrl","https://repsdev.vercel.app");
        service=new RequiredNotificationService(mongo,env,students,users,router,builder);
        student.setFirstName("Ana");student.setLastName("López");
    }
    @ParameterizedTest @CsvSource({"en,SO,Positive Behavior Shout Out!","es,SO,Positive Behavior Shout Out!","en,OPEN,Horseplay","es,CFR,Dress Code","es,AC,Academic Concern","es,BC,Behavioral Concern"})
    void actualRendererDispatchesLocalizedSafeContentWithoutChangingRecipients(String lang,String status,String name) throws Exception {
        realContent();student.setPreferredLanguage(lang);var p=fresh();p.setStatus(status);p.setInfractionName(name);p.setInfractionLevel("1");
        p.setInfractionDescription(List.of("Actual <behavior> & detail"));service.saveCreated(p);dispatchLast();
        assertEquals("ACCEPTED",last().getState());assertEquals("SMTP",last().getProvider());
        var body=org.mockito.ArgumentCaptor.forClass(String.class);var subject=org.mockito.ArgumentCaptor.forClass(String.class);
        verify(router).sendRequiredNotification(eq(student.getParentEmail()),argThat(cc->cc.contains(TEACHER)&&cc.contains(ADMIN)&&cc.contains(student.getStudentEmail())),subject.capture(),body.capture(),anyList(),eq(TEACHER),eq(false),anyString(),isNull(),isNull());
        assertTrue(body.getValue().contains("Actual &lt;behavior&gt; &amp; detail"));assertFalse(body.getValue().contains("{{"));
        assertFalse(subject.getValue().contains("Ana"));assertFalse(subject.getValue().contains("Horseplay"));
        if("SO".equals(status)) { assertFalse(body.getValue().toLowerCase(Locale.ROOT).contains("assignment"));assertTrue(subject.getValue().contains(lang.equals("es")?"Reconocimiento":"recognition")); }
    }
    @ParameterizedTest @org.junit.jupiter.params.provider.ValueSource(strings={"","invalid","one@example.test,two@example.test","Name <guardian@example.test>","guardian@example.test\r\nBcc: other@example.test"})
    void invalidRequiredGuardianFailsDurablyWithoutReroutingOrSending(String guardian) throws Exception {
        student.setParentEmail(guardian);create();dispatchLast();
        assertEquals("FAILED",last().getState());assertEquals("REQUIRED_RECIPIENT_INVALID",last().getReason());assertTrue(last().isRetrySafe());
        verify(router,never()).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        assertNotNull(read());
    }
    @Test void missingRequiredContentStaysFailedAndDoesNotLoseTheCase() throws Exception {
        realContent();var p=fresh();p.setInfractionDescription(List.of());p.setInfractionLevel("1");service.saveCreated(p);dispatchLast();
        assertEquals("FAILED",last().getState());assertEquals("CONTENT_OR_LINK_CONFIGURATION_INVALID",last().getReason());assertNotNull(read());
        verify(router,never()).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }
    @Test void teacherCreationEmbedsTheObligationInTheSameCaseInsertAndDoesNotSend() throws Exception {
        var schools=mock(SchoolRepository.class); var school=new School(); school.setMaxPunishLevel(4); when(schools.findBySchoolNameIgnoreCase("School")).thenReturn(Optional.of(school));
        var email=mock(EmailService.class);
        var utility=new PunishmentUtils(students,mock(org.springframework.data.mongodb.core.MongoTemplate.class),mock(InfractionRepository.class),mock(PunishRepository.class),schools,email,
                mock(EmployeeService.class),mock(GuidanceService.class),mock(OfficeReferralService.class),service);
        var result=utility.handleBasicClose("BC",new PunishmentFormRequest(),fresh(),student,java.time.LocalDate.now());
        assertEquals("BC",result.getPunishment().getStatus()); assertEquals("PENDING",last().getState()); assertEquals(TEACHER,last().getActor());
        assertEquals(student.getParentEmail(),result.getParentToEmail()); assertEquals(TEACHER,result.getTeacherToEmail());
        verify(mongo).insert(argThat((Punishment p)->p.getRequiredNotifications()!=null && p.getRequiredNotifications().size()==1));
        verifyNoInteractions(email); verify(router,never()).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }
    @Test void administratorClosureRetainsAuthorAndOwnerAndUsesAdministratorSender() throws Exception {
        create(); when(users.getCurrentStaffEmail()).thenReturn(ADMIN); action();
        var repo=mock(PunishRepository.class); when(repo.findByPunishmentId(ID)).thenAnswer(i->read()); when(repo.save(any())).thenAnswer(i->mongo.save((Punishment)i.getArgument(0)));
        var email=mock(EmailService.class); var closure=new PunishmentClosureServiceImpl(repo,students,mock(InfractionRepository.class),email,mock(EmailTemplateBuilderService.class),service);
        var result=closure.closeByPunishmentId(ID); assertEquals("CLOSED",result.getPunishment().getStatus());
        assertEquals(TEACHER,read().getOriginalAuthorEmail()); assertEquals(TEACHER,read().getResponsibleStaffEmail()); assertEquals(ADMIN,last().getActor());
        dispatchLast(); assertEquals("ACCEPTED",last().getState());
        verify(router).sendRequiredNotification(eq(student.getParentEmail()),argThat(cc->cc.contains(TEACHER)&&cc.contains(ADMIN)),anyString(),anyString(),anyList(),eq(ADMIN),eq(false),anyString(),isNull(),isNull()); verifyNoInteractions(email);
    }
    @Test void repeatedActionDoesNotAppendAnotherObligation() {
        var p=create(); String id=last().getNotificationId(); assertFalse(service.prepare(p,"CREATED")); assertEquals(id,last().getNotificationId()); assertEquals(1,p.getRequiredNotifications().size());
    }
    @Test void freshServiceCanProcessPendingFromRetainedMockSnapshot() {
        create(); var restarted=new RequiredNotificationService(mongo,env,students,users,router,templates);
        restarted.dispatchOne("punishment",ID,last().getNotificationId()); assertEquals("ACCEPTED",last().getState());
    }
    @Test void twoWorkersOnlyOneProviderAttempt() throws Exception {
        create(); String id=last().getNotificationId(); var second=new RequiredNotificationService(mongo,env,students,users,router,templates);
        var pool=Executors.newFixedThreadPool(2);
        try { var a=pool.submit(()->service.dispatchOne("punishment",ID,id)); var b=pool.submit(()->second.dispatchOne("punishment",ID,id)); a.get(10,TimeUnit.SECONDS); b.get(10,TimeUnit.SECONDS); }
        finally { pool.shutdownNow(); }
        verify(router,times(1)).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any()); assertEquals("ACCEPTED",last().getState());
    }
    @Test void timeoutBecomesUncertainAndCannotBeBlindlyRetried() throws Exception {
        create(); doThrow(new MessagingException("GMAIL_SEND_UNCERTAIN: synthetic timeout")).when(router).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        dispatchLast(); service.dispatchPending(); assertEquals("UNCERTAIN",last().getState()); assertFalse(last().isRetrySafe());
        assertThrows(IllegalStateException.class,()->service.retry("punishment",ID,last().getNotificationId())); verify(router,times(1)).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }
    @Test void confirmedRejectionCanBeQueuedForSafeRetryWithoutSendingInTheRetryRequest() throws Exception {
        create(); doThrow(new MessagingException("GMAIL_SEND_REJECTED: synthetic rejection")).when(router).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        dispatchLast(); assertEquals("FAILED",last().getState()); assertTrue(last().isRetrySafe());
        service.retry("punishment",ID,last().getNotificationId()); assertEquals("PENDING",last().getState()); verify(router,times(1)).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }
    @Test void gmailAuthorizationFailureRemainsUnresolved() throws Exception {
        create(); doThrow(new MessagingException("GMAIL_RECONNECT_REQUIRED: synthetic grant revoked")).when(router).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        dispatchLast(); assertEquals("RECONNECT_REQUIRED",last().getState()); assertTrue(last().isRetrySafe()); service.dispatchPending(); assertEquals(1,last().getAttempts());
    }
    @ParameterizedTest
    @CsvSource({"401,invalid,RECONNECT_REQUIRED,true,INVALID_AUTHORIZATION", "403,accessNotConfigured,FAILED,true,SERVICE_DISABLED",
            "403,domainPolicy,FAILED,true,DOMAIN_POLICY", "403,userRateLimitExceeded,FAILED,true,RATE_LIMITED",
            "429,quota,FAILED,true,RATE_LIMITED", "503,temporary,UNCERTAIN,false,PROVIDER_UNCERTAIN"})
    void structuredGmailFailurePersistsProviderAndSafeReasonWithoutAutomaticRetry(int status, String reason, String state, boolean retrySafe, String classification) throws Exception {
        var tokenStore=mock(com.reps.demogcloud.security.services.GoogleOAuthTokenStore.class);
        when(tokenStore.getToken(TEACHER)).thenReturn(Optional.of(new com.reps.demogcloud.security.services.GoogleOAuthTokenStore.GoogleOAuthToken(
                "synthetic-access",Instant.now().plusSeconds(3600),"synthetic-refresh",Set.of(com.reps.demogcloud.security.services.GoogleOAuthTokenStore.GMAIL_SCOPE))));
        var http=new org.springframework.web.client.RestTemplate();
        var provider=org.springframework.test.web.client.MockRestServiceServer.bindTo(http).build();
        provider.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.anything())
                .andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withStatus(org.springframework.http.HttpStatus.valueOf(status))
                        .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"errors\":[{\"reason\":\""+reason+"\"}],\"message\":\"private provider details\"}}"));
        var gmail=new com.reps.demogcloud.security.services.GoogleMailService(tokenStore,http);
        var request=new com.reps.demogcloud.security.models.gmail.GoogleMailSendRequest();
        request.setFrom(TEACHER);request.setTo("recipient@example.test");request.setSubject("Synthetic");request.setBody("Synthetic");
        var failure=assertThrows(com.reps.demogcloud.security.services.GoogleMailService.SendFailure.class,()->gmail.sendWithReceipt(TEACHER,request));
        create(); doThrow(failure).when(router).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        dispatchLast(); service.dispatchPending();
        assertEquals(state,last().getState()); assertEquals(retrySafe,last().isRetrySafe());
        assertEquals("GMAIL",last().getProvider()); assertEquals("GMAIL_HTTP_"+status+"_"+classification,last().getReason());
        assertNotNull(read()); assertEquals(ID,read().getPunishmentId());
        verify(router,times(1)).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        if(retrySafe) { service.retry("punishment",ID,last().getNotificationId()); assertEquals("PENDING",last().getState()); }
        else assertThrows(IllegalStateException.class,()->service.retry("punishment",ID,last().getNotificationId()));
        provider.verify();
    }
    @Test void interruptedClaimIsUncertainAfterRecoveryNotSentAgain() {
        create(); var n=notifications().get(0); n.put("state","DISPATCHING"); n.put("attemptId","crashed-worker"); n.put("claimedAt",Date.from(Instant.now().minusSeconds(600)));
        service.dispatchPending(); assertEquals("UNCERTAIN",last().getState()); assertEquals("WORKER_INTERRUPTED",last().getReason());
    }
    @Test void partialSmtpAcceptanceRecordsRecipientsAndBlocksWholeMessageRetry() throws Exception {
        create(); var nested=new SendFailedException("synthetic partial",null,new Address[]{new InternetAddress(student.getParentEmail())},new Address[]{new InternetAddress(student.getStudentEmail())},null);
        var failure=new MailSendException(Map.of("synthetic",nested)); doThrow(failure).when(router).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        dispatchLast(); assertEquals("UNCERTAIN",last().getState()); assertEquals("ACCEPTED",last().getRecipientOutcomes().get(student.getParentEmail())); assertFalse(last().isRetrySafe());
    }
    @Test void wrongSchoolOrRemovedActorBlocksBeforeProvider() throws Exception {
        create(); student.setSchool("Other"); dispatchLast(); assertEquals("FAILED",last().getState());
        verify(router,never()).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }
    @Test void statusAndRetryAreCaseAuthorizedAndManualResolutionRequiresAdministrator() {
        create(); doThrow(new AccessDeniedException("unrelated staff")).when(users).requireRecordedCaseCommunicationAccess(any(),any(),any(),any());
        assertThrows(AccessDeniedException.class,()->service.statuses("punishment",ID)); assertThrows(AccessDeniedException.class,()->service.retry("punishment",ID,last().getNotificationId()));
    }
    @Test void administratorCanRecordUncertainExternalResolutionWithoutResending() {
        create(); notifications().get(0).put("state","UNCERTAIN"); when(users.getCurrentStaffEmail()).thenReturn(ADMIN);
        service.resolve("punishment",ID,last().getNotificationId(),"Guardian contacted through approved school channel");
        assertEquals("RESOLVED",last().getState()); assertEquals(ADMIN,last().getResolvedBy());
    }
    @Test void changedGuardianStartsSeparateExternalThreadButKeepsInternalConversation() throws Exception {
        var p=create(); dispatchLast(); String conversation=read().getConversationId();
        p=read(); action(); service.prepare(p,"CLOSED"); mongo.save(p); student.setParentEmail("new-guardian@example.test");
        dispatchLast(); assertEquals(conversation,read().getConversationId()); assertNull(last().getInReplyTo());
        verify(router).sendRequiredNotification(eq("new-guardian@example.test"),any(),any(),any(),any(),any(),anyBoolean(),any(),isNull(),isNull());
    }
    @Test void crossAccountFollowupDoesNotReuseGmailThreadAndUsesPrivacyConsciousSubject() throws Exception {
        create(); when(router.sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any())).thenReturn(Map.of("provider","GMAIL","providerMessageId","gmail-id","gmailThreadId","teacher-thread"));
        dispatchLast(); var p=read(); String conversation=p.getConversationId(); String parent=last().getRfcMessageId();
        when(users.getCurrentStaffEmail()).thenReturn(ADMIN); action(); service.prepare(p,"CLOSED"); mongo.save(p); dispatchLast();
        assertEquals(conversation,read().getConversationId());
        verify(router).sendRequiredNotification(any(),any(),argThat(subject->!subject.contains("student")&&!subject.contains("Synthetic")),any(),any(),eq(ADMIN),eq(false),any(),eq(parent),isNull());
    }
    @Test void oldNotificationWithoutRecordedSubjectDoesNotForceAnIncompatibleGmailThread() throws Exception {
        create();when(router.sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any()))
                .thenReturn(Map.of("provider","GMAIL","providerMessageId","synthetic","gmailThreadId","old-thread"));
        dispatchLast();notifications().get(0).remove("subject");
        var p=read();action();service.prepare(p,"CLOSED");mongo.save(p);dispatchLast();
        assertEquals("ACCEPTED",last().getState());assertNotNull(last().getSubject());
        verify(router,times(2)).sendRequiredNotification(any(),any(),any(),any(),any(),eq(TEACHER),eq(false),any(),any(),isNull());
    }
    @Test void matchingSenderRecipientsAndRecordedSubjectCanReuseGmailThread() throws Exception {
        create();when(router.sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any()))
                .thenReturn(Map.of("provider","GMAIL","providerMessageId","synthetic","gmailThreadId","same-thread"));
        dispatchLast();var p=read();action();service.prepare(p,"CLOSED");mongo.save(p);dispatchLast();
        verify(router).sendRequiredNotification(any(),any(),any(),any(),any(),eq(TEACHER),eq(false),any(),any(),eq("same-thread"));
    }
    @Test void studentSubmissionIsAlwaysSystemAndCanOnlyAffectOwnStudentRecord() {
        var p=fresh(); when(users.isCurrentUserStudent()).thenReturn(true); when(users.getCurrentUserEmail()).thenReturn(student.getStudentEmail());
        assertTrue(service.prepare(p,"SUBMITTED")); assertTrue(p.getRequiredNotifications().get(0).isSystem()); assertNull(p.getRequiredNotifications().get(0).getActor());
        when(users.getCurrentUserEmail()).thenReturn("other-student@example.test"); assertThrows(AccessDeniedException.class,()->service.prepare(fresh(),"SUBMITTED"));
    }
    @Test void modelCallbacksDistinguishNewUuidEntitiesFromLegacyLoadedEntitiesWithoutVersion() throws Exception {
        for(Object entity:List.of(fresh(),new OfficeReferral(),new GuidanceReferral())) {
            var persistable=(org.springframework.data.domain.Persistable<?>)entity; assertTrue(persistable.isNew());
            new CasePersistenceCallbacks().onAfterConvert(entity,new Document(),"synthetic"); assertFalse(persistable.isNew());
        }
        var p=fresh(); service.prepare(p,"CREATED"); var document=write(p); assertTrue(document.containsKey("requiredNotifications"));
        assertFalse(new com.fasterxml.jackson.databind.ObjectMapper().valueToTree(p).has("requiredNotifications")); assertFalse(document.containsKey("persisted"));
    }
    @Test void disabledManagedPipelineDoesNotReadOrWriteOrSend() {
        env.setProperty("reps.email.notifications.enabled","false"); service.dispatchPending(); assertEquals(false,service.listStatuses().get("available")); verifyNoInteractions(mongo);
    }
    @Test void bulkItemsForSameStudentAreDistinctButRetryKeepsEachItemsCaseId() {
        String first=service.creationId("punishment",student.getStudentEmail(),List.of("First infraction"));
        String other=service.creationId("punishment",student.getStudentEmail(),List.of("Other infraction"));
        assertNotEquals(first,other); assertEquals(first,service.creationId("punishment",student.getStudentEmail(),List.of("First infraction")));
    }
    @Test void messageUsesItsRetainedActionContentRatherThanLaterCaseChanges() {
        create(); var changed=read(); changed.setInfractionName("Later case detail"); changed.setInfractionDescription(new ArrayList<>(List.of("Later description"))); mongo.save(changed);
        dispatchLast(); verify(templates).buildCaseMessage(eq("punishment"),eq("CREATED"),argThat(content -> "Synthetic".equals(content.get("infractionName")) && "synthetic description".equals(content.get("description"))),anyString(),anyString(),anyString(),any());
    }
    @Test void retryEndpointRejectsCookieOnlyAuthenticationBeforeQueueMutation() {
        create(); var controller=new com.reps.demogcloud.controllers.EmailController(mock(EmailService.class),service);
        assertThrows(AccessDeniedException.class,()->controller.retryNotification("punishment",ID,last().getNotificationId(),"Basic synthetic"));
        assertEquals("PENDING",last().getState());
    }
    @Test void hardDeletionCannotEraseRetainedObligationsEvenWhenPipelineIsDisabled() {
        create(); env.setProperty("reps.email.notifications.enabled","false");
        assertThrows(IllegalStateException.class,()->service.deleteLegacyCase("punishment",ID));
        verify(mongo,never()).remove(any(Query.class),anyString()); assertEquals(1,read().getRequiredNotifications().size());
    }
    @Test void officeAuthorOwnerAndActorAreSeparateAndMapToStableNotificationField() {
        var office=new OfficeReferral(); office.setOfficeReferralId("office-case"); office.setStudentEmail(student.getStudentEmail()); office.setSchool("School"); office.setTeacherEmail(TEACHER);
        service.prepare(office,"CREATED"); assertEquals(TEACHER,office.getOriginalAuthorEmail()); assertEquals(ADMIN,office.getResponsibleStaffEmail());
        assertEquals(TEACHER,office.getRequiredNotifications().get(0).getActor());
        Document intent=write(office).getList("requiredNotifications",Document.class).get(0); assertTrue(intent.containsKey("notificationId")); assertFalse(intent.containsKey("_id"));
    }
    @Test void duplicateCreationInsertReturnsExistingAuthorizedActionWithoutAnotherObligation() {
        create(); doThrow(new org.springframework.dao.DuplicateKeyException("synthetic duplicate")).when(mongo).insert(any(Punishment.class));
        Object existing=service.saveCreated(fresh()); assertEquals(ID,((Punishment)existing).getPunishmentId()); assertEquals(1,read().getRequiredNotifications().size());
    }
    @Test void confirmedUnsentSmtpRecipientsCanBeRetriedSafely() throws Exception {
        create(); Address[] unsent={new InternetAddress(student.getParentEmail()),new InternetAddress(student.getStudentEmail()),
                new InternetAddress(TEACHER),new InternetAddress(ADMIN),new InternetAddress("spotter@example.test")};
        var nested=new SendFailedException("synthetic rejected envelope",null,new Address[]{},unsent,null);
        doThrow(new MailSendException(Map.of("synthetic",nested))).when(router).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        dispatchLast(); assertEquals("FAILED",last().getState()); assertTrue(last().isRetrySafe());
    }
    @Test void nonAdministratorCannotResolveAnUncertainOutcome() {
        create(); notifications().get(0).put("state","UNCERTAIN");
        doThrow(new AccessDeniedException("administrator required")).when(users).requireAdministratorInSchool("School");
        assertThrows(AccessDeniedException.class,()->service.resolve("punishment",ID,last().getNotificationId(),"External confirmation")); assertEquals("UNCERTAIN",last().getState());
    }
    @Test void providerAcceptanceWithLostDatabaseAcknowledgmentIsNeverResentAutomatically() throws Exception {
        create();
        doAnswer(i->{ synchronized(lock) {
            Update update=i.getArgument(1);
            if("ACCEPTED".equals(update.getUpdateObject().get("$set",Document.class).get("requiredNotifications.$.state")))
                throw new org.springframework.dao.DataAccessResourceFailureException("synthetic database acknowledgement lost");
            boolean matches=match(i.getArgument(0)); if(matches) apply(i.getArgument(0),update); return UpdateResult.acknowledged(matches?1:0,matches?1L:0L,null);
        }}).when(mongo).updateFirst(any(Query.class),any(Update.class),eq("Punishments"));
        dispatchLast(); assertEquals("UNCERTAIN",last().getState()); service.dispatchPending();
        verify(router,times(1)).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }
    @Test void disabledDispatchRetainsPendingWithoutProviderCalls() throws Exception {
        create(); env.setProperty("reps.email.notifications.dispatch-enabled","false"); dispatchLast(); service.dispatchPending();
        assertEquals("PENDING",last().getState()); verify(router,never()).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }
    @Test void dispatchRechecksSpotterRelationshipAndExcludesRevokedSubscriber() throws Exception {
        create();when(users.hasIndependentStudentAccess("spotter@example.test",student)).thenReturn(false);
        dispatchLast();assertFalse(last().getRecipients().contains("spotter@example.test"));assertEquals("ACCEPTED",last().getState());
    }
    @Test void dispatchIncludesOnlyIndependentlyAuthorizedSpotter() throws Exception {
        create();when(users.hasIndependentStudentAccess("spotter@example.test",student)).thenReturn(true);
        dispatchLast();assertTrue(last().getRecipients().contains("spotter@example.test"));assertEquals("ACCEPTED",last().getState());
    }

}
