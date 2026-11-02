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

/** Shared real Spring application fixture; providers intercepted and every class gets a fresh disposable database. */
@EnabledIfEnvironmentVariable(named="REPS_TENANCY_TEST_MONGODB_URI",matches=".+")
@org.springframework.boot.test.context.SpringBootTest(classes={com.reps.demogcloud.DemoGcloudApplication.class, CanonicalApplicationFixture.NoBackground.class},webEnvironment=org.springframework.boot.test.context.SpringBootTest.WebEnvironment.MOCK)
@org.springframework.test.context.ContextConfiguration(initializers=IsolatedCanonicalBootstrap.class)
@org.springframework.test.annotation.DirtiesContext(classMode=org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_CLASS)
abstract class CanonicalApplicationFixture {

    @org.springframework.beans.factory.annotation.Autowired org.springframework.web.context.WebApplicationContext app;
    @org.springframework.beans.factory.annotation.Autowired MongoTemplate mongo;
    @org.springframework.beans.factory.annotation.Autowired JwtUtils jwt;
    MockMvc mvc;
    org.springframework.test.web.client.MockRestServiceServer oauthHttp,gmailHttp;
    @AfterEach void verifyHttp(){oauthHttp.verify();gmailHttp.verify();}

    @org.springframework.test.context.bean.override.mockito.MockitoBean org.springframework.mail.javamail.JavaMailSender smtp;
    @org.springframework.test.context.bean.override.mockito.MockitoBean com.reps.demogcloud.services.translation.TranslationService translation;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.reps.demogcloud.services.email.RequiredNotificationService notifications;
    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean com.reps.demogcloud.services.email.EmailRoutingService router;
    @org.springframework.boot.test.context.TestConfiguration static class NoBackground {
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor suppressSchedules(){return factory->{
            var registry=(org.springframework.beans.factory.support.BeanDefinitionRegistry)factory;
            String name="org.springframework.context.annotation.internalScheduledAnnotationProcessor";
            if(registry.containsBeanDefinition(name))registry.removeBeanDefinition(name);
        };}
    }
    @AfterAll static void cleanup(){if(IsolatedCanonicalBootstrap.database!=null&&IsolatedCanonicalBootstrap.database.matches("reps_a7_activation_[0-9a-f]{32}"))IsolatedCanonicalBootstrap.client.getDatabase(IsolatedCanonicalBootstrap.database).drop();if(IsolatedCanonicalBootstrap.client!=null)IsolatedCanonicalBootstrap.client.close();IsolatedCanonicalBootstrap.client=null;IsolatedCanonicalBootstrap.PROPERTIES.clear();java.net.ProxySelector.setDefault(IsolatedCanonicalBootstrap.previousProxy);}
    @BeforeEach void seed(){
        mvc=MockMvcBuilders.webAppContextSetup(app).defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
        clearInvocations(smtp);
        oauthHttp=org.springframework.test.web.client.MockRestServiceServer.bindTo((org.springframework.web.client.RestTemplate)org.springframework.test.util.ReflectionTestUtils.getField(app.getBean(GoogleOAuthTokenStore.class),"http")).build();
        gmailHttp=org.springframework.test.web.client.MockRestServiceServer.bindTo((org.springframework.web.client.RestTemplate)org.springframework.test.util.ReflectionTestUtils.getField(app.getBean(GoogleMailService.class),"restTemplate")).build();
        when(smtp.createMimeMessage()).thenAnswer(i->new jakarta.mail.internet.MimeMessage(jakarta.mail.Session.getInstance(new Properties())));
        when(translation.translate(any(),any(),any())).thenAnswer(i->i.getArgument(0));
        reset(app.getBean(com.reps.demogcloud.services.email.RequiredNotificationService.class));clearInvocations(app.getBean(com.reps.demogcloud.services.email.EmailRoutingService.class));
        for(String name:mongo.getCollectionNames())if(!name.equals("a7_activation_control"))mongo.getCollection(name).deleteMany(new Document());
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
    void account(String name,String role,String school,boolean roster){
        var u=new UserModel();u.setFirstName("Synthetic");u.setLastName(name);u.setId(name);u.setUsername(name+"@example.test");u.setPassword(app.getBean(org.springframework.security.crypto.password.PasswordEncoder.class).encode("Synthetic-password!"));u.setRoles(Set.of(new RoleModel(null,role)));u.setTenantSchoolId("school-"+school);u.setSchool("Same School");mongo.insert(u);
        if(role.equals("STUDENT")){
            var s=new Student();s.setFirstName("Synthetic");s.setLastName(name);s.setStudentIdNumber(name);s.setStudentEmail(u.getUsername());s.setTenantSchoolId(u.getTenantSchoolId());s.setSchool("Same School");s.setCurrency(12);s.setParentEmail("guardian@example.test");s.setPreferredLanguage("en");s.setGuidanceEmail("guide"+school+"@example.test");s.setAdminEmail("admin"+school+"@example.test");s.setSpotters(List.of("other"+school+"@example.test"));mongo.insert(s);
        }else{
            var e=new Employee();e.setFirstName("Synthetic");e.setLastName(name);e.setEmployeeId(name);e.setEmail(u.getUsername());e.setTenantSchoolId(u.getTenantSchoolId());e.setSchool("Same School");e.setRoles(u.getRoles());e.setCurrency(30);
            if(roster){var c=new Employee.ClassRoster();c.setClassName("Math");c.setClassPeriod("1");c.setClassRoster(List.of("child"+school+"@example.test"));e.setClasses(List.of(c));}mongo.insert(e);
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


    void manifest()throws Exception{
        var actors=new ArrayList<Document>();
        for(String school:List.of("A","B"))for(String role:List.of("teacher","admin","guide")){
            String id=role+school;actors.add(new Document("accountId",id).append("profileId",id).append("tenantSchoolId","school-"+school).append("historyComplete",true).append("evidenceReference","synthetic-reviewed-history").append("legacyNamespaces",List.of(new Document("actor",(id+"@example.test").toLowerCase(Locale.ROOT)).append("school","same school"))));
        }
        var manifest=new Document("_id","synthetic-review").append("schema","reps-currency-replay/v1").append("historyReviewed",true).append("legacyWritersDrained",true).append("reviewReference","synthetic-reviewed").append("actors",actors).append("receipts",List.of());
        var type=Class.forName("com.reps.demogcloud.services.currency.CurrencyReplayManifest");var fingerprint=type.getDeclaredMethod("fingerprint",Document.class);fingerprint.setAccessible(true);
        mongo.insert(manifest,"currency_replay_manifests");((org.springframework.core.env.ConfigurableEnvironment)app.getEnvironment()).getPropertySources().addFirst(new MapPropertySource("review",Map.of("reps.tenancy.currency-replay-manifest-id","synthetic-review","reps.tenancy.currency-replay-manifest-sha256",fingerprint.invoke(null,manifest))));
    }

}
