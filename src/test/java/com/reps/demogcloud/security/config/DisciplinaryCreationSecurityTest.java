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

/** Production filter chain, controllers, creation services, utilities and persisted-identity policies.
 * Only persistence, unrelated services and external transports are mocked. */
@SpringJUnitWebConfig(DisciplinaryCreationSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false","reps.email.routing.phase1-enabled=false","reps.email.notifications.enabled=false"})
class DisciplinaryCreationSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackageClasses=SecurityConfig.class,useDefaultFilters=false,includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=SecurityConfig.class))
    @Import({PunishController.class,OfficeReferralController.class,PunishmentService.class,OfficeReferralService.class,
        com.reps.demogcloud.services.punishment.PunishmentCreationService.class,PunishmentUtils.class,CurrencyService.class,
        UserContextService.class,WorkflowAccessService.class,JwtFilterRequest.class,GlobalExceptionHandler.class})
    static class App { @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);} }
    @Autowired WebApplicationContext context; @Autowired org.springframework.core.env.Environment env;
    @Autowired OfficeReferralService office; @Autowired com.reps.demogcloud.services.punishment.PunishmentCreationService creation;
    @MockitoBean UserRepository users; @MockitoBean EmployeeRepository employees; @MockitoBean StudentRepository students;
    @MockitoBean SchoolRepository schools; @MockitoBean PunishRepository cases; @MockitoBean AssignmentTemplateRepository templates;
    @MockitoBean OfficeReferralRepository referrals; @MockitoBean InfractionRepository infractions;
    @MockitoBean org.springframework.data.mongodb.core.MongoTemplate mongo; @MockitoBean EmailService email;
    @MockitoBean RequiredNotificationService notifications; @MockitoBean EmailSenderService sender;
    @MockitoBean AssignmentService assignments; @MockitoBean GuidanceService guidance;
    @MockitoBean EmployeeService employeeService; @MockitoBean StudentUtils studentUtils; @MockitoBean OfficeReferralUtils officeUtils;
    @MockitoBean com.reps.demogcloud.services.punishment.PunishmentClosureService closure;
    @MockitoBean com.reps.demogcloud.services.punishment.PunishmentQueryService queries;
    @MockitoBean com.reps.demogcloud.services.punishment.PunishmentUpdateService updates;
    @MockitoBean CurrencyTransactions transactions;
    @MockitoBean GoogleOAuthTokenStore google; @MockitoBean CustomOAuth2UserService oidc; @MockitoBean OAuth2LoginSuccessHandler success;
    @MockitoBean OAuth2LoginFailureHandler failure; @MockitoBean CustomUserDetailsService details; @MockitoBean JwtUtils jwt;
    UserModel account; Employee employee; Student child,other; MockMvc mvc;
    static final String STAFF="staff@example.test",CHILD="child@example.test",OTHER="other@example.test";
    static final String SINGLE="/punish/v1/startPunish/form",BULK="/punish/v1/startPunish/formList",OFFICE="/officeReferral/v1/startPunish/adminReferral";
    static final List<String> TYPES=List.of("Dress Code","Behavioral Concern","Academic Concern","Failure to Complete Work","Teacher Guidance Referral","Student Guidance Referral");
    @BeforeEach void setup() throws Exception {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        account=new UserModel();account.setUsername(STAFF);account.setSchool("Testing");role("TEACHER");
        employee=new Employee();employee.setEmail(STAFF);employee.setSchool("Testing");var roster=new Employee.ClassRoster();roster.setClassRoster(List.of(CHILD));employee.setClasses(List.of(roster));
        child=student(CHILD);other=student(OTHER);
        when(users.findByUsername(STAFF)).thenAnswer(i->account);when(employees.findByEmailIgnoreCase(STAFF)).thenReturn(employee);
        when(students.findByStudentEmailIgnoreCase(CHILD)).thenReturn(child);when(students.findByStudentEmailIgnoreCase(OTHER)).thenReturn(other);
        var school=new com.reps.demogcloud.models.school.School();school.setSchoolName("Testing");school.setMaxPunishLevel(4);
        when(schools.findBySchoolNameIgnoreCase("Testing")).thenReturn(Optional.of(school));
        when(infractions.findByInfractionName(anyString())).thenAnswer(i->infraction(i.getArgument(0)));
        when(infractions.findByInfractionNameAndInfractionLevel(anyString(),anyString())).thenAnswer(i->infraction(i.getArgument(0)));
        when(cases.save(any())).thenAnswer(i->i.getArgument(0));when(referrals.save(any())).thenAnswer(i->i.getArgument(0));
        when(notifications.isEnabled()).thenReturn(Boolean.parseBoolean(env.getProperty("reps.email.notifications.enabled","false")));
        when(notifications.creationId(anyString(),anyString(),any())).thenAnswer(i->i.getArgument(0)+":"+i.getArgument(1));
        when(notifications.saveCreated(any())).thenAnswer(i->i.getArgument(0));
        when(notifications.response(any())).thenAnswer(i->{var r=new com.reps.demogcloud.models.punishment.PunishmentResponse();r.setPunishment(i.getArgument(0));return r;});
        when(email.sendEmailBasedOnType(any(),any(),any())).thenAnswer(i->{var r=new com.reps.demogcloud.models.punishment.PunishmentResponse();r.setPunishment(i.getArgument(1));return r;});
    }
    com.reps.demogcloud.models.infraction.Infraction infraction(String name){var i=new com.reps.demogcloud.models.infraction.Infraction();i.setInfractionName(name);i.setInfractionLevel("1");return i;}
    Student student(String email){var s=new Student();s.setStudentEmail(email);s.setStudentIdNumber(email);s.setSchool("Testing");s.setSpotters(List.of(STAFF));return s;}
    void role(String name){account.setRoles(Set.of(new RoleModel(null,name)));}
    String body(String student,String type){return "{\"studentEmail\":\""+student+"\",\"teacherEmail\":\"forged@example.test\",\"school\":\"Forged\",\"infractionName\":\""+type+"\",\"infractionDescription\":\"Synthetic\",\"referralDescription\":[\"Synthetic\"]}";}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,String body){return post(path).with(user(STAFF).roles("ADMIN")).accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(body);}
    void noWrites(){verify(cases,never()).save(any());verify(referrals,never()).save(any());verify(notifications,never()).saveCreated(any());verifyNoInteractions(mongo,email,guidance,transactions);}
    void deniedAll(String student) throws Exception {
        for(String type:TYPES){mvc.perform(request(SINGLE,body(student,type))).andExpect(status().isForbidden());mvc.perform(request(BULK,"["+body(student,type)+"]")).andExpect(status().isForbidden());}
        mvc.perform(request(OFFICE,"["+body(student,"Office")+"]")).andExpect(status().isForbidden());
        mvc.perform(request(BULK,"["+body(student,"Positive Behavior Shout Out!")+"]")).andExpect(status().isForbidden());noWrites();
    }
    @Test void anonymousCannotCreateCases() throws Exception {
        for(String path:List.of(SINGLE,BULK,OFFICE))mvc.perform(post(path).contentType(MediaType.APPLICATION_JSON).content(path.equals(SINGLE)?body(CHILD,"Dress Code"):"["+body(CHILD,"Dress Code")+"]")).andExpect(status().isUnauthorized());noWrites();
    }
    @Test void teacherNeedsIndependentRosterEvenWithSpotterAndForgedAdminAuthority() throws Exception {deniedAll(OTHER);}
    @Test void revokedRosterCannotRetainAccessThroughSpotters() throws Exception {employee.setClasses(List.of());deniedAll(CHILD);}
    @Test void guidanceRequiresCurrentAssignment() throws Exception {role("GUIDANCE");deniedAll(CHILD);}
    @Test void revokedGuidanceCannotUseTeacherRosterOrSpotterStatus() throws Exception {role("GUIDANCE");child.setGuidanceEmail("former@example.test");deniedAll(CHILD);}
    @Test void crossSchoolAdministratorDenied() throws Exception {role("ADMIN");child.setSchool("Other");deniedAll(CHILD);}
    @Test void archivedStudentDeniedEvenForAdministrator() throws Exception {role("ADMIN");child.setArchived(true);deniedAll(CHILD);}
    @Test void missingStaffDenied() throws Exception {when(employees.findByEmailIgnoreCase(STAFF)).thenReturn(null);deniedAll(CHILD);}
    @Test void missingStudentDenied() throws Exception {when(students.findByStudentEmailIgnoreCase(CHILD)).thenReturn(null);deniedAll(CHILD);}
    @Test void automaticLevelFourOfficeReferralRetainsAuthorizationAndTrustedActor() throws Exception {
        var prior=new com.reps.demogcloud.models.punishment.Punishment();prior.setClosedTimes(4);
        when(cases.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(CHILD,"Dress Code","CLOSED")).thenReturn(List.of(prior));
        mvc.perform(request(SINGLE,body(CHILD,"Dress Code"))).andExpect(status().isOk());
        if(notifications.isEnabled()) verify(notifications,atLeast(2)).saveCreated(any());else verify(referrals).save(argThat(r->STAFF.equals(r.getTeacherEmail()) && CHILD.equals(r.getStudentEmail())));
    }
    @Test void disabledActorDenied() throws Exception {account.setEnabled(false);deniedAll(CHILD);}
    @Test void archivedEmployeeDenied() throws Exception {employee.setArchived(true);deniedAll(CHILD);}
    @Test void pendingPasswordSetupDenied() throws Exception {account.setPasswordSetupRequired(true);deniedAll(CHILD);}
    @Test void missingStaffOrMismatchedPersistedSchoolDenied() throws Exception {employee.setSchool("Other");deniedAll(CHILD);}
    @Test void studentCannotCreateDisciplineEvenWithForgedStaffClaims() throws Exception {role("STUDENT");deniedAll(CHILD);}
    @Test void completeBatchAuthorizationPrecedesPhoneGuidanceCurrencyAndNotificationWrites() throws Exception {
        String allowed=body(CHILD,"Academic Concern").replace("\"infractionName\":","\"phoneLogDescription\":\"Synthetic phone\",\"guidanceDescription\":\"Synthetic guidance\",\"infractionName\":");
        mvc.perform(request(BULK,"["+allowed+","+body(OTHER,"Dress Code")+"]")).andExpect(status().isForbidden());
        mvc.perform(request(OFFICE,"["+body(CHILD,"Office")+","+body(OTHER,"Office")+"]")).andExpect(status().isForbidden());
        mvc.perform(request(BULK,"["+body(CHILD,"Positive Behavior Shout Out!")+","+body(OTHER,"Positive Behavior Shout Out!")+"]")).andExpect(status().isForbidden());noWrites();
    }
    @Test void referralFlagsCannotBypassTheSameStudentPolicy() throws Exception {
        String denied=body(OTHER,"Dress Code").replace("\"infractionName\":","\"adminReferral\":true,\"guidanceReferral\":true,\"infractionName\":");
        mvc.perform(request(SINGLE,denied)).andExpect(status().isForbidden());noWrites();
    }
    @Test void replayIsAuthorizedBeforeReadingExistingNotificationOrCurrencyReceipt() throws Exception {
        employee.setClasses(List.of());
        mvc.perform(request(SINGLE,body(CHILD,"Dress Code"))).andExpect(status().isForbidden());
        mvc.perform(request(OFFICE,"["+body(CHILD,"Office")+"]")).andExpect(status().isForbidden());
        mvc.perform(request(SINGLE,body(CHILD,"Positive Behavior Shout Out!"))).andExpect(status().isForbidden());
        verify(notifications,never()).existingCreation(anyString(),any());noWrites();
    }
    @Test void teacherCanCreateAllNonCurrencyTypesWithCanonicalActorAndSchool() throws Exception {allowedAll();}
    @Test void assignedGuidanceCanCreateWithoutTeacherRoster() throws Exception {role("GUIDANCE");employee.setClasses(List.of());child.setGuidanceEmail(STAFF);allowedAll();}
    @Test void sameSchoolAdministratorDoesNotNeedRoster() throws Exception {role("ADMIN");employee.setClasses(List.of());allowedAll();}
    void allowedAll() throws Exception {
        for(String type:TYPES) mvc.perform(request(SINGLE,body(CHILD,type))).andExpect(status().isOk()).andExpect(jsonPath("$.punishment.teacherEmail").value(STAFF)).andExpect(jsonPath("$.punishment.school").value("Testing"));
        mvc.perform(request(BULK,"["+body(CHILD,"Academic Concern")+"]")).andExpect(status().isOk()).andExpect(jsonPath("$[0].punishment.teacherEmail").value(STAFF));
        mvc.perform(request(OFFICE,"["+body(CHILD,"Office")+"]")).andExpect(status().isAccepted()).andExpect(jsonPath("$[0].teacherEmail").value(STAFF)).andExpect(jsonPath("$[0].school").value("Testing"));
        if(notifications.isEnabled()){verify(notifications,atLeastOnce()).saveCreated(any());verifyNoInteractions(email);}else {verify(cases,atLeastOnce()).save(any());verify(referrals).save(any());}
    }
    @Test void directServicesCannotBypassCreationAuthorization() {
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(new org.springframework.security.authentication.TestingAuthenticationToken(STAFF,"unused","ROLE_TEACHER"));
        try {
            var form=new com.reps.demogcloud.models.punishment.PunishmentFormRequest();form.setStudentEmail(OTHER);form.setInfractionDescription("Synthetic");
            var referral=new com.reps.demogcloud.models.officeReferral.OfficeReferralRequest();referral.setStudentEmail(OTHER);
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->creation.createNewPunishForm(form));
            assertThrows(org.springframework.security.access.AccessDeniedException.class,()->office.createNewOfficeReferral(referral));noWrites();
        }finally{org.springframework.security.core.context.SecurityContextHolder.clearContext();}
    }
}
