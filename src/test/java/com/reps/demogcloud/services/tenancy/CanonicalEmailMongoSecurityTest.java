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

/** Real Mongo, security chain, token-store HTTP mocks and notification request service; workers intentionally disconnected. */
@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
class CanonicalEmailMongoSecurityTest {
    static MongoClient client;static MongoTemplate mongo;static String database;
    static AnnotationConfigWebApplicationContext app;static MockMvc mvc;static JwtUtils jwt;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @EnableMongoRepositories(basePackages={"com.reps.demogcloud.data","com.reps.demogcloud.security.models"})
    @Import({SecurityConfig.class,JwtFilterRequest.class,CustomUserDetailsService.class,CanonicalAccountContext.class,CanonicalCoreWorkflows.class,CanonicalEmailOwnership.class,GlobalExceptionHandler.class,com.reps.demogcloud.services.email.CasePersistenceCallbacks.class,Probe.class})
    static class Config {
        @Bean MongoTemplate mongoTemplate(){return mongo;}
        @Bean JwtRevocationStore revocations(){return new JwtRevocationStore(mongo);}
        @Bean JwtUtils jwt(JwtRevocationStore store){return new JwtUtils(JwtTestSupport.environment(),store);}
        @Bean CustomOAuth2UserService oidc(){return mock(CustomOAuth2UserService.class);}
        @Bean OAuth2LoginSuccessHandler success(){return mock(OAuth2LoginSuccessHandler.class);}
        @Bean OAuth2LoginFailureHandler failure(){return mock(OAuth2LoginFailureHandler.class);}
        @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}
        @Bean com.reps.demogcloud.services.UserContextService legacyUsers(){return mock(com.reps.demogcloud.services.UserContextService.class);}
        @Bean com.reps.demogcloud.services.email.EmailRoutingService router(){var router=mock(com.reps.demogcloud.services.email.EmailRoutingService.class);when(router.isPhase1RoutingEnabled()).thenReturn(true);return router;}
        @Bean com.reps.demogcloud.services.email.EmailTemplateBuilderService templates(){return mock(com.reps.demogcloud.services.email.EmailTemplateBuilderService.class);}
        @Bean GoogleOAuthTokenStore grants(){return new GoogleOAuthTokenStore(mailEnv,mongo,registrations,http);}
        @Bean com.reps.demogcloud.services.email.RequiredNotificationService notifications(StudentRepository students,com.reps.demogcloud.services.UserContextService users,com.reps.demogcloud.services.email.EmailRoutingService router,com.reps.demogcloud.services.email.EmailTemplateBuilderService templates){return new com.reps.demogcloud.services.email.RequiredNotificationService(mongo,mailEnv,students,users,router,templates);}
    }
    static org.springframework.mock.env.MockEnvironment mailEnv;
    static org.springframework.web.client.RestTemplate http;
    static org.springframework.test.web.client.MockRestServiceServer server;
    static Optional<org.springframework.security.oauth2.client.registration.ClientRegistrationRepository> registrations;
    static final String PROOF="p".repeat(43);
    @RestController static class Probe {
        final GoogleOAuthTokenStore grants;final com.reps.demogcloud.services.email.RequiredNotificationService notifications;final PunishRepository cases;
        Probe(GoogleOAuthTokenStore grants,com.reps.demogcloud.services.email.RequiredNotificationService notifications,PunishRepository cases){this.grants=grants;this.notifications=notifications;this.cases=cases;}
        @org.springframework.beans.factory.annotation.Autowired CanonicalEmailOwnership owners;
        @GetMapping("/__test__/email/restarted/{email}") Object restarted(@PathVariable String email){var restarted=new GoogleOAuthTokenStore(mailEnv,mongo,registrations,http);restarted.setCanonicalEmailOwnership(owners);return Map.of("state",restarted.getConnectionState(email));}
        @PostMapping("/__test__/email/disconnect/{email}") Object disconnect(@PathVariable String email){return Map.of("revoked",grants.disconnect(email));}
        @GetMapping("/__test__/email/grant/{email}") Object state(@PathVariable String email){return Map.of("state",grants.getConnectionState(email));}
        @GetMapping("/__test__/email/status/{id}") Object status(@PathVariable String id){return notifications.statuses("punishment",id);}
        @PostMapping("/__test__/email/forged/{id}/{email}") Object forged(@PathVariable String id,@PathVariable String email){var record=cases.findById(id).orElseThrow();record.setStudentEmail(email);notifications.prepare(record,"CREATED","synthetic-action",null);return Map.of("saved",true);}
        @PostMapping("/__test__/email/capture/{id}") Object capture(@PathVariable String id){var record=cases.findById(id).orElseThrow();notifications.prepare(record,"CREATED","synthetic-action",null);cases.save(record);return Map.of("saved",true);}
        @PostMapping("/__test__/email/retry/{id}/{notification}") Object retry(@PathVariable String id,@PathVariable String notification){notifications.retry("punishment",id,notification);return Map.of("saved",true);}
        @PostMapping("/__test__/email/dispatch") Object dispatch(){notifications.dispatchPending();return Map.of("saved",true);}
        @GetMapping("/__test__/email/connect/{email}") Object connect(@PathVariable String email){return Map.of("url",grants.beginConnection(email,PROOF));}
        @PostMapping("/__test__/email/callback/{email}/{state}") Object callback(@PathVariable String email,@PathVariable String state){grants.completeConnection(email,state,"synthetic-code",PROOF);return Map.of("usable",grants.hasUsableGmailSendToken(email));}
    }
    @BeforeAll static void start(){
        var uri=new ConnectionString(System.getenv("REPS_TENANCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")));assertNull(uri.getCredential());assertNull(uri.getDatabase());
        database="reps_a7_email_test_"+UUID.randomUUID().toString().replace("-","");client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);
        mailEnv=new org.springframework.mock.env.MockEnvironment();mailEnv.setProperty("reps.email.gmail.connection-enabled","true");mailEnv.setProperty("reps.email.gmail.encryption-key",Base64.getEncoder().encodeToString(new byte[32]));mailEnv.setProperty("reps.email.gmail.redirect-uri","http://localhost:3000/account/gmail/callback");
        var registration=org.springframework.security.oauth2.client.registration.ClientRegistration.withRegistrationId("google").clientId("synthetic-client").clientSecret("synthetic-secret").authorizationGrantType(org.springframework.security.oauth2.core.AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/unused").authorizationUri("https://accounts.google.com/o/oauth2/v2/auth").tokenUri("https://oauth2.googleapis.com/token").build();
        registrations=Optional.of(new org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository(registration));http=GoogleOAuthTokenStore.googleHttp();server=org.springframework.test.web.client.MockRestServiceServer.bindTo(http).build();
        app=new AnnotationConfigWebApplicationContext();app.setServletContext(new MockServletContext());app.getEnvironment().setActiveProfiles("tenant-isolated-verification");
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated",Map.of("reps.tenancy.core-preparation-enabled","true","reps.tenancy.email-preparation-enabled","true","auth.sso.google.enabled","false")));
        app.register(Config.class);app.refresh();jwt=app.getBean(JwtUtils.class);mvc=MockMvcBuilders.webAppContextSetup(app)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
    }
    @AfterAll static void stop(){try{if(app!=null)app.close();if(mongo!=null && database.matches("reps_a7_email_test_[0-9a-f]{32}"))mongo.getDb().drop();}finally{if(client!=null)client.close();}}
    @BeforeEach void seed(){
        server.reset();mailEnv.setProperty("reps.email.notifications.enabled","true");mailEnv.setProperty("reps.email.notifications.dispatch-enabled","true");reset(app.getBean(com.reps.demogcloud.services.UserContextService.class));
        for(String name:mongo.getCollectionNames())mongo.getCollection(name).deleteMany(new Document());
        for(String school:List.of("A","B")){
            var s=new School();s.setSchoolIdNumber("school-"+school);s.setSchoolName("Same School");mongo.insert(s);
            account("admin"+school,"ADMIN",school,false);account("teacher"+school,"TEACHER",school,true);account("guide"+school,"GUIDANCE",school,false);account("other"+school,"TEACHER",school,false);
            account("unassigned"+school,"GUIDANCE",school,false);account("child"+school,"STUDENT",school,false);
            for(var kind:CanonicalCoreWorkflows.Kind.values())mongo.getCollection(kind.collection).insertOne(new Document("_id",kind.name()+school)
                    .append("tenantSchoolId","school-"+school).append("studentEmail","child"+school+"@example.test").append("teacherEmail","teacher"+school+"@example.test")
                    .append("revision",0L).append("assignmentTemplateId","template"+school));
            mongo.getCollection("assignment_templates").insertOne(new Document("_id","template"+school).append("tenantSchoolId","school-"+school));
        }
        mongo.getCollection("TrackedBehaviorEvents").deleteMany(new Document());
        mongo.getCollection("currency_operations").insertOne(new Document("_id","retained-receipt"));
    }
    static void account(String name,String role,String school,boolean roster){
        var u=new UserModel();u.setId(name);u.setUsername(name+"@example.test");u.setPassword("synthetic");u.setRoles(Set.of(new RoleModel(null,role)));u.setTenantSchoolId("school-"+school);u.setSchool("Same School");mongo.insert(u);
        if(role.equals("STUDENT")){
            var s=new Student();s.setStudentIdNumber(name);s.setStudentEmail(u.getUsername());s.setTenantSchoolId(u.getTenantSchoolId());s.setSchool("Same School");s.setCurrency(12);s.setGuidanceEmail("guide"+school+"@example.test");s.setAdminEmail("admin"+school+"@example.test");s.setSpotters(List.of("other"+school+"@example.test"));mongo.insert(s);
        }else{
            var e=new Employee();e.setEmployeeId(name);e.setEmail(u.getUsername());e.setTenantSchoolId(u.getTenantSchoolId());e.setSchool("Same School");e.setRoles(u.getRoles());e.setCurrency(30);
            if(roster){var c=new Employee.ClassRoster();c.setClassRoster(List.of("child"+school+"@example.test"));e.setClasses(List.of(c));}mongo.insert(e);
        }
    }
    String token(String name){return jwt.generateToken(app.getBean(CustomUserDetailsService.class).loadUserByUsername(name+"@example.test"));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getAs(String path,String name){return get("/__test__/email/"+path).header("Authorization","Bearer "+token(name));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAs(String path,String name){return post("/__test__/email/"+path).header("Authorization","Bearer "+token(name));}
    void grant(String owner,String school){mongo.insert(new Document("_id","teachera@example.test").append("ownerAccountId",owner).append("ownerProfileId",owner).append("tenantSchoolId",school).append("gmailAuthorization",new Document("state","CONNECTED").append("cipher","synthetic-opaque-ciphertext").append("version","retained-version")),"oauth2_authorized_clients");}
    @AfterEach void noLegacyAuthorityOrUnmockedHttp(){verifyNoInteractions(app.getBean(com.reps.demogcloud.services.UserContextService.class));server.verify();}
    @Test void ownedGrantStateSurvivesLookupAndRestartWithoutCipherChanges()throws Exception{
        grant("teacherA","school-A");var before=mongo.getCollection("oauth2_authorized_clients").find().first();
        mvc.perform(getAs("grant/teacherA@example.test","teacherA")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONNECTED"));
        mvc.perform(getAs("restarted/teacherA@example.test","teacherA")).andExpect(status().isOk()).andExpect(jsonPath("$.state").value("CONNECTED"));
        assertEquals(before,mongo.getCollection("oauth2_authorized_clients").find().first());
    }
    @Test void foreignAndUnmappedGrantMetadataFailClosedWithoutReconnectOrReencryption()throws Exception{
        grant("teacherB","school-B");var before=mongo.getCollection("oauth2_authorized_clients").find().first();mvc.perform(getAs("grant/teacherA@example.test","teacherA")).andExpect(status().isServiceUnavailable());assertEquals(before,mongo.getCollection("oauth2_authorized_clients").find().first());
        mongo.getCollection("oauth2_authorized_clients").updateOne(new Document("_id","teachera@example.test"),new Document("$unset",new Document("ownerAccountId","").append("ownerProfileId","").append("tenantSchoolId","")));
        before=mongo.getCollection("oauth2_authorized_clients").find().first();mvc.perform(getAs("grant/teacherA@example.test","teacherA")).andExpect(status().isServiceUnavailable());assertEquals(before,mongo.getCollection("oauth2_authorized_clients").find().first());
    }
    @Test void authenticatedActorCannotBorrowAnotherSameNameSchoolsGrant()throws Exception{grant("teacherA","school-A");mvc.perform(getAs("grant/teacherA@example.test","teacherB")).andExpect(status().isForbidden());}
    @Test void firstConnectionBindsTrustedAccountMetadataAndMockedRefreshPreservesOwnership()throws Exception{
        var result=mvc.perform(getAs("connect/teacherA@example.test","teacherA")).andExpect(status().isOk()).andReturn();
        var url=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("url").asText();
        var params=org.springframework.web.util.UriComponentsBuilder.fromUriString(url).build().getQueryParams();String state=java.net.URLDecoder.decode(params.getFirst("state"),java.nio.charset.StandardCharsets.UTF_8);
        String tokenBody="{\"access_token\":\"synthetic-access\",\"refresh_token\":\"synthetic-refresh\",\"token_type\":\"Bearer\",\"expires_in\":1,\"scope\":\"openid email https://www.googleapis.com/auth/gmail.send\"}";
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://oauth2.googleapis.com/token")).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(tokenBody,MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://openidconnect.googleapis.com/v1/userinfo")).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess("{\"email\":\"teachera@example.test\",\"email_verified\":true,\"sub\":\"synthetic-subject\"}",MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://oauth2.googleapis.com/token")).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess(tokenBody.replace("\"expires_in\":1","\"expires_in\":3600"),MediaType.APPLICATION_JSON));
        server.expect(org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo("https://oauth2.googleapis.com/revoke")).andRespond(org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess());
        mvc.perform(postAs("callback/teacherA@example.test/"+state,"teacherA")).andExpect(status().isOk()).andExpect(jsonPath("$.usable").value(true));
        var raw=mongo.getCollection("oauth2_authorized_clients").find().first();assertEquals("teacherA",raw.getString("ownerAccountId"));assertEquals("school-A",raw.getString("tenantSchoolId"));assertFalse(raw.toJson().contains("synthetic-refresh"));
        mvc.perform(postAs("disconnect/teacherA@example.test","teacherA")).andExpect(status().isOk()).andExpect(jsonPath("$.revoked").value(true));
        raw=mongo.getCollection("oauth2_authorized_clients").find().first();assertEquals("school-A",raw.getString("tenantSchoolId"));assertEquals("DISCONNECTED",raw.get("gmailAuthorization",Document.class).getString("state"));assertFalse(raw.get("gmailAuthorization",Document.class).containsKey("cipher"));
        mvc.perform(getAs("connect/teacherA@example.test","teacherA")).andExpect(status().isOk());
    }
    @Test void capturePersistsServerOwnedIntentAndPreservesExistingStates()throws Exception{
        mvc.perform(postAs("capture/PUNISHMENTA","teacherA")).andExpect(status().isOk());var raw=mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first();var intent=raw.getList("requiredNotifications",Document.class).get(0);
        assertEquals("school-A",intent.getString("tenantSchoolId"));assertEquals("teacherA",intent.getString("actorAccountId"));assertEquals("PENDING",intent.getString("state"));
        mvc.perform(postAs("capture/PUNISHMENTA","teacherA")).andExpect(status().isOk());assertEquals(1,mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first().getList("requiredNotifications",Document.class).size());
        mvc.perform(postAs("capture/PUNISHMENTB","adminA")).andExpect(status().isForbidden());
    }
    @Test void revokedRosterAndStaleSpotterCannotReadOrRetryNotification()throws Exception{
        mvc.perform(postAs("capture/PUNISHMENTA","teacherA")).andExpect(status().isOk());
        mvc.perform(getAs("status/PUNISHMENTA","otherA")).andExpect(status().isForbidden());
        mongo.getCollection("employee").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("classes",List.of())));mvc.perform(getAs("status/PUNISHMENTA","teacherA")).andExpect(status().isForbidden());
        mvc.perform(getAs("status/PUNISHMENTB","adminA")).andExpect(status().isNotFound());
    }
    @Test void isolatedWorkerRemainsDisconnectedAndDoesNotResetClaimsOrSend()throws Exception{
        mvc.perform(postAs("capture/PUNISHMENTA","teacherA")).andExpect(status().isOk());var before=mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first();
        mvc.perform(postAs("dispatch","adminA")).andExpect(status().isServiceUnavailable());assertEquals(before,mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first());
        mailEnv.setProperty("reps.email.notifications.dispatch-enabled","false");mvc.perform(postAs("dispatch","adminA")).andExpect(status().isOk());assertEquals(before,mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first());
    }
    @Test void retryPreservesIdentityAndRejectsUncertainOrUnmappedHistory()throws Exception{
        mvc.perform(postAs("capture/PUNISHMENTA","teacherA")).andExpect(status().isOk());
        var before=mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first();var n=before.getList("requiredNotifications",Document.class).get(0);String id=n.getString("notificationId");
        mongo.getCollection("Punishments").updateOne(new Document("_id","PUNISHMENTA"),new Document("$set",new Document("requiredNotifications.0.state","FAILED").append("requiredNotifications.0.retrySafe",true)));
        mvc.perform(postAs("retry/PUNISHMENTA/"+id,"adminA")).andExpect(status().isOk());
        var after=mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first().getList("requiredNotifications",Document.class).get(0);assertEquals(id,after.getString("notificationId"));assertEquals(n.getString("rfcMessageId"),after.getString("rfcMessageId"));assertEquals("PENDING",after.getString("state"));
        mongo.getCollection("Punishments").updateOne(new Document("_id","PUNISHMENTA"),new Document("$set",new Document("requiredNotifications.0.state","UNCERTAIN").append("requiredNotifications.0.retrySafe",false)));
        mvc.perform(postAs("retry/PUNISHMENTA/"+id,"adminA")).andExpect(status().isConflict());
        mongo.getCollection("Punishments").updateOne(new Document("_id","PUNISHMENTA"),new Document("$set",new Document("requiredNotifications.0.state","FAILED").append("requiredNotifications.0.retrySafe",true)).append("$unset",new Document("requiredNotifications.0.actorAccountId","")));
        mvc.perform(postAs("retry/PUNISHMENTA/"+id,"adminA")).andExpect(status().isServiceUnavailable());
        assertEquals("FAILED",mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first().getList("requiredNotifications",Document.class).get(0).getString("state"));
    }
    @Test void disabledRecordedSenderCannotBeRetriedByAnAdministrator()throws Exception{
        mvc.perform(postAs("capture/PUNISHMENTA","teacherA")).andExpect(status().isOk());
        var n=mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first().getList("requiredNotifications",Document.class).get(0);
        mongo.getCollection("Punishments").updateOne(new Document("_id","PUNISHMENTA"),new Document("$set",new Document("requiredNotifications.0.state","FAILED").append("requiredNotifications.0.retrySafe",true)));
        mongo.getCollection("users").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("enabled",false)));
        mvc.perform(postAs("retry/PUNISHMENTA/"+n.getString("notificationId"),"adminA")).andExpect(status().isForbidden());
    }

    @Test void forgedStudentReferenceCannotRebindAnExistingCase()throws Exception{
        var student=new Student();student.setStudentIdNumber("secondA");student.setStudentEmail("second@example.test");student.setSchool("Same School");student.setTenantSchoolId("school-A");mongo.insert(student);
        var before=mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first();
        mvc.perform(postAs("forged/PUNISHMENTA/second@example.test","adminA")).andExpect(status().isForbidden());assertEquals(before,mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first());
    }

}
