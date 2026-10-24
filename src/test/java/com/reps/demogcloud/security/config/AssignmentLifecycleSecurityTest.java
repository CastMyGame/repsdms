package com.reps.demogcloud.security.config;

import com.reps.demogcloud.controllers.*;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.assignments.*;
import com.reps.demogcloud.models.officeReferral.*;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.email.*;
import com.reps.demogcloud.utils.OfficeReferralUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production security chain + real persisted-identity authorization and lifecycle service; persistence/providers mocked. */
@SpringJUnitWebConfig({WorkflowSecurityTest.App.class,AssignmentLifecycleSecurityTest.Extra.class})
class AssignmentLifecycleSecurityTest extends WorkflowSecurityTest {
    @Configuration @Import({AssignmentLifecycleController.class,AssignmentLifecycleService.class,OfficeReferralController.class,OfficeReferralService.class,PunishController.class})
    static class Extra {
        @Bean RequiredNotificationService notificationPolicy(MongoTemplate mongo, Environment env, StudentRepository students,UserContextService users) {
            var router=mock(EmailRoutingService.class);when(router.isPhase1RoutingEnabled()).thenReturn(Boolean.parseBoolean(env.getProperty("reps.email.routing.phase1-enabled","false")));
            return new RequiredNotificationService(mongo,env,students,users,router,mock(EmailTemplateBuilderService.class));
        }
    }
    @MockitoBean MongoTemplate mongo;
    @MockitoBean OfficeReferralRepository offices;
    @MockitoBean OfficeReferralUtils officeUtils;
    @MockitoBean EmailService emailService;
    @MockitoBean EmailSenderService emailSender;
    @MockitoBean PunishmentService punishService;
    Punishment assignmentCase;
    @BeforeEach void assignmentFixture() {
        var q=new AssignmentTemplate.TemplateQuestion();q.setId("q1");q.setType(AssignmentTemplate.QuestionType.EXPLORATORY_OPEN);q.setPrompt("Reflect");
        own.setInfractionName("Synthetic");own.setLevel(1);own.setQuestions(List.of(q));
        assignmentCase=punishments.findById("case").orElseThrow();assignmentCase.setStatus("OPEN");assignmentCase.setRevision(0L);assignmentCase.setAssignmentWork(AssignmentWork.assigned(own));
        when(mongo.findAndModify(any(Query.class),any(Update.class),any(FindAndModifyOptions.class),eq(Punishment.class))).thenReturn(assignmentCase);
        var office=new OfficeReferral();office.setOfficeReferralId("office");office.setSchool(SCHOOL);office.setStudentEmail(CHILD);office.setTeacherEmail(ACTOR);office.setAdminEmail(ACTOR);office.setStatus("OPEN");
        when(offices.findByOfficeReferralId("office")).thenReturn(office);
        when(offices.save(any())).thenAnswer(i -> i.getArgument(0));
        clearInvocations(punishments);
    }
    String command(String tail){return "{\"version\":0,\"actionId\":\""+UUID.randomUUID()+"\",\"answers\":{\"q1\":{\"text\":\"Response\"}},\"currentQuestionId\":\"q1\""+tail+"}";}
    @Test void anonymousCannotReadOrMutateAssignmentCases() throws Exception {
        mvc.perform(get("/assignments/v1/cases/case")).andExpect(status().isUnauthorized());
        mvc.perform(put("/assignments/v1/cases/case/save").contentType(MediaType.APPLICATION_JSON).content(command(""))).andExpect(status().isUnauthorized());
    }
    @Test void ownStudentCanReadAndSaveUsingPersistedIdentity() throws Exception {
        asStudent();mvc.perform(get("/assignments/v1/cases/case").with(user(CHILD).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(put("/assignments/v1/cases/case/save").with(user(CHILD).roles("STUDENT")).contentType(MediaType.APPLICATION_JSON).content(command(""))).andExpect(status().isOk());
    }
    @Test void studentCannotReadOrSaveAnotherStudentsCase() throws Exception {
        asStudent();assignmentCase.setStudentEmail("other@example.test");var other=new com.reps.demogcloud.models.student.Student();other.setStudentEmail("other@example.test");other.setSchool(SCHOOL);when(students.findByStudentEmailIgnoreCase("other@example.test")).thenReturn(other);
        mvc.perform(get("/assignments/v1/cases/case").with(user(CHILD).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(put("/assignments/v1/cases/case/save").with(user(CHILD).roles("STUDENT")).contentType(MediaType.APPLICATION_JSON).content(command(""))).andExpect(status().isForbidden());
    }
    @Test void studentCannotBindReviewOrCloseEvenWithForgedAuthorities() throws Exception {
        asStudent();
        for(var action:List.of("bind","review"))mvc.perform(put("/assignments/v1/cases/case/"+action).with(user(CHILD).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(command(",\"templateId\":\"own\",\"confirmRestart\":true,\"decision\":\"ACCEPT\""))).andExpect(status().isForbidden());
        mvc.perform(post("/punish/v1/close/case").with(user(CHILD).roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void incompleteStudentSubmissionFailsOnServer() throws Exception {
        asStudent();mvc.perform(put("/assignments/v1/cases/case/submit").with(user(CHILD).roles("STUDENT")).contentType(MediaType.APPLICATION_JSON)
                .content(command("").replace("Response","  "))).andExpect(status().isBadRequest());
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","GUIDANCE","ADMIN"})
    void relatedStaffCanReviewButCannotMasqueradeAsAStudent(String role) throws Exception {
        role(role);assignmentCase.setStatus("PENDING");assignmentCase.getAssignmentWork().setState("SUBMITTED");
        var submission=new AssignmentWork.Submission();submission.setId("previous");assignmentCase.getAssignmentWork().getSubmissions().add(submission);
        mvc.perform(put("/assignments/v1/cases/case/review").with(user(ACTOR).roles(role)).contentType(MediaType.APPLICATION_JSON).content(command(",\"decision\":\"ACCEPT\""))).andExpect(status().isOk());
        mvc.perform(put("/assignments/v1/cases/case/save").with(user(ACTOR).roles("STUDENT")).contentType(MediaType.APPLICATION_JSON).content(command(""))).andExpect(status().isForbidden());
    }
    @Test void unrelatedSameSchoolStaffCannotReview() throws Exception {
        assignmentCase.setTeacherEmail("other@example.test");assignmentCase.setStatus("PENDING");assignmentCase.getAssignmentWork().setState("SUBMITTED");
        var submission=new AssignmentWork.Submission();submission.setId("old");assignmentCase.getAssignmentWork().getSubmissions().add(submission);
        mvc.perform(put("/assignments/v1/cases/case/review").with(user(ACTOR).roles("TEACHER")).contentType(MediaType.APPLICATION_JSON).content(command(",\"decision\":\"ACCEPT\""))).andExpect(status().isForbidden());
    }
    @Test void crossSchoolCaseAndTemplateAreRejected() throws Exception {
        assignmentCase.setSchool("Other");mvc.perform(get("/assignments/v1/cases/case").with(user(ACTOR).roles("ADMIN"))).andExpect(status().isForbidden());
        assignmentCase.setSchool(SCHOOL);assignmentCase.setAssignmentWork(AssignmentWork.unavailable("Missing"));
        mvc.perform(put("/assignments/v1/cases/case/bind").with(user(ACTOR).roles("TEACHER")).contentType(MediaType.APPLICATION_JSON).content(command(",\"templateId\":\"foreign\",\"confirmRestart\":true"))).andExpect(status().isForbidden());
    }
    @Test void officeReferralsHaveNoStudentSubmitOrProgressAndOnlyAdminCanClose() throws Exception {
        asStudent();mvc.perform(post("/officeReferral/v1/submit/office").with(user(CHILD).roles("STUDENT"))).andExpect(status().isBadRequest());
        mvc.perform(put("/officeReferral/v1/office/index/1").with(user(CHILD).roles("STUDENT"))).andExpect(status().isBadRequest());
        mvc.perform(post("/officeReferral/v1/closeId").with(user(CHILD).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"office\"}")).andExpect(status().isForbidden());
        account.setUsername(ACTOR);role("TEACHER");mvc.perform(post("/officeReferral/v1/closeId").with(user(ACTOR).roles("TEACHER")).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"office\"}")).andExpect(status().isForbidden());
        role("ADMIN");mvc.perform(post("/officeReferral/v1/closeId").with(user(ACTOR).roles("ADMIN")).header("X-REPS-Action-Id",UUID.randomUUID().toString()).contentType(MediaType.APPLICATION_JSON).content("{\"id\":\"office\",\"comment\":\"Principal review\"}")).andExpect(status().isAccepted());
    }
    @Test void globalLegacyDescriptionAndDeletionMaintenanceIsNotAStudentBypass() throws Exception {
        asStudent();for(var path:List.of("descriptions","updates","emails","schoolName","infractionName","infractionLevel"))
            mvc.perform(put("/punish/v1/"+path).with(user(CHILD).roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void officeCaseReadsAreLimitedToAuthorizedStudentsAndSchools() throws Exception {
        asStudent();mvc.perform(get("/officeReferral/v1/id/office").with(user(CHILD).roles("STUDENT"))).andExpect(status().isAccepted());
        offices.findByOfficeReferralId("office").setSchool("Other");
        mvc.perform(get("/officeReferral/v1/id/office").with(user(CHILD).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/officeReferral/v1/punishments").with(user(CHILD).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(put("/officeReferral/v1/descriptions").with(user(CHILD).roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void legitimateAssignmentPreflightIsNotRedirectedToLogin() throws Exception {
        mvc.perform(options("/assignments/v1/cases/case/save").header("Origin","http://localhost:3000")
                .header("Access-Control-Request-Method","PUT").header("Access-Control-Request-Headers","authorization,content-type,x-reps-action-id"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000"));
    }
}
