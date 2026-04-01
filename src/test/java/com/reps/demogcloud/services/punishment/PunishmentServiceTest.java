package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.Employee;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.punishment.PunishmentResponse;
import com.reps.demogcloud.models.punishment.StudentAnswer;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.EmailService;
import com.reps.demogcloud.services.PunishmentService;
import com.reps.demogcloud.utils.StudentUtils;
import jakarta.mail.MessagingException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class PunishmentServiceTest {

    @Mock
    private PunishmentCreationService punishmentCreationService;

    @Mock
    private PunishmentClosureService punishmentClosureService;

    @Mock
    private PunishmentQueryService punishmentQueryService;

    @Mock
    private PunishmentUpdateService punishmentUpdateService;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PunishRepository punishRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private EmployeeRepository employeeRepository;

    @Mock
    private StudentUtils studentUtils;

    @InjectMocks
    private PunishmentService punishmentService;

    @Test
    void findByStudentEmailAndInfraction_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());

        when(punishmentQueryService.findByStudentEmailAndInfraction("student@example.com", "INF-1"))
                .thenReturn(punishments);

        List<Punishment> result =
                punishmentService.findByStudentEmailAndInfraction("student@example.com", "INF-1");

        assertSame(punishments, result);
        verify(punishmentQueryService).findByStudentEmailAndInfraction("student@example.com", "INF-1");
    }

    @Test
    void findByStudentEmailAndInfraction_shouldPropagateResourceNotFoundException() {
        when(punishmentQueryService.findByStudentEmailAndInfraction("student@example.com", "INF-1"))
                .thenThrow(new ResourceNotFoundException("not found"));

        assertThrows(
                ResourceNotFoundException.class,
                () -> punishmentService.findByStudentEmailAndInfraction("student@example.com", "INF-1")
        );

        verify(punishmentQueryService).findByStudentEmailAndInfraction("student@example.com", "INF-1");
    }

    @Test
    void findByStatus_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());

        when(punishmentQueryService.findByStatus("OPEN")).thenReturn(punishments);

        List<Punishment> result = punishmentService.findByStatus("OPEN");

        assertSame(punishments, result);
        verify(punishmentQueryService).findByStatus("OPEN");
    }

    @Test
    void findByStatus_shouldPropagateResourceNotFoundException() {
        when(punishmentQueryService.findByStatus("OPEN"))
                .thenThrow(new ResourceNotFoundException("not found"));

        assertThrows(ResourceNotFoundException.class, () -> punishmentService.findByStatus("OPEN"));
        verify(punishmentQueryService).findByStatus("OPEN");
    }

    @Test
    void findByPunishmentId_shouldDelegateToQueryService() {
        Punishment punishment = new Punishment();

        when(punishmentQueryService.findByPunishmentId("P-1")).thenReturn(punishment);

        Punishment result = punishmentService.findByPunishmentId("P-1");

        assertSame(punishment, result);
        verify(punishmentQueryService).findByPunishmentId("P-1");
    }

    @Test
    void findByPunishmentId_shouldPropagateResourceNotFoundException() {
        when(punishmentQueryService.findByPunishmentId("P-1"))
                .thenThrow(new ResourceNotFoundException("not found"));

        assertThrows(ResourceNotFoundException.class, () -> punishmentService.findByPunishmentId("P-1"));
        verify(punishmentQueryService).findByPunishmentId("P-1");
    }

    @Test
    void findAll_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentQueryService.findAll()).thenReturn(punishments);

        List<Punishment> result = punishmentService.findAll();

        assertSame(punishments, result);
        verify(punishmentQueryService).findAll();
    }

    @Test
    void findAllPunishmentArchived_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentQueryService.findAllPunishmentArchived(true)).thenReturn(punishments);

        List<Punishment> result = punishmentService.findAllPunishmentArchived(true);

        assertSame(punishments, result);
        verify(punishmentQueryService).findAllPunishmentArchived(true);
    }

    @Test
    void getAllOpenAssignments_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentQueryService.getAllOpenAssignments()).thenReturn(punishments);

        List<Punishment> result = punishmentService.getAllOpenAssignments();

        assertSame(punishments, result);
        verify(punishmentQueryService).getAllOpenAssignments();
    }

    @Test
    void getAllPunishmentsForStudents_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentQueryService.findAllForStudent("student@example.com")).thenReturn(punishments);

        List<Punishment> result = punishmentService.getAllPunishmentsForStudents("student@example.com");

        assertSame(punishments, result);
        verify(punishmentQueryService).findAllForStudent("student@example.com");
    }

    @Test
    void getAllPunishmentByStudentEmail_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentQueryService.getAllPunishmentByStudentEmail("student@example.com")).thenReturn(punishments);

        List<Punishment> result = punishmentService.getAllPunishmentByStudentEmail("student@example.com");

        assertSame(punishments, result);
        verify(punishmentQueryService).getAllPunishmentByStudentEmail("student@example.com");
    }

    @Test
    void getAllPunishmentForStudent_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentQueryService.getAllPunishmentForStudent("student@example.com")).thenReturn(punishments);

        List<Punishment> result = punishmentService.getAllPunishmentForStudent("student@example.com");

        assertSame(punishments, result);
        verify(punishmentQueryService).getAllPunishmentForStudent("student@example.com");
    }

    @Test
    void getTeacherResponse_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());
        List<TeacherDTO> response = List.of(mock(TeacherDTO.class));

        when(punishmentQueryService.getTeacherResponse(punishments)).thenReturn(response);

        List<TeacherDTO> result = punishmentService.getTeacherResponse(punishments);

        assertSame(response, result);
        verify(punishmentQueryService).getTeacherResponse(punishments);
    }

    @Test
    void findAllSchool_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentQueryService.findAllSchool()).thenReturn(punishments);

        List<Punishment> result = punishmentService.findAllSchool();

        assertSame(punishments, result);
        verify(punishmentQueryService).findAllSchool();
    }

    @Test
    void findAllPunishmentsByStudentEmail_shouldDelegateToQueryService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentQueryService.findAllPunishmentsByStudentEmail()).thenReturn(punishments);

        List<Punishment> result = punishmentService.findAllPunishmentsByStudentEmail();

        assertSame(punishments, result);
        verify(punishmentQueryService).findAllPunishmentsByStudentEmail();
    }

    @Test
    void createNewPunishForm_shouldDelegateToCreationService() throws MessagingException {
        PunishmentFormRequest request = new PunishmentFormRequest();
        PunishmentResponse response = mock(PunishmentResponse.class);

        when(punishmentCreationService.createNewPunishForm(request)).thenReturn(response);

        PunishmentResponse result = punishmentService.createNewPunishForm(request);

        assertSame(response, result);
        verify(punishmentCreationService).createNewPunishForm(request);
    }

    @Test
    void createNewPunishForm_shouldPropagateMessagingException() throws MessagingException {
        PunishmentFormRequest request = new PunishmentFormRequest();

        when(punishmentCreationService.createNewPunishForm(request))
                .thenThrow(new MessagingException("mail failed"));

        assertThrows(MessagingException.class, () -> punishmentService.createNewPunishForm(request));
        verify(punishmentCreationService).createNewPunishForm(request);
    }

    @Test
    void createNewPunishFormBulk_shouldDelegateToCreationService() throws MessagingException {
        List<PunishmentFormRequest> requests = List.of(new PunishmentFormRequest());
        List<PunishmentResponse> responses = List.of(mock(PunishmentResponse.class));

        when(punishmentCreationService.createNewPunishFormBulk(requests)).thenReturn(responses);

        List<PunishmentResponse> result = punishmentService.createNewPunishFormBulk(requests);

        assertSame(responses, result);
        verify(punishmentCreationService).createNewPunishFormBulk(requests);
    }

    @Test
    void closePunishment_shouldDelegateToClosureService() throws MessagingException {
        List<StudentAnswer> answers = List.of(mock(StudentAnswer.class));
        PunishmentResponse response = mock(PunishmentResponse.class);

        when(punishmentClosureService.closePunishment("Tardy", "student@example.com", answers))
                .thenReturn(response);

        PunishmentResponse result = punishmentService.closePunishment("Tardy", "student@example.com", answers);

        assertSame(response, result);
        verify(punishmentClosureService).closePunishment("Tardy", "student@example.com", answers);
    }

    @Test
    void rejectLevelThree_shouldDelegateToClosureService() throws MessagingException {
        Punishment punishment = new Punishment();
        when(punishmentClosureService.rejectLevelThree("P-1")).thenReturn(punishment);

        Punishment result = punishmentService.rejectLevelThree("P-1");

        assertSame(punishment, result);
        verify(punishmentClosureService).rejectLevelThree("P-1");
    }

    @Test
    void closeByPunishmentId_shouldDelegateToClosureService() throws MessagingException {
        PunishmentResponse response = mock(PunishmentResponse.class);
        when(punishmentClosureService.closeByPunishmentId("P-1")).thenReturn(response);

        PunishmentResponse result = punishmentService.closeByPunishmentId("P-1");

        assertSame(response, result);
        verify(punishmentClosureService).closeByPunishmentId("P-1");
    }

    @Test
    void archiveRecord_shouldDelegateToClosureService() throws MessagingException {
        Punishment punishment = new Punishment();
        when(punishmentClosureService.archiveRecord("P-1", "user1", "reason")).thenReturn(punishment);

        Punishment result = punishmentService.archiveRecord("P-1", "user1", "reason");

        assertSame(punishment, result);
        verify(punishmentClosureService).archiveRecord("P-1", "user1", "reason");
    }

    @Test
    void restoreRecord_shouldDelegateToClosureService() throws MessagingException {
        Punishment punishment = new Punishment();
        when(punishmentClosureService.restoreRecord("P-1")).thenReturn(punishment);

        Punishment result = punishmentService.restoreRecord("P-1");

        assertSame(punishment, result);
        verify(punishmentClosureService).restoreRecord("P-1");
    }

    @Test
    void deletePunishment_shouldDeleteAndReturnMessage() {
        Punishment punishment = new Punishment();

        doNothing().when(punishRepository).delete(punishment);

        String result = punishmentService.deletePunishment(punishment);

        assertEquals("Punishment has been deleted", result);
        verify(punishRepository).delete(punishment);
    }

    @Test
    void deletePunishment_shouldThrowResourceNotFoundException_whenDeleteFails() {
        Punishment punishment = new Punishment();

        doThrow(new RuntimeException("delete failed")).when(punishRepository).delete(punishment);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> punishmentService.deletePunishment(punishment)
        );

        assertEquals("That punishment does not exist", exception.getMessage());
        verify(punishRepository).delete(punishment);
    }

    @Test
    void updateMapIndex_shouldDelegateToUpdateService() {
        Punishment punishment = new Punishment();
        when(punishmentUpdateService.updateMapIndex("P-1", 3)).thenReturn(punishment);

        Punishment result = punishmentService.updateMapIndex("P-1", 3);

        assertSame(punishment, result);
        verify(punishmentUpdateService).updateMapIndex("P-1", 3);
    }

    @Test
    void updateTimeCreated_shouldDelegateToUpdateService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentUpdateService.updateTimeCreated()).thenReturn(punishments);

        List<Punishment> result = punishmentService.updateTimeCreated();

        assertSame(punishments, result);
        verify(punishmentUpdateService).updateTimeCreated();
    }

    @Test
    void updateDescriptions_shouldDelegateToUpdateService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentUpdateService.updateDescriptions()).thenReturn(punishments);

        List<Punishment> result = punishmentService.updateDescriptions();

        assertSame(punishments, result);
        verify(punishmentUpdateService).updateDescriptions();
    }

    @Test
    void updateStudentEmails_shouldDelegateToUpdateService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentUpdateService.updateStudentEmails()).thenReturn(punishments);

        List<Punishment> result = punishmentService.updateStudentEmails();

        assertSame(punishments, result);
        verify(punishmentUpdateService).updateStudentEmails();
    }

    @Test
    void updateInfractionName_shouldDelegateToUpdateService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentUpdateService.updateInfractionName()).thenReturn(punishments);

        List<Punishment> result = punishmentService.updateInfractionName();

        assertSame(punishments, result);
        verify(punishmentUpdateService).updateInfractionName();
    }

    @Test
    void updateInfractionLevel_shouldDelegateToUpdateService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentUpdateService.updateInfractionLevel()).thenReturn(punishments);

        List<Punishment> result = punishmentService.updateInfractionLevel();

        assertSame(punishments, result);
        verify(punishmentUpdateService).updateInfractionLevel();
    }

    @Test
    void updateSchools_shouldDelegateToUpdateService() {
        List<Punishment> punishments = List.of(new Punishment());
        when(punishmentUpdateService.updateSchools()).thenReturn(punishments);

        List<Punishment> result = punishmentService.updateSchools();

        assertSame(punishments, result);
        verify(punishmentUpdateService).updateSchools();
    }

    @Test
    void alertIssAndDetention_shouldSendDetentionWhenWorkDaysEqualOne_andIssOtherwise() throws MessagingException {
        Punishment detentionPunishment = new Punishment();
        detentionPunishment.setTimeCreated(LocalDate.of(2026, 1, 10));

        Punishment issPunishment = new Punishment();
        issPunishment.setTimeCreated(LocalDate.of(2026, 1, 5));

        when(punishRepository.findByArchivedAndStatus(false, "OPEN"))
                .thenReturn(List.of(detentionPunishment, issPunishment));

        when(studentUtils.getWorkDaysBetweenTwoDates(eq(LocalDate.of(2026, 1, 10)), any(LocalDate.class)))
                .thenReturn(1);
        when(studentUtils.getWorkDaysBetweenTwoDates(eq(LocalDate.of(2026, 1, 5)), any(LocalDate.class)))
                .thenReturn(2);

        punishmentService.alertIssAndDetention();

        verify(emailService).sendAlertEmail("DETENTION", detentionPunishment, "en");
        verify(emailService).sendAlertEmail("ISS", issPunishment, "en");
        verify(punishRepository).findByArchivedAndStatus(false, "OPEN");
    }

    @Test
    void alertIssAndDetention_shouldDoNothingWhenNoOpenPunishments() throws MessagingException {
        when(punishRepository.findByArchivedAndStatus(false, "OPEN")).thenReturn(List.of());

        punishmentService.alertIssAndDetention();

        verify(punishRepository).findByArchivedAndStatus(false, "OPEN");
        verifyNoInteractions(emailService);
    }

    @Test
    void filePositiveWithState_shouldIncludeReward_whenCurrencyGreaterThanZero() throws Exception {
        Student student = new Student();
        student.setStudentEmail("student@example.com");
        student.setFirstName("John");
        student.setLastName("Doe");
        student.setStudentIdNumber("12345");
        student.setStateStudentId(999);

        Employee employee = new Employee();
        employee.setEmail("teacher@example.com");

        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@example.com");
        request.setTeacherEmail("teacher@example.com");
        request.setInfractionDescription("Great job today");
        request.setCurrency(5);

        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);

        when(studentRepository.findByStudentEmailIgnoreCase("student@example.com")).thenReturn(student);
        when(employeeRepository.findByEmailIgnoreCase("teacher@example.com")).thenReturn(employee);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        invokeFilePositiveWithState(request, client);

        verify(studentRepository).findByStudentEmailIgnoreCase("student@example.com");
        verify(employeeRepository).findByEmailIgnoreCase("teacher@example.com");

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        HttpRequest sentRequest = requestCaptor.getValue();
        assertEquals("https://calendar-service-mygto2ljcq-wn.a.run.app/sendincident", sentRequest.uri().toString());
        assertEquals("application/json", sentRequest.headers().firstValue("Content-Type").orElse(null));

        String body = bodyPublisherToString(sentRequest.bodyPublisher().orElseThrow());
        assertTrue(body.contains("Great job today"));
        assertTrue(body.contains("Reward"));
        assertTrue(body.contains("Recognition"));
        assertTrue(body.contains("Parent Contact - Email"));
        assertTrue(body.contains("John"));
        assertTrue(body.contains("Doe"));
        assertTrue(body.contains("12345"));
    }

    @Test
    void filePositiveWithState_shouldNotIncludeReward_whenCurrencyIsZero() throws Exception {
        Student student = new Student();
        student.setStudentEmail("student@example.com");
        student.setFirstName("Jane");
        student.setLastName("Smith");
        student.setStudentIdNumber("67890");
        student.setStateStudentId(111);

        Employee employee = new Employee();
        employee.setEmail("teacher@example.com");

        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@example.com");
        request.setTeacherEmail("teacher@example.com");
        request.setInfractionDescription("Excellent participation");
        request.setCurrency(0);

        HttpClient client = mock(HttpClient.class);
        @SuppressWarnings("unchecked")
        HttpResponse<String> response = mock(HttpResponse.class);

        when(studentRepository.findByStudentEmailIgnoreCase("student@example.com")).thenReturn(student);
        when(employeeRepository.findByEmailIgnoreCase("teacher@example.com")).thenReturn(employee);
        when(client.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class))).thenReturn(response);

        invokeFilePositiveWithState(request, client);

        ArgumentCaptor<HttpRequest> requestCaptor = ArgumentCaptor.forClass(HttpRequest.class);
        verify(client).send(requestCaptor.capture(), any(HttpResponse.BodyHandler.class));

        String body = bodyPublisherToString(requestCaptor.getValue().bodyPublisher().orElseThrow());
        assertTrue(body.contains("Excellent participation"));
        assertFalse(body.contains("Reward"));
        assertTrue(body.contains("Recognition"));
        assertTrue(body.contains("Parent Contact - Email"));
    }

    private void invokeFilePositiveWithState(PunishmentFormRequest request, HttpClient client) throws Exception {
        Method method = PunishmentService.class.getDeclaredMethod(
                "filePositiveWithState",
                PunishmentFormRequest.class,
                HttpClient.class
        );
        method.setAccessible(true);
        method.invoke(punishmentService, request, client);
    }

    private String bodyPublisherToString(HttpRequest.BodyPublisher bodyPublisher) throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        CountDownLatch latch = new CountDownLatch(1);

        bodyPublisher.subscribe(new Flow.Subscriber<>() {
            @Override
            public void onSubscribe(Flow.Subscription subscription) {
                subscription.request(Long.MAX_VALUE);
            }

            @Override
            public void onNext(ByteBuffer item) {
                byte[] bytes = new byte[item.remaining()];
                item.get(bytes);
                outputStream.write(bytes, 0, bytes.length);
            }

            @Override
            public void onError(Throwable throwable) {
                latch.countDown();
                fail(throwable);
            }

            @Override
            public void onComplete() {
                latch.countDown();
            }
        });

        assertTrue(latch.await(2, TimeUnit.SECONDS));
        return outputStream.toString(StandardCharsets.UTF_8);
    }
}

