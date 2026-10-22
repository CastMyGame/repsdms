package com.reps.demogcloud.security.config;

import com.reps.demogcloud.controllers.*;
import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.assignments.*;
import com.reps.demogcloud.models.dto.*;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
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

/** Real production filter chain, controllers, authorization policy and assignment service.
 * Only persistence, DTO aggregation and OAuth collaborators are mocked; no Boot/cloud startup. */
@SpringJUnitWebConfig(WorkflowSecurityTest.App.class)
@TestPropertySource(properties = {"auth.sso.google.enabled=false", "auth.sso.only=false",
        "reps.email.routing.phase1-enabled=false", "reps.email.gmail.connection-enabled=false",
        "reps.email.notifications.enabled=false", "reps.email.notifications.dispatch-enabled=false"})
class WorkflowSecurityTest {
    @Configuration @EnableWebMvc @EnableWebSecurity
    @ComponentScan(basePackageClasses = SecurityConfig.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class))
    @Import({DTOController.class, AssignmentController.class, AssignmentBindingController.class,
            WorkflowAccessService.class, UserContextService.class, AssignmentService.class,
            JwtFilterRequest.class, GlobalExceptionHandler.class})
    static class App {
        @Bean AuthenticationProvider provider() { return mock(AuthenticationProvider.class); }
    }
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
    @MockitoBean PunishRepository punishments;
    @MockitoBean AssignmentRepository legacy;
    @MockitoBean AssignmentTemplateRepository templates;
    @MockitoBean AssignmentTemplateBindingRepository bindings;
    @MockitoBean DTOService dto;
    MockMvc mvc;
    static final String ACTOR = "staff@example.test", CHILD = "student@example.test", SCHOOL = "Testing";
    Student student;
    Employee employee;
    UserModel account;
    AssignmentTemplate own, foreign, system;

    @BeforeEach void setup() throws Exception {
        mvc = MockMvcBuilders.webAppContextSetup(context).defaultRequest(get("/").accept(MediaType.APPLICATION_JSON))
                .apply(springSecurity()).build();
        account = new UserModel(); account.setUsername(ACTOR); account.setSchool(SCHOOL);
        account.setRoles(Set.of(new RoleModel(null, "TEACHER")));
        when(users.findByUsername(anyString())).thenAnswer(i -> i.getArgument(0).equals(account.getUsername()) ? account : null);
        employee = new Employee(); employee.setEmail(ACTOR); employee.setSchool(SCHOOL);
        employee.setRoles(account.getRoles());
        var roster = new Employee.ClassRoster(); roster.setClassRoster(List.of(CHILD)); employee.setClasses(List.of(roster));
        when(employees.findByEmailIgnoreCase(ACTOR)).thenReturn(employee);
        student = new Student(); student.setStudentEmail(CHILD); student.setSchool(SCHOOL);
        when(students.findByStudentEmailIgnoreCase(CHILD)).thenReturn(student);
        var school = new School(); school.setSchoolName(SCHOOL); school.setSchoolIdNumber("school-id");
        when(schools.findBySchoolNameIgnoreCase(SCHOOL)).thenReturn(Optional.of(school));
        own = template("own", ACTOR, SCHOOL); foreign = template("foreign", "other@example.test", "Other school");
        system = new AssignmentTemplate(); system.setId("system"); system.setCreatedBySystem(true);
        for (var t : List.of(own, foreign, system)) when(templates.findById(t.getId())).thenReturn(Optional.of(t));
        when(templates.findAll()).thenReturn(List.of(own, foreign, system));
        when(templates.save(any())).thenAnswer(i -> i.getArgument(0));
        when(dto.getStudentOverData(CHILD)).thenReturn(new StudentOverviewDTO(List.of(), List.of(), school, student));
        when(dto.getLoggedInStudentOverData()).thenReturn(new StudentOverviewDTO(List.of(), List.of(), school, student));
        when(dto.getTeacherOverData()).thenReturn(new TeacherOverviewDTO(List.of(),List.of(),List.of(),List.of(),employee,school));
        when(dto.getAdminOverData()).thenReturn(new AdminOverviewDTO(List.of(),List.of(),List.of(),List.of(employee),List.of(),employee,school));
        var p = new Punishment(); p.setPunishmentId("case"); p.setStudentEmail(CHILD); p.setSchool(SCHOOL);
        p.setTeacherEmail(ACTOR); p.setAssignmentTemplateId("own"); p.setInfractionName("Synthetic"); p.setInfractionLevel("1");
        when(punishments.findById("case")).thenReturn(Optional.of(p));
        var binding = new AssignmentTemplateBinding(); binding.setAssignmentTemplateId("own");
        when(bindings.findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrueOrderByCreatedAtDesc(ACTOR,"Synthetic",1))
                .thenReturn(Optional.of(binding));
    }
    static AssignmentTemplate template(String id, String owner, String school) {
        var t = new AssignmentTemplate(); t.setId(id); t.setCreatedByUserId(owner); t.setSchoolId(school);
        t.setScope(AssignmentTemplate.Scope.TEACHER_DEFAULT); return t;
    }
    void role(String role) { account.setRoles(Set.of(new RoleModel(null, role))); }
    void asStudent() { role("STUDENT"); account.setUsername(CHILD); }
    void assertDenied(ResultActions result) throws Exception {
        int code = result.andReturn().getResponse().getStatus();
        assertTrue(code == 401 || code == 403 || code == 302, "Unauthorized status: " + code);
    }
    @Test void anonymousCannotReadAnyDtoOrTemplateOrWrite() throws Exception {
        for (var path : List.of("/DTO/v1/AdminOverviewData", "/DTO/v1/TeacherOverviewData", "/DTO/v1/punishmentsDTO",
                "/DTO/v1/StudentOverviewData", "/DTO/v1/StudentOverviewData/"+CHILD,
                "/assignments/v1/templates", "/assignments/v1/templates/search", "/assignments/v1/templates/own",
                "/assignments/v1/templates/by-infraction?infractionName=Synthetic&level=1",
                "/assignments/v1/by-punishment/case", "/assignments/v1/templates/for-punishment/case",
                "/assignments/v1/case/assignment-template")) assertDenied(mvc.perform(get(path)));
        for (var request : List.of(post("/assignments/v1/templates"), put("/assignments/v1/templates/own"),
                delete("/assignments/v1/templates/own")))
            assertDenied(mvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}")));
        verifyNoInteractions(dto, templates, legacy, students, bindings, punishments);
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","GUIDANCE","ADMIN"})
    void verifiedStaffCanCreateOwnedSchoolTemplate(String role) throws Exception {
        role(role);
        mvc.perform(post("/assignments/v1/templates").with(user(ACTOR).roles(role))
                .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Synthetic\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.createdByUserId").value(ACTOR))
                .andExpect(jsonPath("$.schoolId").value(SCHOOL)).andExpect(jsonPath("$.createdBySystem").value(false))
                .andExpect(jsonPath("$.scope").value("TEACHER_DEFAULT"));
        verify(templates).save(any());
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","GUIDANCE","ADMIN"})
    void staffCannotForgeOwnerSchoolOrGlobalTemplates(String role) throws Exception {
        role(role);
        for (var body : List.of("{\"createdByUserId\":\"spoof@example.test\"}",
                "{\"schoolId\":\"Other school\"}","{\"createdBySystem\":true}"))
            mvc.perform(post("/assignments/v1/templates").with(user(ACTOR).roles(role))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        verify(templates, never()).save(any());
    }
    @Test void studentsCannotUseStaffCatalogOrWritesButCanReadOwnCaseTemplate() throws Exception {
        asStudent();
        mvc.perform(get("/assignments/v1/by-punishment/case").with(user(CHILD).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(get("/assignments/v1/case/assignment-template").with(user(CHILD).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(get("/assignments/v1/templates/for-punishment/case").with(user(CHILD).roles("STUDENT"))).andExpect(status().isOk());
        mvc.perform(get("/assignments/v1/templates").with(user(CHILD).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(post("/assignments/v1/templates").with(user(CHILD).roles("STUDENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        mvc.perform(get("/DTO/v1/StudentOverviewData").with(user(CHILD).roles("STUDENT"))).andExpect(status().isAccepted());
        mvc.perform(get("/DTO/v1/AdminOverviewData").with(user(CHILD).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/DTO/v1/TeacherOverviewData").with(user(CHILD).roles("STUDENT"))).andExpect(status().isForbidden());
        mvc.perform(get("/DTO/v1/punishmentsDTO").with(user(CHILD).roles("STUDENT"))).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","GUIDANCE","ADMIN"})
    void staffOverviewRolesAndStudentRelationships(String role) throws Exception {
        role(role);
        if (role.equals("GUIDANCE")) { employee.setClasses(List.of()); student.setGuidanceEmail(ACTOR); }
        mvc.perform(get("/DTO/v1/TeacherOverviewData").with(user(ACTOR).roles(role))).andExpect(status().isAccepted());
        mvc.perform(get("/DTO/v1/StudentOverviewData/"+CHILD).with(user(ACTOR).roles(role))).andExpect(status().isAccepted());
        mvc.perform(get("/DTO/v1/AdminOverviewData").with(user(ACTOR).roles(role)))
                .andExpect(role.equals("ADMIN") ? status().isAccepted() : status().isForbidden());
        student.setSchool("Other school");
        mvc.perform(get("/DTO/v1/StudentOverviewData/"+CHILD).with(user(ACTOR).roles(role))).andExpect(status().isForbidden());
        mvc.perform(get("/assignments/v1/by-punishment/case").with(user(ACTOR).roles(role))).andExpect(status().isForbidden());
    }
    @Test void unrelatedTeacherOrGuidanceCannotReadStudent() throws Exception {
        employee.setClasses(List.of());
        for (var r : List.of("TEACHER","GUIDANCE")) { role(r);
            mvc.perform(get("/DTO/v1/StudentOverviewData/"+CHILD).with(user(ACTOR).roles(r))).andExpect(status().isForbidden()); }
        verify(dto, never()).getStudentOverData(anyString());
    }
    @Test void unrelatedStudentAndForeignCaseAreDenied() throws Exception {
        asStudent(); account.setUsername("otherstudent@example.test");
        mvc.perform(get("/DTO/v1/StudentOverviewData/"+CHILD).with(user(account.getUsername()).roles("STUDENT")))
                .andExpect(status().isForbidden());
        mvc.perform(get("/assignments/v1/by-punishment/case").with(user(account.getUsername()).roles("STUDENT")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(dto); verify(templates, never()).findById(anyString());
    }
    @Test void persistedRolesOverrideForgedAuthoritiesAndDisabledOrArchivedStaffFail() throws Exception {
        role("STUDENT");
        mvc.perform(post("/assignments/v1/templates").with(user(ACTOR).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        role("TEACHER"); account.setEnabled(false);
        mvc.perform(get("/DTO/v1/TeacherOverviewData").with(user(ACTOR).roles("TEACHER"))).andExpect(status().isForbidden());
        account.setEnabled(true); employee.setArchived(true);
        mvc.perform(get("/DTO/v1/TeacherOverviewData").with(user(ACTOR).roles("TEACHER"))).andExpect(status().isForbidden());
        employee.setArchived(false); employee.setSchool("Other school");
        mvc.perform(get("/DTO/v1/TeacherOverviewData").with(user(ACTOR).roles("TEACHER"))).andExpect(status().isForbidden());
    }
    @Test void catalogSearchAndDirectReadsExcludeForeignAndOtherPrivateTemplates() throws Exception {
        var other = template("private", "colleague@example.test", SCHOOL);
        when(templates.findAll()).thenReturn(List.of(own,foreign,system,other));
        mvc.perform(get("/assignments/v1/templates").with(user(ACTOR).roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/assignments/v1/templates/search").with(user(ACTOR).roles("TEACHER")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/assignments/v1/templates/foreign").with(user(ACTOR).roles("TEACHER"))).andExpect(status().isForbidden());
        own.setVisibility(AssignmentTemplate.Visibility.SCHOOL); own.setCreatedByUserId("colleague@example.test");
        mvc.perform(get("/assignments/v1/templates/own").with(user(ACTOR).roles("TEACHER"))).andExpect(status().isOk());
        mvc.perform(put("/assignments/v1/templates/own").with(user(ACTOR).roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
    }
    @ParameterizedTest @ValueSource(strings={"TEACHER","GUIDANCE","ADMIN"})
    void ownerCanUpdateAndDeleteButForeignAndSystemWritesFail(String role) throws Exception {
        role(role);
        mvc.perform(put("/assignments/v1/templates/own").with(user(ACTOR).roles(role))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(delete("/assignments/v1/templates/own").with(user(ACTOR).roles(role))).andExpect(status().isNoContent());
        for (var id : List.of("foreign","system")) {
            mvc.perform(put("/assignments/v1/templates/"+id).with(user(ACTOR).roles(role))
                    .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
            mvc.perform(delete("/assignments/v1/templates/"+id).with(user(ACTOR).roles(role))).andExpect(status().isForbidden());
        }
        verify(templates, times(1)).save(any()); verify(templates).delete(own);
    }
    @Test void legacyGlobalWritesAndMigrationAreDeniedEvenForSchoolAdmin() throws Exception {
        role("ADMIN");
        for (var req : List.of(post("/assignments/v1/"),post("/assignments/v1/migrate-legacy"),
                put("/assignments/v1/old"),delete("/assignments/v1/delete/old")))
            mvc.perform(req.with(user(ACTOR).roles("ADMIN")).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        verifyNoInteractions(legacy); verify(templates,never()).save(any());
    }
    @Test void bindingCannotSpoofTeacherOrCrossSchoolTemplate() throws Exception {
        for (var body : List.of("{\"teacherEmail\":\"other@example.test\",\"assignmentTemplateId\":\"own\"}",
                "{\"teacherEmail\":\"staff@example.test\",\"assignmentTemplateId\":\"foreign\"}"))
            mvc.perform(post("/assignments/v1/bindings/teacher-default").with(user(ACTOR).roles("TEACHER"))
                    .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(get("/assignments/v1/bindings/teacher-default?teacherEmail=other@example.test").with(user(ACTOR).roles("TEACHER")))
                .andExpect(status().isForbidden());
        verifyNoInteractions(bindings);
    }
    @Test void bearerJwtWorksForTemplateAndDtoUrls() throws Exception {
        var principal = User.withUsername(ACTOR).password("unused").roles("TEACHER").build();
        when(jwt.extractUserName("synthetic")).thenReturn(ACTOR);
        when(details.loadUserByUsername(ACTOR)).thenReturn(principal);
        when(jwt.validateToken("synthetic", principal)).thenReturn(true);
        mvc.perform(get("/assignments/v1/templates/own").header("Authorization","Bearer synthetic")).andExpect(status().isOk());
        mvc.perform(get("/DTO/v1/TeacherOverviewData").header("Authorization","Bearer synthetic")).andExpect(status().isAccepted());
        verify(details,times(2)).loadUserByUsername(ACTOR);
    }
    @Test void missingStaffAndUnsupportedRolesFailClosed() throws Exception {
        when(employees.findByEmailIgnoreCase(ACTOR)).thenReturn(null);
        mvc.perform(get("/DTO/v1/TeacherOverviewData").with(user(ACTOR).roles("TEACHER"))).andExpect(status().isForbidden());
        when(employees.findByEmailIgnoreCase(ACTOR)).thenReturn(employee); role("PARENT");
        mvc.perform(get("/DTO/v1/StudentOverviewData/"+CHILD).with(user(ACTOR).roles("PARENT")))
                .andExpect(status().isForbidden());
        mvc.perform(post("/assignments/v1/templates").with(user(ACTOR).roles("PARENT"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        verifyNoInteractions(dto); verify(templates,never()).save(any());
    }
    @Test void legacyPersonalTemplateUsesTrustedOwnerSchool() throws Exception {
        own.setSchoolId(null);
        mvc.perform(get("/assignments/v1/templates/own").with(user(ACTOR).roles("TEACHER"))).andExpect(status().isOk());
        var otherOwner = new Employee(); otherOwner.setEmail("colleague@example.test"); otherOwner.setSchool("Other school");
        own.setCreatedByUserId(otherOwner.getEmail());
        when(employees.findByEmailIgnoreCase(otherOwner.getEmail())).thenReturn(otherOwner);
        mvc.perform(get("/assignments/v1/templates/own").with(user(ACTOR).roles("TEACHER"))).andExpect(status().isForbidden());
    }
    @Test void schoolAdminCanManageSchoolTemplateButTeachersCannotCreateSchoolDefaults() throws Exception {
        own.setCreatedByUserId("colleague@example.test"); role("ADMIN");
        mvc.perform(put("/assignments/v1/templates/own").with(user(ACTOR).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isOk());
        mvc.perform(post("/assignments/v1/templates").with(user(ACTOR).roles("ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"SCHOOL_DEFAULT\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.scope").value("SCHOOL_DEFAULT"));
        role("TEACHER");
        mvc.perform(post("/assignments/v1/templates").with(user(ACTOR).roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON).content("{\"scope\":\"SCHOOL_DEFAULT\"}"))
                .andExpect(status().isForbidden());
    }
    @Test void ownTeacherBindingWorksAndCannotBeClearedByAnotherActor() throws Exception {
        when(bindings.save(any())).thenAnswer(i -> i.getArgument(0));
        mvc.perform(post("/assignments/v1/bindings/teacher-default").with(user(ACTOR).roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teacherEmail\":\"staff@example.test\",\"assignmentTemplateId\":\"own\",\"infractionName\":\"Synthetic\",\"level\":1}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.teacherEmail").value(ACTOR));
        mvc.perform(delete("/assignments/v1/bindings/teacher-default").with(user(ACTOR).roles("TEACHER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"teacherEmail\":\"other@example.test\",\"infractionName\":\"Synthetic\",\"level\":1}"))
                .andExpect(status().isForbidden());
        verify(bindings,times(1)).save(any());
    }
    @Test void punishmentDtoFiltersUnrelatedAndForeignCasesOnServer() throws Exception {
        var related = punishments.findById("case").orElseThrow();
        var ownDto = new PunishmentDTO(); ownDto.setPunishment(related);
        var unrelated = new Punishment(); unrelated.setStudentEmail("other@example.test"); unrelated.setSchool(SCHOOL);
        var otherStudent = new Student(); otherStudent.setStudentEmail("other@example.test"); otherStudent.setSchool(SCHOOL);
        when(students.findByStudentEmailIgnoreCase("other@example.test")).thenReturn(otherStudent);
        var unrelatedDto = new PunishmentDTO(); unrelatedDto.setPunishment(unrelated);
        var foreign = new Punishment(); foreign.setStudentEmail(CHILD); foreign.setSchool("Other school");
        var foreignDto = new PunishmentDTO(); foreignDto.setPunishment(foreign);
        when(dto.getDTOPunishments()).thenReturn(List.of(ownDto,unrelatedDto,foreignDto));
        mvc.perform(get("/DTO/v1/punishmentsDTO").with(user(ACTOR).roles("TEACHER")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.length()").value(1));
    }
    @Test void staffOverviewCannotLeakMismatchedStudentProfiles() throws Exception {
        var row = new TeacherDTO(); row.setStudentEmail(CHILD);
        var teacher = dto.getTeacherOverData(); teacher.setPunishmentResponse(List.of(row));
        var admin = dto.getAdminOverData(); admin.setPunishmentResponse(List.of(row));
        student.setSchool("Other school");
        mvc.perform(get("/DTO/v1/TeacherOverviewData").with(user(ACTOR).roles("TEACHER")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.punishmentResponse.length()").value(0));
        role("ADMIN");
        mvc.perform(get("/DTO/v1/AdminOverviewData").with(user(ACTOR).roles("ADMIN")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.punishmentResponse.length()").value(0));
    }
    @Test void authorizationMatrixRunsWithExpectedEmailGates() {
        boolean expected = getClass().equals(WorkflowSecurityEmailEnabledTest.class);
        for (var name : List.of("reps.email.routing.phase1-enabled", "reps.email.gmail.connection-enabled",
                "reps.email.notifications.enabled", "reps.email.notifications.dispatch-enabled"))
            assertEquals(expected, context.getEnvironment().getProperty(name, Boolean.class), name);
    }
    @Test void overviewFiltersMixedSchoolOrStudentRecords() throws Exception {
        var good = new Punishment(); good.setStudentEmail(CHILD); good.setSchool(SCHOOL);
        var foreignRecord = new Punishment(); foreignRecord.setStudentEmail(CHILD); foreignRecord.setSchool("Other school");
        var unrelated = new Punishment(); unrelated.setStudentEmail("other@example.test"); unrelated.setSchool(SCHOOL);
        var overview = dto.getStudentOverData(CHILD); overview.setPunishments(List.of(good,foreignRecord,unrelated));
        mvc.perform(get("/DTO/v1/StudentOverviewData/"+CHILD).with(user(ACTOR).roles("TEACHER")))
                .andExpect(status().isAccepted()).andExpect(jsonPath("$.punishments.length()").value(1));
    }
}
