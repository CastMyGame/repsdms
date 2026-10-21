package com.reps.demogcloud.services.email;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.UserContextService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailNotificationServiceTest {

    @Mock
    private EmailSenderService emailSenderService;

    @Mock
    private EmailTemplateBuilderService templateBuilderService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private UserContextService userContextService;

    @InjectMocks
    private EmailNotificationService service;

    private Student managedStudent() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setParentEmail("parent@test.com");
        student.setSchool("School");
        when(emailSenderService.isPhase1RoutingEnabled()).thenReturn(true);
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        return student;
    }

    @Test
    void administratorClosureUsesAdministratorWhileOriginalTeacherRemainsParticipant() throws Exception {
        Student student = managedStudent();
        student.setSpotters(List.of("former@test.com"));
        when(userContextService.getCurrentStaffEmail()).thenReturn("admin@test.com");
        when(userContextService.getVerifiedStaffEmail("teacher@test.com")).thenReturn("teacher@test.com");
        doNothing().when(userContextService).requireStaffInStudentSchool("teacher@test.com", student);
        doThrow(new org.springframework.security.access.AccessDeniedException("Former staff"))
                .when(userContextService).requireStaffInStudentSchool("former@test.com", student);
        service.sendPtsEmail("parent@test.com", "teacher@test.com", "student@test.com", "Closed", "Subject", "en");
        verify(userContextService).requireCaseCommunicationAccess("teacher@test.com", student);
        verify(emailSenderService).sendStaffBulkEmail("parent@test.com", List.of("teacher@test.com", "student@test.com"),
                "Subject", "Closed", List.of(), "admin@test.com");
        verify(emailSenderService, never()).sendSystemBulkEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void teacherCreationUsesTeacherAsActor() throws Exception {
        managedStudent();
        when(userContextService.getCurrentStaffEmail()).thenReturn("teacher@test.com");
        when(userContextService.getVerifiedStaffEmail("teacher@test.com")).thenReturn("teacher@test.com");
        service.sendPtsEmail("parent@test.com", "teacher@test.com", "student@test.com", "Created", "Subject", "en");
        verify(emailSenderService).sendStaffBulkEmail("parent@test.com", List.of("teacher@test.com", "student@test.com"),
                "Subject", "Created", List.of(), "teacher@test.com");
    }

    @Test
    void forgedFamilyRecipientCannotSend() throws Exception {
        managedStudent();
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () ->
                service.sendPtsEmail("unrelated@test.com", "teacher@test.com", "student@test.com", "Body", "Subject", "en"));
        verify(emailSenderService, never()).sendStaffBulkEmail(any(), any(), any(), any(), any(), any());
        verifyNoInteractions(userContextService);
    }

    @Test
    void studentCompletionNeverUsesStudentsGmail() throws Exception {
        managedStudent();
        when(userContextService.isCurrentUserStudent()).thenReturn(true);
        when(userContextService.getCurrentUserEmail()).thenReturn("student@test.com");
        when(userContextService.getVerifiedStaffEmail("teacher@test.com")).thenReturn("teacher@test.com");
        service.sendPtsEmail("parent@test.com", "teacher@test.com", "student@test.com", "Body", "Subject", "en");
        verify(emailSenderService).sendSystemBulkEmail("parent@test.com", List.of("teacher@test.com", "student@test.com"),
                "Subject", "Body", List.of(), "teacher@test.com");
        verify(userContextService, never()).getCurrentStaffEmail();
    }

    @Test
    void backgroundAlertIsExplicitlySystemEvenWithAuthenticatedAdministrator() throws Exception {
        managedStudent();
        Punishment punishment = new Punishment();
        punishment.setStudentEmail("student@test.com");
        punishment.setTeacherEmail("teacher@test.com");
        when(userContextService.getVerifiedStaffEmail("teacher@test.com")).thenReturn("teacher@test.com");
        service.sendAlertEmail("ISS", punishment, "en");
        verify(emailSenderService).sendSystemBulkEmail(eq("parent@test.com"), any(), eq("ISS REMINDER"), any(), any(), eq("teacher@test.com"));
        verify(userContextService, never()).getCurrentStaffEmail();
        verify(userContextService, never()).isCurrentUserStudent();
    }

    @Test
    void announcementCannotBorrowAnotherTeachersIdentity() {
        when(emailSenderService.isPhase1RoutingEnabled()).thenReturn(true);
        when(userContextService.getCurrentStaffEmail()).thenReturn("admin@test.com");
        ClassAnnouncementRequest request = new ClassAnnouncementRequest();
        request.setTeacherEmail("teacher@test.com");
        assertThrows(org.springframework.security.access.AccessDeniedException.class, () -> service.sendClassAnnouncement(request));
        verifyNoInteractions(employeeRepository, studentRepository);
    }

    @Test
    void sendPtsEmail_shouldSendBulkEmail() throws Exception {
        Student student = new Student();
        student.setSpotters(List.of("spot1@test.com"));

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com"))
                .thenReturn(student);

        service.sendPtsEmail("parent", "teacher", "student@test.com", "msg", "sub", "en");

        verify(emailSenderService).sendBulkEmail(
                eq("parent"),
                eq(List.of("teacher", "student@test.com")),
                eq("sub"),
                eq("msg"),
                eq(List.of("spot1@test.com")),
                any(),
                eq("en")
        );
    }

    @Test
    void sendPtsEmail_shouldThrow_whenStudentNotFound() {
        when(studentRepository.findByStudentEmailIgnoreCase(any()))
                .thenReturn(null);

        assertThrows(
                IllegalArgumentException.class,
                () -> service.sendPtsEmail("p", "t", "s", "m", "sub", "en")
        );
    }

    @Test
    void sendClassAnnouncement_shouldGroupByLanguageAndSend() throws Exception {
        Employee teacher = new Employee();
        Employee.ClassRoster roster = new Employee.ClassRoster();
        roster.setClassName("Math");
        roster.setClassRoster(List.of("s1@test.com", "s2@test.com"));
        teacher.setClasses(List.of(roster));

        when(employeeRepository.findByEmailIgnoreCase("teacher@test.com")).thenReturn(teacher);

        Student s1 = new Student();
        s1.setPreferredLanguage("en");

        Student s2 = new Student();
        s2.setPreferredLanguage("es");

        when(studentRepository.findByStudentEmailIgnoreCase("s1@test.com")).thenReturn(s1);
        when(studentRepository.findByStudentEmailIgnoreCase("s2@test.com")).thenReturn(s2);

        when(templateBuilderService.normalizeLanguage("en")).thenReturn("en");
        when(templateBuilderService.normalizeLanguage("es")).thenReturn("es");
        when(templateBuilderService.translateUserInput(any(), eq("es"))).thenReturn("translated");

        ClassAnnouncementRequest req = new ClassAnnouncementRequest();
        req.setTeacherEmail("teacher@test.com");
        req.setClassName("Math");
        req.setSubject("Subject");
        req.setMsg("Message");

        service.sendClassAnnouncement(req);

        verify(emailSenderService, times(1))
                .sendClassAnnouncement("teacher@test.com", List.of("s1@test.com"), "Subject", "Message");

        verify(emailSenderService, times(1))
                .sendClassAnnouncement("teacher@test.com", List.of("s2@test.com"), "translated", "translated");
    }

    @Test
    void sendClassAnnouncement_shouldReturn_whenNoClass() throws Exception {
        Employee teacher = new Employee();
        teacher.setClasses(List.of());

        when(employeeRepository.findByEmailIgnoreCase(any())).thenReturn(teacher);

        ClassAnnouncementRequest req = new ClassAnnouncementRequest();
        req.setTeacherEmail("t");
        req.setClassName("none");

        service.sendClassAnnouncement(req);

        verify(emailSenderService, never()).sendClassAnnouncement(any(), any(), any(), any());
    }

    @Test
    void sendAlertEmail_shouldCallSendPtsEmail() throws Exception {
        Student student = new Student();
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setParentEmail("parent@test.com");
        student.setStudentEmail("student@test.com");

        Punishment punishment = new Punishment();
        punishment.setStudentEmail("student@test.com");
        punishment.setTeacherEmail("teacher@test.com");

        when(studentRepository.findByStudentEmailIgnoreCase(any()))
                .thenReturn(student);

        EmailNotificationService spy = Mockito.spy(service);
        doNothing().when(spy).sendPtsEmail(any(), any(), any(), any(), any(), any());

        spy.sendAlertEmail("ISS", punishment, "en");

        verify(spy).sendPtsEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void notifyParentViaTextAndEmail_shouldBuildAndSend() throws Exception {
        Student student = new Student();
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setPreferredLanguage("en");

        Infraction infraction = new Infraction();
        infraction.setInfractionLevel("1");
        infraction.setInfractionName("Tardy");

        Punishment punishment = new Punishment();
        punishment.setInfractionDescription(new ArrayList<>(List.of("desc")));

        PunishmentResponse response = new PunishmentResponse();
        response.setParentToEmail("p");
        response.setTeacherToEmail("t");
        response.setStudentToEmail("s");
        response.setSubject("sub");

        when(templateBuilderService.createEmailText(any(), any(), any(), any(), any(), any(), any()))
                .thenReturn("builtMessage");
        when(templateBuilderService.replaceString("desc")).thenReturn("desc");

        EmailNotificationService spy = Mockito.spy(service);
        doNothing().when(spy).sendPtsEmail(any(), any(), any(), any(), any(), any());

        spy.notifyParentViaTextAndEmail(punishment, student, infraction, response);

        assertEquals("builtMessage", response.getMessage());
        verify(spy).sendPtsEmail("p", "t", "s", "builtMessage", "sub", "en");
    }
}
