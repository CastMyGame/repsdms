package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.UserContextService;
import com.reps.demogcloud.utils.SchoolUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PunishmentQueryServiceTest {

    @Mock
    private PunishRepository punishRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private InfractionRepository infractionRepository;

    @Mock
    private MongoTemplate mongoTemplate;

    @Mock
    private SchoolUtils schoolUtils;

    @Mock
    private UserContextService userContextService;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private PunishmentQueryService punishmentQueryService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void findByPunishmentId_shouldReturnPunishment_whenExistsAndNotArchived() {
        Punishment punishment = punishment("P1", false);

        when(punishRepository.findByPunishmentId("P1")).thenReturn(punishment);

        Punishment result = punishmentQueryService.findByPunishmentId("P1");

        assertSame(punishment, result);
        verify(punishRepository).findByPunishmentId("P1");
    }

    @Test
    void findByPunishmentId_shouldThrow_whenMissing() {
        when(punishRepository.findByPunishmentId("P1")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> punishmentQueryService.findByPunishmentId("P1")
        );

        assertEquals("No punishment with that ID exists", exception.getMessage());
        verify(punishRepository).findByPunishmentId("P1");
    }

    @Test
    void findByPunishmentId_shouldThrow_whenArchived() {
        Punishment punishment = punishment("P1", true);

        when(punishRepository.findByPunishmentId("P1")).thenReturn(punishment);

        assertThrows(ResourceNotFoundException.class, () -> punishmentQueryService.findByPunishmentId("P1"));
        verify(punishRepository).findByPunishmentId("P1");
    }

    @Test
    void findAllByArchived_shouldDelegate() {
        List<Punishment> punishments = List.of(punishment("P1", false));
        when(punishRepository.findByArchived(true)).thenReturn(punishments);

        List<Punishment> result = punishmentQueryService.findAllByArchived(true);

        assertSame(punishments, result);
        verify(punishRepository).findByArchived(true);
    }

    @Test
    void findAllForStudent_shouldFilterOutArchived() {
        Punishment active = punishment("P1", false);
        active.setStudentEmail("student@school.com");

        Punishment archived = punishment("P2", true);
        archived.setStudentEmail("student@school.com");

        when(punishRepository.findByStudentEmailIgnoreCase("student@school.com"))
                .thenReturn(List.of(active, archived));

        List<Punishment> result = punishmentQueryService.findAllForStudent("student@school.com");

        assertEquals(1, result.size());
        assertSame(active, result.get(0));
        verify(punishRepository).findByStudentEmailIgnoreCase("student@school.com");
    }

    @Test
    void updateMapIndex_shouldUpdateAndSave() {
        Punishment punishment = punishment("P1", false);
        punishment.setMapIndex(1);

        when(punishRepository.findByPunishmentId("P1")).thenReturn(punishment);
        when(punishRepository.save(punishment)).thenReturn(punishment);

        Punishment result = punishmentQueryService.updateMapIndex("P1", 5);

        assertEquals(5, result.getMapIndex());
        verify(punishRepository).findByPunishmentId("P1");
        verify(punishRepository).save(punishment);
    }

    @Test
    void updateMapIndex_shouldThrow_whenMissing() {
        when(punishRepository.findByPunishmentId("P1")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> punishmentQueryService.updateMapIndex("P1", 5)
        );

        assertEquals("No Punishment with Id P1 exists", exception.getMessage());
        verify(punishRepository).findByPunishmentId("P1");
    }

    @Test
    void updateDescriptions_shouldOnlySaveRecordsWithMoreThanOneDescription() {
        Punishment p1 = punishment("P1", false);
        p1.setInfractionDescription(new ArrayList<>(List.of("remove-me", "keep-me")));

        Punishment p2 = punishment("P2", false);
        p2.setInfractionDescription(new ArrayList<>(List.of("only-one")));

        when(punishRepository.findAll()).thenReturn(List.of(p1, p2));
        when(punishRepository.save(p1)).thenReturn(p1);

        List<Punishment> result = punishmentQueryService.updateDescriptions();

        assertEquals(1, result.size());
        assertEquals(List.of("keep-me"), p1.getInfractionDescription());
        verify(punishRepository).findAll();
        verify(punishRepository).save(p1);
        verify(punishRepository, never()).save(p2);
    }

    @Test
    void updateInfractionName_shouldPopulateAndSaveAll() {
        Punishment p1 = punishment("P1", false);
        p1.setInfractionId("I1");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("I1");
        infraction.setInfractionName("Tardy");

        when(punishRepository.findAll()).thenReturn(List.of(p1));
        when(infractionRepository.findByInfractionId("I1")).thenReturn(infraction);
        when(punishRepository.save(p1)).thenReturn(p1);

        List<Punishment> result = punishmentQueryService.updateInfractionName();

        assertEquals(1, result.size());
        assertEquals("Tardy", p1.getInfractionName());
        verify(infractionRepository).findByInfractionId("I1");
        verify(punishRepository).save(p1);
    }

    @Test
    void updateInfractionLevel_shouldPopulateAndSaveAll() {
        Punishment p1 = punishment("P1", false);
        p1.setInfractionId("I1");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("I1");
        infraction.setInfractionLevel("3");

        when(punishRepository.findAll()).thenReturn(List.of(p1));
        when(infractionRepository.findByInfractionId("I1")).thenReturn(infraction);
        when(punishRepository.save(p1)).thenReturn(p1);

        List<Punishment> result = punishmentQueryService.updateInfractionLevel();

        assertEquals(1, result.size());
        assertEquals("3", p1.getInfractionLevel());
        verify(infractionRepository).findByInfractionId("I1");
        verify(punishRepository).save(p1);
    }

    @Test
    void updateStudentEmails_shouldSaveAll() {
        Punishment p1 = punishment("P1", false);
        p1.setStudentEmail("student@school.com");

        when(punishRepository.findAll()).thenReturn(List.of(p1));
        when(punishRepository.save(p1)).thenReturn(p1);

        List<Punishment> result = punishmentQueryService.updateStudentEmails();

        assertEquals(1, result.size());
        assertEquals("student@school.com", result.get(0).getStudentEmail());
        verify(punishRepository).findAll();
        verify(punishRepository).save(p1);
    }

    @Test
    void updateSchools_shouldPopulateSchoolFromStudent() {
        Punishment p1 = punishment("P1", false);
        p1.setStudentEmail("student@school.com");

        Student student = new Student();
        student.setStudentEmail("student@school.com");
        student.setSchool("Test School");

        when(punishRepository.findAll()).thenReturn(List.of(p1));
        when(studentRepository.findByStudentEmailIgnoreCase("student@school.com")).thenReturn(student);
        when(punishRepository.save(p1)).thenReturn(p1);

        List<Punishment> result = punishmentQueryService.updateSchools();

        assertEquals(1, result.size());
        assertEquals("Test School", p1.getSchool());
        verify(studentRepository).findByStudentEmailIgnoreCase("student@school.com");
        verify(punishRepository).save(p1);
    }

    @Test
    void findByStudentEmailAndInfraction_shouldReturnNonArchivedResults() {
        Punishment active = punishment("P1", false);
        Punishment archived = punishment("P2", true);

        when(punishRepository.findByStudentEmailAndInfractionId("student@school.com", "I1"))
                .thenReturn(List.of(active, archived));

        List<Punishment> result =
                punishmentQueryService.findByStudentEmailAndInfraction("student@school.com", "I1");

        assertEquals(1, result.size());
        assertSame(active, result.get(0));
    }

    @Test
    void findByStudentEmailAndInfraction_shouldThrow_whenNoUsableResults() {
        when(punishRepository.findByStudentEmailAndInfractionId("student@school.com", "I1"))
                .thenReturn(List.of());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> punishmentQueryService.findByStudentEmailAndInfraction("student@school.com", "I1")
        );

        assertEquals("That student does not exist", exception.getMessage());
    }

    @Test
    void findAll_shouldReturnNonArchivedPunishments() {
        List<Punishment> punishments = List.of(punishment("P1", false));
        when(punishRepository.findByArchived(false)).thenReturn(punishments);

        List<Punishment> result = punishmentQueryService.findAll();

        assertSame(punishments, result);
        verify(punishRepository).findByArchived(false);
    }

    @Test
    void findAllPunishmentArchived_shouldReturnRecords_whenFound() {
        List<Punishment> punishments = List.of(punishment("P1", true));
        when(punishRepository.findByArchived(true)).thenReturn(punishments);

        List<Punishment> result = punishmentQueryService.findAllPunishmentArchived(true);

        assertSame(punishments, result);
        verify(punishRepository).findByArchived(true);
    }

    @Test
    void findAllPunishmentArchived_shouldThrow_whenEmpty() {
        when(punishRepository.findByArchived(true)).thenReturn(List.of());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> punishmentQueryService.findAllPunishmentArchived(true)
        );

        assertEquals("No Archived Records exist in punihsment table", exception.getMessage());
    }

    @Test
    void getAllPunishmentByStudentEmail_shouldDelegate() {
        List<Punishment> punishments = List.of(punishment("P1", false));
        when(punishRepository.getAllPunishmentByStudentEmail("student@school.com")).thenReturn(punishments);

        List<Punishment> result = punishmentQueryService.getAllPunishmentByStudentEmail("student@school.com");

        assertSame(punishments, result);
        verify(punishRepository).getAllPunishmentByStudentEmail("student@school.com");
    }

    @Test
    void getAllPunishmentForStudent_shouldDelegate() {
        List<Punishment> punishments = List.of(punishment("P1", false));
        when(punishRepository.findByStudentEmailIgnoreCase("student@school.com")).thenReturn(punishments);

        List<Punishment> result = punishmentQueryService.getAllPunishmentForStudent("student@school.com");

        assertSame(punishments, result);
        verify(punishRepository).findByStudentEmailIgnoreCase("student@school.com");
    }

    @Test
    void getAllOpenAssignments_shouldFilterArchived() {
        Punishment active = punishment("P1", false);
        Punishment archived = punishment("P2", true);

        when(punishRepository.findByStatusAndTimeCreatedBefore(eq("OPEN"), any(LocalDate.class)))
                .thenReturn(List.of(active, archived));

        List<Punishment> result = punishmentQueryService.getAllOpenAssignments();

        assertEquals(1, result.size());
        assertSame(active, result.get(0));
    }

    @SuppressWarnings("unchecked")
    @Test
    void getTeacherResponse_shouldReturnMappedResults() {
        Punishment punishment = punishment("P1", false);
        punishment.setStudentEmail("student@school.com");

        TeacherDTO dto = mock(TeacherDTO.class);
        AggregationResults<TeacherDTO> aggregationResults = mock(AggregationResults.class);

        when(aggregationResults.getMappedResults()).thenReturn(List.of(dto));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("Punishments"), eq(TeacherDTO.class)))
                .thenReturn(aggregationResults);

        List<TeacherDTO> result = punishmentQueryService.getTeacherResponse(List.of(punishment));

        assertEquals(1, result.size());
        assertSame(dto, result.get(0));
        verify(mongoTemplate).aggregate(any(Aggregation.class), eq("Punishments"), eq(TeacherDTO.class));
    }

    @Test
    void findByStatus_shouldReturnMatchesWithinSchool() {
        Punishment open = punishment("P1", false);
        open.setStatus("OPEN");

        Punishment closed = punishment("P2", false);
        closed.setStatus("CLOSED");

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(open, closed));

        List<Punishment> result = punishmentQueryService.findByStatus("OPEN");

        assertEquals(1, result.size());
        assertSame(open, result.get(0));
    }

    @Test
    void findByStatus_shouldThrow_whenNoMatches() {
        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School")).thenReturn(List.of());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> punishmentQueryService.findByStatus("OPEN")
        );

        assertEquals("No punishments with that status exist", exception.getMessage());
    }

    @Test
    void findAllSchool_shouldReturnSchoolScopedPunishments() {
        List<Punishment> punishments = List.of(punishment("P1", false));
        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School")).thenReturn(punishments);

        List<Punishment> result = punishmentQueryService.findAllSchool();

        assertSame(punishments, result);
    }

    @Test
    void findAllPunishmentsByStudentEmail_shouldReturnLoggedInStudentMatches() {
        Punishment mine = punishment("P1", false);
        mine.setStudentEmail("student@school.com");

        Punishment other = punishment("P2", false);
        other.setStudentEmail("other@school.com");

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@school.com");

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(mine, other));

        List<Punishment> result = punishmentQueryService.findAllPunishmentsByStudentEmail();

        assertEquals(1, result.size());
        assertSame(mine, result.get(0));
    }

    @Test
    void findByArchivedAndSchool_shouldUseUserContextSchool() {
        List<Punishment> punishments = List.of(punishment("P1", false));

        when(userContextService.getCurrentUserSchool()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School")).thenReturn(punishments);

        List<Punishment> result = punishmentQueryService.findByArchivedAndSchool(false);

        assertSame(punishments, result);
    }

    @Test
    void findByArchivedStatusAndStatus_shouldFilterByStatus() {
        Punishment open = punishment("P1", false);
        open.setStatus("OPEN");

        Punishment closed = punishment("P2", false);
        closed.setStatus("CLOSED");

        when(userContextService.getCurrentUserSchool()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(open, closed));

        List<Punishment> result = punishmentQueryService.findByArchivedStatusAndStatus(false, "OPEN");

        assertEquals(1, result.size());
        assertSame(open, result.get(0));
    }

    @Test
    void findByArchivedAndSchoolAndStatus_shouldFilterByStatus() {
        Punishment open = punishment("P1", false);
        open.setStatus("OPEN");

        Punishment closed = punishment("P2", false);
        closed.setStatus("CLOSED");

        when(userContextService.getCurrentUserSchool()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(open, closed));

        List<Punishment> result = punishmentQueryService.findByArchivedAndSchoolAndStatus(false, "OPEN");

        assertEquals(1, result.size());
        assertSame(open, result.get(0));
    }

    @Test
    void findByLoggedInTeacher_shouldFilterByTeacherEmail() {
        Punishment mine = punishment("P1", false);
        mine.setTeacherEmail("teacher@school.com");

        Punishment other = punishment("P2", false);
        other.setTeacherEmail("other@school.com");

        when(userContextService.getCurrentUsername()).thenReturn("teacher@school.com");
        when(userContextService.getCurrentUserSchool()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(mine, other));

        List<Punishment> result = punishmentQueryService.findByLoggedInTeacher(false);

        assertEquals(1, result.size());
        assertSame(mine, result.get(0));
    }

    @Test
    void findByLoggedInStudent_shouldFilterByStudentEmail() {
        Punishment mine = punishment("P1", false);
        mine.setStudentEmail("student@school.com");

        Punishment other = punishment("P2", false);
        other.setStudentEmail("other@school.com");

        when(userContextService.getCurrentUsername()).thenReturn("student@school.com");
        when(userContextService.getCurrentUserSchool()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(mine, other));

        List<Punishment> result = punishmentQueryService.findByLoggedInStudent(false);

        assertEquals(1, result.size());
        assertSame(mine, result.get(0));
    }

    @Test
    void filterPunishmentObjBySchool_shouldReturnOnlyMatchingSchool() {
        Punishment p1 = punishment("P1", false);
        p1.setStudentEmail("student1@school.com");

        Punishment p2 = punishment("P2", false);
        p2.setStudentEmail("student2@school.com");

        Student s1 = new Student();
        s1.setSchool("Test School");

        Student s2 = new Student();
        s2.setSchool("Other School");

        when(studentRepository.findByStudentEmailIgnoreCase("student1@school.com")).thenReturn(s1);
        when(studentRepository.findByStudentEmailIgnoreCase("student2@school.com")).thenReturn(s2);

        List<Punishment> result =
                punishmentQueryService.filterPunishmentObjBySchool(List.of(p1, p2), "Test School");

        assertEquals(1, result.size());
        assertSame(p1, result.get(0));
    }

    @Test
    void fetchPunishmentDataByArchivedAndSchool_shouldReturnEmptyList_whenNoResults() {
        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School")).thenReturn(List.of());

        List<Punishment> result = punishmentQueryService.fetchPunishmentDataByArchivedAndSchool(false);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void fetchPunishmentDataByArchivedAndSchoolAndStatus_shouldFilterResults() {
        Punishment open = punishment("P1", false);
        open.setStatus("OPEN");

        Punishment closed = punishment("P2", false);
        closed.setStatus("CLOSED");

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(open, closed));

        List<Punishment> result =
                punishmentQueryService.fetchPunishmentDataByArchivedAndSchoolAndStatus(false, "OPEN");

        assertEquals(1, result.size());
        assertSame(open, result.get(0));
    }

    @Test
    void fetchPunishmentDataByInfractionNameAndArchived_shouldReturnRepositoryResults() {
        List<Punishment> punishments = List.of(punishment("P1", false));

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(punishRepository.findByInfractionIdAndArchivedAndSchool("I1", false, "Test School"))
                .thenReturn(punishments);

        List<Punishment> result =
                punishmentQueryService.fetchPunishmentDataByInfractionNameAndArchived("I1", false);

        assertSame(punishments, result);
    }

    @Test
    void filterPunishmentsByTeacherEmail_shouldFilterIgnoringCase() {
        Punishment mine = punishment("P1", false);
        mine.setTeacherEmail("Teacher@School.com");

        Punishment other = punishment("P2", false);
        other.setTeacherEmail("other@school.com");

        List<Punishment> result =
                punishmentQueryService.filterPunishmentsByTeacherEmail(List.of(mine, other), "teacher@school.com");

        assertEquals(1, result.size());
        assertSame(mine, result.get(0));
    }

    @Test
    void filterPunishmentObjByStudent_shouldFilterIgnoringCase() {
        Punishment mine = punishment("P1", false);
        mine.setStudentEmail("Student@School.com");

        Punishment other = punishment("P2", false);
        other.setStudentEmail("other@school.com");

        List<Punishment> result =
                punishmentQueryService.filterPunishmentObjByStudent(List.of(mine, other), "student@school.com");

        assertEquals(1, result.size());
        assertSame(mine, result.get(0));
    }

    @Test
    void loggedInUserFetchPunishmentDataByArchivedAndSchool_shouldFilterByAuthenticationName() {
        Punishment mine = punishment("P1", false);
        mine.setTeacherEmail("teacher@school.com");

        Punishment other = punishment("P2", false);
        other.setTeacherEmail("other@school.com");

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("teacher@school.com");

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(mine, other));

        List<Punishment> result =
                punishmentQueryService.loggedInUserFetchPunishmentDataByArchivedAndSchool(false);

        assertEquals(1, result.size());
        assertSame(mine, result.get(0));
    }

    @Test
    void loggedInStudentFetchPunishmentDataByArchivedAndSchool_shouldFilterByAuthenticationName() {
        Punishment mine = punishment("P1", false);
        mine.setStudentEmail("student@school.com");

        Punishment other = punishment("P2", false);
        other.setStudentEmail("other@school.com");

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@school.com");

        when(schoolUtils.fetchSchoolName()).thenReturn("Test School");
        when(punishRepository.findByArchivedAndSchool(false, "Test School"))
                .thenReturn(List.of(mine, other));

        List<Punishment> result =
                punishmentQueryService.loggedInStudentFetchPunishmentDataByArchivedAndSchool(false);

        assertEquals(1, result.size());
        assertSame(mine, result.get(0));
    }

    private Punishment punishment(String id, boolean archived) {
        Punishment punishment = new Punishment();
        punishment.setPunishmentId(id);
        punishment.setArchived(archived);
        punishment.setStatus("OPEN");
        punishment.setTeacherEmail("teacher@school.com");
        punishment.setStudentEmail("student@school.com");
        punishment.setInfractionDescription(new ArrayList<>(List.of("desc")));
        return punishment;
    }
}
