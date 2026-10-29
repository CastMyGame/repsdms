package com.reps.demogcloud.services.tenancy;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.config.*;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.*;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.*;
import org.springframework.core.env.MapPropertySource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockServletContext;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual MongoDB, repositories, JWT signing/revocation, SecurityConfig and domain services.
 * Production Student, Guidance and HistoricalCase controllers are covered; transports and setup mail are mocked. */
@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
class CanonicalFullWorkflowMongoSecurityTest {
    static MongoClient client;static MongoTemplate mongo;static String database;
    static AnnotationConfigWebApplicationContext app;static MockMvc mvc;static JwtUtils jwt;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @EnableMongoRepositories(basePackages={"com.reps.demogcloud.data","com.reps.demogcloud.security.models"})
    @Import({SecurityConfig.class,JwtFilterRequest.class,CustomUserDetailsService.class,CanonicalAccountContext.class,CanonicalCoreWorkflows.class,CanonicalDomainAccess.class,CanonicalWorkflowAccess.class,HistoricalCaseAccess.class,
        com.reps.demogcloud.security.controllers.AccountAccessController.class,com.reps.demogcloud.security.services.AccountAccessService.class,com.reps.demogcloud.controllers.PunishController.class,com.reps.demogcloud.controllers.OfficeReferralController.class,com.reps.demogcloud.controllers.AssignmentLifecycleController.class,
        com.reps.demogcloud.services.PunishmentService.class,com.reps.demogcloud.services.OfficeReferralService.class,com.reps.demogcloud.utils.OfficeReferralUtils.class,com.reps.demogcloud.utils.PunishmentUtils.class,
        com.reps.demogcloud.services.punishment.PunishmentCreationService.class,com.reps.demogcloud.services.punishment.PunishmentQueryService.class,com.reps.demogcloud.services.punishment.PunishmentUpdateService.class,com.reps.demogcloud.services.punishment.PunishmentClosureServiceImpl.class,
        com.reps.demogcloud.services.AssignmentService.class,com.reps.demogcloud.services.AssignmentResolver.class,com.reps.demogcloud.services.AssignmentLifecycleService.class,
        com.reps.demogcloud.services.currency.CurrencyService.class,com.reps.demogcloud.services.currency.CurrencyTransactions.class,com.reps.demogcloud.services.currency.CanonicalCurrencyTransactions.class,
        CanonicalEmailOwnership.class,
        com.reps.demogcloud.controllers.StudentController.class,com.reps.demogcloud.controllers.GuidanceController.class,com.reps.demogcloud.controllers.HistoricalCaseController.class,
        com.reps.demogcloud.services.UserContextService.class,com.reps.demogcloud.services.WorkflowAccessService.class,com.reps.demogcloud.services.UserAdministrationService.class,com.reps.demogcloud.services.StudentService.class,
        com.reps.demogcloud.services.student.StudentQueryService.class,com.reps.demogcloud.services.student.StudentAdminService.class,com.reps.demogcloud.services.student.StudentMutationService.class,com.reps.demogcloud.services.student.StudentSpotterService.class,com.reps.demogcloud.services.student.StudentPointService.class,
        com.reps.demogcloud.security.services.AuthService.class,com.reps.demogcloud.services.GuidanceService.class,com.reps.demogcloud.services.employee.EmployeeClassService.class,com.reps.demogcloud.services.email.CasePersistenceCallbacks.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean MongoTemplate mongoTemplate(){return mongo;}
        @Bean JwtRevocationStore revocations(){return new JwtRevocationStore(mongo);}
        @Bean JwtUtils jwt(JwtRevocationStore store){return new JwtUtils(JwtTestSupport.environment(),store);}
        @Bean CustomOAuth2UserService oidc(){return mock(CustomOAuth2UserService.class);}
        @Bean OAuth2LoginSuccessHandler success(){return mock(OAuth2LoginSuccessHandler.class);}
        @Bean OAuth2LoginFailureHandler failure(){return mock(OAuth2LoginFailureHandler.class);}
        @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}
        @Bean com.reps.demogcloud.services.EmployeeService unusedEmployees(){return mock(com.reps.demogcloud.services.EmployeeService.class);}
        @Bean com.reps.demogcloud.services.email.EmailSenderService sender(){return mock(com.reps.demogcloud.services.email.EmailSenderService.class);}
        @Bean com.reps.demogcloud.services.EmailService mail(){return mock(com.reps.demogcloud.services.EmailService.class);}
        @Bean com.reps.demogcloud.services.email.EmailRoutingService router(){var router=mock(com.reps.demogcloud.services.email.EmailRoutingService.class);when(router.isPhase1RoutingEnabled()).thenReturn(true);return router;}
        @Bean com.reps.demogcloud.services.email.RequiredNotificationService notifications(org.springframework.core.env.Environment env,StudentRepository students,com.reps.demogcloud.services.UserContextService users,com.reps.demogcloud.services.email.EmailRoutingService router,com.reps.demogcloud.services.email.EmailTemplateBuilderService templates){return spy(new com.reps.demogcloud.services.email.RequiredNotificationService(mongo,env,students,users,router,templates));}
        @Bean com.reps.demogcloud.services.email.EmailTemplateBuilderService templates(){var builder=mock(com.reps.demogcloud.services.email.EmailTemplateBuilderService.class);when(builder.buildManagedSubject(anyString(),anyMap(),any())).thenReturn("Synthetic case");when(builder.buildCaseMessage(anyString(),anyString(),anyMap(),anyString(),anyString(),any(),any())).thenReturn("<p>Synthetic content</p>");return builder;}
        @Bean com.reps.demogcloud.utils.StudentUtils studentUtils(){return mock(com.reps.demogcloud.utils.StudentUtils.class);}
        @Bean com.reps.demogcloud.utils.SchoolUtils schoolUtils(){return mock(com.reps.demogcloud.utils.SchoolUtils.class);}
        @Bean org.springframework.security.crypto.password.PasswordEncoder encoder(){var encoder=mock(org.springframework.security.crypto.password.PasswordEncoder.class);when(encoder.encode(any())).thenReturn("synthetic-encoded");return encoder;}
    }
    @BeforeAll static void start(){
        var uri=new ConnectionString(System.getenv("REPS_TENANCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")));assertNull(uri.getCredential());assertNull(uri.getDatabase());
        database="reps_a7_workflow_test_"+UUID.randomUUID().toString().replace("-","");client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);
        app=new AnnotationConfigWebApplicationContext();app.setServletContext(new MockServletContext());app.getEnvironment().setActiveProfiles("tenant-isolated-verification");
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated",new java.util.HashMap<>(Map.of("reps.tenancy.core-preparation-enabled","true","reps.tenancy.domain-preparation-enabled","true","reps.tenancy.currency-preparation-enabled","true","reps.tenancy.email-preparation-enabled","true","reps.email.notifications.enabled","true","reps.email.notifications.dispatch-enabled","true","auth.sso.google.enabled","false","reps.accounts.public-base-url","http://localhost:3000"))));
        app.register(Config.class);app.refresh();jwt=app.getBean(JwtUtils.class);mvc=MockMvcBuilders.webAppContextSetup(app)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
    }
    @AfterAll static void stop(){try{if(app!=null)app.close();if(mongo!=null && database.matches("reps_a7_workflow_test_[0-9a-f]{32}"))mongo.getDb().drop();}finally{if(client!=null)client.close();}}
    @BeforeEach void seed(){
        reset(app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class));clearInvocations(app.getBean(com.reps.demogcloud.services.email.EmailRoutingService.class));
        for(String name:mongo.getCollectionNames())mongo.getCollection(name).deleteMany(new Document());
        for(String school:List.of("A","B")){
            var s=new School();s.setSchoolIdNumber("school-"+school);s.setSchoolName("Same School");s.setMaxPunishLevel(4);mongo.insert(s);
            account("admin"+school,"ADMIN",school,false);account("teacher"+school,"TEACHER",school,true);account("guide"+school,"GUIDANCE",school,false);account("other"+school,"TEACHER",school,false);
            account("unassigned"+school,"GUIDANCE",school,false);account("child"+school,"STUDENT",school,false);
            for(var kind:CanonicalCoreWorkflows.Kind.values())mongo.getCollection(kind.collection).insertOne(new Document("_id",kind.name()+school)
                    .append("tenantSchoolId","school-"+school).append("studentEmail","child"+school+"@example.test").append("teacherEmail","teacher"+school+"@example.test")
                    .append("revision",0L).append("assignmentTemplateId","template"+school));
            mongo.getCollection("assignment_templates").insertOne(new Document("_id","template"+school).append("tenantSchoolId","school-"+school));
        }
        for(String name:List.of("Dress Code","Positive Behavior Shout Out!")){var infraction=new com.reps.demogcloud.models.infraction.Infraction();infraction.setInfractionId(name);infraction.setInfractionName(name);infraction.setInfractionLevel("1");mongo.insert(infraction);}
        var template=new com.reps.demogcloud.models.assignments.AssignmentTemplate();template.setId("dress-template");template.setInfractionName("Dress Code");template.setLevel(1);template.setTenantSchoolId("school-A");template.setScope(com.reps.demogcloud.models.assignments.AssignmentTemplate.Scope.SCHOOL_DEFAULT);template.setVisibility(com.reps.demogcloud.models.assignments.AssignmentTemplate.Visibility.SCHOOL);
        var q=new com.reps.demogcloud.models.assignments.AssignmentTemplate.TemplateQuestion();q.setId("q1");q.setType(com.reps.demogcloud.models.assignments.AssignmentTemplate.QuestionType.EXPLORATORY_OPEN);q.setPrompt("Reflect");q.setMinLength(1);template.setQuestions(List.of(q));mongo.insert(template);
        mongo.getCollection("TrackedBehaviorEvents").deleteMany(new Document());
        mongo.getCollection("currency_operations").insertOne(new Document("_id","retained-receipt"));
    }
    static void account(String name,String role,String school,boolean roster){
        var u=new UserModel();u.setId(name);u.setUsername(name+"@example.test");u.setPassword("synthetic");u.setRoles(Set.of(new RoleModel(null,role)));u.setTenantSchoolId("school-"+school);u.setSchool("Same School");mongo.insert(u);
        if(role.equals("STUDENT")){
            var s=new Student();s.setStudentIdNumber(name);s.setStudentEmail(u.getUsername());s.setTenantSchoolId(u.getTenantSchoolId());s.setSchool("Same School");s.setCurrency(12);s.setParentEmail("guardian@example.test");s.setPreferredLanguage("en");s.setGuidanceEmail("guide"+school+"@example.test");s.setAdminEmail("admin"+school+"@example.test");s.setSpotters(List.of("other"+school+"@example.test"));mongo.insert(s);
        }else{
            var e=new Employee();e.setEmployeeId(name);e.setEmail(u.getUsername());e.setTenantSchoolId(u.getTenantSchoolId());e.setSchool("Same School");e.setRoles(u.getRoles());e.setCurrency(30);
            if(roster){var c=new Employee.ClassRoster();c.setClassRoster(List.of("child"+school+"@example.test"));e.setClasses(List.of(c));}mongo.insert(e);
        }
    }
    String token(String name){return jwt.generateToken(app.getBean(CustomUserDetailsService.class).loadUserByUsername(name+"@example.test"));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getAs(String path,String actor){return get(path).header("Authorization","Bearer "+token(actor));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAs(String path,String actor){return post(path).header("Authorization","Bearer "+token(actor)).contentType(MediaType.APPLICATION_JSON);}
    String createGuidance()throws Exception{
        var response=mvc.perform(postAs("/guidance/v1/guidance/new","teacherA").content("{\"guidance\":{\"studentEmail\":\"childA@example.test\",\"referralDescription\":[\"Initial historical facts\"]}}"));
        var result=response.andExpect(status().isAccepted()).andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("guidance").get(0).get("guidanceId").asText();
    }

    String body(String email,String infraction,int amount){return "[{\"studentEmail\":\""+email+"\",\"infractionName\":\""+infraction+"\",\"infractionDescription\":\"Original facts\",\"currency\":"+amount+"}]";}
    String createCase(String actor,String name,int amount,String action)throws Exception{
        var result=mvc.perform(postAs("/punish/v1/startPunish/formList",actor).header("X-REPS-Action-Id",action).content(body("childA@example.test",name,amount))).andExpect(status().isOk()).andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get(0).get("punishment").get("punishmentId").asText();
    }
    @Test void ordinaryCreationAssignmentAndTenantDenialsAreRealDomainOperations()throws Exception{
        String action="00000000-0000-4000-8000-000000000001";String id=createCase("teacherA","Dress Code",0,action);
        var row=mongo.getCollection("Punishments").find(new Document("_id",id)).first();assertEquals("school-A",row.getString("tenantSchoolId"));assertEquals("dress-template",row.getString("assignmentTemplateId"));assertEquals(1,row.getList("requiredNotifications",Document.class).size());assertNotNull(row.get("historicalAccess"));
        assertEquals(id,createCase("teacherA","Dress Code",0,action));assertEquals(1,mongo.getCollection("Punishments").countDocuments(new Document("_id",id)));
        mvc.perform(getAs("/assignments/v1/cases/"+id,"childA")).andExpect(status().isOk()).andExpect(jsonPath("$.available").value(true));
        mvc.perform(getAs("/assignments/v1/cases/"+id,"childB")).andExpect(status().isForbidden());
        long count=mongo.getCollection("Punishments").countDocuments();
        for(String actor:List.of("teacherB","adminB","otherA","unassignedA"))mvc.perform(postAs("/punish/v1/startPunish/formList",actor).header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(body("childA@example.test","Dress Code",0))).andExpect(status().isForbidden());
        assertEquals(count,mongo.getCollection("Punishments").countDocuments());verify(app.getBean(com.reps.demogcloud.services.email.EmailRoutingService.class),never()).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
    }
    @Test void actualAssignmentSavesRejectStaleWritesAndNeverGrantFormerAuthorReview()throws Exception{
        String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());
        String command="{\"version\":0,\"actionId\":\"synthetic-save-01\",\"answers\":{\"q1\":{\"text\":\"Saved work\"}},\"currentQuestionId\":\"q1\"}";
        mvc.perform(put("/assignments/v1/cases/"+id+"/save").header("Authorization","Bearer "+token("childA")).contentType(MediaType.APPLICATION_JSON).content(command)).andExpect(status().isOk());
        mvc.perform(getAs("/assignments/v1/cases/"+id,"childA")).andExpect(status().isOk()).andExpect(jsonPath("$.work.answers.q1.text").value("Saved work"));
        mvc.perform(put("/assignments/v1/cases/"+id+"/save").header("Authorization","Bearer "+token("childA")).contentType(MediaType.APPLICATION_JSON).content(command.replace("synthetic-save-01","synthetic-save-02"))).andExpect(status().isConflict());
        mongo.getCollection("employee").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("classes",List.of())));
        mvc.perform(getAs("/history/v1/cases/punishment/"+id,"teacherA")).andExpect(status().isOk());
        mvc.perform(put("/assignments/v1/cases/"+id+"/review").header("Authorization","Bearer "+token("teacherA")).contentType(MediaType.APPLICATION_JSON).content(command)).andExpect(status().isForbidden());
    }
    void manifest()throws Exception{
        var actors=new ArrayList<Document>();
        for(String school:List.of("A","B"))for(String role:List.of("teacher","admin","guide")){
            String id=role+school;actors.add(new Document("accountId",id).append("profileId",id).append("tenantSchoolId","school-"+school).append("historyComplete",true).append("evidenceReference","synthetic-reviewed-history").append("legacyNamespaces",List.of(new Document("actor",(id+"@example.test").toLowerCase(Locale.ROOT)).append("school","same school"))));
        }
        var manifest=new Document("_id","synthetic-review").append("schema","reps-currency-replay/v1").append("historyReviewed",true).append("legacyWritersDrained",true).append("reviewReference","synthetic-reviewed").append("actors",actors).append("receipts",List.of());
        var type=Class.forName("com.reps.demogcloud.services.currency.CurrencyReplayManifest");var fingerprint=type.getDeclaredMethod("fingerprint",Document.class);fingerprint.setAccessible(true);
        mongo.insert(manifest,"currency_replay_manifests");app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("review",Map.of("reps.tenancy.currency-replay-manifest-id","synthetic-review","reps.tenancy.currency-replay-manifest-sha256",fingerprint.invoke(null,manifest))));
    }
    @Test void productionCurrencyServiceCommitsAndReplaysOnce()throws Exception{
        manifest();String action=UUID.randomUUID().toString();String id=createCase("teacherA","Positive Behavior Shout Out!",3,action);assertEquals(id,createCase("teacherA","Positive Behavior Shout Out!",3,action));
        assertEquals(27,mongo.findById("teacherA",Employee.class).getCurrency());assertEquals(15,mongo.findById("childA",Student.class).getCurrency());
        var recognition=mongo.getCollection("Punishments").find(new Document("_id",id)).first();assertEquals("school-A",recognition.getString("tenantSchoolId"));assertEquals(1,recognition.getList("requiredNotifications",Document.class).size());assertEquals(1,mongo.getCollection("currency_operations").countDocuments(new Document("receiptVersion",2)));
    }
    @Test void productionBulkCurrencyRollsBackIfSecondRecognitionPersistenceFails()throws Exception{
        manifest();account("secondA","STUDENT","A",false);var employee=mongo.findById("teacherA",Employee.class);employee.getClasses().get(0).setClassRoster(List.of("childA@example.test","secondA@example.test"));app.getBean(EmployeeRepository.class).save(employee);
        var notifications=app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class);var calls=new java.util.concurrent.atomic.AtomicInteger();doAnswer(call->{if(calls.incrementAndGet()==2)throw new IllegalStateException("Synthetic persistence interruption");return call.callRealMethod();}).when(notifications).saveCurrencyCreated(any());
        String batch=body("childA@example.test","Positive Behavior Shout Out!",3).replace("]",",")+body("secondA@example.test","Positive Behavior Shout Out!",3).substring(1);
        long cases=mongo.getCollection("Punishments").countDocuments();mvc.perform(postAs("/punish/v1/startPunish/formList","teacherA").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(batch)).andExpect(status().is5xxServerError());
        assertEquals(30,mongo.findById("teacherA",Employee.class).getCurrency());assertEquals(12,mongo.findById("childA",Student.class).getCurrency());assertEquals(12,mongo.findById("secondA",Student.class).getCurrency());assertEquals(cases,mongo.getCollection("Punishments").countDocuments());assertEquals(0,mongo.getCollection("currency_operations").countDocuments(new Document("receiptVersion",2)));
    }
    @Test void recordedOwnerWorkerSendsOnceThroughMockAndRecoversInterruptedClaimWithoutResend()throws Exception{
        String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());var service=app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class);var router=app.getBean(com.reps.demogcloud.services.email.EmailRoutingService.class);
        when(router.sendRequiredNotification(anyString(),anyList(),anyString(),anyString(),anyList(),any(),anyBoolean(),anyString(),any(),any())).thenReturn(Map.of("provider","SMTP","providerMessageId","synthetic-message"));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();service.dispatchPending();service.dispatchPending();
        var row=mongo.getCollection("Punishments").find(new Document("_id",id)).first();var intent=row.getList("requiredNotifications",Document.class).get(0);assertEquals("ACCEPTED",intent.getString("state"));verify(router,times(1)).sendRequiredNotification(anyString(),anyList(),anyString(),anyString(),anyList(),eq("teacherA@example.test"),eq(false),anyString(),any(),any());
        assertNull(app.getBean(CanonicalEmailOwnership.class).recordedActor());
        mongo.getCollection("Punishments").updateOne(new Document("_id",id),new Document("$set",new Document("requiredNotifications.0.state","DISPATCHING").append("requiredNotifications.0.claimedAt",Date.from(java.time.Instant.now().minusSeconds(600)))));
        service.dispatchPending();assertEquals("UNCERTAIN",mongo.getCollection("Punishments").find(new Document("_id",id)).first().getList("requiredNotifications",Document.class).get(0).getString("state"));verify(router,times(1)).sendRequiredNotification(anyString(),anyList(),anyString(),anyString(),anyList(),any(),anyBoolean(),anyString(),any(),any());
    }
    @Test void revokedRelationshipPreventsWorkerSendButRetainsHistoricalSnapshot()throws Exception{
        String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());mongo.getCollection("employee").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("classes",List.of())));
        org.springframework.security.core.context.SecurityContextHolder.clearContext();app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class).dispatchPending();
        var intent=mongo.getCollection("Punishments").find(new Document("_id",id)).first().getList("requiredNotifications",Document.class).get(0);assertEquals("FAILED",intent.getString("state"));assertEquals(0,intent.getInteger("attempts"));
        verify(app.getBean(com.reps.demogcloud.services.email.EmailRoutingService.class),never()).sendRequiredNotification(any(),any(),any(),any(),any(),any(),anyBoolean(),any(),any(),any());
        mvc.perform(getAs("/history/v1/cases/punishment/"+id,"teacherA")).andExpect(status().isOk());mvc.perform(getAs("/punish/v1/"+id,"teacherA")).andExpect(status().isForbidden());
    }

    @Test void emailGatesDisabledCannotBypassCaseAuthorizationOrLoseTheCase()throws Exception{
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("email-disabled",Map.of("reps.email.notifications.enabled","false")));
        try {
            String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());var row=mongo.getCollection("Punishments").find(new Document("_id",id)).first();assertEquals("school-A",row.getString("tenantSchoolId"));
            assertTrue(!row.containsKey("requiredNotifications") || row.getList("requiredNotifications",Document.class).isEmpty());
            mvc.perform(postAs("/punish/v1/startPunish/formList","otherA").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(body("childA@example.test","Dress Code",0))).andExpect(status().isForbidden());
        }finally{app.getEnvironment().getPropertySources().remove("email-disabled");}
    }

    @Test void pendingCanonicalStudentCanUseSchoolAssistedSetupWithoutTenantChanges()throws Exception{
        account("setup","STUDENT","A",false);mongo.getCollection("users").updateOne(new Document("_id","setup"),new Document("$set",new Document("passwordSetupRequired",true)));
        var profile=mongo.findById("teacherA",Employee.class);profile.getClasses().get(0).setClassRoster(List.of("childA@example.test","setup@example.test"));app.getBean(EmployeeRepository.class).save(profile);
        mvc.perform(postAs("/accounts/v1/students/setup@example.test/setup","teacherB").content("{\"identityVerified\":true,\"privateEmailAllowed\":false}")).andExpect(status().isForbidden());
        var result=mvc.perform(postAs("/accounts/v1/students/setup@example.test/setup","teacherA").content("{\"identityVerified\":true,\"privateEmailAllowed\":false}")).andExpect(status().isOk()).andReturn();
        String credential=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("credential").asText();
        String completion="{\"username\":\"setup@example.test\",\"credential\":\""+credential+"\",\"newPassword\":\"Synthetic-long-password!\"}";
        mvc.perform(post("/account-access/complete").contentType(MediaType.APPLICATION_JSON).content(completion)).andExpect(status().isNoContent());
        var user=mongo.findById("setup",UserModel.class);assertFalse(user.isPasswordSetupRequired());assertEquals("school-A",user.getTenantSchoolId());assertEquals("STUDENT",user.getRoles().iterator().next().getRole());
        mvc.perform(post("/account-access/complete").contentType(MediaType.APPLICATION_JSON).content(completion)).andExpect(status().isBadRequest());
    }
    @Test void canonicalGoogleSetupRequiresPersistedMatchingMembershipAndNeverRepairsOwnership()throws Exception{
        account("newstaff","TEACHER","A",false);mongo.getCollection("users").updateOne(new Document("_id","newstaff"),new Document("$set",new Document("passwordSetupRequired",true)));
        var service=app.getBean(AccountAccessService.class);var user=mongo.findById("newstaff",UserModel.class);service.googleEstablished(user,true);assertFalse(mongo.findById("newstaff",UserModel.class).isPasswordSetupRequired());
        user.setTenantSchoolId("school-B");var forged=user;assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.googleEstablished(forged,true));assertEquals("school-A",mongo.findById("newstaff",UserModel.class).getTenantSchoolId());
        mongo.getCollection("employee").updateOne(new Document("_id","newstaff"),new Document("$set",new Document("tenantSchoolId","school-B")));var current=mongo.findById("newstaff",UserModel.class);assertThrows(org.springframework.security.access.AccessDeniedException.class,()->service.googleEstablished(current,true));
    }

    @Test void officeCreationReplayAndAdminClosureUseRealOwnedCaseAndNoAssignment()throws Exception{
        String action=UUID.randomUUID().toString(),body="[{\"studentEmail\":\"childA@example.test\",\"referralDescription\":[\"Original office facts\"],\"referralCode\":{\"codeKey\":1,\"codeName\":\"Synthetic offense\"}}]";
        var result=mvc.perform(postAs("/officeReferral/v1/startPunish/adminReferral","teacherA").header("X-REPS-Action-Id",action).content(body)).andExpect(status().isAccepted()).andReturn();
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get(0).get("officeReferralId").asText();
        mvc.perform(postAs("/officeReferral/v1/startPunish/adminReferral","teacherA").header("X-REPS-Action-Id",action).content(body)).andExpect(status().isAccepted());assertEquals(1,mongo.getCollection("OfficeReferrals").countDocuments(new Document("_id",id)));
        String closure="{\"id\":\""+id+"\",\"comment\":\"Private later outcome\"}";
        mvc.perform(postAs("/officeReferral/v1/closeId","teacherA").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(closure)).andExpect(status().isForbidden());
        mvc.perform(postAs("/officeReferral/v1/closeId","adminB").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(closure)).andExpect(status().isForbidden());
        mvc.perform(postAs("/officeReferral/v1/closeId","adminA").header("X-REPS-Action-Id",UUID.randomUUID().toString()).content(closure)).andExpect(status().isAccepted());
        var row=mongo.getCollection("OfficeReferrals").find(new Document("_id",id)).first();assertEquals("school-A",row.getString("tenantSchoolId"));assertEquals("CLOSED",row.getString("status"));assertFalse(row.containsKey("assignmentWork"));
        assertEquals(2,row.getList("requiredNotifications",Document.class).size());
        var history=mvc.perform(getAs("/history/v1/cases/office/"+id,"teacherA")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();assertFalse(history.contains("Private later outcome"));
    }
    @Test void actualStudentSubmissionRejectionAndResubmissionPreserveHistoryAndSafeReviewReplay()throws Exception{
        String id=createCase("teacherA","Dress Code",0,UUID.randomUUID().toString());var mapper=new com.fasterxml.jackson.databind.ObjectMapper();
        java.util.function.BiFunction<Integer,String,String> submission=(v,action)->{try{return mapper.writeValueAsString(Map.of("version",v,"actionId",action,"answers",Map.of("q1",Map.of("text","Completed reflection"))));}catch(Exception e){throw new RuntimeException(e);}};
        mvc.perform(put("/assignments/v1/cases/"+id+"/submit").header("Authorization","Bearer "+token("childA")).contentType(MediaType.APPLICATION_JSON).content(submission.apply(0,"submit-action-0001"))).andExpect(status().isOk());
        String reject=mapper.writeValueAsString(Map.of("version",1,"actionId","review-action-0001","decision","REJECT","feedback","Please revise"));
        mvc.perform(put("/assignments/v1/cases/"+id+"/review").header("Authorization","Bearer "+token("teacherA")).contentType(MediaType.APPLICATION_JSON).content(reject)).andExpect(status().isOk());
        mvc.perform(put("/assignments/v1/cases/"+id+"/submit").header("Authorization","Bearer "+token("childA")).contentType(MediaType.APPLICATION_JSON).content(submission.apply(2,"submit-action-0002"))).andExpect(status().isOk());
        String accept=mapper.writeValueAsString(Map.of("version",3,"actionId","review-action-0002","decision","ACCEPT"));
        for(int i=0;i<2;i++)mvc.perform(put("/assignments/v1/cases/"+id+"/review").header("Authorization","Bearer "+token("teacherA")).contentType(MediaType.APPLICATION_JSON).content(accept)).andExpect(status().isOk());
        var row=mongo.getCollection("Punishments").find(new Document("_id",id)).first();assertEquals("CLOSED",row.getString("status"));assertEquals(List.of("Original facts"),row.get("infractionDescription"));assertEquals(2,row.get("assignmentWork",Document.class).getList("submissions",Document.class).size());
    }

    @Test void concurrentProductionAwardRetriesCommitOnlyOnce()throws Exception{
        manifest();String action=UUID.randomUUID().toString();var executor=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var start=new java.util.concurrent.CountDownLatch(1);java.util.concurrent.Callable<MvcResult> call=()->{start.await();return mvc.perform(postAs("/punish/v1/startPunish/formList","teacherA").header("X-REPS-Action-Id",action).content(body("childA@example.test","Positive Behavior Shout Out!",3))).andReturn();};
            var first=executor.submit(call);var second=executor.submit(call);start.countDown();var outcomes=List.of(first.get(20,java.util.concurrent.TimeUnit.SECONDS),second.get(20,java.util.concurrent.TimeUnit.SECONDS));
            assertTrue(outcomes.stream().anyMatch(result->result.getResponse().getStatus()==200));
            for(var result:outcomes)assertTrue(Set.of(200,409).contains(result.getResponse().getStatus()),"Only success or bounded concurrent conflict is permitted");
            String replay=createCase("teacherA","Positive Behavior Shout Out!",3,action);
            for(var result:outcomes)if(result.getResponse().getStatus()==200)assertEquals(replay,new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get(0).get("punishment").get("punishmentId").asText());
            assertEquals(27,mongo.findById("teacherA",Employee.class).getCurrency());assertEquals(15,mongo.findById("childA",Student.class).getCurrency());assertEquals(1,mongo.getCollection("currency_operations").countDocuments(new Document("receiptVersion",2)));
        }finally{executor.shutdownNow();}
    }

}
