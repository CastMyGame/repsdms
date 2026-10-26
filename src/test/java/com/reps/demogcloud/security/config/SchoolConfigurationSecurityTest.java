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

/** Production filter chain and persisted authorization; MongoDB and external providers mocked. */
@SpringJUnitWebConfig(SchoolConfigurationSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false","reps.email.routing.phase1-enabled=false","reps.email.notifications.enabled=false"})
class SchoolConfigurationSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackageClasses=SecurityConfig.class,useDefaultFilters=false,includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE,classes=SecurityConfig.class))
    @Import({SchoolController.class,SchoolService.class,UserContextService.class,WorkflowAccessService.class,JwtFilterRequest.class,GlobalExceptionHandler.class})
    static class App { @Bean AuthenticationProvider provider(){return mock(AuthenticationProvider.class);} }
    @Autowired WebApplicationContext context;
    @MockitoBean UserRepository users; @MockitoBean EmployeeRepository employees; @MockitoBean StudentRepository students;
    @MockitoBean SchoolRepository schools; @MockitoBean PunishRepository cases; @MockitoBean AssignmentTemplateRepository templates;
    @MockitoBean org.springframework.data.mongodb.core.MongoTemplate mongo;
    @MockitoBean GoogleOAuthTokenStore google; @MockitoBean CustomOAuth2UserService oidc; @MockitoBean OAuth2LoginSuccessHandler success;
    @MockitoBean OAuth2LoginFailureHandler failure; @MockitoBean CustomUserDetailsService details; @MockitoBean JwtUtils jwt;
    UserModel account; Employee employee; com.reps.demogcloud.models.school.School school; MockMvc mvc;
    static final String STAFF="staff@example.test";
    @BeforeEach void setup(){
        mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        account=new UserModel();account.setUsername(STAFF);account.setSchool("Testing");role("ADMIN");
        employee=new Employee();employee.setEmail(STAFF);employee.setSchool("Testing");
        school=new com.reps.demogcloud.models.school.School();school.setSchoolIdNumber("trusted-school");school.setSchoolName("Testing");school.setCurrency("Points");school.setCity("Town");school.setState("SC");school.setZip("00000");school.setNormalizedKey("original");
        when(users.findByUsername(STAFF)).thenAnswer(i->account);when(employees.findByEmailIgnoreCase(STAFF)).thenReturn(employee);
        when(schools.findBySchoolNameIgnoreCase("Testing")).thenReturn(Optional.of(school));
        when(schools.findAll()).thenReturn(List.of(school));when(schools.findByCityIgnoreCaseAndStateIgnoreCaseOrderBySchoolNameAsc("Town","SC")).thenReturn(List.of(school));
        when(mongo.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),any(org.springframework.data.mongodb.core.query.Update.class),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(com.reps.demogcloud.models.school.School.class))).thenReturn(school);
    }
    void role(String role){account.setRoles(Set.of(new RoleModel(null,role)));}
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder edit(){return put("/school/v1/Testing").with(user(STAFF).roles("ADMIN")).param("currency","Tokens").accept(MediaType.APPLICATION_JSON);}
    void noWrites(){verifyNoInteractions(mongo);verify(schools,never()).save(any());verify(schools,never()).delete(any());}
    @Test void anonymousCanReadDirectoryButCannotEditOrProvision() throws Exception {
        mvc.perform(get("/school/v1/all")).andExpect(status().isOk());
        mvc.perform(get("/school/v1/search").param("city","Town").param("state","SC")).andExpect(status().isOk());
        mvc.perform(put("/school/v1/Testing").param("currency","Forged")).andExpect(status().isUnauthorized());
        mvc.perform(post("/school/v1/newSchool").contentType(MediaType.APPLICATION_JSON).content("{\"schoolName\":\"Forged\"}")).andExpect(status().isForbidden()).andExpect(content().string("School activation requires approval from the REPS team."));noWrites();
    }
    @Test void provisioningUnavailableForEveryRoleEvenWithForgedOwnership() throws Exception {
        for(String role:List.of("STUDENT","TEACHER","GUIDANCE","ADMIN","PARENT","OPERATOR")){
            role(role);mvc.perform(post("/school/v1/newSchool").with(user(STAFF).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{\"schoolName\":\"Other\",\"schoolIdNumber\":\"trusted-school\",\"roles\":[\"ADMIN\"]}")).andExpect(status().isForbidden());
        }noWrites();verify(schools,never()).findByNormalizedKey(anyString());
    }
    @Test void onlyPersistedSameSchoolAdministratorCanEdit() throws Exception {
        for(String role:List.of("STUDENT","TEACHER","GUIDANCE","PARENT","OPERATOR")){role(role);mvc.perform(edit()).andExpect(status().isForbidden());}noWrites();
    }
    @Test void crossSchoolAdminAndMismatchedEmployeeCannotEdit() throws Exception {
        account.setSchool("Other");employee.setSchool("Other");mvc.perform(edit()).andExpect(status().isForbidden());
        account.setSchool("Testing");mvc.perform(edit()).andExpect(status().isForbidden());noWrites();
    }
    @Test void validAdministratorUsesTargetedAllowlistUpdate() throws Exception {
        mvc.perform(edit().param("maxPunishLevel","4")).andExpect(status().isOk()).andExpect(jsonPath("$.school.schoolIdNumber").value("trusted-school")).andExpect(jsonPath("$.school.normalizedKey").value("original"));
        var query=org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Query.class);var update=org.mockito.ArgumentCaptor.forClass(org.springframework.data.mongodb.core.query.Update.class);
        verify(mongo).findAndModify(query.capture(),update.capture(),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(com.reps.demogcloud.models.school.School.class));
        assertEquals("trusted-school",query.getValue().getQueryObject().getString("_id"));
        assertEquals(new org.bson.Document("$set",new org.bson.Document("currency","Tokens").append("maxPunishLevel",4)),update.getValue().getUpdateObject());verify(schools,never()).save(any());
    }
    @Test void arbitraryIdentityLocationAndSchoolFieldsRejectWholeRequest() throws Exception {
        for(String field:List.of("schoolIdNumber","schoolName","normalizedKey","school","city","state","zip","enabled","roles","class","currency.balance","$set","unknown"))
            mvc.perform(edit().param(field,"forged")).andExpect(status().isBadRequest());noWrites();
    }
    @Test void invalidLevelAndCurrencyCannotPartiallyUpdate() throws Exception {
        for(String level:List.of("0","-1","5","2147483648","1.5","invalid",""))mvc.perform(edit().param("maxPunishLevel",level)).andExpect(status().isBadRequest());
        for(String label:List.of("","  ","x".repeat(81),"bad\nlabel"))mvc.perform(put("/school/v1/Testing").with(user(STAFF)).param("currency",label)).andExpect(status().isBadRequest());noWrites();
    }
    @Test void disabledArchivedPendingAndMissingStaffAreDenied() throws Exception {
        account.setEnabled(false);mvc.perform(edit()).andExpect(status().isForbidden());account.setEnabled(true);
        employee.setArchived(true);mvc.perform(edit()).andExpect(status().isForbidden());employee.setArchived(false);
        account.setPasswordSetupRequired(true);mvc.perform(edit()).andExpect(status().isForbidden());account.setPasswordSetupRequired(false);
        when(employees.findByEmailIgnoreCase(STAFF)).thenReturn(null);mvc.perform(edit()).andExpect(status().isForbidden());noWrites();
    }
    @Test void staleSchoolFailsWithoutUpsertOrWholeDocumentSave() throws Exception {
        when(mongo.findAndModify(any(org.springframework.data.mongodb.core.query.Query.class),any(org.springframework.data.mongodb.core.query.Update.class),any(org.springframework.data.mongodb.core.FindAndModifyOptions.class),eq(com.reps.demogcloud.models.school.School.class))).thenReturn(null);
        mvc.perform(edit()).andExpect(status().isConflict());verify(schools,never()).save(any());
    }
    @Test void deletionBulkAndUnsupportedMaintenanceCannotWrite() throws Exception {
        for(String path:List.of("/school/v1/Testing","/school/v1/delete/Testing","/school/v1/bulk","/school/v1/migrate")){
            mvc.perform(delete(path).with(user(STAFF))).andExpect(status().isForbidden());
            mvc.perform(patch(path).with(user(STAFF)).contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        }noWrites();
    }
}
