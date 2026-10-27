package com.reps.demogcloud.security.config;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.security.controllers.AuthControllers;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.*;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.email.EmailRoutingService;
import com.reps.demogcloud.services.tenancy.CanonicalAccountContext;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.authentication.*;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real JWT validation and production filter chain; only this test controller opts into new context. */
@SpringJUnitWebConfig(CanonicalAccountContextSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false"})
class CanonicalAccountContextSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @Import({SecurityConfig.class,JwtFilterRequest.class,CustomUserDetailsService.class,CanonicalAccountContext.class,
            AuthControllers.class,GlobalExceptionHandler.class,Probe.class})
    static class App {
        @Bean JwtRevocationStore revocations(){return JwtTestSupport.store();}
        @Bean JwtUtils jwt(JwtRevocationStore store){return new JwtUtils(JwtTestSupport.environment(),store);}
        @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);}
    }
    @RestController static class Probe {
        private final CanonicalAccountContext context;
        Probe(CanonicalAccountContext context){this.context=context;}
        @GetMapping("/__test__/a7/context") public CanonicalAccountContext.Actor actor(){return context.requireCurrent();}
        @GetMapping("/__test__/a7/legacy") public String legacy(){return "unchanged";}
    }
    @Autowired WebApplicationContext application;
    @Autowired JwtUtils jwt;
    @Autowired CustomUserDetailsService details;
    @Autowired AuthenticationProvider provider;
    @MockitoBean CustomOAuth2UserService oidc;
    @MockitoBean OAuth2LoginSuccessHandler oauthSuccess;
    @MockitoBean OAuth2LoginFailureHandler oauthFailure;
    @MockitoBean UserRepository users;
    @MockitoBean EmployeeRepository employees;
    @MockitoBean StudentRepository students;
    @MockitoBean SchoolRepository schools;
    @MockitoBean UserAccountService accounts;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean AccountAccessService setup;
    @MockitoBean PasswordResetTokenRepository resets;
    @MockitoBean EmailService mail;
    @MockitoBean GoogleOAuthTokenStore gmail;
    @MockitoBean UserContextService legacyContext;
    @MockitoBean EmailRoutingService routing;
    MockMvc mvc;UserModel account;Employee employee;Student child;

    @BeforeEach void init(){
        mvc=MockMvcBuilders.webAppContextSetup(application).defaultRequest(get("/").accept(MediaType.APPLICATION_JSON)).apply(springSecurity()).build();
        account=new UserModel();account.setId("account-A");account.setUsername("actor@example.test");account.setPassword("synthetic-encoded");
        account.setSchool("Same School");account.setTenantSchoolId("school-A");account.setRoles(Set.of(new RoleModel(null,"TEACHER")));
        employee=new Employee();employee.setEmployeeId("employee-A");employee.setEmail(account.getUsername());employee.setSchool("Same School");employee.setTenantSchoolId("school-A");employee.setRoles(account.getRoles());
        child=new Student();child.setStudentIdNumber("student-A");child.setStudentEmail(account.getUsername());child.setTenantSchoolId("school-A");child.setSchool("Same School");
        when(users.findByUsername(account.getUsername())).thenReturn(account);when(accounts.loadUserModelByUsername(account.getUsername())).thenReturn(account);
        when(employees.findByEmailIgnoreCase(account.getUsername())).thenAnswer(i->studentRole()?null:employee);
        when(students.findByStudentEmailIgnoreCase(account.getUsername())).thenAnswer(i->studentRole()?child:null);
        for(String id:List.of("school-A","school-B")){var s=new School();s.setSchoolIdNumber(id);s.setSchoolName("Same School");when(schools.findById(id)).thenReturn(Optional.of(s));}
        reset(provider);when(provider.supports(any())).thenReturn(true);
        when(provider.authenticate(any())).thenAnswer(i->{var d=details.loadUserByUsername(account.getUsername());return new UsernamePasswordAuthenticationToken(d,null,d.getAuthorities());});
    }
    boolean studentRole(){return account.getRoles().stream().anyMatch(r->"STUDENT".equals(r.getRole()));}
    String token(){return jwt.generateToken(details.loadUserByUsername(account.getUsername()));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder context(String token){return get("/__test__/a7/context").header("Authorization","Bearer "+token);}

    @Test void anonymousCannotResolveContext()throws Exception{mvc.perform(get("/__test__/a7/context")).andExpect(status().isUnauthorized());verifyNoInteractions(users,employees,students,schools);}
    @ParameterizedTest @ValueSource(strings={"TEACHER","GUIDANCE","ADMIN","STUDENT"})
    void matchingPersistedMembershipForSupportedRoles(String role)throws Exception{
        account.setRoles(Set.of(new RoleModel(null,role)));employee.setRoles(account.getRoles());
        mvc.perform(context(token()).param("schoolId","school-B").param("accountId","forged").header("X-School-Id","school-B"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tenantSchoolId").value("school-A")).andExpect(jsonPath("$.roles[0]").value(role));
    }
    @Test void sameNameDoesNotHideAccountProfileConflict()throws Exception{
        employee.setTenantSchoolId("school-B");mvc.perform(context(token())).andExpect(status().isForbidden()).andExpect(content().string("Access denied"));
    }
    @Test void legacyLoginRemainsAvailableButCanonicalContextFailsClosed()throws Exception{
        account.setTenantSchoolId(null);employee.setTenantSchoolId(null);String t=token();
        mvc.perform(context(t)).andExpect(status().isForbidden());
        mvc.perform(get("/__test__/a7/legacy").header("Authorization","Bearer "+t)).andExpect(status().isOk());
    }
    @Test void disabledArchivedAndCredentialChangedActorsAreRejected()throws Exception{
        String t=token();account.setEnabled(false);mvc.perform(context(t)).andExpect(status().isUnauthorized());
        account.setEnabled(true);employee.setArchived(true);mvc.perform(context(t)).andExpect(status().isForbidden());
        employee.setArchived(false);account.setCredentialVersion(1);mvc.perform(context(t)).andExpect(status().isUnauthorized());
    }
    @Test void revokedSessionCannotResolveContext()throws Exception{
        String t=token();jwt.blacklistToken(t);mvc.perform(context(t)).andExpect(status().isUnauthorized());verifyNoInteractions(employees,students,schools);
    }
    @Test void passwordLoginIssuesUsableTokenWithoutBindingLegacyOwnership()throws Exception{
        var result=mvc.perform(post("/auth").contentType(MediaType.APPLICATION_JSON).content("{\"username\":\"actor@example.test\",\"password\":\"synthetic\"}"))
                .andExpect(status().isOk()).andReturn();
        String t=new com.fasterxml.jackson.databind.ObjectMapper().readTree(result.getResponse().getContentAsString()).get("response").asText();
        mvc.perform(context(t)).andExpect(status().isOk());assertEquals("PASSWORD",jwt.extractLoginMethod(t));
    }
    @Test void googleSuccessHandlerIssuesTheSameVersionedRepsPrincipalWithoutGmailCalls()throws Exception{
        var handler=new OAuth2LoginSuccessHandler(jwt,accounts,details,new MockEnvironment(),new com.fasterxml.jackson.databind.ObjectMapper(),setup,students,employees);
        var id=OidcIdToken.withTokenValue("synthetic").subject("google-subject").issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300))
                .claim("email",account.getUsername()).claim("email_verified",true).build();
        var oidcUser=new DefaultOidcUser(List.of(),id);
        var response=new MockHttpServletResponse();handler.onAuthenticationSuccess(new MockHttpServletRequest(),response,new OAuth2AuthenticationToken(oidcUser,List.of(),"google"));
        String t=new com.fasterxml.jackson.databind.ObjectMapper().readTree(response.getContentAsString()).get("response").asText();
        assertEquals("GOOGLE",jwt.extractLoginMethod(t));mvc.perform(context(t)).andExpect(status().isOk());verifyNoInteractions(gmail,mail);
    }
}
