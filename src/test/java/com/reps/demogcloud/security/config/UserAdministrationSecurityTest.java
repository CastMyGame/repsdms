package com.reps.demogcloud.security.config;

import com.reps.demogcloud.controllers.EmployeeController;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.employee.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.controllers.*;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.*;
import com.reps.demogcloud.services.employee.*;
import com.reps.demogcloud.services.email.EmailRoutingService;
import com.reps.demogcloud.utils.SchoolUtils;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real chain/controllers/business services. Repositories/password encoding/providers are mocked only.
 * No Boot initialization, Mongo connections, emails, Google or Stripe calls. */
@SpringJUnitWebConfig(UserAdministrationSecurityTest.App.class)
@TestPropertySource(properties={"auth.sso.google.enabled=false", "auth.sso.only=false"})
class UserAdministrationSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackageClasses=SecurityConfig.class, useDefaultFilters=false,
            includeFilters=@ComponentScan.Filter(type=FilterType.ASSIGNABLE_TYPE, classes=SecurityConfig.class))
    @Import({JwtFilterRequest.class, UserControllers.class, EmployeeController.class, AuthControllers.class,
            UserAdministrationService.class, WorkflowAccessService.class, UserContextService.class,
            EmployeeService.class, EmployeeCreationService.class, EmployeeMutationService.class,
            EmployeeClassService.class, EmployeeQueryService.class, SchoolUtils.class,
            UserAccountService.class, AuthService.class, CustomUserDetailsService.class, GlobalExceptionHandler.class})
    static class App { @Bean AuthenticationProvider provider() { return mock(AuthenticationProvider.class); } }
    @Autowired WebApplicationContext context;
    @MockitoBean CustomOAuth2UserService oidc;
    @MockitoBean OAuth2LoginSuccessHandler success;
    @MockitoBean OAuth2LoginFailureHandler failure;
    @MockitoBean JwtUtils jwt;
    @MockitoBean UserRepository users;
    @MockitoBean EmployeeRepository employees;
    @MockitoBean StudentRepository students;
    @MockitoBean SchoolRepository schools;
    @MockitoBean PunishRepository cases;
    @MockitoBean AssignmentTemplateRepository templates;
    @MockitoBean PasswordResetTokenRepository resets;
    @MockitoBean PasswordEncoder encoder;
    @MockitoBean EmailService email;
    @MockitoBean GoogleOAuthTokenStore google;
    @MockitoBean EmailRoutingService routing;
    MockMvc mvc;
    Map<String,UserModel> accounts;
    Map<String,Employee> staff;
    static final String SCHOOL="Testing", ADMIN="admin@example.test", TEACHER="teacher@example.test", CHILD="student@example.test";
    static Set<RoleModel> roles(String... roles) {
        Set<RoleModel> result=new LinkedHashSet<>(); for(String role:roles) result.add(new RoleModel(null,role)); return result;
    }
    UserModel account(String email, String school, String role) {
        var u=new UserModel(); u.setId(email); u.setUsername(email); u.setSchool(school); u.setRoles(roles(role));
        u.setPassword("synthetic-encoded"); accounts.put(email,u); return u;
    }
    Employee employee(String email,String school,String role) {
        var e=new Employee(); e.setEmployeeId(email); e.setEmail(email); e.setSchool(school); e.setRoles(roles(role));
        e.setClasses(new ArrayList<>()); staff.put(email,e); return e;
    }
    @BeforeEach void setup() {
        mvc=MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .apply(springSecurity()).build();
        accounts=new LinkedHashMap<>(); staff=new LinkedHashMap<>();
        for(var role:List.of("ADMIN","TEACHER","GUIDANCE")) {
            String name=role.toLowerCase()+"@example.test"; account(name,SCHOOL,role); employee(name,SCHOOL,role);
        }
        account(CHILD,SCHOOL,"STUDENT");
        account("foreign@example.test","Other school","TEACHER"); employee("foreign@example.test","Other school","TEACHER");
        var child=new Student(); child.setStudentEmail(CHILD); child.setSchool(SCHOOL); child.setLastName("Synthetic");
        when(students.findByStudentEmailIgnoreCase(CHILD)).thenReturn(child);
        when(students.findBySchool(SCHOOL)).thenReturn(List.of(child));
        when(users.findByUsername(anyString())).thenAnswer(i -> accounts.get(i.getArgument(0)));
        when(users.findById(anyString())).thenAnswer(i -> Optional.ofNullable(accounts.get(i.getArgument(0))));
        when(users.findAll()).thenAnswer(i -> new ArrayList<>(accounts.values()));
        when(users.existsByUsername(anyString())).thenAnswer(i -> accounts.containsKey(i.getArgument(0)));
        when(users.save(any())).thenAnswer(i -> {UserModel u=i.getArgument(0); if(u.getId()==null)u.setId(u.getUsername()); accounts.put(u.getUsername(),u);return u;});
        when(employees.findByEmailIgnoreCase(anyString())).thenAnswer(i -> staff.get(i.getArgument(0)));
        when(employees.findById(anyString())).thenAnswer(i -> Optional.ofNullable(staff.get(i.getArgument(0))));
        when(employees.findByArchivedAndSchool(anyBoolean(),anyString())).thenAnswer(i -> staff.values().stream()
                .filter(e -> e.isArchived()==i.<Boolean>getArgument(0) && e.getSchool().equals(i.getArgument(1))).toList());
        when(employees.save(any())).thenAnswer(i -> {Employee e=i.getArgument(0);if(e.getEmployeeId()==null)e.setEmployeeId(e.getEmail());staff.put(e.getEmail(),e);return e;});
        doAnswer(i -> {accounts.get(i.getArgument(0)).setRoles(i.getArgument(2));return null;})
                .when(users).updateRolesInSchool(anyString(),anyString(),anySet());
        doAnswer(i -> {staff.get(i.getArgument(0)).setRoles(i.getArgument(2));return null;})
                .when(employees).updateRolesInSchool(anyString(),anyString(),anySet());
        doAnswer(i -> {accounts.get(i.getArgument(0)).setEnabled(i.getArgument(2));return null;})
                .when(users).updateEnabledInSchool(anyString(),anyString(),anyBoolean());
        doAnswer(i -> {staff.get(i.getArgument(0)).setArchived(i.getArgument(2));return null;})
                .when(employees).updateArchivedInSchool(anyString(),anyString(),anyBoolean());
        when(encoder.encode(anyString())).thenReturn("synthetic-encoded");
    }
    MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request,String who,String body) {
        return request.with(user(who).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content(body);
    }
    @Test void anonymousAdministrationIsBlockedBeforePersistence() throws Exception {
        for(var req:List.of(get("/users/v1/users"),get("/employees/v1/employees"),
                put("/users/v1/users/"+TEACHER+"/roles"),put("/employees/v1/employees/"+TEACHER+"/roles"),
                put("/users/v1/users/"+TEACHER+"/enabled"),delete("/users/v1/users/"+TEACHER),
                delete("/employees/v1/employees/"+TEACHER),post("/employees/v1/employees"),post("/employees/v1/employees/list"),
                post("/users/create/Testing"),post("/users/v1/users/create/Testing"))) {
            int status=mvc.perform(req.contentType(MediaType.APPLICATION_JSON).content("{}")).andReturn().getResponse().getStatus();
            assertTrue(status==302||status==401||status==403, "Anonymous status "+status);
        }
        verifyNoInteractions(users,employees,students,email,google);
    }
    @ParameterizedTest @ValueSource(strings={"teacher@example.test","guidance@example.test","student@example.test"})
    void nonAdminsCannotEscalateDeleteActivateListOrCreateDespiteForgedAuthorities(String who) throws Exception {
        mvc.perform(get("/users/v1/users").with(user(who).roles("ADMIN"))).andExpect(status().isForbidden());
        for(var path:List.of("/users/v1/users/"+who+"/roles","/employees/v1/employees/"+TEACHER+"/roles"))
            mvc.perform(json(put(path),who,"[{\"id\":\"forged\",\"role\":\"ADMIN\"}]")).andExpect(status().isForbidden());
        mvc.perform(json(put("/users/v1/users/"+TEACHER+"/enabled"),who,"{\"enabled\":true}")).andExpect(status().isForbidden());
        mvc.perform(delete("/users/v1/users/"+TEACHER).with(user(who).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(delete("/employees/v1/employees/"+TEACHER).with(user(who).roles("ADMIN"))).andExpect(status().isForbidden());
        mvc.perform(json(post("/employees/v1/employees"),who,"{\"email\":\"new@example.test\",\"roles\":[{\"role\":\"ADMIN\"}]}"))
                .andExpect(status().isForbidden());
        verify(users,never()).save(any());verify(employees,never()).save(any());
        verify(users,never()).updateRolesInSchool(any(),any(),any());verify(users,never()).updateEnabledInSchool(any(),any(),anyBoolean());
        verify(employees,never()).updateRolesInSchool(any(),any(),any());
    }
    @Test void administratorListsOnlyOwnSchoolIncludingLegacySchoolName() throws Exception {
        accounts.get(TEACHER).setSchool(null); accounts.get(TEACHER).setLegacySchoolName(SCHOOL);
        mvc.perform(get("/users/v1/users").with(user(ADMIN))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[*].password").doesNotExist());
        mvc.perform(get("/users/v1/users/TEACHER").with(user(ADMIN))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
    }
    @ParameterizedTest @ValueSource(strings={"users/v1/users","employees/v1/employees"})
    void bothRoleRoutesSynchronizeKnownRolesAndIgnoreRoleIds(String prefix) throws Exception {
        mvc.perform(json(put("/"+prefix+"/"+TEACHER+"/roles"),ADMIN,"[{\"id\":\"forged\",\"role\":\"guidance\"}]"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.roles[0].role").value("GUIDANCE"));
        assertEquals(roles("GUIDANCE"),accounts.get(TEACHER).getRoles()); assertEquals(roles("GUIDANCE"),staff.get(TEACHER).getRoles());
        var ordered=inOrder(employees,users); ordered.verify(employees).updateRolesInSchool(eq(TEACHER),eq(SCHOOL),anySet());
        ordered.verify(users).updateRolesInSchool(eq(TEACHER),eq(SCHOOL),anySet());
        verify(users,never()).save(any());verify(employees,never()).save(any());
    }
    @Test void crossSchoolRolesActivationDeletionCreationAndAliasesAreDenied() throws Exception {
        String other="foreign@example.test";
        for(var req:List.of(put("/users/v1/users/"+other+"/roles"),put("/employees/v1/employees/"+other+"/roles")))
            mvc.perform(json(req,ADMIN,"[{\"role\":\"ADMIN\"}]")).andExpect(status().isForbidden());
        mvc.perform(json(put("/users/v1/users/"+other+"/enabled"),ADMIN,"{\"enabled\":true}")).andExpect(status().isForbidden());
        for(var path:List.of("/users/v1/users/","/employees/v1/employees/"))
            mvc.perform(delete(path+other).with(user(ADMIN))).andExpect(status().isForbidden());
        mvc.perform(json(post("/employees/v1/employees"),ADMIN,"{\"email\":\"new@example.test\",\"school\":\"Other school\"}"))
                .andExpect(status().isForbidden());
        for(var path:List.of("/users/create/Other school","/users/v1/users/create/Other school"))
            mvc.perform(post(path).with(user(ADMIN))).andExpect(status().isForbidden());
        verify(users,never()).updateRolesInSchool(any(),any(),any());verify(users,never()).updateEnabledInSchool(any(),any(),anyBoolean());
        verify(users,never()).save(any());verify(employees,never()).save(any());
    }
    @Test void duplicateAndInjectedIdentifiersCannotOverwriteAccounts() throws Exception {
        for(var body:List.of("{\"email\":\"new@example.test\",\"employeeId\":\"foreign@example.test\"}",
                "{\"email\":\"teacher@example.test\"}")) {
            var result=mvc.perform(json(post("/employees/v1/employees"),ADMIN,body)).andReturn().getResponse().getStatus();
            assertTrue(result==403||result==409);
        }
        verify(users,never()).save(any());verify(employees,never()).save(any());
    }
    @Test void adminTeacherOnboardingDefaultsSchoolAndRoleWithoutAcceptingPrivilegeFields() throws Exception {
        mvc.perform(json(post("/employees/v1/employees"),ADMIN,
                "{\"email\":\"NEW@example.test\",\"firstName\":\"Synthetic\",\"lastName\":\"Teacher\",\"currency\":999,\"archived\":true}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.employee.school").value(SCHOOL))
                .andExpect(jsonPath("$.employee.roles[0].role").value("TEACHER"))
                .andExpect(jsonPath("$.employee.archived").value(false));
        assertTrue(accounts.get("new@example.test").isEnabled());
        assertEquals(roles("TEACHER"),accounts.get("new@example.test").getRoles());
        assertNull(staff.get("new@example.test").getCurrency()); verifyNoInteractions(email,google);
    }
    @Test void batchAuthorizationIsValidatedBeforeAnyCreation() throws Exception {
        mvc.perform(json(post("/employees/v1/employees/list"),ADMIN,
                "[{\"email\":\"new@example.test\"},{\"email\":\"othernew@example.test\",\"school\":\"Other school\"}]"))
                .andExpect(status().isForbidden());
        verify(users,never()).save(any());verify(employees,never()).save(any());
    }
    @Test void unknownMixedAndStudentToStaffRolesAreRejected() throws Exception {
        mvc.perform(json(put("/users/v1/users/"+TEACHER+"/roles"),ADMIN,"[{\"role\":\"SUPERADMIN\"}]"))
                .andExpect(status().isBadRequest());
        mvc.perform(json(put("/users/v1/users/"+TEACHER+"/roles"),ADMIN,"[{\"role\":\"STUDENT\"},{\"role\":\"ADMIN\"}]"))
                .andExpect(status().isConflict());
        mvc.perform(json(put("/users/v1/users/"+CHILD+"/roles"),ADMIN,"[{\"role\":\"ADMIN\"}]"))
                .andExpect(status().isConflict());
        verify(users,never()).updateRolesInSchool(any(),any(),any());
    }
    @ParameterizedTest @ValueSource(strings={"users/v1/users","employees/v1/employees"})
    void deletionRevokesLoginBeforeArchivingAndPreservesRecords(String prefix) throws Exception {
        mvc.perform(delete("/"+prefix+"/"+TEACHER).with(user(ADMIN))).andExpect(status().isOk());
        assertFalse(accounts.get(TEACHER).isEnabled()); assertTrue(staff.get(TEACHER).isArchived());
        var ordered=inOrder(users,employees); ordered.verify(users).updateEnabledInSchool(TEACHER,SCHOOL,false);
        ordered.verify(employees).updateArchivedInSchool(TEACHER,SCHOOL,true);
        verify(users,never()).deleteById(any());verify(employees,never()).deleteById(any());
        mvc.perform(get("/employees/v1/employees").with(user(TEACHER).roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void pendingTeacherActivationIsExplicitSchoolAdministratorOnly() throws Exception {
        accounts.get(TEACHER).setEnabled(false); staff.get(TEACHER).setArchived(true);
        mvc.perform(json(put("/users/v1/users/"+TEACHER+"/enabled"),ADMIN,"{\"enabled\":true}"))
                .andExpect(status().isNoContent());
        assertTrue(accounts.get(TEACHER).isEnabled());assertFalse(staff.get(TEACHER).isArchived());
    }
    @Test void selfAdministrativeChangesAndStaleDisabledAdminAreRejected() throws Exception {
        mvc.perform(json(put("/users/v1/users/"+ADMIN+"/roles"),ADMIN,"[{\"role\":\"TEACHER\"}]"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/users/v1/users/"+ADMIN).with(user(ADMIN))).andExpect(status().isForbidden());
        accounts.get(ADMIN).setEnabled(false);
        mvc.perform(get("/users/v1/users").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void inconsistentLinkedSchoolsAreRejectedBeforeWrites() throws Exception {
        staff.get(TEACHER).setSchool("Other school");
        mvc.perform(json(put("/users/v1/users/"+TEACHER+"/roles"),ADMIN,"[{\"role\":\"GUIDANCE\"}]"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/users/v1/users/"+TEACHER).with(user(ADMIN))).andExpect(status().isForbidden());
        verify(users,never()).updateEnabledInSchool(any(),any(),anyBoolean());
        verify(users,never()).updateRolesInSchool(any(),any(),any());
    }
    @Test void teacherCanEditOwnClassButNotOtherEmployeesOrForeignStudentReferences() throws Exception {
        String body="{\"classToUpdate\":{\"className\":\"Synthetic\",\"classRoster\":[\"student@example.test\"]}}";
        mvc.perform(json(put("/employees/v1/updateClass/"+TEACHER),TEACHER,body)).andExpect(status().isOk());
        mvc.perform(json(put("/employees/v1/updateClass/guidance@example.test"),TEACHER,body)).andExpect(status().isForbidden());
        var foreign=new Student(); foreign.setStudentEmail("foreignstudent@example.test"); foreign.setSchool("Other school");
        when(students.findByStudentEmailIgnoreCase(foreign.getStudentEmail())).thenReturn(foreign);
        mvc.perform(json(put("/employees/v1/updateClass/"+TEACHER),TEACHER,body.replace(CHILD,foreign.getStudentEmail())))
                .andExpect(status().isForbidden());
        mvc.perform(json(post("/employees/v1/deleteClass/guidance@example.test"),TEACHER,body)).andExpect(status().isForbidden());
        verify(employees,times(1)).save(any());
    }
    @Test void staffDirectoryAndSelfReadRespectEstablishedRolesAndSchools() throws Exception {
        mvc.perform(get("/employees/v1/employees").with(user(TEACHER))).andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/employees/v1/employees/TEACHER").with(user("guidance@example.test")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/employees/v1/employees/email/"+TEACHER).with(user(TEACHER))).andExpect(status().isOk());
        mvc.perform(get("/employees/v1/employees/email/foreign@example.test").with(user(ADMIN))).andExpect(status().isForbidden());
        mvc.perform(get("/employees/v1/employees/email/"+TEACHER).with(user(CHILD))).andExpect(status().isForbidden());
    }
    @Test void bothBulkStudentProvisioningAliasesRemainSchoolAdministratorOnly() throws Exception {
        for(var path:List.of("/users/create/Testing","/users/v1/users/create/Testing")) {
            mvc.perform(post(path).with(user(TEACHER))).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user(ADMIN))).andExpect(status().isOk());
        }
    }
    @Test void legacyRegistrationAndMaintenanceAreDisabled() throws Exception {
        int legacyStatus=mvc.perform(post("/register").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andReturn().getResponse().getStatus();
        assertTrue(legacyStatus==302||legacyStatus==403||legacyStatus==401);
        mvc.perform(json(post("/register"),ADMIN,"{}")).andExpect(status().isForbidden());
        for(var path:List.of("/users/v1/users/Testing","/employees/v1/updateAll"))
            mvc.perform(put(path).with(user(ADMIN))).andExpect(status().isForbidden());
        verify(users,never()).save(any());verify(employees,never()).save(any());
    }
    @Test void passwordResetCannotRestoreSnapshotRolesOrReactivateDeletedAccount() throws Exception {
        var snapshot=new UserModel();snapshot.setId(TEACHER);snapshot.setUsername(TEACHER);snapshot.setRoles(roles("ADMIN"));
        var token=new PasswordResetToken(snapshot,"synthetic-token",5);
        when(resets.findByToken("synthetic-token")).thenReturn(token);
        when(users.updatePasswordIfEnabled(TEACHER,"synthetic-encoded")).thenReturn(1L);
        mvc.perform(post("/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"synthetic-token\",\"newPassword\":\"synthetic\"}")).andExpect(status().isOk());
        assertEquals(roles("TEACHER"),accounts.get(TEACHER).getRoles());
        verify(users,never()).save(any());verify(users).updatePasswordIfEnabled(TEACHER,"synthetic-encoded");
        when(users.updatePasswordIfEnabled(TEACHER,"synthetic-encoded")).thenReturn(0L);
        mvc.perform(post("/reset-password").contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"synthetic-token\",\"newPassword\":\"synthetic\"}")).andExpect(status().isBadRequest());
    }
    @Test void staleJwtAuthoritiesNeverOverrideCurrentPersistedTeacherRole() throws Exception {
        when(jwt.extractUserName("synthetic")).thenReturn(TEACHER);
        when(jwt.validateToken(eq("synthetic"),any())).thenReturn(true);
        mvc.perform(put("/users/v1/users/"+TEACHER+"/roles").header("Authorization","Bearer synthetic")
                .contentType(MediaType.APPLICATION_JSON).content("[{\"role\":\"ADMIN\"}]")).andExpect(status().isForbidden());
        verify(users,never()).updateRolesInSchool(any(),any(),any());
    }
    @Test void roleWriteFailureDoesNotClaimSuccessOrSaveStaleUserFields() throws Exception {
        doThrow(new IllegalStateException("synthetic failure")).when(users).updateRolesInSchool(eq(TEACHER),eq(SCHOOL),any());
        mvc.perform(json(put("/users/v1/users/"+TEACHER+"/roles"),ADMIN,"[{\"role\":\"GUIDANCE\"}]"))
                .andExpect(status().isInternalServerError());
        assertEquals(roles("TEACHER"),accounts.get(TEACHER).getRoles());verify(users,never()).save(any());
    }

    @Test void employeeRoleProjectionCannotGrantAdministratorPermission() throws Exception {
        staff.get(TEACHER).setRoles(roles("ADMIN"));
        mvc.perform(get("/users/v1/users").with(user(TEACHER).roles("ADMIN"))).andExpect(status().isForbidden());
        staff.remove(ADMIN);
        mvc.perform(get("/users/v1/users").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
    }
    @Test void archiveFailureStillLeavesLoginRevoked() throws Exception {
        doThrow(new IllegalStateException("synthetic archive failure")).when(employees).updateArchivedInSchool(TEACHER,SCHOOL,true);
        mvc.perform(delete("/users/v1/users/"+TEACHER).with(user(ADMIN))).andExpect(status().isInternalServerError());
        assertFalse(accounts.get(TEACHER).isEnabled());verify(users,never()).save(any());
    }
    @Test void roleProjectionFailureDoesNotGrantAuthoritativeRole() throws Exception {
        doThrow(new IllegalStateException("synthetic projection failure")).when(employees)
                .updateRolesInSchool(eq(TEACHER),eq(SCHOOL),any());
        mvc.perform(json(put("/users/v1/users/"+TEACHER+"/roles"),ADMIN,"[{\"role\":\"ADMIN\"}]"))
                .andExpect(status().isInternalServerError());
        verify(users,never()).updateRolesInSchool(any(),any(),any());
    }
    @Test void administratorCanEditSameSchoolClassButCannotResetOtherSchoolCurrency() throws Exception {
        String body="{\"classToUpdate\":{\"className\":\"Synthetic\",\"classRoster\":[\"student@example.test\"]}}";
        mvc.perform(json(put("/employees/v1/updateClass/"+TEACHER),ADMIN,body)).andExpect(status().isOk());
        mvc.perform(json(put("/employees/v1/updateClass/foreign@example.test"),ADMIN,body)).andExpect(status().isForbidden());
        mvc.perform(put("/employees/v1/Other school").with(user(ADMIN))).andExpect(status().isForbidden());
        mvc.perform(put("/employees/v1/Testing").with(user(TEACHER))).andExpect(status().isForbidden());
        verify(employees,times(1)).save(any());
    }
    @Test void activationCannotGrantAccessToForeignOrMissingStudentProfile() throws Exception {
        var child=students.findByStudentEmailIgnoreCase(CHILD); child.setSchool("Other school");
        mvc.perform(json(put("/users/v1/users/"+CHILD+"/enabled"),ADMIN,"{\"enabled\":true}"))
                .andExpect(status().isForbidden());
        when(students.findByStudentEmailIgnoreCase(CHILD)).thenReturn(null);
        mvc.perform(json(put("/users/v1/users/"+CHILD+"/enabled"),ADMIN,"{\"enabled\":true}"))
                .andExpect(status().isConflict());
        verify(users,never()).updateEnabledInSchool(any(),any(),anyBoolean());
    }
    @Test void missingTargetReturns404OnlyAfterAdministratorAuthorization() throws Exception {
        mvc.perform(json(put("/users/v1/users/missing/roles"),ADMIN,"[{\"role\":\"TEACHER\"}]"))
                .andExpect(status().isNotFound());
        mvc.perform(json(put("/users/v1/users/missing/roles"),TEACHER,"[{\"role\":\"TEACHER\"}]"))
                .andExpect(status().isForbidden());
    }

    @Test void ambiguousStudentStaffActorCannotAdministerAccounts() throws Exception {
        accounts.get(ADMIN).setRoles(roles("ADMIN","STUDENT"));
        mvc.perform(get("/users/v1/users").with(user(ADMIN).roles("ADMIN"))).andExpect(status().isForbidden());
        verify(users,never()).updateRolesInSchool(any(),any(),any());
    }
    @Test void bulkProvisioningSkipsArchivedStudentsAndUsesOnlyStudentRole() throws Exception {
        var inactive=new Student();inactive.setStudentEmail("inactive@example.test");inactive.setSchool(SCHOOL);inactive.setArchived(true);
        var active=new Student();active.setStudentEmail("newstudent@example.test");active.setSchool(SCHOOL);active.setLastName("Synthetic");
        when(students.findBySchool(SCHOOL)).thenReturn(List.of(inactive,active));
        mvc.perform(post("/users/v1/users/create/Testing").with(user(ADMIN))).andExpect(status().isOk());
        assertFalse(accounts.containsKey("inactive@example.test"));
        assertEquals(roles("STUDENT"),accounts.get("newstudent@example.test").getRoles());
    }
}
