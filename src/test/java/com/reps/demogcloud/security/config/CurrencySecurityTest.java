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

/** Production security chain and currency/profile policy. Only persistence/transaction execution and external providers mocked. */
@SpringJUnitWebConfig(CurrencySecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false",
    "reps.email.routing.phase1-enabled=false","reps.email.notifications.enabled=false"})
class CurrencySecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackageClasses=SecurityConfig.class,useDefaultFilters=false,
        includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=SecurityConfig.class))
    @Import({EmployeeController.class,StudentController.class,GlobalExceptionHandler.class,JwtFilterRequest.class,
        UserAdministrationService.class,UserContextService.class,WorkflowAccessService.class,
        EmployeeService.class,EmployeeMutationService.class,StudentService.class,StudentAdminService.class,CurrencyService.class})
    static class App { @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);} }
    @Autowired WebApplicationContext context;
    @MockitoBean CurrencyTransactions transactions;
    @MockitoBean org.springframework.data.mongodb.core.MongoTemplate mongo;
    @MockitoBean RequiredNotificationService notifications;
    @MockitoBean EmployeeCreationService employeeCreation;
    @MockitoBean EmployeeClassService employeeClasses;
    @MockitoBean EmployeeQueryService employeeQuery;
    @MockitoBean StudentQueryService studentQuery;
    @MockitoBean StudentMutationService studentMutation;
    @MockitoBean StudentPointService points;
    @MockitoBean StudentSpotterService spotters;
    @MockitoBean StudentUtils utils;
    @MockitoBean UserRepository users;
    @MockitoBean EmployeeRepository employees;
    @MockitoBean StudentRepository students;
    @MockitoBean SchoolRepository schools;
    @MockitoBean PunishRepository cases;
    @MockitoBean AssignmentTemplateRepository templates;
    @MockitoBean com.reps.demogcloud.security.services.AccountAccessService accountAccess;
    @MockitoBean GoogleOAuthTokenStore google;
    @MockitoBean CustomOAuth2UserService oidc;
    @MockitoBean OAuth2LoginSuccessHandler success;
    @MockitoBean OAuth2LoginFailureHandler failure;
    @MockitoBean CustomUserDetailsService details;
    @MockitoBean JwtUtils jwt;
    UserModel account;Employee employee;Student child;MockMvc mvc;
    static final String STAFF="teacher@example.test",CHILD="child@example.test";
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        account=new UserModel();account.setUsername(STAFF);account.setSchool("Testing");account.setRoles(Set.of(new RoleModel(null,"TEACHER")));
        employee=new Employee();employee.setEmployeeId("staff");employee.setEmail(STAFF);employee.setSchool("Testing");employee.setCurrency(20);var roster=new Employee.ClassRoster();roster.setClassRoster(List.of(CHILD));employee.setClasses(List.of(roster));
        child=new Student();child.setStudentIdNumber("child");child.setStudentEmail(CHILD);child.setSchool("Testing");child.setCurrency(10);child.setSpotters(List.of(STAFF));
        when(users.findByUsername(STAFF)).thenAnswer(i->account);when(employees.findByEmailIgnoreCase(STAFF)).thenAnswer(i->employee);
        when(students.findByStudentEmailIgnoreCase(CHILD)).thenAnswer(i->child);when(students.findById("child")).thenAnswer(i->Optional.of(child));
        doAnswer(i->{var receipt=new CurrencyOperation();receipt.setSchool("Testing");i.<Consumer<CurrencyOperation>>getArgument(2).accept(receipt);return receipt;}).when(transactions).execute(anyString(),any(),any());
    }
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request(String path,String body){return put(path).with(user(STAFF).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body);}
    String redemption(int amount){return "[{\"studentEmail\":\""+CHILD+"\",\"currencyTransferred\":"+amount+"}]";}
    @Test void anonymousCannotReachCurrencyOrBulkProfileWrites() throws Exception {
        for(String path:List.of("/employees/v1/currency/spend","/employees/v1/Testing","/student/v1/updateStudents")) {
            int status=mvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("[]")).andReturn().getResponse().getStatus();assertTrue(status==401||status==403||status==302);
        }
        verifyNoInteractions(transactions,students,employees);
    }
    @Test void studentAndDisabledStaffCannotSpendDespiteForgedAuthorities() throws Exception {
        account.setRoles(Set.of(new RoleModel(null,"STUDENT")));
        mvc.perform(request("/employees/v1/currency/spend",redemption(1))).andExpect(status().isForbidden());
        account.setRoles(Set.of(new RoleModel(null,"TEACHER")));account.setEnabled(false);
        mvc.perform(request("/employees/v1/currency/spend",redemption(1))).andExpect(status().isForbidden());
        verifyNoInteractions(transactions);
    }
    @Test void crossSchoolUnrelatedAndArchivedStudentsAreDenied() throws Exception {
        child.setSchool("Other");mvc.perform(request("/employees/v1/currency/spend",redemption(1))).andExpect(status().isForbidden());
        child.setSchool("Testing");child.setSpotters(List.of());employee.setClasses(List.of());mvc.perform(request("/employees/v1/currency/spend",redemption(1))).andExpect(status().isForbidden());
        child.setSpotters(List.of(STAFF));child.setArchived(true);mvc.perform(request("/employees/v1/currency/spend",redemption(1))).andExpect(status().isForbidden());
        verify(mongo,never()).updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),any(org.springframework.data.mongodb.core.query.Update.class),any(Class.class));
    }
    @Test void redemptionRejectsNonpositiveAndFractionalAmounts() throws Exception {
        for(int amount:List.of(0,-2))mvc.perform(request("/employees/v1/currency/spend",redemption(amount))).andExpect(status().isBadRequest());
        mvc.perform(request("/employees/v1/currency/spend",redemption(1).replace(":1}",":1.5}"))).andExpect(status().isBadRequest());
        verify(mongo,never()).updateFirst(any(org.springframework.data.mongodb.core.query.Query.class),any(org.springframework.data.mongodb.core.query.Update.class),any(Class.class));
    }
    @Test void onlyPersistedSameSchoolAdminMayResetBudgets() throws Exception {
        mvc.perform(request("/employees/v1/Testing","{}")).andExpect(status().isForbidden());
        account.setRoles(Set.of(new RoleModel(null,"ADMIN")));
        mvc.perform(request("/employees/v1/Other","{}")).andExpect(status().isForbidden());verifyNoInteractions(transactions);
    }
    @Test void bulkProfilesCannotChangeWalletOrMoveSchool() throws Exception {
        for(String field:List.of("\"currency\":999","\"school\":\"Other\"","\"studentEmail\":\"other@example.test\""))
            mvc.perform(request("/student/v1/updateStudents","[{\"studentIdNumber\":\"child\","+field+"}]")).andExpect(status().isBadRequest());
        verify(students,never()).save(any());
    }
    @Test void authorizedProfileChangeDoesNotWriteCurrency() throws Exception {
        when(mongo.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),any(org.springframework.data.mongodb.core.query.Update.class),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(Student.class))).thenReturn(child);
        mvc.perform(request("/student/v1/updateStudents","[{\"studentIdNumber\":\"child\",\"firstName\":\"Updated\",\"currency\":10}]")).andExpect(status().isAccepted());
        var update=org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),update.capture(),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(Student.class));
        assertFalse(update.getValue().getUpdateObject().toJson().contains("currency"));verify(students,never()).save(any());
    }
}
