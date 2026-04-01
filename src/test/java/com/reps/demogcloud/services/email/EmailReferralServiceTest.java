package com.reps.demogcloud.services.email;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.EmailService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EmailReferralServiceTest {

    @Mock
    private EmailTemplateBuilderService emailTemplateBuilderService;

    @Mock
    private PunishRepository punishRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private InfractionRepository infractionRepository;

    @Mock
    private SchoolRepository schoolRepository;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private EmailReferralService emailReferralService;

    @Test
    void sendEmailBasedOnType_shouldSendReferralEmail_whenClosedTimesEqualsMaxLevel() throws MessagingException {
        PunishmentFormRequest formRequest = new PunishmentFormRequest();

        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setParentEmail("parent@test.com");
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setSchool("Burke High");
        student.setPreferredLanguage("en");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("INF-1");
        infraction.setInfractionName("Tardy");
        infraction.setInfractionLevel("3");

        Punishment punishment = new Punishment();
        punishment.setPunishmentId("P-1");
        punishment.setStudentEmail("student@test.com");
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionId("INF-1");
        punishment.setClosedTimes(3);

        School school = new School();
        school.setSchoolName("Burke High");
        school.setMaxPunishLevel(3);

        Punishment history1 = new Punishment();
        history1.setTimeCreated(LocalDate.of(2026, 1, 10));
        history1.setTimeClosed(LocalDate.of(2026, 1, 11));
        history1.setInfractionDescription(new ArrayList<>(List.of("Late to class")));

        Punishment history2 = new Punishment();
        history2.setTimeCreated(LocalDate.of(2026, 1, 12));
        history2.setTimeClosed(LocalDate.of(2026, 1, 13));
        history2.setInfractionDescription(new ArrayList<>(List.of("Skipped class")));

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(infractionRepository.findByInfractionId("INF-1")).thenReturn(infraction);
        when(schoolRepository.findBySchoolNameIgnoreCase("Burke High")).thenReturn(Optional.of(school));
        when(punishRepository.save(punishment)).thenReturn(punishment);

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndArchived(
                "student@test.com", "Tardy", "CLOSED", false))
                .thenReturn(List.of(history1));
        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndArchived(
                "student@test.com", "Tardy", "REFERRAL", false))
                .thenReturn(List.of(history2));
        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionIdAndStatusAndArchived(
                "student@test.com", "Tardy", "CFR", false))
                .thenReturn(List.of());

        when(emailTemplateBuilderService.translateUserInput(String.valueOf(history1.getInfractionDescription()), "en"))
                .thenReturn(String.valueOf(history1.getInfractionDescription()));
        when(emailTemplateBuilderService.translateUserInput(String.valueOf(history2.getInfractionDescription()), "en"))
                .thenReturn(String.valueOf(history2.getInfractionDescription()));
        when(emailTemplateBuilderService.replaceString(anyString())).thenAnswer(inv -> inv.getArgument(0));
        when(emailTemplateBuilderService.buildSubject("Burke High", "John", "Doe", "en", true))
                .thenReturn("Office Referral Subject");
        when(emailTemplateBuilderService.buildOfficeReferralMessage(
                eq("John"), eq("Doe"), eq("Tardy"), anyString(), eq("en")))
                .thenReturn("Office Referral Message");

        PunishmentResponse result = emailReferralService.sendEmailBasedOnType(formRequest, punishment, emailService);

        assertNotNull(result);
        assertEquals("REFERRAL", punishment.getStatus());
        assertNotNull(punishment.getTimeClosed());
        assertEquals("Office Referral Subject", result.getSubject());
        assertEquals("Office Referral Message", result.getMessage());
        assertEquals("parent@test.com", result.getParentToEmail());
        assertEquals("student@test.com", result.getStudentToEmail());
        assertEquals("teacher@test.com", result.getTeacherToEmail());

        verify(punishRepository, times(1)).save(punishment);
        verify(emailService, times(1))
                .sendEmail("teacher@test.com", "Office Referral Subject", "Office Referral Message", "en");
        verify(emailService, never()).sendPtsEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendEmailBasedOnType_shouldSendInfractionNotification_whenClosedTimesNotAtMaxLevel() throws MessagingException {
        PunishmentFormRequest formRequest = new PunishmentFormRequest();

        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setParentEmail("parent@test.com");
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setSchool("Burke High");
        student.setPreferredLanguage("en");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("INF-1");
        infraction.setInfractionName("Tardy");
        infraction.setInfractionLevel("2");

        Punishment punishment = new Punishment();
        punishment.setPunishmentId("P-1");
        punishment.setStudentEmail("student@test.com");
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionId("INF-1");
        punishment.setClosedTimes(1);
        punishment.setInfractionDescription(new ArrayList<>(List.of("Talking during lesson")));

        School school = new School();
        school.setSchoolName("Burke High");
        school.setMaxPunishLevel(3);

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(infractionRepository.findByInfractionId("INF-1")).thenReturn(infraction);
        when(schoolRepository.findBySchoolNameIgnoreCase("Burke High")).thenReturn(Optional.of(school));
        when(emailTemplateBuilderService.replaceString("Talking during lesson")).thenReturn("Talking during lesson");
        when(emailTemplateBuilderService.createEmailText(
                "John", "Doe", "2", "Tardy", "Talking during lesson", "student@test.com", "en"))
                .thenReturn("Infraction Message");
        when(emailTemplateBuilderService.buildSubject("Burke High", "John", "Doe", "en", false))
                .thenReturn("Infraction Subject");

        PunishmentResponse result = emailReferralService.sendEmailBasedOnType(formRequest, punishment, emailService);

        assertNotNull(result);
        assertEquals("Infraction Message", result.getMessage());
        assertEquals("Infraction Subject", result.getSubject());
        assertEquals("parent@test.com", result.getParentToEmail());
        assertEquals("student@test.com", result.getStudentToEmail());
        assertEquals("teacher@test.com", result.getTeacherToEmail());

        verify(emailService, times(1)).sendPtsEmail(
                "parent@test.com",
                "teacher@test.com",
                "student@test.com",
                "Infraction Message",
                "Infraction Subject",
                "en"
        );
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
        verify(punishRepository, never()).save(any(Punishment.class));
    }

    @Test
    void sendEmailBasedOnType_shouldThrow_whenSchoolNotFound() throws MessagingException {
        PunishmentFormRequest formRequest = new PunishmentFormRequest();

        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setSchool("Missing School");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("INF-1");

        Punishment punishment = new Punishment();
        punishment.setStudentEmail("student@test.com");
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionId("INF-1");

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(infractionRepository.findByInfractionId("INF-1")).thenReturn(infraction);
        when(schoolRepository.findBySchoolNameIgnoreCase("Missing School")).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> emailReferralService.sendEmailBasedOnType(formRequest, punishment, emailService)
        );

        assertEquals("School not found: Missing School", exception.getMessage());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
        verify(emailService, never()).sendPtsEmail(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void sendInfractionNotification_shouldUseBlankDescription_whenDescriptionsAreNull() throws MessagingException {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setParentEmail("parent@test.com");
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setSchool("Burke High");
        student.setPreferredLanguage("en");

        Infraction infraction = new Infraction();
        infraction.setInfractionName("Tardy");
        infraction.setInfractionLevel("1");

        Punishment punishment = new Punishment();
        punishment.setStudentEmail("student@test.com");
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionDescription(null);

        PunishmentResponse response = new PunishmentResponse();
        response.setParentToEmail("parent@test.com");
        response.setStudentToEmail("student@test.com");
        response.setTeacherToEmail("teacher@test.com");

        when(emailTemplateBuilderService.replaceString("")).thenReturn("");
        when(emailTemplateBuilderService.createEmailText(
                "John", "Doe", "1", "Tardy", "", "student@test.com", "en"))
                .thenReturn("Message");
        when(emailTemplateBuilderService.buildSubject("Burke High", "John", "Doe", "en", false))
                .thenReturn("Subject");

        assertDoesNotThrow(() ->
                emailReferralService.sendInfractionNotification(punishment, emailService, student, infraction, response)
        );

        assertEquals("Message", response.getMessage());
        assertEquals("Subject", response.getSubject());

        verify(emailService, times(1)).sendPtsEmail(
                "parent@test.com", "teacher@test.com", "student@test.com", "Message", "Subject", "en"
        );
    }

    @Test
    void sendCFREmailBasedOnType_shouldBuildResponseAndSetTimeClosed() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setParentEmail("parent@test.com");
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setSchool("Burke High");
        student.setPreferredLanguage("en");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("INF-1");
        infraction.setInfractionName("Tardy");

        Punishment punishment = new Punishment();
        punishment.setPunishmentId("P-1");
        punishment.setStudentEmail("student@test.com");
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionId("INF-1");

        School school = new School();
        school.setSchoolName("Burke High");

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(infractionRepository.findByInfractionId("INF-1")).thenReturn(infraction);
        when(schoolRepository.findBySchoolNameIgnoreCase("Burke High")).thenReturn(Optional.of(school));
        when(emailTemplateBuilderService.buildSubject("Burke High", "John", "Doe", "en", false))
                .thenReturn("CFR Subject");
        when(emailTemplateBuilderService.createCFRMessage(
                "John", "Doe", "Tardy", "teacher@test.com", "student@test.com", "en"))
                .thenReturn("CFR Message");

        PunishmentResponse result = emailReferralService.sendCFREmailBasedOnType(punishment);

        assertNotNull(result);
        assertEquals("CFR Subject", result.getSubject());
        assertEquals("CFR Message", result.getMessage());
        assertEquals("parent@test.com", result.getParentToEmail());
        assertEquals("student@test.com", result.getStudentToEmail());
        assertEquals("teacher@test.com", result.getTeacherToEmail());
        assertNotNull(punishment.getTimeClosed());
    }

    @Test
    void sendCFREmailBasedOnType_shouldThrow_whenSchoolNotFound() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setSchool("Missing School");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("INF-1");

        Punishment punishment = new Punishment();
        punishment.setStudentEmail("student@test.com");
        punishment.setTeacherEmail("teacher@test.com");
        punishment.setInfractionId("INF-1");

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(infractionRepository.findByInfractionId("INF-1")).thenReturn(infraction);
        when(schoolRepository.findBySchoolNameIgnoreCase("Missing School")).thenReturn(Optional.empty());

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> emailReferralService.sendCFREmailBasedOnType(punishment)
        );

        assertEquals("School not found: Missing School", exception.getMessage());
    }
}