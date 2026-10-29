package com.reps.demogcloud.security.config;

import com.mongodb.ConnectionString;
import com.mongodb.client.*;
import com.reps.demogcloud.controllers.EmailController;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.*;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.email.*;
import com.reps.demogcloud.services.tenancy.CanonicalAccountContext;
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
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual production controller, orchestration, sender boundary, JWT/filter chain and Mongo repositories.
 * All A7 integration flags stay absent/disabled. Only translations, OAuth and transports are mocked. */
@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
class EmailCommunicationSecurityTest {
    static MongoClient client; static MongoTemplate mongo; static String database;
    static AnnotationConfigWebApplicationContext app; static MockMvc mvc; static JwtUtils jwt;
    static EmailRoutingService router; static Map<String,Object> properties;

    @Configuration @EnableWebMvc @EnableWebSecurity
    @EnableMongoRepositories(basePackages={"com.reps.demogcloud.data","com.reps.demogcloud.security.models"})
    @Import({SecurityConfig.class,JwtFilterRequest.class,CustomUserDetailsService.class,
        CanonicalAccountContext.class,ClassAnnouncementAccess.class,UserContextService.class,
        EmailController.class,EmailService.class,EmailNotificationService.class,EmailSenderService.class,
        RequiredNotificationService.class,GlobalExceptionHandler.class})
    static class Config {
        @Bean MongoTemplate mongoTemplate(){return mongo;}
        @Bean JwtRevocationStore revocations(){return new JwtRevocationStore(mongo);}
        @Bean JwtUtils jwt(JwtRevocationStore store){return new JwtUtils(JwtTestSupport.environment(),store);}
        @Bean CustomOAuth2UserService oidc(){return mock(CustomOAuth2UserService.class);}
        @Bean OAuth2LoginSuccessHandler success(){return mock(OAuth2LoginSuccessHandler.class);}
        @Bean OAuth2LoginFailureHandler failure(){return mock(OAuth2LoginFailureHandler.class);}
        @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}
        @Bean EmailReferralService referral(){return mock(EmailReferralService.class);}
        @Bean EmailRoutingService router(){return mock(EmailRoutingService.class);}
        @Bean org.springframework.mail.javamail.JavaMailSender smtp(){return mock(org.springframework.mail.javamail.JavaMailSender.class);}
        @Bean EmailTemplateBuilderService templates(){var result=mock(EmailTemplateBuilderService.class);when(result.normalizeLanguage(any())).thenReturn("en");return result;}
    }
    @BeforeAll static void start(){
        var uri=new ConnectionString(System.getenv("REPS_TENANCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")));
        assertNull(uri.getCredential());assertNull(uri.getDatabase());
        database="reps_announcement_test_"+UUID.randomUUID().toString().replace("-","");
        client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);
        assertTrue(mongo.executeCommand(new Document("hello",1)).containsKey("setName"),"Disposable replica set required");
        app=new AnnotationConfigWebApplicationContext();app.setServletContext(new MockServletContext());
        properties=new HashMap<>(Map.of("auth.sso.google.enabled","false","reps.email.notifications.enabled","false","reps.email.notifications.dispatch-enabled","false","reps.tenancy.id-enforcement-enabled","false"));
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated-announcements",properties));
        app.register(Config.class);app.refresh();jwt=app.getBean(JwtUtils.class);router=app.getBean(EmailRoutingService.class);
        mvc=MockMvcBuilders.webAppContextSetup(app).defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
    }
    @AfterAll static void stop(){try{if(app!=null)app.close();if(mongo!=null && database.matches("reps_announcement_test_[0-9a-f]{32}"))mongo.getDb().drop();}finally{if(client!=null)client.close();}}
    @BeforeEach void seed(){
        SecurityContextHolder.clearContext();reset(router);gates(false);
        for(String name:mongo.getCollectionNames())mongo.getCollection(name).deleteMany(new Document());
        for(String school:List.of("A","B")){
            var s=new School();s.setSchoolIdNumber("school-"+school);s.setSchoolName("Same School");mongo.insert(s);
            account("teacher"+school,"TEACHER",school);account("child"+school,"STUDENT",school);
        }
        roster("teacherA",List.of("childA@example.test"));
    }
    void gates(boolean enabled){when(router.isPhase1RoutingEnabled()).thenReturn(enabled);properties.put("reps.email.notifications.enabled",Boolean.toString(enabled));properties.put("reps.email.notifications.dispatch-enabled",Boolean.toString(enabled));}
    void account(String name,String role,String school){
        var u=new UserModel();u.setId("account-"+name);u.setUsername(name+"@example.test");u.setPassword("synthetic-password");u.setSchool("Same School");u.setRoles(Set.of(new RoleModel(null,role)));u.setTenantSchoolId("school-"+school);mongo.insert(u);
        if(role.equals("STUDENT")){var s=new Student();s.setStudentIdNumber(name);s.setStudentEmail(u.getUsername());s.setSchool("Same School");s.setTenantSchoolId(u.getTenantSchoolId());s.setPreferredLanguage("en");mongo.insert(s);}
        else{var e=new Employee();e.setEmployeeId(name);e.setEmail(u.getUsername());e.setSchool("Same School");e.setTenantSchoolId(u.getTenantSchoolId());e.setRoles(u.getRoles());mongo.insert(e);}
    }
    void roster(String name,List<String> emails){var e=mongo.findById(name,Employee.class);var c=new Employee.ClassRoster();c.setClassName("Math");c.setClassPeriod("1");c.setClassRoster(emails);e.setClasses(List.of(c));mongo.save(e);}
    void change(String collection,String id,String field,Object value){mongo.getCollection(collection).updateOne(new Document("_id",id),new Document("$set",new Document(field,value)));}
    String token(String actor){return jwt.generateToken(app.getBean(CustomUserDetailsService.class).loadUserByUsername(actor+"@example.test"));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String actor){return post("/email/v1/classAnnouncement").header("Authorization","Bearer "+token(actor)).contentType(MediaType.APPLICATION_JSON);}
    String item(String teacher,String name){return "{\"teacherEmail\":\""+teacher+"@example.test\",\"className\":\""+name+"\",\"classPeriod\":\"1\",\"subject\":\"Reminder\",\"msg\":\"Bring <book> & notes\"}";}
    String body(){return "["+item("teacherA","Math")+"]";}
    Map<String,List<Document>> snapshot(){var result=new TreeMap<String,List<Document>>();for(String c:mongo.getCollectionNames())result.put(c,mongo.getCollection(c).find().into(new ArrayList<>()));return result;}
    void noSend(){assertFalse(mockingDetails(router).getInvocations().stream().anyMatch(i->i.getMethod().getName().startsWith("send")));verifyNoInteractions(app.getBean(org.springframework.mail.javamail.JavaMailSender.class));}
    void denied()throws Exception{var before=snapshot();mvc.perform(request("teacherA").content(body())).andExpect(status().isForbidden());app.getBean(RequiredNotificationService.class).dispatchPending();noSend();assertEquals(before,snapshot(),"Authorization failure must not persist obligations or other changes");}

    @ParameterizedTest @ValueSource(booleans={false,true}) void authorizedRosterWorksWithA7Inactive(boolean enabled)throws Exception{gates(enabled);mvc.perform(request("teacherA").content(body())).andExpect(status().isOk());
        var sends=mockingDetails(router).getInvocations().stream().filter(i->i.getMethod().getName().equals("sendBulkEmail")).toList();assertEquals(1,sends.size());assertEquals("childA@example.test",sends.get(0).getArgument(0));assertEquals("teacherA@example.test",sends.get(0).getArgument(5));assertTrue(sends.get(0).getArgument(3).toString().contains("&lt;book&gt; &amp; notes"));}
    @ParameterizedTest @ValueSource(booleans={false,true}) void sameNameForeignStudentRejected(boolean enabled)throws Exception{gates(enabled);roster("teacherA",List.of("childB@example.test"));denied();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void missingStudentOwnershipRejected(boolean enabled)throws Exception{gates(enabled);change("students","childA","tenantSchoolId",null);denied();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void conflictingStudentAccountRejected(boolean enabled)throws Exception{gates(enabled);change("users","account-childA","tenantSchoolId","school-B");denied();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void legacyActorRejectedOnlyAtAnnouncementBoundary(boolean enabled)throws Exception{gates(enabled);change("users","account-teacherA","tenantSchoolId",null);denied();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void forgedSenderRejected(boolean enabled)throws Exception{gates(enabled);mvc.perform(request("teacherA").content("["+item("teacherB","Math")+"]")).andExpect(status().isForbidden());noSend();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void revokedRosterAndStaleSpotterRejected(boolean enabled)throws Exception{gates(enabled);change("students","childA","spotters",List.of("teacherA@example.test"));roster("teacherA",List.of());denied();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void archivedStudentRejected(boolean enabled)throws Exception{gates(enabled);change("students","childA","archived",true);denied();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void disabledRecipientRejected(boolean enabled)throws Exception{gates(enabled);change("users","account-childA","enabled",false);denied();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void archivedStaffRejected(boolean enabled)throws Exception{gates(enabled);change("employee","teacherA","archived",true);denied();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void mixedRecipientBatchSendsNothing(boolean enabled)throws Exception{gates(enabled);roster("teacherA",List.of("childA@example.test","childB@example.test"));denied();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void laterInvalidClassPreventsEarlierSend(boolean enabled)throws Exception{gates(enabled);mvc.perform(request("teacherA").content("["+item("teacherA","Math")+","+item("teacherA","Missing")+"]")).andExpect(status().isForbidden());noSend();}
    @ParameterizedTest @ValueSource(booleans={false,true}) void retriesAndWorkerNeverRecoverDeniedAnnouncement(boolean enabled)throws Exception{gates(enabled);roster("teacherA",List.of("childB@example.test"));denied();denied();}
    @Test void anonymousRejected()throws Exception{mvc.perform(post("/email/v1/classAnnouncement").contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isUnauthorized());noSend();}
    @Test void studentCannotImpersonateStaff()throws Exception{mvc.perform(request("childA").content(body())).andExpect(status().isForbidden());noSend();}
    @Test void disabledActorRejectedByRealJwtFilter()throws Exception{String bearer=token("teacherA");change("users","account-teacherA","enabled",false);mvc.perform(post("/email/v1/classAnnouncement").header("Authorization","Bearer "+bearer).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isUnauthorized());noSend();}
    @Test void missingProfileOwnerRejected()throws Exception{change("employee","teacherA","tenantSchoolId",null);denied();}
    @Test void nonexistentSchoolRejected()throws Exception{mongo.getCollection("schools").deleteOne(new Document("_id","school-A"));denied();}
    @Test void ambiguousStudentEmailRejected()throws Exception{var copy=mongo.findById("childA",Student.class);copy.setStudentIdNumber("duplicate");copy.setTenantSchoolId("school-B");mongo.insert(copy);denied();}
    @Test void missingRecipientAccountRejected()throws Exception{mongo.getCollection("users").deleteOne(new Document("_id","account-childA"));denied();}
    @Test void pendingStudentSetupCanReceiveAuthorizedAnnouncement()throws Exception{change("users","account-childA","passwordSetupRequired",true);mvc.perform(request("teacherA").content(body())).andExpect(status().isOk());}
    @Test void forgedExtraStudentAndSchoolFieldsDoNotSelectRecipients()throws Exception{String forged=body().replace("\"subject\":","\"studentEmail\":\"childB@example.test\",\"schoolId\":\"school-B\",\"school\":\"Same School\",\"subject\":");mvc.perform(request("teacherA").content(forged)).andExpect(status().isOk());assertTrue(mockingDetails(router).getInvocations().stream().filter(i->i.getMethod().getName().startsWith("send")).allMatch(i->"childA@example.test".equals(i.getArgument(0))));}
    @Test void backgroundDirectSenderHasNoAuthority() {SecurityContextHolder.clearContext();assertThrows(org.springframework.security.access.AccessDeniedException.class,()->app.getBean(EmailSenderService.class).sendClassAnnouncement("teacherA@example.test",List.of("childA@example.test"),"Reminder","Body"));noSend();}
    @Test void directSenderRejectsMixedBatchAndRevokedRoster() {
        var principal=app.getBean(CustomUserDetailsService.class).loadUserByUsername("teacherA@example.test");
        SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(principal,null,principal.getAuthorities()));
        try {
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->app.getBean(EmailSenderService.class).sendClassAnnouncement("teacherA@example.test",List.of("childA@example.test","childB@example.test"),"Reminder","Body"));
            roster("teacherA",List.of());
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->app.getBean(EmailSenderService.class).sendClassAnnouncement("teacherA@example.test",List.of("childA@example.test"),"Reminder","Body"));
            noSend();
        } finally { SecurityContextHolder.clearContext(); }
    }
    @Test void forgedSpotterCannotChooseAnotherStaffClass()throws Exception {
        change("students","childA","spotters",List.of("teacherB@example.test"));
        mvc.perform(request("teacherB").content("["+item("teacherB","Math")+"]")).andExpect(status().isForbidden());noSend();
    }
    @Test void conflictingEmployeeOwnerRejected()throws Exception {change("employee","teacherA","tenantSchoolId","school-B");denied();}
    @Test void schoolDisplayNameIsNotAuthorizationInput()throws Exception {change("students","childA","school","Different label");mvc.perform(request("teacherA").content(body())).andExpect(status().isOk());}
    @Test void classPeriodAmbiguityDoesNotSend()throws Exception{var e=mongo.findById("teacherA",Employee.class);var second=new Employee.ClassRoster();second.setClassName("Math");second.setClassPeriod("2");second.setClassRoster(List.of("childA@example.test"));e.setClasses(List.of(e.getClasses().get(0),second));mongo.save(e);mvc.perform(request("teacherA").content(body().replace(",\"classPeriod\":\"1\"",""))).andExpect(status().isConflict());noSend();}
}
