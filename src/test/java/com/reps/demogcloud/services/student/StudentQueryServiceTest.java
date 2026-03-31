package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.utils.SchoolUtils;
import com.reps.demogcloud.utils.StudentUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentQueryServiceTest {

    @Mock
    private StudentUtils studentUtils;

    @Mock
    private SchoolUtils schoolUtils;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private PunishRepository punishRepository;

    @Mock
    private SecurityContext securityContext;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private StudentQueryService studentQueryService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void getIssList_shouldReturnUniqueStudentsWithOpenPunishmentsOlderThanOneWorkday() {
        Punishment p1 = new Punishment();
        p1.setStatus("OPEN");
        p1.setStudentEmail("student1@test.com");
        p1.setTimeCreated(LocalDate.now().minusDays(3));

        Punishment p2 = new Punishment();
        p2.setStatus("OPEN");
        p2.setStudentEmail("student1@test.com");
        p2.setTimeCreated(LocalDate.now().minusDays(4));

        Punishment p3 = new Punishment();
        p3.setStatus("CLOSED");
        p3.setStudentEmail("student2@test.com");
        p3.setTimeCreated(LocalDate.now().minusDays(3));

        Punishment p4 = new Punishment();
        p4.setStatus("OPEN");
        p4.setStudentEmail("student3@test.com");
        p4.setTimeCreated(LocalDate.now().minusDays(1));

        Student s1 = new Student();
        s1.setFirstName("John");
        s1.setLastName("Doe");

        when(punishRepository.findAllBySchoolAndArchived("Burke High", false))
                .thenReturn(List.of(p1, p2, p3, p4));
        when(studentUtils.getWorkDaysBetweenTwoDates(eq(p1.getTimeCreated()), any(LocalDate.class))).thenReturn(2);
        when(studentUtils.getWorkDaysBetweenTwoDates(eq(p2.getTimeCreated()), any(LocalDate.class))).thenReturn(3);
        when(studentUtils.getWorkDaysBetweenTwoDates(eq(p4.getTimeCreated()), any(LocalDate.class))).thenReturn(1);
        when(studentRepository.findByStudentEmailIgnoreCase("student1@test.com")).thenReturn(s1);

        List<PunishmentDTO> result = studentQueryService.getIssList("Burke High");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("student1@test.com", result.get(0).getStudentEmail());
        assertEquals("John", result.get(0).getStudentFirstName());
        assertEquals("Doe", result.get(0).getStudentLastName());

        verify(punishRepository).findAllBySchoolAndArchived("Burke High", false);
        verify(studentRepository).findByStudentEmailIgnoreCase("student1@test.com");
    }

    @Test
    void getIssList_shouldSkipWhenStudentLookupReturnsNull() {
        Punishment p1 = new Punishment();
        p1.setStatus("OPEN");
        p1.setStudentEmail("student1@test.com");
        p1.setTimeCreated(LocalDate.now().minusDays(3));

        when(punishRepository.findAllBySchoolAndArchived("Burke High", false))
                .thenReturn(List.of(p1));
        when(studentUtils.getWorkDaysBetweenTwoDates(eq(p1.getTimeCreated()), any(LocalDate.class))).thenReturn(2);
        when(studentRepository.findByStudentEmailIgnoreCase("student1@test.com")).thenReturn(null);

        List<PunishmentDTO> result = studentQueryService.getIssList("Burke High");

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getDetentionList_shouldReturnUniqueStudentsWithOpenPunishmentsAtOneWorkday() {
        Punishment p1 = new Punishment();
        p1.setStatus("OPEN");
        p1.setStudentEmail("student1@test.com");
        p1.setTimeCreated(LocalDate.now().minusDays(1));

        Punishment p2 = new Punishment();
        p2.setStatus("OPEN");
        p2.setStudentEmail("student1@test.com");
        p2.setTimeCreated(LocalDate.now().minusDays(1));

        Punishment p3 = new Punishment();
        p3.setStatus("OPEN");
        p3.setStudentEmail("student2@test.com");
        p3.setTimeCreated(LocalDate.now().minusDays(2));

        Student s1 = new Student();
        s1.setFirstName("Jane");
        s1.setLastName("Smith");

        when(punishRepository.findAllBySchoolAndArchived("Burke High", false))
                .thenReturn(List.of(p1, p2, p3));

        lenient().when(studentUtils.getWorkDaysBetweenTwoDates(eq(p1.getTimeCreated()), any(LocalDate.class))).thenReturn(1);
        lenient().when(studentUtils.getWorkDaysBetweenTwoDates(eq(p2.getTimeCreated()), any(LocalDate.class))).thenReturn(1);
        lenient().when(studentUtils.getWorkDaysBetweenTwoDates(eq(p3.getTimeCreated()), any(LocalDate.class))).thenReturn(2);

        when(studentRepository.findByStudentEmailIgnoreCase("student1@test.com")).thenReturn(s1);

        List<PunishmentDTO> result = studentQueryService.getDetentionList("Burke High");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("student1@test.com", result.get(0).getStudentEmail());
        assertEquals("Jane", result.get(0).getStudentFirstName());
        assertEquals("Smith", result.get(0).getStudentLastName());
    }

    @Test
    void findStudentByParentEmail_shouldReturnNonArchivedStudents() {
        Student active = new Student();
        active.setArchived(false);

        Student archived = new Student();
        archived.setArchived(true);

        when(studentRepository.findByParentEmail("parent@test.com"))
                .thenReturn(List.of(active, archived));

        List<Student> result = studentQueryService.findStudentByParentEmail("parent@test.com");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertSame(active, result.get(0));
    }

    @Test
    void findStudentByParentEmail_shouldThrowWhenNoNonArchivedStudentsExist() {
        Student archived = new Student();
        archived.setArchived(true);

        when(studentRepository.findByParentEmail("parent@test.com"))
                .thenReturn(List.of(archived));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentQueryService.findStudentByParentEmail("parent@test.com")
        );

        assertEquals("That student does not exist", exception.getMessage());
    }

    @Test
    void findByStudentLastName_shouldReturnNonArchivedStudents() {
        Student active = new Student();
        active.setArchived(false);

        Student archived = new Student();
        archived.setArchived(true);

        StudentQueryService spy = spy(studentQueryService);
        doReturn(List.of(active, archived)).when(spy).findByLastNameAndSchool("Doe");

        List<Student> result = spy.findByStudentLastName("Doe");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertSame(active, result.get(0));
    }

    @Test
    void findByStudentLastName_shouldThrowWhenNoNonArchivedStudentsExist() {
        Student archived = new Student();
        archived.setArchived(true);

        StudentQueryService spy = spy(studentQueryService);
        doReturn(List.of(archived)).when(spy).findByLastNameAndSchool("Doe");

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> spy.findByStudentLastName("Doe")
        );

        assertEquals("That student does not exist", exception.getMessage());
    }

    @Test
    void findByStudentEmail_shouldReturnStudent() throws Exception {
        Student student = new Student();

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);

        Student result = studentQueryService.findByStudentEmail("student@test.com");

        assertSame(student, result);
    }

    @Test
    void findByStudentEmail_shouldThrowWhenStudentNotFound() {
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);

        Exception exception = assertThrows(
                Exception.class,
                () -> studentQueryService.findByStudentEmail("student@test.com")
        );

        assertEquals("No student with that email exists", exception.getMessage());
    }

    @Test
    void findByStudentEmailList_shouldReturnStudents() throws Exception {
        Student s1 = new Student();
        Student s2 = new Student();

        when(studentRepository.findByStudentEmailIgnoreCase("student1@test.com")).thenReturn(s1);
        when(studentRepository.findByStudentEmailIgnoreCase("student2@test.com")).thenReturn(s2);

        List<Student> result = studentQueryService.findByStudentEmailList(
                List.of("student1@test.com", "student2@test.com")
        );

        assertNotNull(result);
        assertEquals(2, result.size());
        assertSame(s1, result.get(0));
        assertSame(s2, result.get(1));
    }

    @Test
    void findByStudentEmailList_shouldThrowWhenAnyStudentNotFound() {
        Student s1 = new Student();

        when(studentRepository.findByStudentEmailIgnoreCase("student1@test.com")).thenReturn(s1);
        when(studentRepository.findByStudentEmailIgnoreCase("student2@test.com")).thenReturn(null);

        Exception exception = assertThrows(
                Exception.class,
                () -> studentQueryService.findByStudentEmailList(
                        List.of("student1@test.com", "student2@test.com")
                )
        );

        assertEquals("No student with that email exists", exception.getMessage());
    }

    @Test
    void findByLoggedInStudent_shouldReturnStudent() throws Exception {
        Student student = new Student();

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@test.com");
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);

        Student result = studentQueryService.findByLoggedInStudent();

        assertSame(student, result);
    }

    @Test
    void findByLoggedInStudent_shouldThrowWhenNotFound() {
        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("student@test.com");
        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(null);

        Exception exception = assertThrows(
                Exception.class,
                () -> studentQueryService.findByLoggedInStudent()
        );

        assertEquals("No student with that email exists", exception.getMessage());
    }

    @Test
    void getAllStudents_shouldReturnStudentsSortedByLastName() {
        Student zStudent = new Student();
        zStudent.setLastName("Zimmer");

        Student aStudent = new Student();
        aStudent.setLastName("Anderson");

        StudentQueryService spy = spy(studentQueryService);
        doReturn(new java.util.ArrayList<>(List.of(zStudent, aStudent)))
                .when(spy).findByArchivedAndSchool(false);

        List<Student> result = spy.getAllStudents(false);

        assertEquals(2, result.size());
        assertEquals("Anderson", result.get(0).getLastName());
        assertEquals("Zimmer", result.get(1).getLastName());
    }

    @Test
    void findByStudentId_shouldReturnStudent() {
        Student student = new Student();

        when(studentRepository.findByStudentIdNumber("S1")).thenReturn(student);

        Student result = studentQueryService.findByStudentId("S1");

        assertSame(student, result);
    }

    @Test
    void findByStudentId_shouldThrowWhenNotFound() {
        when(studentRepository.findByStudentIdNumber("S1")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentQueryService.findByStudentId("S1")
        );

        assertEquals("No students with that ID exist", exception.getMessage());
    }

    @Test
    void findAllStudentArchived_shouldReturnArchivedRecords() {
        List<Student> students = List.of(new Student());

        when(studentRepository.findByArchived(true)).thenReturn(students);

        List<Student> result = studentQueryService.findAllStudentArchived(true);

        assertSame(students, result);
    }

    @Test
    void findAllStudentArchived_shouldThrowWhenNoArchivedRecordsExist() {
        when(studentRepository.findByArchived(true)).thenReturn(List.of());

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentQueryService.findAllStudentArchived(true)
        );

        assertEquals("No Archived Records exist in students table", exception.getMessage());
    }

    @Test
    void findBySchool_shouldDelegateToRepository() {
        List<Student> students = List.of(new Student());

        when(studentRepository.findBySchool("Burke High")).thenReturn(students);

        List<Student> result = studentQueryService.findBySchool("Burke High");

        assertSame(students, result);
    }

    @Test
    void findByLastNameAndSchool_shouldDelegateToRepositoryUsingFetchedSchool() {
        List<Student> students = List.of(new Student());

        when(schoolUtils.fetchSchoolName()).thenReturn("Burke High");
        when(studentRepository.findByArchivedAndLastNameAndSchool(false, "Doe", "Burke High"))
                .thenReturn(students);

        List<Student> result = studentQueryService.findByLastNameAndSchool("Doe");

        assertSame(students, result);
    }

    @Test
    void findByArchivedAndSchool_shouldReturnStudents() {
        List<Student> students = List.of(new Student());

        when(schoolUtils.fetchSchoolName()).thenReturn("Burke High");
        when(studentRepository.findByArchivedAndSchool(false, "Burke High")).thenReturn(students);

        List<Student> result = studentQueryService.findByArchivedAndSchool(false);

        assertSame(students, result);
    }

    @Test
    void findByArchivedAndSchool_shouldReturnEmptyListWhenRepositoryReturnsEmpty() {
        when(schoolUtils.fetchSchoolName()).thenReturn("Burke High");
        when(studentRepository.findByArchivedAndSchool(false, "Burke High")).thenReturn(List.of());

        List<Student> result = studentQueryService.findByArchivedAndSchool(false);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }
}
