package com.reps.demogcloud.services;

import com.reps.demogcloud.data.*;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.punishment.PunishmentFormRequest;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.punishment.PunishmentQueryService;
import com.reps.demogcloud.utils.PunishmentUtils;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@Disabled
public class PunishmentServiceTest {
    @Mock
    private PunishRepository punishRepository;
    @Mock
    private PunishmentQueryService punishmentQueryService;
    @InjectMocks
    private PunishmentService punishmentService;

    // Helper to create a base request
    private PunishmentFormRequest getBaseRequest() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@example.com");
        request.setTeacherEmail("teacher@example.com");
        request.setInfractionName("Student Guidance Referral");
        request.setInfractionDescription("Infraction description");
        request.setInfractionPeriod("3");
        request.setAdminReferral(true);
        request.setGuidanceDescription("Guidance notes here");
        request.setPhoneLogDescription("Phone call notes");
        request.setCurrency(0);
        return request;
    }

    // Helper to create a mock student with basic setup
    private Student getMockStudent() {
        Student student = new Student();
        student.setStudentEmail("student@example.com");
        student.setSchool("Test School");
        student.setNotesArray(new java.util.ArrayList<>());
        return student;
    }

    // Helper to create a mock school
    private School getMockSchool() {
        School school = new School();
        school.setSchoolName("Test School");
        school.setMaxPunishLevel(4);
        return school;
    }

    private List<Punishment> getClosedPunishments() {
        Punishment p = new Punishment();
        p.setClosedTimes(3);
        return List.of(p);
    }

    @Test
    void testFindByStudentEmailAndInfraction_ReturnsFilteredList() {
        String email = "student@example.com";
        String infractionId = "inf123";

        Punishment p1 = new Punishment();
        p1.setArchived(false);

        Punishment p2 = new Punishment();
        p2.setArchived(true); // should be excluded

        when(punishRepository.findByStudentEmailAndInfractionId(email, infractionId))
                .thenReturn(List.of(p1, p2));

        List<Punishment> result = punishmentService.findByStudentEmailAndInfraction(email, infractionId);

        assertEquals(1, result.size());
        assertFalse(result.get(0).isArchived());

        verify(punishRepository).findByStudentEmailAndInfractionId(email, infractionId);
    }

    @Test
    void testFindByStudentEmailAndInfraction_ThrowsResourceNotFoundException() {
        String email = "unknown@example.com";
        String infractionId = "inf123";

        when(punishRepository.findByStudentEmailAndInfractionId(email, infractionId))
                .thenReturn(Collections.emptyList());

        assertThrows(ResourceNotFoundException.class, () ->
                punishmentService.findByStudentEmailAndInfraction(email, infractionId)
        );

        verify(punishRepository).findByStudentEmailAndInfractionId(email, infractionId);
    }

    @Test
    void testFindAll_ReturnsUnarchivedPunishments() {
        Punishment p1 = new Punishment();
        p1.setArchived(false);

        when(punishRepository.findByArchived(false)).thenReturn(List.of(p1));

        List<Punishment> result = punishmentService.findAll();

        assertEquals(1, result.size());
        assertFalse(result.get(0).isArchived());

        verify(punishRepository).findByArchived(false);
    }

    @Test
    void testFindByStatus_ReturnsPunishments() {
        String status = "IN_PROGRESS";
        Punishment p1 = new Punishment();

        when(punishmentQueryService.FetchPunishmentDataByArchivedAndSchoolAndStatus(false, status))
                .thenReturn(List.of(p1));

        List<Punishment> result = punishmentService.findByStatus(status);

        assertEquals(1, result.size());
        verify(punishmentQueryService).FetchPunishmentDataByArchivedAndSchoolAndStatus(false, status);
    }

    @Test
    void testFindByStatus_ThrowsResourceNotFoundException() {
        String status = "INVALID_STATUS";

        when(punishmentQueryService.FetchPunishmentDataByArchivedAndSchoolAndStatus(false, status))
                .thenReturn(Collections.emptyList());

        assertThrows(ResourceNotFoundException.class, () ->
                punishmentService.findByStatus(status)
        );

        verify(punishmentQueryService).FetchPunishmentDataByArchivedAndSchoolAndStatus(false, status);
    }

    @Test
    void testFindByPunishmentId_ReturnsPunishment() {
        String punishmentId = "pun123";
        Punishment p = new Punishment();
        p.setArchived(false);

        when(punishRepository.findByPunishmentId(punishmentId)).thenReturn(p);

        Punishment result = punishmentService.findByPunishmentId(punishmentId);

        assertEquals(p, result);
        verify(punishRepository).findByPunishmentId(punishmentId);
    }

    @Test
    void testFindByPunishmentId_ThrowsWhenNotFound() {
        String punishmentId = "missingId";

        when(punishRepository.findByPunishmentId(punishmentId)).thenReturn(null);

        assertThrows(ResourceNotFoundException.class, () ->
                punishmentService.findByPunishmentId(punishmentId)
        );

        verify(punishRepository).findByPunishmentId(punishmentId);
    }

    @Test
    void testFindByPunishmentId_ThrowsWhenArchived() {
        String punishmentId = "archivedId";
        Punishment p = new Punishment();
        p.setArchived(true);

        when(punishRepository.findByPunishmentId(punishmentId)).thenReturn(p);

        assertThrows(ResourceNotFoundException.class, () ->
                punishmentService.findByPunishmentId(punishmentId)
        );

        verify(punishRepository).findByPunishmentId(punishmentId);
    }

    @Test
    void testFindAllSchool_ReturnsUnarchivedPunishments() {
        Punishment p1 = new Punishment();
        p1.setArchived(false);

        when(punishmentQueryService.FetchPunishmentDataByArchivedAndSchool(false)).thenReturn(List.of(p1));

        List<Punishment> result = punishmentService.findAllSchool();

        assertEquals(1, result.size());
        assertFalse(result.get(0).isArchived());
        verify(punishmentQueryService).FetchPunishmentDataByArchivedAndSchool(false);
    }

    @Test
    void testFindAllPunishmentsByStudentEmail_ReturnsUnarchivedPunishments() {
        Punishment p1 = new Punishment();
        p1.setArchived(false);

        when(punishmentQueryService.LoggedInStudentFetchPunishmentDataByArchivedAndSchool(false))
                .thenReturn(List.of(p1));

        List<Punishment> result = punishmentService.findAllPunishmentsByStudentEmail();

        assertEquals(1, result.size());
        assertFalse(result.get(0).isArchived());
        verify(punishmentQueryService).LoggedInStudentFetchPunishmentDataByArchivedAndSchool(false);
    }

    @Test
    void createNewPunishForm_ThrowsWhenNoDescription() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setInfractionDescription("");
        IllegalArgumentException thrown = assertThrows(IllegalArgumentException.class, () -> {
            punishmentService.createNewPunishForm(request);
        });
        assertEquals("Infraction description is required.", thrown.getMessage());
    }

//    @Test
//    void createNewPunishForm_Level4_TriggersOfficeReferralAndClosesPunishment() throws MessagingException {
//        // Arrange
//        PunishmentFormRequest request = mockPunishRequest();
//        request.setInfractionName("Some Infraction");
//        request.setInfractionDescription("Some description");
//        request.setStudentEmail("student@example.com");
//
//        Punishment closed1 = new Punishment();
//        closed1.setClosedTimes(1);
//        Punishment closed2 = new Punishment();
//        closed2.setClosedTimes(2);
//        Punishment closed3 = new Punishment();
//        closed3.setClosedTimes(3);
//
//        Student student = new Student();
//        student.setStudentEmail("student@example.com");
//        student.setSchool("Test School");
//        student.setFirstName("John");
//        student.setLastName("Doe");
//        student.setParentEmail("parent@example.com");
//        student.setSpotters(List.of("spotter1@example.com"));
//
//        when(studentRepository.findByStudentEmailIgnoreCase(anyString())).thenReturn(student);
//
//        School school = new School();
//        school.setMaxPunishLevel(4);
//        school.setSchoolName("Test School");
//        when(schoolRepository.findSchoolBySchoolName(anyString())).thenReturn(school);
//
//        // Simulate closed punishments so that levelCheck returns "4"
//        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(anyString(), anyString(), eq("CLOSED")))
//                .thenReturn(List.of(closed1, closed2, closed3));
//
//        // Mock infraction fetch for level 4
//        Infraction infraction = new Infraction();
//        infraction.setInfractionLevel("4");
//        infraction.setInfractionName("Some Infraction");
//        infraction.setInfractionId("1L");
//        when(infractionRepository.findByInfractionNameAndInfractionLevel(eq("Some Infraction"), anyString()))
//                .thenReturn(infraction);
//        // No open punishments found
//        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(anyString(), anyString(), eq("OPEN"))).thenReturn(Collections.emptyList());
//        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(anyString(), anyString(), eq("PENDING"))).thenReturn(Collections.emptyList());
//
//        // Mock office referral service call
//        when(officeReferralService.createNewOfficeReferral(any())).thenReturn(null);
//
//        // Mock punishRepository.save to return saved punishment
//        when(punishRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
//
//        // Act
//        PunishmentResponse response = punishmentService.createNewPunishForm(request);
//
//        // Assert
//        assertNotNull(response);
//        verify(officeReferralService, times(1)).createNewOfficeReferral(any());
//        verify(punishRepository, atLeastOnce()).save(any());
//        // Verify email service called (sendEmailBasedOnType or sendCFREmailBasedOnType)
//        verify(emailService, atLeastOnce()).sendEmail(any(), any(), any());
//    }

//    @Test
//    void createNewPunishForm_AdminReferral_SetsStatusOpenAndSendsEmail() throws MessagingException {
//        PunishmentFormRequest request = mockPunishRequest();
//        request.setAdminReferral(true);
//        request.setInfractionDescription("Admin referral description");
//        request.setInfractionName("Some Infraction");
//
//        Student student = new Student();
//        student.setStudentEmail(request.getStudentEmail());
//        student.setSchool("Test School");
//        when(studentRepository.findByStudentEmailIgnoreCase(anyString())).thenReturn(student);
//
//        School school = new School();
//        school.setMaxPunishLevel(3);
//        school.setSchoolName("Test School");
//        when(schoolRepository.findSchoolBySchoolName(anyString())).thenReturn(school);
//
//        Infraction infraction = new Infraction();
//        infraction.setInfractionName("Some Infraction");
//        infraction.setInfractionLevel("1");
//        infraction.setInfractionId("1L");
//        when(infractionRepository.findByInfractionName(anyString())).thenReturn(infraction);
//
//        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(anyString(), anyString(), eq("CLOSED")))
//                .thenReturn(Collections.emptyList());
//
//        when(punishRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
//
//        // Act
//        PunishmentResponse response = punishmentService.createNewPunishForm(request);
//
//        // Assert
//        assertNotNull(response);
//        verify(punishRepository, times(1)).save(any());
//        verify(emailService, times(1)).sendEmail(any(), any(), any());
//    }

//    @Test
//    void createNewPunishForm_PositiveBehaviorShoutOut_TransfersCurrencyAndSendsEmail() throws MessagingException {
//        PunishmentFormRequest request = mockPunishRequest();
//        request.setInfractionName("Positive Behavior Shout Out!");
//        request.setCurrency(5);
//        request.setInfractionDescription("Good job!");
//
//        Student student = new Student();
//        student.setStudentEmail(request.getStudentEmail());
//        student.setSchool("Test School");
//        when(studentRepository.findByStudentEmailIgnoreCase(anyString())).thenReturn(student);
//
//        School school = new School();
//        school.setMaxPunishLevel(3);
//        school.setSchoolName("Test School");
//        when(schoolRepository.findSchoolBySchoolName(anyString())).thenReturn(school);
//
//        Infraction infraction = new Infraction();
//        infraction.setInfractionName("Positive Behavior Shout Out!");
//        infraction.setInfractionLevel("1");
//        infraction.setInfractionId("1L");
////        when(infractionRepository.findByInfractionName(anyString())).thenReturn(infraction);
//        when(infractionRepository.findByInfractionName(null)).thenReturn(infraction);
//        when(infractionRepository.findByInfractionName("Positive Behavior Shout Out!")).thenReturn(infraction);
//
//
//        when(punishRepository.findByStudentEmailIgnoreCaseAndInfractionNameAndStatus(anyString(), anyString(), eq("CLOSED")))
//                .thenReturn(Collections.emptyList());
//
//        when(punishRepository.save(any())).thenAnswer(i -> i.getArguments()[0]);
//
//        // Act
//        PunishmentResponse response = punishmentService.createNewPunishForm(request);
//
//        // Assert
//        verify(employeeService, times(1)).transferCurrency(any());
//        verify(punishRepository, times(1)).save(any());
//        verify(emailService, times(1)).sendEmail(any(), any(), any());
//        assertNotNull(response);
//    }

    // Utility method to create a mock request with common fields
    private PunishmentFormRequest mockPunishRequest() {
        PunishmentFormRequest request = new PunishmentFormRequest();
        request.setStudentEmail("student@example.com");
        request.setTeacherEmail("teacher@example.com");
        request.setInfractionDescription("Test Infraction");
        request.setInfractionName("Test Infraction");
        request.setInfractionPeriod("2nd");
        request.setCurrency(0);
        request.setAdminReferral(false);
        request.setGuidanceDescription("");
        request.setPhoneLogDescription("");
        return request;
    }
}

