package com.reps.demogcloud.security.config;
import com.reps.demogcloud.security.controllers.AccountAccessController;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.email.EmailSenderService;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.employee.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.data.mongodb.core.*;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.http.MediaType;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringJUnitWebConfig(AccountAccessSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","reps.accounts.public-base-url=https://repsdev.vercel.app"})
class AccountAccessSecurityTest {
 @Configuration @EnableWebMvc @EnableWebSecurity
 @Import({SecurityConfig.class,JwtFilterRequest.class,AccountAccessController.class,AccountAccessService.class,WorkflowAccessService.class,UserContextService.class,GlobalExceptionHandler.class})
 static class App {@Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}}
 @Autowired WebApplicationContext context;
 @MockitoBean CustomOAuth2UserService oidc;
 @MockitoBean OAuth2LoginSuccessHandler success;
 @MockitoBean OAuth2LoginFailureHandler failure;
 @MockitoBean CustomUserDetailsService details;
 @MockitoBean JwtUtils jwt;
 @MockitoBean UserRepository users;
 @MockitoBean EmployeeRepository employees;
 @MockitoBean StudentRepository students;
 @MockitoBean SchoolRepository schools;
 @MockitoBean PunishRepository cases;
 @MockitoBean AssignmentTemplateRepository templates;
 @MockitoBean EmailSenderService sender;
 @MockitoBean PasswordEncoder encoder;
 @MockitoBean MongoTemplate mongo;
 MockMvc mvc;Map<String,UserModel> accounts;Map<String,Employee> staff;
 @BeforeEach void setup(){mvc=MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();accounts=new HashMap<>();staff=new HashMap<>();for(String role:List.of("ADMIN","TEACHER","GUIDANCE","STUDENT")){String email=role.toLowerCase()+"@example.test";var u=new UserModel();u.setId(email);u.setUsername(email);u.setSchool("Testing");u.setRoles(Set.of(new RoleModel(null,role)));accounts.put(email,u);if(!role.equals("STUDENT")){var e=new Employee();e.setEmail(email);e.setSchool("Testing");var c=new Employee.ClassRoster();c.setClassRoster(List.of("student@example.test"));e.setClasses(List.of(c));staff.put(email,e);}}var child=new Student();child.setStudentEmail("student@example.test");child.setSchool("Testing");when(users.findByUsername(anyString())).thenAnswer(i->accounts.get(i.getArgument(0)));when(users.findById(anyString())).thenAnswer(i->Optional.ofNullable(accounts.get(i.getArgument(0))));when(employees.findByEmailIgnoreCase(anyString())).thenAnswer(i->staff.get(i.getArgument(0)));when(students.findByStudentEmailIgnoreCase("student@example.test")).thenReturn(child);when(mongo.findAndModify(any(),any(),any(FindAndModifyOptions.class),eq(UserModel.class))).thenAnswer(i->{var u=accounts.get("student@example.test");u.setAccessTicket((UserModel.AccessTicket)((org.bson.Document)((Update)i.getArgument(1)).getUpdateObject().get("$set")).get("accessTicket"));return u;});}
 @ParameterizedTest @ValueSource(strings={"ADMIN","TEACHER"}) void relatedStaffCanIssueSingleUseHandoff(String role)throws Exception{mvc.perform(post("/accounts/v1/students/student@example.test/setup").with(user(role.toLowerCase()+"@example.test").roles(role)).contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true,\"privateEmailAllowed\":false}")) .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.maximumAttempts").value(5)).andExpect(jsonPath("$.credential").isString());verifyNoInteractions(sender);}
 @ParameterizedTest @ValueSource(strings={"STUDENT","GUIDANCE"}) void unauthorizedRolesCannotIssue(String role)throws Exception{mvc.perform(post("/accounts/v1/students/student@example.test/setup").with(user(role.toLowerCase()+"@example.test").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}")) .andExpect(status().isForbidden());verifyNoInteractions(mongo);}
 @Test void unrelatedTeacherCannotIssue()throws Exception{staff.get("teacher@example.test").setClasses(List.of());mvc.perform(post("/accounts/v1/students/student@example.test/setup").with(user("teacher@example.test").roles("TEACHER")).contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}")) .andExpect(status().isForbidden());}
 @Test void crossSchoolCannotIssue()throws Exception{accounts.get("admin@example.test").setSchool("Other");staff.get("admin@example.test").setSchool("Other");mvc.perform(post("/accounts/v1/students/student@example.test/setup").with(user("admin@example.test").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}")) .andExpect(status().isForbidden());}
 @Test void mismatchedStudentAccountSchoolCannotIssue()throws Exception{accounts.get("student@example.test").setSchool("Other");mvc.perform(post("/accounts/v1/students/student@example.test/setup").with(user("admin@example.test").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}")) .andExpect(status().isForbidden());}
 @Test void identityConfirmationRequired()throws Exception{mvc.perform(post("/accounts/v1/students/student@example.test/setup").with(user("admin@example.test").roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}")) .andExpect(status().isForbidden());verifyNoInteractions(mongo);}
 @Test void anonymousCannotIssue()throws Exception{mvc.perform(post("/accounts/v1/students/student@example.test/setup").contentType(MediaType.APPLICATION_JSON).content("{\"identityVerified\":true}")) .andExpect(status().isUnauthorized());verifyNoInteractions(mongo);}
 @Test void setupCompletionPublicButInvalidCredentialGetsNoSession()throws Exception{mvc.perform(post("/account-access/complete").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"student@example.test\",\"credential\":\"bad\",\"newPassword\":\"a-new-password-123\"}")) .andExpect(status().isBadRequest());verifyNoInteractions(jwt);}
 @Test void teacherCannotInviteStaff()throws Exception{mvc.perform(post("/accounts/v1/admin@example.test/invitation").with(user("teacher@example.test").roles("ADMIN"))).andExpect(status().isForbidden());}
}
