package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.punishment.StudentAnswer;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.EmailService;
import com.reps.demogcloud.services.email.EmailTemplateBuilderService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PunishmentClosureServiceImplTest {

    @Mock private PunishRepository punishRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private InfractionRepository infractionRepository;
    @Mock private EmailService emailService;
    @Mock private EmailTemplateBuilderService templateBuilder;

    @InjectMocks
    private PunishmentClosureServiceImpl service;

    // -------------------------
    // closePunishment
    // -------------------------

    @Test
    void closePunishment_shouldReturnPending_whenAnswersExist() throws MessagingException {
        Punishment punishment = basePunishment();
        punishment.setInfractionDescription(new ArrayList<>(List.of("base")));

        Student student = baseStudent();

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "OPEN"))
                .thenReturn(List.of(punishment));

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com"))
                .thenReturn(student);

        List<StudentAnswer> answers = List.of(mock(StudentAnswer.class));

        PunishmentResponse result =
                service.closePunishment("Tardy", "student@test.com", answers);

        assertEquals("PENDING", punishment.getStatus());
        assertEquals(2, punishment.getInfractionDescription().size());

        verify(punishRepository).save(punishment);
        verifyNoInteractions(emailService);
    }

    @Test
    void closePunishment_shouldCloseAndSendEmail_whenNoAnswers() throws MessagingException {
        Punishment punishment = basePunishment();
        Student student = baseStudent();

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "OPEN"))
                .thenReturn(List.of(punishment));

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com"))
                .thenReturn(student);

        when(templateBuilder.buildCompletionMessage(any(), any(), any(), any(), any()))
                .thenReturn("msg");
        when(templateBuilder.buildCompletionSubject(any(), any(), any(), any(), anyBoolean()))
                .thenReturn("sub");

        PunishmentResponse result =
                service.closePunishment("Tardy", "student@test.com", List.of());

        assertEquals("CLOSED", punishment.getStatus());
        assertEquals(1, punishment.getClosedTimes());
        assertNotNull(punishment.getTimeClosed());

        verify(emailService).sendPtsEmail(any(), any(), any(), eq("msg"), eq("sub"), any());
    }

    @Test
    void closePunishment_shouldThrow_whenNoOpenPunishments() {
        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                any(), any(), any()))
                .thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class,
                () -> service.closePunishment("Tardy", "student@test.com", List.of()));
    }

    // -------------------------
    // closeByPunishmentId
    // -------------------------

    @Test
    void closeByPunishmentId_shouldCloseAndSendEmail() throws MessagingException {
        Punishment punishment = basePunishment();
        Student student = baseStudent();
        Infraction infraction = new Infraction();
        infraction.setInfractionName("Tardy");

        when(punishRepository.findByPunishmentId("P1")).thenReturn(punishment);
        when(studentRepository.findByStudentEmailIgnoreCase(any())).thenReturn(student);
        when(infractionRepository.findByInfractionId(any())).thenReturn(infraction);

        when(templateBuilder.buildCompletionMessage(any(), any(), any(), any(), any()))
                .thenReturn("msg");
        when(templateBuilder.buildCompletionSubject(any(), any(), any(), any(), anyBoolean()))
                .thenReturn("sub");

        PunishmentResponse result = service.closeByPunishmentId("P1");

        assertEquals("CLOSED", punishment.getStatus());
        verify(emailService).sendPtsEmail(any(), any(), any(), any(), any(), any());
    }

    @Test
    void closeByPunishmentId_shouldThrow_whenNotFound() {
        when(punishRepository.findByPunishmentId("P1")).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.closeByPunishmentId("P1"));
    }

    // -------------------------
    // rejectLevelThree
    // -------------------------

    @Test
    void rejectLevelThree_shouldResetAndSendEmail() throws MessagingException {
        Punishment punishment = basePunishment();
        punishment.setInfractionDescription(new ArrayList<>(List.of("", "reset", "feedback1")));

        Student student = baseStudent();

        when(punishRepository.findByPunishmentId("P1")).thenReturn(punishment);
        when(studentRepository.findByStudentEmailIgnoreCase(any())).thenReturn(student);

        when(templateBuilder.buildLevelThreeRejectSubject(any(), any(), any()))
                .thenReturn("sub");
        when(templateBuilder.buildLevelThreeRejectMessage(any(), any()))
                .thenReturn("msg");

        Punishment result = service.rejectLevelThree("P1");

        assertEquals("OPEN", result.getStatus());
        assertEquals(0, result.getMapIndex());
        assertEquals(2, result.getInfractionDescription().size());

        verify(emailService).sendPtsEmail(any(), any(), any(), eq("msg"), eq("sub"), any());
    }

    // -------------------------
    // archiveRecord
    // -------------------------

    @Test
    void archiveRecord_shouldArchiveAndSendEmail() throws MessagingException {
        Punishment punishment = basePunishment();
        Student student = baseStudent();
        Infraction infraction = new Infraction();
        infraction.setInfractionLevel("3");

        when(punishRepository.findByPunishmentId("P1")).thenReturn(punishment);
        when(studentRepository.findByStudentEmailIgnoreCase(any())).thenReturn(student);
        when(infractionRepository.findByInfractionId(any())).thenReturn(infraction);

        when(templateBuilder.buildPunishmentDeletedMessage(any(), any(), any(), any(), any(), any()))
                .thenReturn("msg");
        when(templateBuilder.buildPunishmentDeletedSubject(any(), any(), any(), any()))
                .thenReturn("sub");

        Punishment result = service.archiveRecord("P1", "admin", "reason");

        assertTrue(result.isArchived());
        assertEquals("admin", result.getArchivedBy());

        verify(emailService).sendPtsEmail(any(), any(), any(), eq("msg"), eq("sub"), any());
    }

    // -------------------------
    // restoreRecord
    // -------------------------

    @Test
    void restoreRecord_shouldRestoreAndSendEmail() throws MessagingException {
        Punishment punishment = basePunishment();
        punishment.setArchived(true);

        Student student = baseStudent();

        when(punishRepository.findByPunishmentIdAndArchived("P1", true)).thenReturn(punishment);
        when(studentRepository.findByStudentEmailIgnoreCase(any())).thenReturn(student);

        when(templateBuilder.buildPunishmentRestoredSubject(any(), any(), any(), any()))
                .thenReturn("sub");
        when(templateBuilder.buildPunishmentRestoredMessage(any(), any(), any()))
                .thenReturn("msg");

        Punishment result = service.restoreRecord("P1");

        assertFalse(result.isArchived());
        assertNull(result.getArchivedBy());

        verify(emailService).sendPtsEmail(any(), any(), any(), eq("msg"), eq("sub"), any());
    }

    @Test
    void restoreRecord_shouldThrow_whenNotFound() {
        when(punishRepository.findByPunishmentIdAndArchived("P1", true)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class,
                () -> service.restoreRecord("P1"));
    }

    // -------------------------
    // helpers
    // -------------------------

    private Punishment basePunishment() {
        Punishment p = new Punishment();
        p.setPunishmentId("P1");
        p.setStudentEmail("student@test.com");
        p.setTeacherEmail("teacher@test.com");
        p.setInfractionName("Tardy");
        p.setInfractionId("I1");
        p.setClosedTimes(0);
        p.setInfractionDescription(new ArrayList<>(List.of("base")));
        p.setAnswerHistory(new HashMap<>());
        return p;
    }

    private Student baseStudent() {
        Student s = new Student();
        s.setStudentEmail("student@test.com");
        s.setFirstName("John");
        s.setLastName("Doe");
        s.setPreferredLanguage("en");
        s.setParentEmail("parent@test.com");
        s.setSchool("Test School");
        return s;
    }
}
