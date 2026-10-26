package com.reps.demogcloud.services;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.GlobalExceptionHandler;
import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.*;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.security.models.*;
import com.reps.demogcloud.security.services.*;
import com.reps.demogcloud.security.utils.JwtUtils;
import com.reps.demogcloud.services.email.*;
import com.reps.demogcloud.services.punishment.PunishmentCreationService;
import com.reps.demogcloud.utils.PunishmentUtils;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.bson.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.convert.*;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Mongo mapping and application services; persistence and all transports are mocked. */
class LegacyStaffIdentityTest {
    static final String EMAIL = "teacher@example.test";
    final UserRepository users = mock(UserRepository.class);
    final EmployeeRepository employees = mock(EmployeeRepository.class);
    final StudentRepository students = mock(StudentRepository.class);
    final UserContextService identity = new UserContextService(users, students, employees);
    MappingMongoConverter converter;
    Document legacy;
    UserModel user;
    Employee employee;

    @BeforeEach void setup() throws Exception {
        var context = new MongoMappingContext();
        var conversions = MongoCustomConversions.create(adapter -> {});
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder()); context.afterPropertiesSet();
        converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions); converter.afterPropertiesSet();
        legacy = new Document("username", EMAIL).append("password", "synthetic-hash")
                .append("schoolName", "Testing").append("roles", List.of(new Document("role", "TEACHER")));
        user = converter.read(UserModel.class, legacy);
        employee = new Employee(); employee.setEmail(EMAIL); employee.setSchool("Testing");
        bind();
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(EMAIL, "unused", "ROLE_TEACHER"));
    }
    void bind() {
        when(users.findByUsername(EMAIL)).thenReturn(user);
        when(employees.findByEmailIgnoreCase(EMAIL)).thenReturn(employee);
    }
    @AfterEach void clear() { SecurityContextHolder.clearContext(); RequestContextHolder.resetRequestAttributes(); }

    @Test void legacyMongoShapeRetainsEnabledDefaultAndSchoolAcrossMapping() {
        assertFalse(legacy.containsKey("enabled"));
        assertTrue(user.isEnabled()); assertEquals("Testing", user.getSchool());
        assertEquals("TEACHER", user.getRoles().iterator().next().getRole());
        assertEquals(EMAIL, identity.getCurrentStaffEmail());
        var stored = new Document(); converter.write(user, stored);
        assertEquals("Testing", stored.getString("schoolName"));
        assertEquals("Testing", converter.read(UserModel.class, stored).getSchool());
    }
    @Test void canonicalSchoolRemainsAuthoritativeAndCannotBeMaskedByLegacyAlias() {
        legacy.put("school", "Other school"); user = converter.read(UserModel.class, legacy); bind();
        assertEquals("Other school", user.getSchool());
        assertThrows(AccessDeniedException.class, identity::getCurrentStaffEmail);
        user.setSchool("Testing"); assertEquals(EMAIL, identity.getCurrentStaffEmail());
        var canonical = new Document(); converter.write(user, canonical);
        assertEquals("Testing", canonical.getString("school"));
    }
    @ParameterizedTest
    @ValueSource(strings = {"missing-user", "missing-employee", "disabled", "archived", "student", "missing-role", "email", "school", "missing-school", "missing-user-school", "principal"})
    void invalidIdentityOrMembershipIsRejected(String invalid) {
        switch (invalid) {
            case "missing-user" -> user = null;
            case "missing-employee" -> employee = null;
            case "disabled" -> user.setEnabled(false);
            case "archived" -> employee.setArchived(true);
            case "student" -> user.setRoles(Set.of(new RoleModel(null, "STUDENT")));
            case "missing-role" -> user.setRoles(null);
            case "email" -> employee.setEmail("other@example.test");
            case "school" -> employee.setSchool("Other school");
            case "missing-school" -> employee.setSchool(null);
            case "missing-user-school" -> { legacy.remove("schoolName"); user = converter.read(UserModel.class, legacy); }
            case "principal" -> SecurityContextHolder.getContext().setAuthentication(
                    new TestingAuthenticationToken("attacker@example.test", "unused", "ROLE_TEACHER"));
        }
        bind(); assertThrows(AccessDeniedException.class, identity::getCurrentStaffEmail);
    }

    @ParameterizedTest @ValueSource(strings = {"PASSWORD", "GOOGLE"})
    void legacyTeacherReachesAtomicCaptureAndCorrectMockedTransport(String loginMethod) throws Exception {
        var details = new CustomUserDetailsService(users);
        var jwt = com.reps.demogcloud.security.utils.JwtTestSupport.jwt();
        var request = new MockHttpServletRequest("POST", "/punish/v1/startPunish/formList");
        request.addHeader("Authorization", "Bearer " + jwt.generateToken(details.loadUserByUsername(EMAIL), loginMethod));
        request.addHeader("X-REPS-Action-Id", UUID.randomUUID().toString());
        new JwtFilterRequest(jwt, details).doFilter(request, new MockHttpServletResponse(), new MockFilterChain());
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        assertEquals(EMAIL, SecurityContextHolder.getContext().getAuthentication().getName());

        var env = new MockEnvironment().withProperty("reps.email.routing.phase1-enabled", "true")
                .withProperty("reps.email.notifications.enabled", "true")
                .withProperty("mail.service.provider", "hybrid").withProperty("mail.service.user-emails.enabled", "true");
        var smtp = mock(JavaMailSender.class); var gmail = mock(GoogleMailService.class);
        var tokens = mock(GoogleOAuthTokenStore.class);
        var router = new EmailRoutingService(env, smtp, gmail, tokens, identity);
        var mongo = mock(MongoTemplate.class);
        var templates = mock(EmailTemplateBuilderService.class, invocation ->
                invocation.getMethod().getReturnType() == String.class ? "<p>Synthetic only</p>" : RETURNS_DEFAULTS.answer(invocation));
        var notifications = new RequiredNotificationService(mongo, env, students, identity, router, templates);
        var student = new Student(); student.setStudentEmail("student@example.test");
        student.setParentEmail("guardian@example.test"); student.setSchool("Testing");
        when(students.findByStudentEmailIgnoreCase(student.getStudentEmail())).thenReturn(student);
        var schools = mock(SchoolRepository.class); var school = new School();
        school.setMaxPunishLevel(4); when(schools.findBySchoolNameIgnoreCase("Testing")).thenReturn(Optional.of(school));
        var infractions = mock(InfractionRepository.class); var infraction = new Infraction();
        infraction.setInfractionName("Talking"); infraction.setInfractionLevel("1");
        when(infractions.findByInfractionNameAndInfractionLevel("Talking", "1")).thenReturn(infraction);
        var legacyEmail = mock(EmailService.class);
        var utils = new PunishmentUtils(students,mock(org.springframework.data.mongodb.core.MongoTemplate.class), infractions, mock(PunishRepository.class), schools,
                legacyEmail, mock(EmployeeService.class), mock(GuidanceService.class), mock(OfficeReferralService.class), notifications);
        var assignments = mock(AssignmentService.class); var template = new AssignmentTemplate(); template.setId("synthetic-template");
        when(assignments.resolveTemplateForInfraction(anyString(), anyString(), anyString(), anyInt())).thenReturn(template);
        var sender = mock(EmailSenderService.class); when(sender.isPhase1RoutingEnabled()).thenReturn(true);
        var creation = new PunishmentCreationService(utils, assignments, identity, sender, notifications, mock(com.reps.demogcloud.services.currency.CurrencyService.class));
        var persisted = new AtomicReference<Document>();
        when(mongo.insert(any(Punishment.class))).thenAnswer(invocation -> {
            Punishment punishment = invocation.getArgument(0); var doc = new Document(); converter.write(punishment, doc);
            persisted.set(doc); return punishment;
        });
        var form = new PunishmentFormRequest(); form.setStudentEmail(student.getStudentEmail());
        form.setTeacherEmail("spoofed@example.test"); form.setInfractionName("Talking"); form.setInfractionDescription("Synthetic only");
        var punishment = creation.createNewPunishForm(form).getPunishment();
        assertEquals(EMAIL, punishment.getTeacherEmail()); assertEquals(EMAIL, form.getTeacherEmail());
        assertNotNull(persisted.get());
        var intent = punishment.getRequiredNotifications().get(0);
        assertEquals(EMAIL, intent.getActor()); assertEquals("PENDING", intent.getState());
        assertEquals("PENDING", persisted.get().getList("requiredNotifications", Document.class).get(0).getString("state"));
        verifyNoInteractions(smtp, gmail, tokens, legacyEmail);

        boolean connected = loginMethod.equals("GOOGLE");
        when(tokens.getConnectionState(EMAIL)).thenReturn(connected ? GoogleOAuthTokenStore.ConnectionState.CONNECTED : GoogleOAuthTokenStore.ConnectionState.NEVER_CONNECTED);
        var mime = new MimeMessage(Session.getInstance(new Properties()));
        if (connected) {
            when(tokens.hasUsableGmailSendToken(EMAIL)).thenReturn(true);
            when(gmail.sendWithReceipt(eq(EMAIL), any())).thenReturn(Map.of("provider", "GMAIL"));
        } else when(smtp.createMimeMessage()).thenReturn(mime);
        var receipt = router.sendRequiredNotification("guardian@example.test", List.of(), "Synthetic only", "<p>Synthetic only</p>",
                List.of(), intent.getActor(), false, intent.getRfcMessageId(), null, null);
        assertEquals(connected ? "GMAIL" : "SMTP", receipt.get("provider"));
        if (connected) verifyNoInteractions(smtp);
        else { verify(smtp).send(mime); assertEquals(EMAIL, mime.getReplyTo()[0].toString()); verifyNoInteractions(gmail); }
    }

    @RestController static class DeniedController {
        @GetMapping("/denied") void denied() { throw new AccessDeniedException("Private staff/school details"); }
    }
    @Test void authorizationFailureReturnsGeneric403WithoutInternalDetails() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new DeniedController()).setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(get("/denied")).andExpect(status().isForbidden()).andExpect(content().string("Access denied"));
    }
}
