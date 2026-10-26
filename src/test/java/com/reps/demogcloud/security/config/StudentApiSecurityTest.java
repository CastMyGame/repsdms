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

/** Real production security filter chain and student services; persistence and providers are mocked. */
@SpringJUnitWebConfig(StudentApiSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false",
    "reps.email.routing.phase1-enabled=false","reps.email.notifications.enabled=false"})
class StudentApiSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackageClasses=SecurityConfig.class,useDefaultFilters=false,
        includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=SecurityConfig.class))
    @Import({StudentController.class,GlobalExceptionHandler.class,JwtFilterRequest.class,
        UserAdministrationService.class,UserContextService.class,WorkflowAccessService.class,
        StudentService.class,StudentAdminService.class,StudentMutationService.class,StudentQueryService.class,
        StudentSpotterService.class,StudentPointService.class})
    static class App { @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);} }
    @Autowired WebApplicationContext context;
    @MockitoBean org.springframework.data.mongodb.core.MongoTemplate mongo;
    @MockitoBean StudentUtils utils;
    @MockitoBean SchoolUtils schoolUtils;
    @MockitoBean UserRepository users;
    @MockitoBean EmployeeRepository employees;
    @MockitoBean StudentRepository students;
    @MockitoBean SchoolRepository schools;
    @MockitoBean PunishRepository cases;
    @MockitoBean AssignmentTemplateRepository templates;
    @MockitoBean AccountAccessService accountAccess;
    @MockitoBean AuthService auth;
    @MockitoBean GoogleOAuthTokenStore google;
    @MockitoBean CustomOAuth2UserService oidc;
    @MockitoBean OAuth2LoginSuccessHandler success;
    @MockitoBean OAuth2LoginFailureHandler failure;
    @MockitoBean CustomUserDetailsService details;
    @MockitoBean JwtUtils jwt;
    UserModel account; Employee employee; Student child,other; MockMvc mvc;
    static final String STAFF="teacher@example.test",CHILD="child@example.test",OTHER="other@example.test";
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        account=new UserModel();account.setId("staff-id");account.setUsername(STAFF);account.setSchool("Testing");role("TEACHER");
        employee=new Employee();employee.setEmployeeId("staff");employee.setEmail(STAFF);employee.setSchool("Testing");
        var roster=new Employee.ClassRoster();roster.setClassRoster(List.of(CHILD));employee.setClasses(List.of(roster));
        child=child("child",CHILD); other=child("other",OTHER);
        when(users.findByUsername(STAFF)).thenAnswer(i->account);when(employees.findByEmailIgnoreCase(STAFF)).thenAnswer(i->employee);
        when(students.findByStudentEmailIgnoreCase(CHILD)).thenAnswer(i->child);
        when(students.findByStudentEmailIgnoreCase(OTHER)).thenAnswer(i->other);
        when(students.findByStudentIdNumber("child")).thenAnswer(i->child);
        when(students.findByStudentIdNumber("other")).thenAnswer(i->other);
        when(students.findById("child")).thenAnswer(i->Optional.of(child));
        when(schoolUtils.fetchSchoolName()).thenReturn("Testing");
        when(students.findByArchivedAndSchool(false,"Testing")).thenAnswer(i->List.of(child,other));
        when(mongo.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),any(org.springframework.data.mongodb.core.query.Update.class),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(Student.class))).thenAnswer(i->child);
    }
    Student child(String id,String email) { var s=new Student();s.setStudentIdNumber(id);s.setStudentEmail(email);s.setSchool("Testing");s.setFirstName("Synthetic");s.setLastName("Student");s.setCurrency(10);s.setPoints(9);s.setSpotters(List.of(STAFF));return s; }
    void role(String name){account.setRoles(Set.of(new RoleModel(null,name)));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder read(String path){return get(path).accept(MediaType.APPLICATION_JSON).with(user(STAFF).roles("ADMIN"));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder write(String path,String body){return put(path).accept(MediaType.APPLICATION_JSON).with(user(STAFF).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body);}
    String spotter(String student,String staff){return "{\"studentEmail\":[\""+student+"\"],\"spotters\":[\""+staff+"\"]}";}
    @Test void anonymousEndpointsAreProtected() throws Exception {
        for(String path:List.of("/student/v1/email/"+CHILD,"/student/v1/studentid/child","/student/v1/allStudents","/student/v1/allStudents/","/student/v1/archived","/student/v1/lastname/Student","/student/v1/parentEmail/test@example.test","/student/v1/detentionList/Testing","/student/v1/issList/Testing","/student/v1/findBySpotter/"+STAFF,"/student/v1/enrollment-directory"))
            mvc.perform(get(path)).andExpect(status().isUnauthorized());
        verify(students,never()).save(any());
    }
    @Test void relatedTeacherCanReadButSpotterAloneAndForgedAuthoritiesCannot() throws Exception {
        mvc.perform(read("/student/v1/email/"+CHILD)).andExpect(status().isAccepted());
        mvc.perform(read("/student/v1/email/"+OTHER)).andExpect(status().isForbidden());
        employee.setClasses(List.of());
        mvc.perform(read("/student/v1/email/"+CHILD)).andExpect(status().isForbidden());
        role("STUDENT");mvc.perform(read("/student/v1/email/"+CHILD)).andExpect(status().isForbidden());
    }
    @Test void studentSelfOnlyAndPersistedDisabledOrArchivedDenied() throws Exception {
        role("STUDENT"); account.setUsername(CHILD);when(users.findByUsername(CHILD)).thenReturn(account);
        mvc.perform(get("/student/v1/email/"+CHILD).with(user(CHILD))).andExpect(status().isAccepted());
        mvc.perform(get("/student/v1/email/"+OTHER).with(user(CHILD))).andExpect(status().isForbidden());
        child.setArchived(true);mvc.perform(get("/student/v1/email/"+CHILD).with(user(CHILD))).andExpect(status().isForbidden());
        child.setArchived(false);account.setEnabled(false);mvc.perform(get("/student/v1/email/"+CHILD).with(user(CHILD))).andExpect(status().isForbidden());
    }
    @Test void adminAndAssignedGuidanceHaveSameSchoolAccessOnly() throws Exception {
        role("ADMIN");mvc.perform(read("/student/v1/email/"+OTHER)).andExpect(status().isAccepted());
        other.setSchool("Other");mvc.perform(read("/student/v1/email/"+OTHER)).andExpect(status().isForbidden());
        role("GUIDANCE");child.setGuidanceEmail(STAFF);mvc.perform(read("/student/v1/email/"+CHILD)).andExpect(status().isAccepted());
        child.setGuidanceEmail("revoked@example.test");mvc.perform(read("/student/v1/email/"+CHILD)).andExpect(status().isForbidden());
    }
    @Test void listsReportsAndDirectoryHaveDistinctScopes() throws Exception {
        mvc.perform(read("/student/v1/allStudents")).andExpect(status().isAccepted()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
        mvc.perform(read("/student/v1/allStudents/")).andExpect(status().isAccepted()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
        other.setParentEmail("private@example.test");other.setAddress("private address");
        mvc.perform(read("/student/v1/enrollment-directory")).andExpect(status().isOk()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(2)))
            .andExpect(jsonPath("$[1].parentEmail").doesNotExist()).andExpect(jsonPath("$[1].currency").doesNotExist()).andExpect(jsonPath("$[1].spotters").doesNotExist());
        for(String prefix:List.of("detentionList","issList"))mvc.perform(read("/student/v1/"+prefix+"/Other")).andExpect(status().isForbidden());
        when(students.findByParentEmail("parent@example.test")).thenReturn(List.of(child,other));
        mvc.perform(read("/student/v1/parentEmail/parent@example.test")).andExpect(status().isAccepted()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
    }
    @Test void validSelfSpottingIsAtomicAndForgedStaffOrRevokedAccessDenied() throws Exception {
        mvc.perform(write("/student/v1/addAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isAccepted());
        var update=org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),update.capture(),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(Student.class));
        assertTrue(update.getValue().getUpdateObject().containsKey("$addToSet"));
        mvc.perform(write("/student/v1/addAsSpotter",spotter(CHILD,"forged@example.test"))).andExpect(status().isForbidden());
        mvc.perform(write("/student/v1/removeAsSpotter",spotter(CHILD,"forged@example.test"))).andExpect(status().isForbidden());
        employee.setClasses(List.of());
        mvc.perform(write("/student/v1/addAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isForbidden());
        mvc.perform(write("/student/v1/removeAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isForbidden());
        mvc.perform(read("/student/v1/findBySpotter/"+STAFF)).andExpect(status().isAccepted()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(0)));
    }
    @Test void spotterCrossSchoolAndForgedStudentIdAreDenied() throws Exception {
        child.setSchool("Other");mvc.perform(write("/student/v1/addAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isForbidden());
        mvc.perform(write("/student/v1/remove-spotter/"+STAFF,"{\"studentIdNumber\":\"other\",\"school\":\"Testing\",\"studentEmail\":\""+CHILD+"\"}")).andExpect(status().isForbidden());
    }
    @Test void notesAndTimeRequireRelationshipAndUseTargetedUpdates() throws Exception {
        mvc.perform(write("/student/v1/notes/child","{\"event\":\"Note\",\"content\":\"Synthetic note\",\"createdBy\":\"forged\"}")).andExpect(status().isAccepted());
        var update=org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),update.capture(),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(Student.class));
        assertEquals(STAFF,((com.reps.demogcloud.models.punishment.ThreadEvent)((org.bson.Document)update.getValue().getUpdateObject().get("$push")).get("notesArray")).getCreatedBy());
        mvc.perform(post("/student/v1/"+CHILD+"/add-time?hours=0&minutes=-1").with(user(STAFF))).andExpect(status().isBadRequest());
        employee.setClasses(List.of());mvc.perform(write("/student/v1/notes/child","{\"event\":\"Note\",\"content\":\"Synthetic\"}")).andExpect(status().isForbidden());
        verify(students,never()).save(any());
    }
    @Test void bulkCannotForgeSpottersPointsSchoolOrRevision() throws Exception {
        for(String field:List.of("\"spotters\":[\"attacker@example.test\"]","\"points\":1000","\"school\":\"Other\""))
            mvc.perform(write("/student/v1/updateStudents","[{\"studentIdNumber\":\"child\","+field+"}]")).andExpect(status().isBadRequest());
        child.setProfileRevision(1);
        mvc.perform(write("/student/v1/updateStudents","[{\"studentIdNumber\":\"child\",\"firstName\":\"New\"}]")).andExpect(status().isConflict());
    }
    @Test void destructiveAndGlobalMaintenanceAreRetiredAndArchiveIsAdminOnly() throws Exception {
        mvc.perform(delete("/student/v1/delete").with(user(STAFF)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isGone());
        mvc.perform(write("/student/v1/assignSchool","{}")).andExpect(status().isGone());
        mvc.perform(write("/student/v1/archived/child","{}")).andExpect(status().isForbidden());
        role("ADMIN"); child.setSchool("Other");mvc.perform(write("/student/v1/archived/child","{}")).andExpect(status().isForbidden());
        verify(students,never()).delete(any(Student.class));
    }
    @Test void legacyPointsAreAlwaysRetiredWithoutPersistence() throws Exception {
        for(int amount:List.of(-1,0,5)) {
            mvc.perform(post("/student/v1/points/add?studentEmail="+CHILD+"&points="+amount).with(user(STAFF))).andExpect(status().isGone());
            mvc.perform(post("/student/v1/points/delete?studentEmail="+CHILD+"&points="+amount).with(user(STAFF))).andExpect(status().isGone());
        }
        String body="{\"givingStudentEmail\":\""+CHILD+"\",\"receivingStudentEmail\":\""+OTHER+"\",\"pointsTransferred\":5}";
        for(int amount:List.of(-1,0,5,5))mvc.perform(post("/student/v1/points/transfer").with(user(STAFF)).contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"pointsTransferred\":5","\"pointsTransferred\":"+amount))).andExpect(status().isGone());
        mvc.perform(post("/student/v1/points/transfer").contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        verifyNoInteractions(students,mongo);
    }
    @Test void adminArchiveDisablesAccountAndRevokesTicketsBeforeTargetedArchive() throws Exception {
        role("ADMIN");var target=new UserModel();target.setId("student-account");target.setUsername(CHILD);target.setSchool("Testing");target.setRoles(Set.of(new RoleModel(null,"STUDENT")));
        when(users.findByUsernameIgnoreCase(CHILD)).thenReturn(target);when(users.findById(target.getId())).thenReturn(Optional.of(target));
        mvc.perform(write("/student/v1/archived/child","{}")).andExpect(status().isAccepted());
        var order=inOrder(users,accountAccess,mongo);order.verify(users).updateEnabledInSchool(target.getId(),"Testing",false);
        order.verify(accountAccess).revoke(target.getId());
        order.verify(mongo).findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),any(org.springframework.data.mongodb.core.query.Update.class),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(Student.class));
        verify(students,never()).delete(any(Student.class));verify(students,never()).save(any());
    }
    @Test void adminMayRemoveOtherStaffSubscriptionButTeacherCannot() throws Exception {
        role("ADMIN");mvc.perform(write("/student/v1/removeAsSpotter",spotter(CHILD,"other-staff@example.test"))).andExpect(status().isAccepted());
        role("TEACHER");mvc.perform(write("/student/v1/removeAsSpotter",spotter(CHILD,"other-staff@example.test"))).andExpect(status().isForbidden());
    }
    @Test void disabledArchivedAndPendingStaffCannotSelfSpot() throws Exception {
        account.setEnabled(false);mvc.perform(write("/student/v1/addAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isForbidden());
        account.setEnabled(true);employee.setArchived(true);mvc.perform(write("/student/v1/addAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isForbidden());
        employee.setArchived(false);account.setPasswordSetupRequired(true);mvc.perform(write("/student/v1/addAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isForbidden());
    }
    @Test void staleSpotterCannotAuthorizeCommunicationsAfterRosterRevocation() {
        var security=org.springframework.security.core.context.SecurityContextHolder.createEmptyContext();
        security.setAuthentication(org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(STAFF,"unused",List.of()));
        org.springframework.security.core.context.SecurityContextHolder.setContext(security);
        try {
            var policy=context.getBean(UserContextService.class);assertTrue(policy.hasIndependentStudentAccess(STAFF,child));
            employee.setClasses(List.of());assertFalse(policy.hasIndependentStudentAccess(STAFF,child));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->policy.requireCaseCommunicationAccess("original@example.test",child));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->policy.requireRecordedCaseCommunicationAccess(STAFF,"original@example.test","owner@example.test",child));
        } finally { org.springframework.security.core.context.SecurityContextHolder.clearContext(); }
    }

    @Test void studentSelfProjectionExcludesStaffNotesAndSpotterList() throws Exception {
        role("STUDENT");account.setUsername(CHILD);when(users.findByUsername(CHILD)).thenReturn(account);
        child.setNotesArray(List.of(new com.reps.demogcloud.models.punishment.ThreadEvent()));
        mvc.perform(get("/student/v1/email/"+CHILD).accept(MediaType.APPLICATION_JSON).with(user(CHILD))).andExpect(status().isAccepted())
                .andExpect(jsonPath("$.notesArray").isEmpty()).andExpect(jsonPath("$.spotters").isEmpty());
        assertNotNull(child.getNotesArray());assertNotNull(child.getSpotters());
    }

    @Test void foreignIdOrParentRecordCannotBorrowAuthorizationFromDuplicateEmail() throws Exception {
        var foreign=child("foreign",CHILD);foreign.setSchool("Other");
        when(students.findByStudentIdNumber("foreign")).thenReturn(foreign);when(students.findById("foreign")).thenReturn(Optional.of(foreign));
        mvc.perform(read("/student/v1/studentid/foreign")).andExpect(status().isForbidden());
        mvc.perform(write("/student/v1/notes/foreign","{\"event\":\"Note\",\"content\":\"Synthetic\"}")).andExpect(status().isForbidden());
        mvc.perform(write("/student/v1/updateStudents","[{\"studentIdNumber\":\"foreign\",\"firstName\":\"Forged\"}]")).andExpect(status().isForbidden());
        when(students.findByParentEmail("parent@example.test")).thenReturn(List.of(child,foreign));
        mvc.perform(read("/student/v1/parentEmail/parent@example.test")).andExpect(status().isAccepted()).andExpect(jsonPath("$",org.hamcrest.Matchers.hasSize(1)));
        verifyNoInteractions(mongo);
    }

    @Test void assignedGuidanceMaySelfSubscribeButRevokedAssignmentCannot() throws Exception {
        role("GUIDANCE");child.setGuidanceEmail(STAFF);
        mvc.perform(write("/student/v1/addAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isAccepted());
        mvc.perform(write("/student/v1/removeAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isAccepted());
        child.setGuidanceEmail("new-guidance@example.test");
        mvc.perform(write("/student/v1/addAsSpotter",spotter(CHILD,STAFF))).andExpect(status().isForbidden());
        mvc.perform(read("/student/v1/email/"+CHILD)).andExpect(status().isForbidden());
    }
    @Test void concurrentLegacyPointsRequestsCannotReachPersistence() {
        java.util.stream.IntStream.range(0,20).parallel().forEach(i -> {
            try { mvc.perform(post("/student/v1/points/add?studentEmail="+CHILD+"&points=5").with(user(STAFF))).andExpect(status().isGone()); }
            catch(Exception ex) { throw new AssertionError(ex); }
        });
        verifyNoInteractions(mongo,students);
    }

}
