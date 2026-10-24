package com.reps.demogcloud.security.config;

import com.reps.demogcloud.controllers.EmailController;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.security.models.RoleModel;
import com.reps.demogcloud.services.EmailService;
import com.reps.demogcloud.services.email.*;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Actual production chain, user context and communication orchestration; mail and repositories mocked. */
@Import({EmailController.class,EmailService.class,EmailNotificationService.class})
class EmailCommunicationSecurityTest extends WorkflowSecurityTest {
    @MockitoBean EmailSenderService mail;
    @MockitoBean EmailTemplateBuilderService emailTemplates;
    @MockitoBean EmailReferralService referralEmails;
    @MockitoBean RequiredNotificationService notifications;
    @BeforeEach void classRecipients() {
        var first=new Employee.ClassRoster();first.setClassName("Math");first.setClassPeriod("1");first.setClassRoster(List.of(CHILD));
        var second=new Employee.ClassRoster();second.setClassName("Math");second.setClassPeriod("2");second.setClassRoster(List.of(CHILD));
        employee.setClasses(List.of(first,second));student.setPreferredLanguage("en");
        when(emailTemplates.normalizeLanguage(any())).thenReturn("en");
    }
    String body(String period) { return "[{\"teacherEmail\":\""+ACTOR+"\",\"className\":\"Math\","+(period==null?"":"\"classPeriod\":\""+period+"\",")+"\"subject\":\"School reminder\",\"msg\":\"Bring <book> & notes\"}]"; }
    @Test void anonymousCannotSendAnnouncement() throws Exception {
        mvc.perform(post("/email/v1/classAnnouncement").contentType(MediaType.APPLICATION_JSON).content(body("2"))).andExpect(status().isUnauthorized());
        verifyNoInteractions(mail);
    }
    @Test void persistedStudentCannotSendEvenWithForgedTeacherAuthority() throws Exception {
        account.setRoles(Set.of(new RoleModel(null,"STUDENT")));
        mvc.perform(post("/email/v1/classAnnouncement").with(user(ACTOR).roles("TEACHER")).contentType(MediaType.APPLICATION_JSON).content(body("2"))).andExpect(status().isForbidden());
        verifyNoInteractions(mail);
    }
    @Test void crossSchoolRosterCannotLeakMailEvenWithLegacyRouting() throws Exception {
        student.setSchool("Other school");
        mvc.perform(post("/email/v1/classAnnouncement").with(user(ACTOR).roles("TEACHER")).contentType(MediaType.APPLICATION_JSON).content(body("2"))).andExpect(status().isForbidden());
        verifyNoInteractions(mail);
    }
    @Test void duplicateNamesRequirePeriodRatherThanSelectingFirstRoster() throws Exception {
        mvc.perform(post("/email/v1/classAnnouncement").with(user(ACTOR).roles("TEACHER")).contentType(MediaType.APPLICATION_JSON).content(body(null))).andExpect(status().isConflict());
        verifyNoInteractions(mail);
    }
    @Test void authorizedExactClassEscapesPlainTextAndUsesOnlyItsRoster() throws Exception {
        employee.getClasses().get(0).setClassRoster(List.of("not-selected@example.test"));
        mvc.perform(post("/email/v1/classAnnouncement").with(user(ACTOR).roles("TEACHER")).contentType(MediaType.APPLICATION_JSON).content(body("2"))).andExpect(status().isOk());
        verify(mail).sendClassAnnouncement(eq(ACTOR),eq(List.of(CHILD)),eq("School reminder"),argThat(body->body.contains("Bring &lt;book&gt; &amp; notes") && !body.contains("Bring <book>")));
        verify(students,never()).findByStudentEmailIgnoreCase("not-selected@example.test");
    }
}
