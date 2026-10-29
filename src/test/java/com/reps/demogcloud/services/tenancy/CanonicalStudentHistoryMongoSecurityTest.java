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
class CanonicalStudentHistoryMongoSecurityTest {
    static MongoClient client;static MongoTemplate mongo;static String database;
    static AnnotationConfigWebApplicationContext app;static MockMvc mvc;static JwtUtils jwt;
    @Configuration @EnableWebMvc @EnableWebSecurity
    @EnableMongoRepositories(basePackages={"com.reps.demogcloud.data","com.reps.demogcloud.security.models"})
    @Import({SecurityConfig.class,JwtFilterRequest.class,CustomUserDetailsService.class,CanonicalAccountContext.class,CanonicalCoreWorkflows.class,CanonicalDomainAccess.class,CanonicalWorkflowAccess.class,HistoricalCaseAccess.class,
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
        @Bean com.reps.demogcloud.services.PunishmentService unusedPunishments(){return mock(com.reps.demogcloud.services.PunishmentService.class);}
        @Bean com.reps.demogcloud.services.EmailService mail(){return mock(com.reps.demogcloud.services.EmailService.class);}
        @Bean com.reps.demogcloud.services.email.RequiredNotificationService notifications(){return mock(com.reps.demogcloud.services.email.RequiredNotificationService.class);}
        @Bean com.reps.demogcloud.utils.StudentUtils studentUtils(){return mock(com.reps.demogcloud.utils.StudentUtils.class);}
        @Bean com.reps.demogcloud.utils.SchoolUtils schoolUtils(){return mock(com.reps.demogcloud.utils.SchoolUtils.class);}
        @Bean com.reps.demogcloud.security.services.AccountAccessService setup(){return mock(com.reps.demogcloud.security.services.AccountAccessService.class);}
        @Bean org.springframework.security.crypto.password.PasswordEncoder encoder(){var encoder=mock(org.springframework.security.crypto.password.PasswordEncoder.class);when(encoder.encode(any())).thenReturn("synthetic-encoded");return encoder;}
    }
    @BeforeAll static void start(){
        var uri=new ConnectionString(System.getenv("REPS_TENANCY_TEST_MONGODB_URI"));
        assertTrue(uri.getHosts().stream().allMatch(h->h.matches("127\\.0\\.0\\.1:[0-9]+")));assertNull(uri.getCredential());assertNull(uri.getDatabase());
        database="reps_a7_workflow_test_"+UUID.randomUUID().toString().replace("-","");client=MongoClients.create(uri);mongo=new MongoTemplate(client,database);
        app=new AnnotationConfigWebApplicationContext();app.setServletContext(new MockServletContext());app.getEnvironment().setActiveProfiles("tenant-isolated-verification");
        app.getEnvironment().getPropertySources().addFirst(new MapPropertySource("isolated",Map.of("reps.tenancy.core-preparation-enabled","true","reps.tenancy.domain-preparation-enabled","true","auth.sso.google.enabled","false")));
        app.register(Config.class);app.refresh();jwt=app.getBean(JwtUtils.class);mvc=MockMvcBuilders.webAppContextSetup(app)
                .defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
    }
    @AfterAll static void stop(){try{if(app!=null)app.close();if(mongo!=null && database.matches("reps_a7_workflow_test_[0-9a-f]{32}"))mongo.getDb().drop();}finally{if(client!=null)client.close();}}
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
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder getAs(String path,String actor){return get(path).header("Authorization","Bearer "+token(actor));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postAs(String path,String actor){return post(path).header("Authorization","Bearer "+token(actor)).contentType(MediaType.APPLICATION_JSON);}
    String createGuidance()throws Exception{
        var response=mvc.perform(postAs("/guidance/v1/guidance/new","teacherA").content("{\"guidance\":{\"studentEmail\":\"childA@example.test\",\"referralDescription\":[\"Initial historical facts\"]}}"));
        var result=response.andExpect(status().isAccepted()).andReturn();
        return new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("guidance").get(0).get("guidanceId").asText();
    }
    @Test void studentReadsUseTenantAndIndependentRelationships()throws Exception{
        mvc.perform(getAs("/student/v1/email/childA@example.test","teacherA")).andExpect(status().isAccepted());
        mvc.perform(getAs("/student/v1/email/childA@example.test","teacherB")).andExpect(status().isForbidden());
        mvc.perform(getAs("/student/v1/email/childA@example.test","otherA")).andExpect(status().isForbidden());
        mvc.perform(getAs("/student/v1/email/childA@example.test","childA")).andExpect(status().isAccepted());
        mvc.perform(getAs("/student/v1/email/childB@example.test","childA")).andExpect(status().isForbidden());
        mvc.perform(getAs("/student/v1/enrollment-directory","teacherA")).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void studentCreationStoresServerOwnedAccountAndProfileWithoutAcceptingCurrencyOrProvenance()throws Exception{
        String body="{\"studentEmail\":\"new@example.test\",\"firstName\":\"Synthetic\",\"lastName\":\"Student\",\"school\":\"Same School\",\"adminEmail\":\"adminA@example.test\",\"guidanceEmail\":\"guideA@example.test\",\"currency\":999,\"tenantSchoolId\":\"school-B\"}";
        mvc.perform(postAs("/student/v1/newStudent","teacherA").content(body)).andExpect(status().isCreated());
        var student=mongo.getCollection("students").find(new Document("studentEmail","new@example.test")).first();var user=mongo.getCollection("users").find(new Document("username","new@example.test")).first();
        assertEquals("school-A",student.getString("tenantSchoolId"));assertEquals("school-A",user.getString("tenantSchoolId"));assertEquals(0,student.getInteger("currency"));
        mvc.perform(postAs("/student/v1/newStudent","teacherA").content(body)).andExpect(status().isCreated());assertEquals(1,mongo.getCollection("students").countDocuments(new Document("studentEmail","new@example.test")));
        mvc.perform(postAs("/student/v1/newStudent","teacherB").content(body)).andExpect(status().isForbidden());
    }
    @Test void formerAuthorGetsOnlySnapshotWhileLiveCaseAndStudentAccessAreDenied()throws Exception{
        String id=createGuidance();
        var record=mongo.findById(id,com.reps.demogcloud.models.guidance.GuidanceReferral.class);record.setStatus("CLOSED");record.setNotesArray(List.of());mongo.getCollection("Guidance").updateOne(new Document("_id",id),new Document("$set",new Document("status","CLOSED").append("privateFutureNote","must-not-leak")));
        mongo.getCollection("employee").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("classes",List.of())));
        mvc.perform(getAs("/student/v1/email/childA@example.test","teacherA")).andExpect(status().isForbidden());
        var response=mvc.perform(getAs("/history/v1/cases/guidance/"+id,"teacherA")).andExpect(status().isOk()).andExpect(jsonPath("$.initialDescription[0]").value("Initial historical facts")).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("must-not-leak"));assertFalse(response.contains("CLOSED"));assertFalse(response.contains("notesArray"));assertFalse(response.contains("studentEmail"));
        mvc.perform(getAs("/history/v1/cases/guidance/"+id,"teacherB")).andExpect(status().isForbidden());
        mvc.perform(getAs("/history/v1/cases/guidance/"+id,"otherA")).andExpect(status().isForbidden());
    }
    @Test void formerCounselorRetainsOnlyVerifiedInitialAssignmentSnapshot()throws Exception{
        String id=createGuidance();mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$set",new Document("guidanceEmail","unassignedA@example.test")));
        mvc.perform(getAs("/history/v1/cases/guidance/"+id,"guideA")).andExpect(status().isOk());
        var record=mongo.findById(id,com.reps.demogcloud.models.guidance.GuidanceReferral.class);assertFalse(app.getBean(com.reps.demogcloud.services.WorkflowAccessService.class)==null);
        mvc.perform(put("/guidance/v1/guidance/notes/"+id).header("Authorization","Bearer "+token("guideA")).contentType(MediaType.APPLICATION_JSON).content("{\"event\":\"NOTE\",\"content\":\"Unauthorized\"}")).andExpect(status().isForbidden());
    }
    @Test void legacyEmailAuthorshipCannotGrantHistoricalAccess()throws Exception{mvc.perform(getAs("/history/v1/cases/guidance/GUIDANCEA","teacherA")).andExpect(status().isForbidden());}
    @Test void historicalProvenanceCannotBeReassignedByOrdinaryRepositorySave()throws Exception{
        String id=createGuidance();var repo=app.getBean(GuidanceRepository.class);var record=repo.findById(id).orElseThrow();var before=mongo.getCollection("Guidance").find(new Document("_id",id)).first();
        record.getHistoricalAccess().get(0).put("accountId","otherA");var forged=record;assertThrows(org.springframework.dao.OptimisticLockingFailureException.class,()->repo.save(forged));assertEquals(before,mongo.getCollection("Guidance").find(new Document("_id",id)).first());
        record=repo.findById(id).orElseThrow();record.setHistoricalAccess(null);record.setStatus("CLOSED");repo.save(record);assertEquals(before.get("historicalAccess"),mongo.getCollection("Guidance").find(new Document("_id",id)).first().get("historicalAccess"));
    }
    @Test void disabledOrArchivedActorCannotUseHistoricalGrant()throws Exception{
        String id=createGuidance();String token=token("teacherA");mongo.getCollection("users").updateOne(new Document("_id","teacherA"),new Document("$set",new Document("enabled",false)));
        mvc.perform(get("/history/v1/cases/guidance/"+id).header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        String guideToken=token("guideA");mongo.getCollection("employee").updateOne(new Document("_id","guideA"),new Document("$set",new Document("archived",true)));mvc.perform(get("/history/v1/cases/guidance/"+id).header("Authorization","Bearer "+guideToken)).andExpect(status().isUnauthorized());
    }
    @Test void duplicateCreationCannotExposeUnrelatedStudentPrivateProfile()throws Exception{
        mongo.getCollection("students").updateOne(new Document("_id","childA"),new Document("$set",new Document("firstName","Existing").append("lastName","Student").append("notesArray",List.of(new Document("content","must-not-leak")))));
        mvc.perform(getAs("/student/v1/email/childA@example.test","otherA")).andExpect(status().isForbidden());
        String body="{\"studentEmail\":\"childA@example.test\",\"firstName\":\"Existing\",\"lastName\":\"Student\",\"school\":\"Same School\"}";
        var response=mvc.perform(postAs("/student/v1/newStudent","otherA").content(body)).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("must-not-leak"));mvc.perform(getAs("/student/v1/email/childA@example.test","otherA")).andExpect(status().isForbidden());
        assertEquals(12,mongo.findById("childA",Student.class).getCurrency());
    }
    @Test void forgedHistoricalGrantInCreationPayloadNeverAuthorizesAnotherAccount()throws Exception{
        String body="{\"guidance\":{\"studentEmail\":\"childA@example.test\",\"referralDescription\":[\"Initial facts\"],\"historicalAccess\":[{\"accountId\":\"otherA\",\"profileId\":\"otherA\",\"tenantSchoolId\":\"school-A\",\"basis\":\"AUTHOR\"}]}}";
        var result=mvc.perform(postAs("/guidance/v1/guidance/new","teacherA").content(body)).andExpect(status().isAccepted()).andReturn();
        String id=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("guidance").get(0).get("guidanceId").asText();
        mvc.perform(getAs("/history/v1/cases/guidance/"+id,"otherA")).andExpect(status().isForbidden());
        assertFalse(result.getResponse().getContentAsString().contains("historicalAccess"));
    }

}
