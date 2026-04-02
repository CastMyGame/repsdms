package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.punishment.StudentAnswer;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.EmailService;
import com.reps.demogcloud.services.EmployeeService;
import com.reps.demogcloud.services.GuidanceService;
import com.reps.demogcloud.services.OfficeReferralService;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PunishmentUtilsTest {

    @Mock
    private StudentRepository studentRepository;
    @Mock
    private InfractionRepository infractionRepository;
    @Mock
    private PunishRepository punishRepository;
    @Mock
    private SchoolRepository schoolRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private EmployeeService employeeService;
    @Mock
    private GuidanceService guidanceService;
    @Mock
    private OfficeReferralService officeReferralService;

    @InjectMocks
    private PunishmentUtils punishmentUtils;

    @Test
    void validateFormRequest_shouldThrow_whenRequestIsNull() {
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> punishmentUtils.validateFormRequest(null)
        );

        assertEquals("Infraction description is required.", ex.getMessage());
    }

    @Test
    void validateFormRequest_shouldThrow_whenDescriptionIsNull() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setInfractionDescription(null);

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> punishmentUtils.validateFormRequest(request)
        );

        assertEquals("Infraction description is required.", ex.getMessage());
    }

    @Test
    void validateFormRequest_shouldThrow_whenDescriptionIsBlank() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setInfractionDescription("   ");

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> punishmentUtils.validateFormRequest(request)
        );

        assertEquals("Infraction description is required.", ex.getMessage());
    }

    @Test
    void validateFormRequest_shouldPass_whenDescriptionIsPresent() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setInfractionDescription("Disruptive behavior");

        assertDoesNotThrow(() -> punishmentUtils.validateFormRequest(request));
    }

    @Test
    void fetchStudent_shouldReturnStudent() {
        Student student = new Student();
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);

        Student result = punishmentUtils.fetchStudent("student@test.com");

        assertSame(student, result);
    }

    @Test
    void fetchSchool_shouldReturnOptionalSchool() {
        School school = new School();
        when(schoolRepository.findBySchoolNameIgnoreCase("Test School")).thenReturn(Optional.of(school));

        Optional<School> result = punishmentUtils.fetchSchool("Test School");

        assertTrue(result.isPresent());
        assertSame(school, result.get());
    }

    @Test
    void getClosedLevel_shouldReturnOne_whenNoClosedPunishmentsExist() {
        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "CLOSED"
        )).thenReturn(List.of());

        String result = punishmentUtils.getClosedLevel("student@test.com", "Tardy", 4);

        assertEquals("1", result);
    }

    @Test
    void getClosedLevel_shouldReturnHighestClosedTimes_cappedAtMaxLevel() {
        Punishment p1 = new Punishment();
        p1.setClosedTimes(2);

        Punishment p2 = new Punishment();
        p2.setClosedTimes(7);

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "CLOSED"
        )).thenReturn(List.of(p1, p2));

        String result = punishmentUtils.getClosedLevel("student@test.com", "Tardy", 4);

        assertEquals("4", result);
    }

    @Test
    void resolveInfraction_shouldUseLevelLookup_whenNotSpecialCaseAndNotAdminReferral() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@test.com");
        request.setInfractionName("Tardy");
        request.setAdminReferral(false);

        Punishment closed = new Punishment();
        closed.setClosedTimes(2);

        Infraction infraction = new Infraction();

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "CLOSED"
        )).thenReturn(List.of(closed));
        when(infractionRepository.findByInfractionNameAndInfractionLevel("Tardy", "2"))
                .thenReturn(infraction);

        Infraction result = punishmentUtils.resolveInfraction(request, new School(), 4);

        assertSame(infraction, result);
        verify(infractionRepository).findByInfractionNameAndInfractionLevel("Tardy", "2");
        verify(infractionRepository, never()).findByInfractionName(anyString());
    }

    @Test
    void resolveInfraction_shouldUseNameLookup_whenAdminReferralIsTrue() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@test.com");
        request.setInfractionName("Tardy");
        request.setAdminReferral(true);

        Infraction infraction = new Infraction();

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "CLOSED"
        )).thenReturn(List.of());
        when(infractionRepository.findByInfractionName("Tardy")).thenReturn(infraction);

        Infraction result = punishmentUtils.resolveInfraction(request, new School(), 4);

        assertSame(infraction, result);
        verify(infractionRepository).findByInfractionName("Tardy");
    }

    @Test
    void buildPunishment_shouldPopulateExpectedFields() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@test.com");
        request.setTeacherEmail("teacher@test.com");
        request.setInfractionPeriod("2nd");
        request.setInfractionDescription("Was late to class");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("INF-1");
        infraction.setInfractionName("Tardy");
        infraction.setInfractionLevel("2");

        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setSchool("Test School");

        Punishment closed = new Punishment();
        closed.setClosedTimes(2);

        LocalDate now = LocalDate.of(2026, 4, 1);

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "CLOSED"
        )).thenReturn(List.of(closed));

        Punishment result = punishmentUtils.buildPunishment(request, infraction, student, 4, now);

        assertEquals("student@test.com", result.getStudentEmail());
        assertEquals("teacher@test.com", result.getTeacherEmail());
        assertEquals("2nd", result.getClassPeriod());
        assertEquals("INF-1", result.getInfractionId());
        assertEquals("Tardy", result.getInfractionName());
        assertEquals("2", result.getInfractionLevel());
        assertEquals("Test School", result.getSchool());
        assertEquals(now, result.getTimeCreated());
        assertEquals(2, result.getClosedTimes());
        assertNotNull(result.getPunishmentId());
        assertEquals(1, result.getInfractionDescription().size());
        assertEquals("Was late to class", result.getInfractionDescription().get(0));
    }

    @Test
    void savePhoneLogIfNeeded_shouldSaveStudent_whenPhoneLogExists() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setTeacherEmail("teacher@test.com");
        request.setPhoneLogDescription("Called parent");

        Student student = new Student();
        student.setNotesArray(new ArrayList<>());

        LocalDate now = LocalDate.of(2026, 4, 1);

        punishmentUtils.savePhoneLogIfNeeded(request, student, now);

        assertNotNull(student.getNotesArray());
        assertEquals(1, student.getNotesArray().size());

        ThreadEvent log = student.getNotesArray().get(0);
        assertEquals("teacher@test.com", log.getCreatedBy());
        assertEquals(now, log.getDate());
        assertEquals("Called parent", log.getContent());
        assertEquals("Phone", log.getEvent());

        verify(studentRepository).save(student);
    }

    @Test
    void savePhoneLogIfNeeded_shouldInitializeNotesArray_whenNull() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setTeacherEmail("teacher@test.com");
        request.setPhoneLogDescription("Called home");

        Student student = new Student();
        student.setNotesArray(null);

        punishmentUtils.savePhoneLogIfNeeded(request, student, LocalDate.now());

        assertNotNull(student.getNotesArray());
        assertEquals(1, student.getNotesArray().size());
        verify(studentRepository).save(student);
    }

    @Test
    void savePhoneLogIfNeeded_shouldDoNothing_whenDescriptionBlank() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setPhoneLogDescription("   ");

        Student student = new Student();
        student.setNotesArray(new ArrayList<>());

        punishmentUtils.savePhoneLogIfNeeded(request, student, LocalDate.now());

        assertTrue(student.getNotesArray().isEmpty());
        verify(studentRepository, never()).save(any(Student.class));
    }

    @Test
    void linkGuidanceIfNeeded_shouldCallGuidanceService_whenDescriptionPresent() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setGuidanceDescription("Needs counselor follow-up");

        Student student = new Student();
        Punishment punishment = new Punishment();

        punishmentUtils.linkGuidanceIfNeeded(request, student, punishment);

        verify(guidanceService).LinkAssignmentToGuidance(student, request, punishment);
    }

    @Test
    void linkGuidanceIfNeeded_shouldNotCallGuidanceService_whenDescriptionBlank() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setGuidanceDescription("   ");

        punishmentUtils.linkGuidanceIfNeeded(request, new Student(), new Punishment());

        verify(guidanceService, never()).LinkAssignmentToGuidance(any(), any(), any());
    }

    @Test
    void handlePositiveShoutout_shouldTransferCurrencySaveLinkAndSendEmail() throws MessagingException {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setTeacherEmail("teacher@test.com");
        request.setStudentEmail("student@test.com");
        request.setCurrency(5);
        request.setGuidanceDescription("guidance note");

        Punishment punishment = new Punishment();
        Student student = new Student();
        LocalDate now = LocalDate.of(2026, 4, 1);

        Punishment saved = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(punishRepository.save(punishment)).thenReturn(saved);
        when(emailService.sendEmailBasedOnType(request, saved, emailService)).thenReturn(response);

        PunishmentResponse result = punishmentUtils.handlePositiveShoutout(request, punishment, student, now);

        assertSame(response, result);
        assertEquals("SO", punishment.getStatus());
        assertEquals(now, punishment.getTimeClosed());

        verify(employeeService).transferCurrency(any());
        verify(punishRepository).save(punishment);
        verify(guidanceService).LinkAssignmentToGuidance(student, request, saved);
        verify(emailService).sendEmailBasedOnType(request, saved, emailService);
    }

    @Test
    void handlePositiveShoutout_shouldNotTransferCurrency_whenCurrencyIsZero() throws MessagingException {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setCurrency(0);

        Punishment punishment = new Punishment();
        Student student = new Student();
        LocalDate now = LocalDate.now();

        Punishment saved = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(punishRepository.save(punishment)).thenReturn(saved);
        when(emailService.sendEmailBasedOnType(request, saved, emailService)).thenReturn(response);

        PunishmentResponse result = punishmentUtils.handlePositiveShoutout(request, punishment, student, now);

        assertSame(response, result);
        verify(employeeService, never()).transferCurrency(any());
    }

    @Test
    void handleBasicClose_shouldSetStatusCloseSaveAndEmail() throws MessagingException {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setGuidanceDescription("follow-up");

        Punishment punishment = new Punishment();
        Student student = new Student();
        LocalDate now = LocalDate.of(2026, 4, 1);

        Punishment saved = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(punishRepository.save(punishment)).thenReturn(saved);
        when(emailService.sendEmailBasedOnType(request, saved, emailService)).thenReturn(response);

        PunishmentResponse result = punishmentUtils.handleBasicClose("CLOSED", request, punishment, student, now);

        assertSame(response, result);
        assertEquals("CLOSED", punishment.getStatus());
        assertEquals(now, punishment.getTimeClosed());
        verify(guidanceService).LinkAssignmentToGuidance(student, request, saved);
    }

    @Test
    void handleLevelFourReferral_shouldCreateReferralCloseIncrementSaveAndEmail() throws Exception {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setInfractionDescription("Repeated issue");
        request.setStudentEmail("student@test.com");
        request.setTeacherEmail("teacher@test.com");
        request.setInfractionPeriod("3rd");
        request.setGuidanceDescription("guidance");

        Punishment punishment = new Punishment();
        punishment.setClosedTimes(2);

        Student student = new Student();
        Punishment saved = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(punishRepository.save(punishment)).thenReturn(saved);
        when(emailService.sendEmailBasedOnType(request, saved, emailService)).thenReturn(response);

        PunishmentResponse result = punishmentUtils.handleLevelFourReferral(request, punishment, student);

        assertSame(response, result);
        assertEquals("CLOSED", punishment.getStatus());
        assertEquals(3, punishment.getClosedTimes());
        assertNotNull(punishment.getTimeClosed());

        verify(officeReferralService).createNewOfficeReferral(any());
        verify(punishRepository).save(punishment);
        verify(guidanceService).LinkAssignmentToGuidance(student, request, saved);
        verify(emailService).sendEmailBasedOnType(request, saved, emailService);
    }

    @Test
    void handleAdminReferral_shouldSetOpenSaveAndEmail() throws MessagingException {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setGuidanceDescription("needs follow-up");

        Punishment punishment = new Punishment();
        Student student = new Student();
        LocalDate now = LocalDate.of(2026, 4, 1);

        Punishment saved = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(punishRepository.save(punishment)).thenReturn(saved);
        when(emailService.sendEmailBasedOnType(request, saved, emailService)).thenReturn(response);

        PunishmentResponse result = punishmentUtils.handleAdminReferral(request, punishment, student, now);

        assertSame(response, result);
        assertEquals("OPEN", punishment.getStatus());
        assertEquals(now, punishment.getTimeClosed());
        verify(guidanceService).LinkAssignmentToGuidance(student, request, saved);
    }

    @Test
    void handleDefaultOpen_shouldSetOpenAndSendNormalEmail_whenNoExistingOpenOrPending() throws Exception {
        PunishmentFormRequest request = new PunishmentFormRequest();

        Punishment punishment = new Punishment();
        punishment.setStudentEmail("student@test.com");
        punishment.setInfractionName("Tardy");

        Student student = new Student();
        Punishment saved = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "OPEN"
        )).thenReturn(List.of());
        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "PENDING"
        )).thenReturn(List.of());
        when(punishRepository.save(punishment)).thenReturn(saved);
        when(emailService.sendEmailBasedOnType(request, saved, emailService)).thenReturn(response);

        PunishmentResponse result = punishmentUtils.handleDefaultOpen(request, punishment, student);

        assertSame(response, result);
        assertEquals("OPEN", punishment.getStatus());
        verify(emailService).sendEmailBasedOnType(request, saved, emailService);
        verify(emailService, never()).sendCFREmailBasedOnType(any());
    }

    @Test
    void handleDefaultOpen_shouldSetCfrAndSendCfrEmail_whenExistingActivePunishmentExists() throws Exception {
        PunishmentFormRequest request = new PunishmentFormRequest();

        Punishment punishment = new Punishment();
        punishment.setStudentEmail("student@test.com");
        punishment.setInfractionName("Tardy");

        Student student = new Student();
        Punishment existing = new Punishment();
        existing.setArchived(false);

        Punishment saved = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "OPEN"
        )).thenReturn(List.of(existing));
        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "PENDING"
        )).thenReturn(List.of());
        when(punishRepository.save(punishment)).thenReturn(saved);
        when(emailService.sendCFREmailBasedOnType(saved)).thenReturn(response);

        PunishmentResponse result = punishmentUtils.handleDefaultOpen(request, punishment, student);

        assertSame(response, result);
        assertEquals("CFR", punishment.getStatus());
        assertNotNull(punishment.getTimeClosed());
        verify(emailService).sendCFREmailBasedOnType(saved);
        verify(emailService, never()).sendEmailBasedOnType(any(), any(), any());
    }

    @Test
    void handleDefaultOpen_shouldIgnoreArchivedExistingPunishments() throws Exception {
        PunishmentFormRequest request = new PunishmentFormRequest();

        Punishment punishment = new Punishment();
        punishment.setStudentEmail("student@test.com");
        punishment.setInfractionName("Tardy");

        Student student = new Student();
        Punishment archived = new Punishment();
        archived.setArchived(true);

        Punishment saved = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "OPEN"
        )).thenReturn(List.of(archived));
        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "PENDING"
        )).thenReturn(List.of());
        when(punishRepository.save(punishment)).thenReturn(saved);
        when(emailService.sendEmailBasedOnType(request, saved, emailService)).thenReturn(response);

        PunishmentResponse result = punishmentUtils.handleDefaultOpen(request, punishment, student);

        assertSame(response, result);
        assertEquals("OPEN", punishment.getStatus());
    }

    @Test
    void fetchOpenPunishment_shouldReturnFirstNonArchivedOpenPunishment() {
        Punishment archived = new Punishment();
        archived.setArchived(true);

        Punishment active = new Punishment();
        active.setArchived(false);

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "OPEN"
        )).thenReturn(List.of(archived, active));

        Punishment result = punishmentUtils.fetchOpenPunishment("student@test.com", "Tardy");

        assertSame(active, result);
    }

    @Test
    void fetchOpenPunishment_shouldThrow_whenNoNonArchivedOpenPunishmentExists() {
        Punishment archived = new Punishment();
        archived.setArchived(true);

        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(
                "student@test.com", "Tardy", "OPEN"
        )).thenReturn(List.of(archived));

        assertThrows(ResourceNotFoundException.class,
                () -> punishmentUtils.fetchOpenPunishment("student@test.com", "Tardy"));
    }

    @Test
    void appendStudentAnswers_shouldAppendToExistingDescriptions() {
        Punishment punishment = new Punishment();
        punishment.setInfractionDescription(new ArrayList<>(List.of("Original description")));

        StudentAnswer answer1 = mock(StudentAnswer.class);
        StudentAnswer answer2 = mock(StudentAnswer.class);

        when(answer1.toString()).thenReturn("Answer 1");
        when(answer2.toString()).thenReturn("Answer 2");

        punishmentUtils.appendStudentAnswers(punishment, List.of(answer1, answer2));

        assertEquals(3, punishment.getInfractionDescription().size());
        assertEquals("Original description", punishment.getInfractionDescription().get(0));
        assertEquals("Answer 1", punishment.getInfractionDescription().get(1));
        assertEquals("Answer 2", punishment.getInfractionDescription().get(2));
    }

    @Test
    void appendStudentAnswers_shouldInitializeDescriptionList_whenNull() {
        Punishment punishment = new Punishment();
        punishment.setInfractionDescription(null);

        StudentAnswer answer = mock(StudentAnswer.class);
        when(answer.toString()).thenReturn("Answer A");

        punishmentUtils.appendStudentAnswers(punishment, List.of(answer));

        assertNotNull(punishment.getInfractionDescription());
        assertEquals(1, punishment.getInfractionDescription().size());
        assertEquals("Answer A", punishment.getInfractionDescription().get(0));
    }

    @Test
    void appendStudentAnswers_shouldIgnoreNullAnswersList() {
        Punishment punishment = new Punishment();
        punishment.setInfractionDescription(new ArrayList<>(List.of("Original")));

        punishmentUtils.appendStudentAnswers(punishment, null);

        assertEquals(1, punishment.getInfractionDescription().size());
        assertEquals("Original", punishment.getInfractionDescription().get(0));
    }

    @Test
    void handlePositiveShoutout_shouldBuildCurrencyTransferRequestWithExpectedValues() throws Exception {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setTeacherEmail("teacher@test.com");
        request.setStudentEmail("student@test.com");
        request.setCurrency(10);

        Punishment punishment = new Punishment();
        Student student = new Student();
        LocalDate now = LocalDate.now();

        Punishment saved = new Punishment();
        PunishmentResponse response = new PunishmentResponse();

        when(punishRepository.save(punishment)).thenReturn(saved);
        when(emailService.sendEmailBasedOnType(request, saved, emailService)).thenReturn(response);

        punishmentUtils.handlePositiveShoutout(request, punishment, student, now);

        ArgumentCaptor<com.reps.demogcloud.models.employee.CurrencyTransferRequest> captor =
                ArgumentCaptor.forClass(com.reps.demogcloud.models.employee.CurrencyTransferRequest.class);

        verify(employeeService).transferCurrency(captor.capture());

        assertEquals("teacher@test.com", captor.getValue().getTeacherEmail());
        assertEquals("student@test.com", captor.getValue().getStudentEmail());
        assertEquals(10, captor.getValue().getCurrencyTransferred());
    }
}
