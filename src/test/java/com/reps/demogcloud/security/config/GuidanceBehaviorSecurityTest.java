package com.reps.demogcloud.security.config;

import com.reps.demogcloud.controllers.*;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.employee.*;
import com.reps.demogcloud.services.student.*;
import com.reps.demogcloud.services.currency.*;
import com.reps.demogcloud.services.email.*;
import com.reps.demogcloud.utils.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Production security chain, real policy/services, mocked persistence and external transports. */
@SpringJUnitWebConfig(GuidanceBehaviorSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false","reps.email.routing.phase1-enabled=false","reps.email.notifications.enabled=false"})
class GuidanceBehaviorSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackageClasses=SecurityConfig.class,useDefaultFilters=false,includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=SecurityConfig.class))
    @Import({GuidanceController.class,TrackedBehaviorController.class,GuidanceService.class,
        com.reps.demogcloud.services.trackedBehavior.TrackedBehaviorServiceImpl.class,
        UserContextService.class,WorkflowAccessService.class,JwtFilterRequest.class,GlobalExceptionHandler.class})
    static class App { @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);} }
    @Autowired WebApplicationContext context; @Autowired org.springframework.core.env.Environment env;
    @MockitoBean UserRepository users; @MockitoBean EmployeeRepository employees; @MockitoBean StudentRepository students;
    @MockitoBean GuidanceRepository guidances; @MockitoBean TrackedBehaviorEventRepository behaviors;
    @MockitoBean SchoolRepository schools; @MockitoBean PunishRepository cases; @MockitoBean AssignmentTemplateRepository templates;
    @MockitoBean org.springframework.data.mongodb.core.MongoTemplate mongo; @MockitoBean EmailService email;
    @MockitoBean RequiredNotificationService notifications; @MockitoBean PunishmentService punishments;
    @MockitoBean GoogleOAuthTokenStore google; @MockitoBean CustomOAuth2UserService oidc; @MockitoBean OAuth2LoginSuccessHandler success;
    @MockitoBean OAuth2LoginFailureHandler failure; @MockitoBean CustomUserDetailsService details; @MockitoBean JwtUtils jwt;
    UserModel account; Employee employee; Student child,other; com.reps.demogcloud.models.guidance.GuidanceReferral referral; MockMvc mvc;
    static final String STAFF="staff@example.test",CHILD="child@example.test",OTHER="other@example.test";
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        account=new UserModel();account.setUsername(STAFF);account.setSchool("Testing");role("TEACHER");
        employee=new Employee();employee.setEmail(STAFF);employee.setSchool("Testing");var roster=new Employee.ClassRoster();roster.setClassRoster(List.of(CHILD));employee.setClasses(List.of(roster));
        child=student(CHILD);other=student(OTHER);child.setGuidanceEmail("counselor@example.test");
        referral=new com.reps.demogcloud.models.guidance.GuidanceReferral();referral.setGuidanceId("case");referral.setSchool("Testing");referral.setStudentEmail(CHILD);referral.setTeacherEmail(STAFF);referral.setGuidanceEmail("old-counselor@example.test");referral.setStatus("OPEN");referral.setNotesArray(new ArrayList<>());
        when(users.findByUsername(STAFF)).thenAnswer(i->account);when(employees.findByEmailIgnoreCase(STAFF)).thenReturn(employee);
        when(students.findByStudentEmailIgnoreCase(CHILD)).thenReturn(child);when(students.findByStudentEmailIgnoreCase(OTHER)).thenReturn(other);
        when(guidances.findBySchoolIgnoreCase("Testing")).thenAnswer(i->List.of(referral));when(guidances.findBySchoolIgnoreCaseAndStatus(eq("Testing"),anyString())).thenAnswer(i->List.of(referral));when(guidances.findById("case")).thenAnswer(i->Optional.of(referral));when(guidances.save(any())).thenAnswer(i->i.getArgument(0));
        when(behaviors.saveAll(anyList())).thenAnswer(i->i.getArgument(0));
        when(notifications.isEnabled()).thenReturn(Boolean.parseBoolean(env.getProperty("reps.email.notifications.enabled","false")));
    }
    Student student(String email){var s=new Student();s.setStudentEmail(email);s.setSchool("Testing");s.setSpotters(List.of(STAFF));return s;}
    void role(String name){account.setRoles(Set.of(new RoleModel(null,name)));}
    void guidance(){role("GUIDANCE");child.setGuidanceEmail(STAFF);}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder read(String path){return get(path).accept(MediaType.APPLICATION_JSON).with(user(STAFF).roles("ADMIN"));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder write(String path,String body){return put(path).accept(MediaType.APPLICATION_JSON).with(user(STAFF).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body);}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder create(String path,String body){return post(path).accept(MediaType.APPLICATION_JSON).with(user(STAFF).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body);}
    String note(){return "{\"event\":\"Notes\",\"content\":\"Synthetic note\",\"createdBy\":\"forged@example.test\"}";}
    String guidanceBody(String student){return "{\"guidance\":{\"studentEmail\":\""+student+"\",\"referralDescription\":[\"Synthetic request\"]}}";}
    String behavior(String student){return "{\"studentEmail\":\""+student+"\",\"behaviorCode\":\"PHONE_OUT\",\"consequenceCode\":\"WARNING\"}";}
    @Test void anonymousAllRoutesAreProtected() throws Exception {
        for(String path:List.of("/guidance/v1/referrals","/guidance/v1/referrals/","/guidance/v1/guidanceStatus/OPEN","/tracked-behaviors/v1/types","/tracked-behaviors/v1/student/"+CHILD+"/timeline","/tracked-behaviors/v1/student/"+CHILD+"/totals"))mvc.perform(get(path)).andExpect(status().isUnauthorized());
        for(String path:List.of("/guidance/v1/guidance/new","/tracked-behaviors/v1/save","/tracked-behaviors/v1/students/totals"))mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        for(String path:List.of("notes","followup","status","resources"))mvc.perform(put("/guidance/v1/guidance/"+path+"/case").contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/guidance/v1/guidance/delete/case")).andExpect(status().isUnauthorized());verifyNoInteractions(guidances,behaviors,email);
    }
    @Test void teacherReadsOnlyOwnGuidanceWithIndependentRelationship() throws Exception {
        mvc.perform(read("/guidance/v1/referrals")).andExpect(status().isAccepted()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
        referral.setTeacherEmail("other-staff@example.test");mvc.perform(read("/guidance/v1/referrals")).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
        referral.setTeacherEmail(STAFF);employee.setClasses(List.of());mvc.perform(read("/guidance/v1/referrals")).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
        mvc.perform(write("/guidance/v1/guidance/notes/case",note())).andExpect(status().isForbidden());
    }
    @Test void currentAssignedGuidanceNotHistoricalOwnerControlsCase() throws Exception {
        guidance();mvc.perform(read("/guidance/v1/guidanceStatus/OPEN")).andExpect(status().isAccepted()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(write("/guidance/v1/guidance/status/case","{\"status\":\"CLOSED\"}")).andExpect(status().isAccepted());
        child.setGuidanceEmail("new@example.test");referral.setGuidanceEmail(STAFF);
        mvc.perform(read("/guidance/v1/referrals")).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
        mvc.perform(write("/guidance/v1/guidance/status/case","{\"status\":\"OPEN\"}")).andExpect(status().isForbidden());
    }
    @Test void notesHaveTrustedActorAndTeachersCannotManageCounselorState() throws Exception {
        mvc.perform(write("/guidance/v1/guidance/notes/case",note())).andExpect(status().isAccepted()).andExpect(jsonPath("$.notesArray[0].createdBy").value(STAFF));
        for(String action:List.of("status","followup","resources"))mvc.perform(write("/guidance/v1/guidance/"+action+"/case",action.equals("followup")?"{\"followUpDate\":\"20261201\",\"status\":\"DORMANT\"}":"{\"status\":\"CLOSED\"}")).andExpect(status().isForbidden());
        mvc.perform(delete("/guidance/v1/guidance/delete/case").with(user(STAFF))).andExpect(status().isForbidden());verifyNoInteractions(email);
    }
    @Test void administratorSameSchoolOnlyAndHistoricalSchoolMustMatch() throws Exception {
        role("ADMIN");mvc.perform(read("/guidance/v1/referrals")).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
        referral.setSchool("Other");mvc.perform(read("/guidance/v1/referrals")).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
        mvc.perform(write("/guidance/v1/guidance/notes/case",note())).andExpect(status().isForbidden());
        mvc.perform(create("/tracked-behaviors/v1/save","["+behavior(OTHER)+"]")).andExpect(status().isOk());
        other.setSchool("Other");mvc.perform(create("/tracked-behaviors/v1/save","["+behavior(OTHER)+"]")).andExpect(status().isForbidden());
    }
    @Test void studentCanRequestOwnGuidanceButCannotReadStaffNotesOrChangeRecords() throws Exception {
        role("STUDENT");account.setUsername(CHILD);when(users.findByUsername(CHILD)).thenReturn(account);
        mvc.perform(post("/guidance/v1/guidance/new").with(user(CHILD)).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(guidanceBody(CHILD))).andExpect(status().isAccepted()).andExpect(jsonPath("$.guidance[0].teacherEmail").isEmpty());
        mvc.perform(post("/guidance/v1/guidance/new").with(user(CHILD)).contentType(MediaType.APPLICATION_JSON).content(guidanceBody(OTHER))).andExpect(status().isForbidden());
        mvc.perform(get("/guidance/v1/referrals").with(user(CHILD))).andExpect(status().isForbidden());
        mvc.perform(put("/guidance/v1/guidance/notes/case").with(user(CHILD)).contentType(MediaType.APPLICATION_JSON).content(note())).andExpect(status().isForbidden());
        mvc.perform(post("/tracked-behaviors/v1/save").with(user(CHILD)).contentType(MediaType.APPLICATION_JSON).content("["+behavior(CHILD)+"]")).andExpect(status().isForbidden());
        mvc.perform(get("/tracked-behaviors/v1/student/"+CHILD+"/timeline").with(user(CHILD))).andExpect(status().isOk());
        mvc.perform(get("/tracked-behaviors/v1/student/"+OTHER+"/timeline").with(user(CHILD))).andExpect(status().isForbidden());
    }
    @Test void guidanceCreationRejectsForgedActorSchoolAndStudent() throws Exception {
        for(String body:List.of(guidanceBody(OTHER),guidanceBody(CHILD).replace("\"studentEmail\":","\"teacherEmail\":\"forged@example.test\",\"studentEmail\":"),guidanceBody(CHILD).replace("\"studentEmail\":","\"school\":\"Other\",\"studentEmail\":")))
            mvc.perform(create("/guidance/v1/guidance/new",body)).andExpect(status().isForbidden());
        verify(guidances,never()).save(any());
    }
    @Test void creationSanitizesClientHistoryAndBindsPersistedActorAndSchool() throws Exception {
        String body=guidanceBody(CHILD).replace("\"studentEmail\":","\"guidanceId\":\"overwrite\",\"status\":\"CLOSED\",\"archived\":true,\"notesArray\":[{\"content\":\"forged history\"}],\"studentEmail\":");
        mvc.perform(create("/guidance/v1/guidance/new",body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.guidance[0].teacherEmail").value(STAFF)).andExpect(jsonPath("$.guidance[0].school").value("Testing")).andExpect(jsonPath("$.guidance[0].status").value("OPEN")).andExpect(jsonPath("$.guidance[0].archived").value(false)).andExpect(jsonPath("$.guidance[0].notesArray").isEmpty());
    }
    @Test void archivePreservesRecordAndHistoryInsteadOfDeleting() throws Exception {
        guidance();mvc.perform(delete("/guidance/v1/guidance/delete/case").with(user(STAFF))).andExpect(status().isAccepted());assertTrue(referral.isArchived());assertEquals("CLOSED",referral.getStatus());
        verify(guidances).save(referral);verify(guidances,never()).delete(any());verifyNoInteractions(mongo);
        mvc.perform(read("/guidance/v1/referrals")).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(write("/guidance/v1/guidance/notes/case",note())).andExpect(status().isForbidden());
    }
    @Test void invalidStatusesDatesAndMissingIdsCannotWrite() throws Exception {
        guidance();mvc.perform(write("/guidance/v1/guidance/status/case","{\"status\":\"ADMIN\"}")).andExpect(status().isBadRequest());
        mvc.perform(write("/guidance/v1/guidance/followup/case","{\"status\":\"DORMANT\"}")).andExpect(status().isBadRequest());
        mvc.perform(write("/guidance/v1/guidance/followup/case","{\"followUpDate\":\"20260231\",\"status\":\"DORMANT\"}")).andExpect(status().isBadRequest());
        mvc.perform(write("/guidance/v1/guidance/notes/forged-id",note())).andExpect(status().isForbidden());verify(guidances,never()).save(any());
    }
    @Test void disabledArchivedAndPendingActorsAreDenied() throws Exception {
        account.setEnabled(false);deniedActor();account.setEnabled(true);employee.setArchived(true);deniedActor();employee.setArchived(false);account.setPasswordSetupRequired(true);deniedActor();
    }
    void deniedActor() throws Exception {mvc.perform(read("/guidance/v1/referrals")).andExpect(status().isForbidden());mvc.perform(create("/tracked-behaviors/v1/save","["+behavior(CHILD)+"]")).andExpect(status().isForbidden());}
    @Test void trackedHistoryFiltersForgedLegacySchoolAndHasNoSpotterBypass() throws Exception {
        var own=new com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent();own.setSchool("Testing");own.setStudentEmail(CHILD);own.setBehaviorCode("PHONE_OUT");
        var foreign=new com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent();foreign.setSchool("Other");foreign.setStudentEmail(CHILD);
        when(behaviors.findByStudentEmailOrderByTimeCreatedDesc(CHILD)).thenReturn(List.of(own,foreign));
        mvc.perform(read("/tracked-behaviors/v1/student/"+CHILD+"/timeline")).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(read("/tracked-behaviors/v1/student/"+CHILD+"/totals")).andExpect(jsonPath("$.PHONE_OUT").value(1));
        employee.setClasses(List.of());mvc.perform(read("/tracked-behaviors/v1/student/"+CHILD+"/timeline")).andExpect(status().isForbidden());
    }
    @Test void trackedBatchPrevalidationRejectsForgedActorSchoolAndUnrelatedStudent() throws Exception {
        for(String body:List.of("["+behavior(CHILD)+","+behavior(OTHER)+"]","["+behavior(CHILD).replace("\"studentEmail\":","\"teacherEmail\":\"forged@example.test\",\"studentEmail\":")+"]","["+behavior(CHILD).replace("\"studentEmail\":","\"school\":\"Other\",\"studentEmail\":")+"]"))mvc.perform(create("/tracked-behaviors/v1/save",body)).andExpect(status().isForbidden());
        verify(behaviors,never()).saveAll(anyList());
    }
    @Test void trackedCreationUsesCanonicalServerFields() throws Exception {
        mvc.perform(create("/tracked-behaviors/v1/save","["+behavior(CHILD).replace("\"studentEmail\":","\"consequenceName\":\"Forged\",\"studentEmail\":")+"]")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].teacherEmail").value(STAFF)).andExpect(jsonPath("$[0].school").value("Testing")).andExpect(jsonPath("$[0].consequenceName").value("Warning"));
    }
    @Test void trackedTotalsBatchCannotTrustSchoolOrUnrelatedEmail() throws Exception {
        mvc.perform(create("/tracked-behaviors/v1/students/totals","{\"school\":\"Other\",\"studentEmails\":[\""+CHILD+"\"]}")).andExpect(status().isForbidden());
        mvc.perform(create("/tracked-behaviors/v1/students/totals","{\"school\":\"Testing\",\"studentEmails\":[\""+CHILD+"\",\""+OTHER+"\"]}")).andExpect(status().isForbidden());verifyNoInteractions(behaviors);
    }
    @Test void archivedStudentsDenyNewMutationsAndAdminRetainsHistoryRead() throws Exception {
        child.setArchived(true);mvc.perform(create("/guidance/v1/guidance/new",guidanceBody(CHILD))).andExpect(status().isForbidden());mvc.perform(create("/tracked-behaviors/v1/save","["+behavior(CHILD)+"]")).andExpect(status().isForbidden());
        role("ADMIN");mvc.perform(read("/guidance/v1/referrals")).andExpect(status().isAccepted()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(read("/tracked-behaviors/v1/student/"+CHILD+"/timeline")).andExpect(status().isOk());mvc.perform(write("/guidance/v1/guidance/notes/case",note())).andExpect(status().isForbidden());
    }
    @Test void counselorTrackingRequiresCurrentAssignmentAndAdministratorReadsStayScoped() throws Exception {
        guidance();mvc.perform(create("/tracked-behaviors/v1/save","["+behavior(CHILD)+"]")).andExpect(status().isOk());
        child.setGuidanceEmail("new@example.test");mvc.perform(create("/tracked-behaviors/v1/save","["+behavior(CHILD)+"]")).andExpect(status().isForbidden());
        role("ADMIN");child.setSchool("Other");mvc.perform(read("/tracked-behaviors/v1/student/"+CHILD+"/timeline")).andExpect(status().isForbidden());
    }
    @Test void resourcesUseAuthorizedGuidanceStateUnderBothGateConfigurations() throws Exception {
        guidance();String body="{\"resourceOptionList\":[{\"label\":\"Synthetic\",\"url\":\"https://example.test/resource\"}]}";
        mvc.perform(write("/guidance/v1/guidance/resources/case",body)).andExpect(status().isAccepted()).andExpect(jsonPath("$.guidance[0].guidanceId").value("case"));
        if(notifications.isEnabled()) {verify(notifications).prepare(eq(referral),eq("RESOURCES"),any(),anyString());verifyNoInteractions(email);}
        else verify(email).sendEmailGeneric(anyList(),eq(CHILD),anyString(),anyString(),any());
        child.setGuidanceEmail("new@example.test");mvc.perform(write("/guidance/v1/guidance/resources/case",body)).andExpect(status().isForbidden());
    }

    @Test void localGuidancePreflightAndAuthorizedRequestUseExistingCorsPolicy() throws Exception {
        mvc.perform(options("/guidance/v1/guidance/notes/case").header("Origin","http://localhost:3000").header("Access-Control-Request-Method","PUT").header("Access-Control-Request-Headers","content-type,authorization"))
            .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin","http://localhost:3000"));
        mvc.perform(write("/guidance/v1/guidance/notes/case",note()).header("Origin","http://localhost:3000")).andExpect(status().isAccepted());
    }

    @Test void malformedHistoricalStudentReferenceAndNullTotalsIdFailClosed() throws Exception {
        role("ADMIN");referral.setStudentEmail(null);mvc.perform(read("/guidance/v1/referrals")).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
        mvc.perform(create("/tracked-behaviors/v1/students/totals","{\"studentEmails\":[null]}")).andExpect(status().isBadRequest());verifyNoInteractions(behaviors);
    }

}
