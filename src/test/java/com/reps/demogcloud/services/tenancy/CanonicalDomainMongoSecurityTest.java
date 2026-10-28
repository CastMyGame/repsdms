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
 * Probe controllers are test-only; production controllers and disconnected services are not covered here. */
@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
class CanonicalDomainMongoSecurityTest {
    static MongoClient client;static MongoTemplate mongo;static String database;
    static AnnotationConfigWebApplicationContext app;static MockMvc mvc;static JwtUtils jwt;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @EnableMongoRepositories(basePackages={"com.reps.demogcloud.data","com.reps.demogcloud.security.models"})
    @Import({SecurityConfig.class,JwtFilterRequest.class,CustomUserDetailsService.class,CanonicalAccountContext.class,CanonicalCoreWorkflows.class,GlobalExceptionHandler.class,CanonicalDomainAccess.class,com.reps.demogcloud.services.GuidanceService.class,com.reps.demogcloud.services.trackedBehavior.TrackedBehaviorServiceImpl.class,com.reps.demogcloud.services.employee.EmployeeClassService.class,com.reps.demogcloud.services.student.StudentAdminService.class,com.reps.demogcloud.services.email.CasePersistenceCallbacks.class,Probe.class})
    static class Config {
        @Bean MongoTemplate mongoTemplate(){return mongo;}
        @Bean JwtRevocationStore revocations(){return new JwtRevocationStore(mongo);}
        @Bean JwtUtils jwt(JwtRevocationStore store){return new JwtUtils(JwtTestSupport.environment(),store);}
        @Bean CustomOAuth2UserService oidc(){return mock(CustomOAuth2UserService.class);}
        @Bean OAuth2LoginSuccessHandler success(){return mock(OAuth2LoginSuccessHandler.class);}
        @Bean OAuth2LoginFailureHandler failure(){return mock(OAuth2LoginFailureHandler.class);}
        @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}
        @Bean com.reps.demogcloud.services.WorkflowAccessService legacyAccess(){return mock(com.reps.demogcloud.services.WorkflowAccessService.class);}
        @Bean com.reps.demogcloud.services.UserContextService legacyUsers(){return mock(com.reps.demogcloud.services.UserContextService.class);}
        @Bean com.reps.demogcloud.services.EmailService mail(){return mock(com.reps.demogcloud.services.EmailService.class);}
        @Bean com.reps.demogcloud.services.email.RequiredNotificationService notifications(){return mock(com.reps.demogcloud.services.email.RequiredNotificationService.class);}
        @Bean com.reps.demogcloud.utils.StudentUtils studentUtils(){return mock(com.reps.demogcloud.utils.StudentUtils.class);}
        @Bean com.reps.demogcloud.services.UserAdministrationService administration(){return mock(com.reps.demogcloud.services.UserAdministrationService.class);}
        @Bean org.springframework.data.mongodb.core.mapping.event.BeforeSaveCallback<com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent> failpoint(){return (entity,doc,name)->{if(failSecond && inserts.incrementAndGet()==2)throw new org.springframework.dao.DataIntegrityViolationException("Synthetic second insert failure");return entity;};}
    }
    static volatile boolean failSecond;static final java.util.concurrent.atomic.AtomicInteger inserts=new java.util.concurrent.atomic.AtomicInteger();
    @RestController static class Probe {
        final com.reps.demogcloud.services.GuidanceService guidance;
        final com.reps.demogcloud.services.trackedBehavior.TrackedBehaviorServiceImpl behavior;
        final com.reps.demogcloud.services.employee.EmployeeClassService rosters;
        @org.springframework.beans.factory.annotation.Autowired com.reps.demogcloud.services.student.StudentAdminService studentAdmin;
        @org.springframework.beans.factory.annotation.Autowired Probe(CanonicalCoreWorkflows core,com.reps.demogcloud.services.GuidanceService guidance,com.reps.demogcloud.services.trackedBehavior.TrackedBehaviorServiceImpl behavior,com.reps.demogcloud.services.employee.EmployeeClassService rosters){this.core=core;this.guidance=guidance;this.behavior=behavior;this.rosters=rosters;}
        @PostMapping("/__test__/domain/guidance") Object create(@RequestBody com.reps.demogcloud.models.guidance.GuidanceRequest request){return guidance.createNewGuidanceFormSimple(request);}
        @GetMapping("/__test__/domain/guidance") Object list(){return guidance.findAll();}
        @PutMapping("/__test__/domain/guidance/{id}/note") Object note(@PathVariable String id,@RequestBody com.reps.demogcloud.models.punishment.ThreadEvent event){return guidance.updateGuidance(id,event);}
        @PutMapping("/__test__/domain/guidance/{id}/status/{status}") Object status(@PathVariable String id,@PathVariable String status){return guidance.updateGuidanceStatus(id,status);}
        @PostMapping("/__test__/domain/guidance/{id}/resources") Object resources(@PathVariable String id)throws Exception{return guidance.sendResourcesAndMakeNotes(id,new com.reps.demogcloud.models.punishment.ResourceUpdateRequest());}
        @PostMapping("/__test__/domain/behavior") Object save(@RequestBody java.util.List<com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorRequest> requests){return behavior.saveTrackedBehaviorEvents(requests);}
        @GetMapping("/__test__/domain/behavior/{email}") Object history(@PathVariable String email){return behavior.getStudentTrackedBehaviorTimeline(email);}
        @PostMapping("/__test__/domain/roster/{email}") Object rosterChange(@PathVariable String email,@RequestBody com.reps.demogcloud.models.employee.ClassRequest request){return rosters.addOrUpdateClassToEmployee(email,request);}
        @PostMapping("/__test__/domain/student/{id}/note") Object studentNote(@PathVariable String id,@RequestBody com.reps.demogcloud.models.punishment.ThreadEvent event){return studentAdmin.updateStudentNotes(id,event);}
        final CanonicalCoreWorkflows core;
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
        database="reps_a7_domain_test_"+UUID.randomUUID().toString().replace("-","");client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);
        app=new AnnotationConfigWebApplicationContext();app.setServletContext(new MockServletContext());app.getEnvironment().setActiveProfiles("tenant-isolated-verification");
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated",Map.of("reps.tenancy.core-preparation-enabled","true","reps.tenancy.domain-preparation-enabled","true","auth.sso.google.enabled","false")));
        app.register(Config.class);app.refresh();jwt=app.getBean(JwtUtils.class);mvc=MockMvcBuilders.webAppContextSetup(app)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
    }
    @AfterAll static void stop(){try{if(app!=null)app.close();if(mongo!=null && database.matches("reps_a7_domain_test_[0-9a-f]{32}"))mongo.getDb().drop();}finally{if(client!=null)client.close();}}
    @BeforeEach void seed(){
        failSecond=false;inserts.set(0);reset(app.getBean(com.reps.demogcloud.services.WorkflowAccessService.class),app.getBean(com.reps.demogcloud.services.UserContextService.class),app.getBean(com.reps.demogcloud.services.EmailService.class),app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class));
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
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getAs(String path,String name){return get("/__test__/core/"+path).header("Authorization","Bearer "+token(name));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAs(String path,String name){return post("/__test__/core/"+path).header("Authorization","Bearer "+token(name)).contentType(MediaType.APPLICATION_JSON);}
    void rawSet(String collection,String id,String field,Object value){mongo.getCollection(collection).updateOne(new Document("_id",id),new Document("$set",new Document(field,value)));}
    List<Document> studentState(){return mongo.getCollection("students").find().sort(new Document("_id",1)).into(new ArrayList<>());}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder dpost(String path,String name){return post("/__test__/domain/"+path).header("Authorization","Bearer "+token(name)).contentType(MediaType.APPLICATION_JSON);}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder dput(String path,String name){return put("/__test__/domain/"+path).header("Authorization","Bearer "+token(name)).contentType(MediaType.APPLICATION_JSON);}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder dget(String path,String name){return get("/__test__/domain/"+path).header("Authorization","Bearer "+token(name));}
    String event(String student,String consequence){return "{\"studentEmail\":\""+student+"@example.test\",\"behaviorCode\":\"PHONE_OUT\",\"consequenceCode\":\""+consequence+"\"}";}
    @AfterEach void noLegacyAuthorityOrProviders(){verifyNoInteractions(app.getBean(com.reps.demogcloud.services.WorkflowAccessService.class),app.getBean(com.reps.demogcloud.services.UserContextService.class),app.getBean(com.reps.demogcloud.services.EmailService.class),app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class));}

    @Test void actualGuidanceCreationDerivesOwnerAndPreservesHistoryOnNoteAndClosure()throws Exception{
        var response=mvc.perform(dpost("guidance","teacherA").content("{\"guidance\":{\"studentEmail\":\"childA@example.test\",\"referralDescription\":[\"Synthetic request\"]}}"))
                .andExpect(status().isOk()).andReturn();
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getResponse().getContentAsString()).get("guidance").get(0).get("guidanceId").asText();
        mvc.perform(dput("guidance/"+id+"/note","teacherA").content("{\"event\":\"NOTE\",\"content\":\"Synthetic note\",\"createdBy\":\"spoof@example.test\"}")).andExpect(status().isOk());
        mvc.perform(dput("guidance/"+id+"/status/CLOSED","guideA")).andExpect(status().isOk());
        var raw=mongo.getCollection("Guidance").find(new Document("_id",id)).first();assertEquals("school-A",raw.getString("tenantSchoolId"));
        assertEquals("CLOSED",raw.getString("status"));assertEquals(2,((List<?>)raw.get("notesArray")).size());assertEquals(2L,((Number)raw.get("revision")).longValue());
        assertEquals("teacherA@example.test",((Document)((List<?>)raw.get("notesArray")).get(0)).getString("createdBy"));
    }
    @Test void actualGuidanceRejectsCrossTenantAndUnassignedMutations()throws Exception{
        mvc.perform(dput("guidance/GUIDANCEB/status/CLOSED","adminA")).andExpect(status().isForbidden());
        mvc.perform(dput("guidance/GUIDANCEA/status/CLOSED","unassignedA")).andExpect(status().isForbidden());
        mvc.perform(dpost("guidance","adminA").content("{\"guidance\":{\"studentEmail\":\"childB@example.test\",\"referralDescription\":[\"Synthetic\"]}}")) .andExpect(status().isForbidden());
        mvc.perform(dpost("guidance","teacherA").content("{\"guidance\":{\"studentEmail\":\"childA@example.test\",\"school\":\"Same School\",\"referralDescription\":[\"Synthetic\"]}}")) .andExpect(status().isForbidden());
        assertEquals(2,mongo.getCollection("Guidance").countDocuments());
    }
    @Test void actualGuidanceListIsScopedAndResourcesFailBeforeEffects()throws Exception{
        mvc.perform(dget("guidance","adminA")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        var before=mongo.getCollection("Guidance").find(new Document("_id","GUIDANCEA")).first();
        mvc.perform(dpost("guidance/GUIDANCEA/resources","guideA")).andExpect(status().isServiceUnavailable());
        assertEquals(before,mongo.getCollection("Guidance").find(new Document("_id","GUIDANCEA")).first());
    }
    @Test void actualTrackedBehaviorTransactionCommitsCanonicalRecordsAndScopedReads()throws Exception{
        mvc.perform(dpost("behavior","teacherA").content("["+event("childA","WARNING")+","+event("childA","PHONE_CONFISCATED")+"]")).andExpect(status().isOk());
        assertEquals(2,mongo.getCollection("TrackedBehaviorEvents").countDocuments(new Document("tenantSchoolId","school-A")));
        mvc.perform(dget("behavior/childA@example.test","teacherA")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(dget("behavior/childA@example.test","adminB")).andExpect(status().isForbidden());
        assertEquals(30,mongo.findById("teacherA",Employee.class).getCurrency());assertEquals(12,mongo.findById("childA",Student.class).getCurrency());
    }
    @Test void actualTrackedBehaviorValidatesWholeBatchBeforeFirstWrite()throws Exception{
        mvc.perform(dpost("behavior","teacherA").content("["+event("childA","WARNING")+","+event("childB","WARNING")+"]")).andExpect(status().isForbidden());
        assertEquals(0,mongo.getCollection("TrackedBehaviorEvents").countDocuments());
    }
    @Test void actualTrackedBehaviorRollsBackSecondWriteFailure(){
        failSecond=true;
        var service=app.getBean(com.reps.demogcloud.services.trackedBehavior.TrackedBehaviorServiceImpl.class);
        var details=app.getBean(CustomUserDetailsService.class).loadUserByUsername("teacherA@example.test");
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(details,null,details.getAuthorities()));
        try{
            var request=new com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorRequest();request.setStudentEmail("childA@example.test");request.setBehaviorCode("PHONE_OUT");request.setConsequenceCode("WARNING");
            var second=new com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorRequest();second.setStudentEmail("childA@example.test");second.setBehaviorCode("PHONE_OUT");second.setConsequenceCode("PHONE_CONFISCATED");
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,()->service.saveTrackedBehaviorEvents(List.of(request,second)));
            assertEquals(0,mongo.getCollection("TrackedBehaviorEvents").countDocuments());
        }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
    @Test void actualRosterServiceUsesCanonicalOwnerAndRevision()throws Exception{
        String body="{\"expectedRosterRevision\":0,\"createOnly\":true,\"classToUpdate\":{\"className\":\"Science\",\"classPeriod\":\"2\",\"classRoster\":[\"childA@example.test\"]}}";
        mvc.perform(dpost("roster/teacherA@example.test","teacherA").content(body)).andExpect(status().isOk());
        mvc.perform(dpost("roster/teacherA@example.test","teacherA").content(body)).andExpect(status().isConflict());
        mvc.perform(dpost("roster/teacherB@example.test","adminA").content(body)).andExpect(status().isForbidden());
        assertEquals("school-A",mongo.findById("teacherA",Employee.class).getTenantSchoolId());
    }
    @Test void disabledArchivedAndUnmappedActorsCannotReachDomainWrites()throws Exception{
        String t=token("teacherA");rawSet("users","teacherA","enabled",false);
        mvc.perform(post("/__test__/domain/behavior").header("Authorization","Bearer "+t).contentType(MediaType.APPLICATION_JSON).content("["+event("childA","WARNING")+"]")).andExpect(status().isUnauthorized());
        rawSet("students","childA","archived",true);mvc.perform(dpost("behavior","adminA").content("["+event("childA","WARNING")+"]")).andExpect(status().isForbidden());
        rawSet("users","adminA","tenantSchoolId",null);mvc.perform(dget("guidance","adminA")).andExpect(status().isForbidden());
        assertEquals(0,mongo.getCollection("TrackedBehaviorEvents").countDocuments());
    }
    @Test void actualStudentNoteUsesCanonicalRelationshipAndPreservesBalance()throws Exception{
        String note="{\"event\":\"NOTE\",\"content\":\"Synthetic note\",\"createdBy\":\"forged@example.test\"}";
        mvc.perform(dpost("student/childA/note","teacherA").content(note)).andExpect(status().isOk());
        mvc.perform(dpost("student/childB/note","adminA").content(note)).andExpect(status().isForbidden());
        rawSet("employee","teacherA","classes",List.of());mvc.perform(dpost("student/childA/note","teacherA").content(note)).andExpect(status().isForbidden());
        var student=mongo.findById("childA",Student.class);assertEquals(12,student.getCurrency());assertEquals(1,student.getNotesArray().size());assertEquals("teacherA@example.test",student.getNotesArray().get(0).getCreatedBy());
    }
    record Root(Class<?> type,Class<?> repository,String collection,String version){}
    Root root(String name){return switch(name){
        case "punishment"->new Root(com.reps.demogcloud.models.punishment.Punishment.class,PunishRepository.class,"Punishments","revision");
        case "office"->new Root(com.reps.demogcloud.models.officeReferral.OfficeReferral.class,OfficeReferralRepository.class,"OfficeReferrals","revision");
        case "guidance"->new Root(com.reps.demogcloud.models.guidance.GuidanceReferral.class,GuidanceRepository.class,"Guidance","revision");
        case "behavior"->new Root(com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent.class,TrackedBehaviorEventRepository.class,"TrackedBehaviorEvents","persistenceRevision");
        case "template"->new Root(com.reps.demogcloud.models.assignments.AssignmentTemplate.class,AssignmentTemplateRepository.class,"assignment_templates","persistenceRevision");
        case "binding"->new Root(com.reps.demogcloud.models.assignments.AssignmentTemplateBinding.class,AssignmentTemplateBindingRepository.class,"assignment_template_bindings","persistenceRevision");
        default->throw new IllegalArgumentException();};}
    @ParameterizedTest @ValueSource(strings={"punishment","office","guidance","behavior","template","binding"})
    @SuppressWarnings({"rawtypes","unchecked"}) void allRootRepositorySavesPreserveOwnersAndRejectStaleOrForgedUpdates(String name){
        var r=root(name);var original=new Document("_id","protected-root").append("tenantSchoolId","school-A").append(r.version(),0L).append("retainedExtension",new Document("marker","history"));
        mongo.getCollection(r.collection()).insertOne(original);
        var repo=(org.springframework.data.mongodb.repository.MongoRepository)app.getBean(r.repository());
        Object stale=repo.findById("protected-root").orElseThrow(),current=repo.findById("protected-root").orElseThrow();
        new org.springframework.beans.BeanWrapperImpl(current).setPropertyValue("tenantSchoolId",null);
        Object saved=repo.save(current);assertEquals("school-A",new org.springframework.beans.BeanWrapperImpl(saved).getPropertyValue("tenantSchoolId"));
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->repo.save(stale));
        Object forged=repo.findById("protected-root").orElseThrow();new org.springframework.beans.BeanWrapperImpl(forged).setPropertyValue("tenantSchoolId","school-B");
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->repo.save(forged));
        var raw=mongo.getCollection(r.collection()).find(new Document("_id","protected-root")).first();assertEquals(original.get("retainedExtension"),raw.get("retainedExtension"));assertEquals("school-A",raw.getString("tenantSchoolId"));assertEquals(1L,((Number)raw.get(r.version())).longValue());
    }
    @Test void rootSaveOwnershipRaceCannotOverwriteReviewedMetadata(){
        var repo=app.getBean(GuidanceRepository.class);var record=repo.findById("GUIDANCEA").orElseThrow();var spy=spy(mongo);
        doAnswer(call->{rawSet("Guidance","GUIDANCEA","tenantSchoolId","school-B");return call.callRealMethod();}).when(spy)
                .findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),any(org.springframework.data.mongodb.core.query.Update.class),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(com.reps.demogcloud.models.guidance.GuidanceReferral.class));
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->TenantRootSave.save(spy,record));
        assertEquals("school-B",mongo.getCollection("Guidance").find(new Document("_id","GUIDANCEA")).first().getString("tenantSchoolId"));
    }
    @Test void legacyUnboundTemplateUpgradeUsesAbsentRevisionAndNeverInfersOwner(){
        mongo.getCollection("assignment_templates").insertOne(new Document("_id","legacy").append("schoolId","Same School").append("unknownHistory","preserved"));
        var repo=app.getBean(AssignmentTemplateRepository.class);var first=repo.findById("legacy").orElseThrow();var stale=repo.findById("legacy").orElseThrow();first.setName("Updated");repo.save(first);
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->repo.save(stale));
        var raw=mongo.getCollection("assignment_templates").find(new Document("_id","legacy")).first();assertFalse(raw.containsKey("tenantSchoolId"));assertEquals("Same School",raw.getString("schoolId"));assertEquals("preserved",raw.getString("unknownHistory"));
    }
    @Test void snapshotCopiesRetainHiddenOwnershipAndHistoricalSubmissions(){
        var template=new com.reps.demogcloud.models.assignments.AssignmentTemplate();template.setId("templateA");template.setTenantSchoolId("school-A");template.setPersistenceRevision(5L);
        var work=new com.reps.demogcloud.models.assignments.AssignmentWork();work.setTemplate(template);
        var submission=new com.reps.demogcloud.models.assignments.AssignmentWork.Submission();submission.setTemplate(template);submission.setOutcome("REJECTED");work.setSubmissions(new ArrayList<>(List.of(submission)));
        var copy=com.reps.demogcloud.models.assignments.AssignmentWork.copy(work,com.reps.demogcloud.models.assignments.AssignmentWork.class);
        assertEquals("school-A",copy.getTemplate().getTenantSchoolId());assertEquals(5L,copy.getTemplate().getPersistenceRevision());assertEquals("school-A",copy.getSubmissions().get(0).getTemplate().getTenantSchoolId());
        var p=app.getBean(PunishRepository.class).findById("PUNISHMENTA").orElseThrow();p.setAssignmentWork(copy);app.getBean(PunishRepository.class).save(p);
        var loaded=app.getBean(PunishRepository.class).findById("PUNISHMENTA").orElseThrow();assertEquals("REJECTED",loaded.getAssignmentWork().getSubmissions().get(0).getOutcome());
        loaded.getAssignmentWork().getTemplate().setTenantSchoolId("school-B");assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->app.getBean(PunishRepository.class).save(loaded));
    }
    @Test void canonicalTemplateCannotBeHardDeletedByLegacyRepositoryPath(){
        var repo=app.getBean(AssignmentTemplateRepository.class);var template=repo.findById("templateA").orElseThrow();
        assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->repo.delete(template));assertTrue(repo.findById("templateA").isPresent());
    }
}
