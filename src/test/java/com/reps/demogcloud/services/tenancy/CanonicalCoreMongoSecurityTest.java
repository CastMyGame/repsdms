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

/** Actual MongoDB, repositories, JWT signing/revocation, SecurityConfig, and preparation adapter.
 * Probe controllers are test-only. This does not exercise production business execution adapters. */
@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
class CanonicalCoreMongoSecurityTest {
    static MongoClient client;static MongoTemplate mongo;static String database;
    static AnnotationConfigWebApplicationContext app;static MockMvc mvc;static JwtUtils jwt;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @EnableMongoRepositories(basePackages={"com.reps.demogcloud.data","com.reps.demogcloud.security.models"})
    @Import({SecurityConfig.class,JwtFilterRequest.class,CustomUserDetailsService.class,CanonicalAccountContext.class,CanonicalCoreWorkflows.class,GlobalExceptionHandler.class,Probe.class})
    static class Config {
        @Bean MongoTemplate mongoTemplate(){return mongo;}
        @Bean JwtRevocationStore revocations(){return new JwtRevocationStore(mongo);}
        @Bean JwtUtils jwt(JwtRevocationStore store){return new JwtUtils(JwtTestSupport.environment(),store);}
        @Bean CustomOAuth2UserService oidc(){return mock(CustomOAuth2UserService.class);}
        @Bean OAuth2LoginSuccessHandler success(){return mock(OAuth2LoginSuccessHandler.class);}
        @Bean OAuth2LoginFailureHandler failure(){return mock(OAuth2LoginFailureHandler.class);}
        @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}
    }
    @RestController static class Probe {
        final CanonicalCoreWorkflows core;Probe(CanonicalCoreWorkflows core){this.core=core;}
        @GetMapping("/__test__/core/student/{id}") Object student(@PathVariable String id){return core.readStudent(id);}
        @GetMapping("/__test__/core/record/{kind}/{id}") Object record(@PathVariable CanonicalCoreWorkflows.Kind kind,@PathVariable String id){return core.readRecord(kind,id);}
        @GetMapping("/__test__/core/assignment/{id}") Object assignment(@PathVariable String id){return core.readAssignmentCase(id);}
        @PostMapping("/__test__/core/prepare") Object prepare(@RequestBody List<CanonicalCoreWorkflows.Draft> drafts){return core.prepareCreations(drafts);}
        @PostMapping("/__test__/core/roster/{id}") Object roster(@PathVariable String id,@RequestBody List<String> ids){return core.validateRoster(id,ids);}
        @PostMapping("/__test__/core/note/{id}/{version}") Object note(@PathVariable String id,@PathVariable long version){return core.appendStudentNote(id,version,"Synthetic note");}
        @PostMapping("/__test__/core/spotter/{id}/{version}") Object spotter(@PathVariable String id,@PathVariable long version){return core.changeOwnSpotter(id,version,true);}
    }
    @BeforeAll static void start(){
        var uri=new ConnectionString(System.getenv("REPS_TENANCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")));assertNull(uri.getCredential());assertNull(uri.getDatabase());
        database="reps_a7_core_test_"+UUID.randomUUID().toString().replace("-","");client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);
        app=new AnnotationConfigWebApplicationContext();app.setServletContext(new MockServletContext());
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated",Map.of("reps.tenancy.core-preparation-enabled","true","auth.sso.google.enabled","false")));
        app.register(Config.class);app.refresh();jwt=app.getBean(JwtUtils.class);mvc=MockMvcBuilders.webAppContextSetup(app)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
    }
    @AfterAll static void stop(){try{if(app!=null)app.close();if(mongo!=null && database.matches("reps_a7_core_test_[0-9a-f]{32}"))mongo.getDb().drop();}finally{if(client!=null)client.close();}}
    @BeforeEach void seed(){
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
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getAs(String path,String name){return get("/__test__/core/"+path).header("Authorization","Bearer "+token(name));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAs(String path,String name){return post("/__test__/core/"+path).header("Authorization","Bearer "+token(name)).contentType(MediaType.APPLICATION_JSON);}
    void rawSet(String collection,String id,String field,Object value){mongo.getCollection(collection).updateOne(new Document("_id",id),new Document("$set",new Document(field,value)));}
    List<Document> studentState(){return mongo.getCollection("students").find().sort(new Document("_id",1)).into(new ArrayList<>());}
    @Test void anonymousIsRejectedByActualFilterChain()throws Exception{mvc.perform(get("/__test__/core/student/childA")).andExpect(status().isUnauthorized());}
    @ParameterizedTest @ValueSource(strings={"teacherA","guideA","adminA","childA"})
    void everyRoleIsIsolatedDespiteIdenticalDisplayNames(String actor)throws Exception{
        mvc.perform(getAs("student/childA",actor)).andExpect(status().isOk());mvc.perform(getAs("student/childB",actor)).andExpect(status().isForbidden());
        mvc.perform(getAs("record/PUNISHMENT/PUNISHMENTA",actor)).andExpect(status().isOk());mvc.perform(getAs("record/PUNISHMENT/PUNISHMENTB",actor)).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"otherA","unassignedA"})
    void staleSpottersAndUnassignedStaffDoNotGrantAccess(String actor)throws Exception{mvc.perform(getAs("student/childA",actor)).andExpect(status().isForbidden());}
    @Test void revokedRosterAndGuidanceAssignmentAreCheckedFresh()throws Exception{
        rawSet("employee","teacherA","classes",List.of());rawSet("students","childA","guidanceEmail","unassignedA@example.test");
        mvc.perform(getAs("record/PUNISHMENT/PUNISHMENTA","teacherA")).andExpect(status().isForbidden());
        mvc.perform(getAs("student/childA","guideA")).andExpect(status().isForbidden());
        mvc.perform(postAs("spotter/childA/0","teacherA")).andExpect(status().isForbidden());
    }
    @Test void disabledArchivedAndRevokedIdentitiesFailClosed()throws Exception{
        String t=token("teacherA");rawSet("users","teacherA","enabled",false);mvc.perform(get("/__test__/core/student/childA").header("Authorization","Bearer "+t)).andExpect(status().isUnauthorized());
        rawSet("students","childA","archived",true);mvc.perform(getAs("student/childA","adminA")).andExpect(status().isForbidden());
        String admin=token("adminA");jwt.blacklistToken(admin);mvc.perform(get("/__test__/core/student/childB").header("Authorization","Bearer "+admin)).andExpect(status().isUnauthorized());
    }
    @Test void noteAndSelfSpotterWritesAreTenantScopedRevisionGuardedAndCurrencySafe()throws Exception{
        mvc.perform(postAs("note/childA/0","teacherA")).andExpect(status().isOk());mvc.perform(postAs("note/childA/0","teacherA")).andExpect(status().isConflict());
        mvc.perform(postAs("spotter/childA/1","teacherA")).andExpect(status().isOk());mvc.perform(postAs("note/childB/0","adminA")).andExpect(status().isForbidden());
        var s=mongo.findById("childA",Student.class);assertEquals(12,s.getCurrency());assertEquals("school-A",s.getTenantSchoolId());assertEquals("teacherA@example.test",s.getNotesArray().get(0).getCreatedBy());
        assertTrue(s.getSpotters().contains("teacherA@example.test"));assertEquals(2,s.getProfileRevision());
    }
    @Test void forgedClaimsAndInvalidBulkProduceNoWritesOrSideEffects()throws Exception{
        var before=studentState();
        for(String claims:List.of("{\"actorAccountId\":\"adminB\"}","{\"tenantSchoolId\":\"school-A\"}","{\"school\":\"Same School\"}"))
            mvc.perform(postAs("prepare","teacherA").content("[{\"kind\":\"PUNISHMENT\",\"studentId\":\"childA\",\"claims\":"+claims+"}]")).andExpect(status().isForbidden());
        mvc.perform(postAs("prepare","teacherA").content("[{\"kind\":\"PUNISHMENT\",\"studentId\":\"childA\"},{\"kind\":\"OFFICE\",\"studentId\":\"childB\"}]")).andExpect(status().isForbidden());
        assertEquals(before,studentState());assertEquals(2,mongo.getCollection("Punishments").countDocuments());assertEquals(1,mongo.getCollection("currency_operations").countDocuments());
        assertEquals(0,mongo.getCollection("Punishments").countDocuments(new Document("requiredNotifications",new Document("$exists",true))));
    }
    @Test void serverDerivesCreationOwnershipAndStudentMayRequestOnlyOwnGuidance()throws Exception{
        mvc.perform(postAs("prepare","teacherA").content("[{\"kind\":\"PUNISHMENT\",\"studentId\":\"childA\"}]")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].tenantSchoolId").value("school-A")).andExpect(jsonPath("$[0].actorAccountId").value("teacherA"));
        mvc.perform(postAs("prepare","childA").content("[{\"kind\":\"GUIDANCE\",\"studentId\":\"childA\"}]")).andExpect(status().isOk());
        mvc.perform(postAs("prepare","childA").content("[{\"kind\":\"OFFICE\",\"studentId\":\"childA\"}]")).andExpect(status().isForbidden());
    }
    @Test void rosterValidationRequiresOwnClassOrSameTenantAdminAndChecksEntireBatch()throws Exception{
        mvc.perform(postAs("roster/teacherA","teacherA").content("[\"childA\"]")).andExpect(status().isOk());
        mvc.perform(postAs("roster/teacherB","adminA").content("[\"childA\"]")).andExpect(status().isForbidden());
        mvc.perform(postAs("roster/teacherA","otherA").content("[\"childA\"]")).andExpect(status().isForbidden());
        mvc.perform(postAs("roster/teacherA","teacherA").content("[\"childA\",\"childB\"]")).andExpect(status().isForbidden());
    }
    @Test void immutableHistoricalCaseOwnerIsNotInferredFromCurrentStudent()throws Exception{
        var before=mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first();rawSet("students","childA","tenantSchoolId","school-B");
        mvc.perform(getAs("record/PUNISHMENT/PUNISHMENTA","adminA")).andExpect(status().isForbidden());mvc.perform(getAs("record/PUNISHMENT/PUNISHMENTA","adminB")).andExpect(status().isForbidden());
        assertEquals(before,mongo.getCollection("Punishments").find(new Document("_id","PUNISHMENTA")).first());
    }
    @Test void assignmentTemplateAndSnapshotOwnershipAreCheckedWithoutChangingWork()throws Exception{
        mvc.perform(getAs("assignment/PUNISHMENTA","childA")).andExpect(status().isOk());
        rawSet("assignment_templates","templateA","tenantSchoolId","school-B");mvc.perform(getAs("assignment/PUNISHMENTA","childA")).andExpect(status().isForbidden());
        var snapshot=new Document("id","templateA").append("tenantSchoolId","school-A");rawSet("Punishments","PUNISHMENTA","assignmentWork",new Document("template",snapshot));
        mongo.getCollection("assignment_templates").deleteOne(new Document("_id","templateA"));mvc.perform(getAs("assignment/PUNISHMENTA","childA")).andExpect(status().isOk());
        rawSet("Punishments","PUNISHMENTA","assignmentWork",new Document("template",new Document("id","templateA").append("tenantSchoolId","school-B")));
        mvc.perform(getAs("assignment/PUNISHMENTA","childA")).andExpect(status().isForbidden());
    }
    @Test void guidanceNotesAndLinkedCasesRemainParticipantScoped()throws Exception{
        mvc.perform(getAs("record/GUIDANCE/GUIDANCEA","guideA")).andExpect(status().isOk());mvc.perform(getAs("record/GUIDANCE/GUIDANCEA","childA")).andExpect(status().isForbidden());
        rawSet("Guidance","GUIDANCEA","linkToPunishment","PUNISHMENTB");mvc.perform(getAs("record/GUIDANCE/GUIDANCEA","adminA")).andExpect(status().isForbidden());
    }
    @Test void missingLegacyIdsNeverFallBackToNames()throws Exception{
        mongo.getCollection("Punishments").updateOne(new Document("_id","PUNISHMENTA"),new Document("$unset",new Document("tenantSchoolId","")));
        mvc.perform(getAs("record/PUNISHMENT/PUNISHMENTA","adminA")).andExpect(status().isForbidden());
        rawSet("students","childA","tenantSchoolId",null);mvc.perform(getAs("student/childA","adminA")).andExpect(status().isForbidden());
    }
    @Test void conflictingTargetAccountBlocksEvenSameSchoolAdministrator()throws Exception{
        rawSet("users","childA","tenantSchoolId","school-B");mvc.perform(getAs("student/childA","adminA")).andExpect(status().isForbidden());
        mvc.perform(postAs("roster/teacherA","adminA").content("[\"childA\"]")).andExpect(status().isForbidden());
    }
    @Test void verifiedGlobalTemplateIsAllowedButLegacySchoolNameIsNotAuthority()throws Exception{
        var global=new Document("_id","global").append("createdBySystem",true).append("scope","SYSTEM_DEFAULT");mongo.getCollection("assignment_templates").insertOne(global);
        rawSet("Punishments","PUNISHMENTA","assignmentTemplateId","global");mvc.perform(getAs("assignment/PUNISHMENTA","childA")).andExpect(status().isOk());
        rawSet("assignment_templates","global","schoolId","Same School");mvc.perform(getAs("assignment/PUNISHMENTA","childA")).andExpect(status().isForbidden());
    }
    @Test void concurrentNotesHaveOneRevisionWinner()throws Exception{
        var pool=java.util.concurrent.Executors.newFixedThreadPool(2);var start=new java.util.concurrent.CountDownLatch(1);
        try{
            var tasks=new ArrayList<java.util.concurrent.Future<Integer>>();
            for(int i=0;i<2;i++)tasks.add(pool.submit(()->{start.await();return mvc.perform(postAs("note/childA/0","teacherA")).andReturn().getResponse().getStatus();}));
            start.countDown();var statuses=new ArrayList<Integer>();for(var task:tasks)statuses.add(task.get(15,java.util.concurrent.TimeUnit.SECONDS));
            Collections.sort(statuses);assertEquals(List.of(200,409),statuses);assertEquals(1,mongo.findById("childA",Student.class).getNotesArray().size());
        }finally{pool.shutdownNow();}
    }
    @Test void invalidPreparationIsIndependentOfEmailFeatureGates()throws Exception{
        var before=studentState();
        try{for(String enabled:List.of("true","false")){
            app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("email-test",Map.of("reps.email.routing.phase1-enabled",enabled,
                    "reps.email.gmail.connection-enabled",enabled,"reps.email.notifications.enabled",enabled,"reps.email.notifications.dispatch-enabled",enabled)));
            mvc.perform(postAs("prepare","teacherA").content("[{\"kind\":\"PUNISHMENT\",\"studentId\":\"childB\"}]")).andExpect(status().isForbidden());
        }}finally{app.getEnvironment().getPropertySources().remove("email-test");}
        assertEquals(before,studentState());assertEquals(2,mongo.getCollection("Punishments").countDocuments());
    }
    @Test void officeAndGuidanceStaffReferencesMustBelongToCanonicalTenant()throws Exception{
        mvc.perform(postAs("prepare","teacherA").content("[{\"kind\":\"OFFICE\",\"studentId\":\"childA\"}]")).andExpect(status().isOk());
        rawSet("students","childA","adminEmail","adminB@example.test");
        mvc.perform(postAs("prepare","teacherA").content("[{\"kind\":\"OFFICE\",\"studentId\":\"childA\"}]")).andExpect(status().isForbidden());
        rawSet("students","childA","guidanceEmail","guideB@example.test");
        mvc.perform(postAs("prepare","teacherA").content("[{\"kind\":\"GUIDANCE\",\"studentId\":\"childA\"}]")).andExpect(status().isForbidden());
    }
    @Test void malformedRelatedRecordFailsClosedWithAuthorizationError()throws Exception{
        rawSet("employee","teacherA","email",null);
        mvc.perform(postAs("roster/teacherA","adminA").content("[\"childA\"]")).andExpect(status().isForbidden());
        rawSet("Guidance","GUIDANCEA","linkToPunishment",42);
        mvc.perform(getAs("record/GUIDANCE/GUIDANCEA","adminA")).andExpect(status().isForbidden());
    }
}
