package com.reps.demogcloud.services.email;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.email.ClassAnnouncementRequest;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.student.Student;
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

    @InjectMocks
    private EmailNotificationService service;

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

        verifyNoInteractions(emailSenderService);
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
        verify(spy).sendPtsEmail(any(), any(), any(), any(), any(), any());
    }
}
