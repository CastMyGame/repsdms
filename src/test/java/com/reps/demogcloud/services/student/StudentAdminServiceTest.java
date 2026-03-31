package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.SchoolRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
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

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentAdminServiceTest {

    @Mock private SchoolRepository schoolRepository;
    @Mock private StudentRepository studentRepository;
    @Mock private StudentUtils studentUtils;

    @Mock private SecurityContext securityContext;
    @Mock private Authentication authentication;

    @InjectMocks
    private StudentAdminService service;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void addTimeToStudent_shouldHandleMinuteOverflow() {
        Student student = new Student();
        student.setStudentEmail("test@test.com");
        student.setTimeBank(new Student.TimeBank(1, 50));

        when(studentRepository.findByStudentEmailIgnoreCase("test@test.com")).thenReturn(student);
        when(studentRepository.save(student)).thenReturn(student);

        Student result = service.addTimeToStudent("test@test.com", 1, 20);

        assertEquals(3, result.getTimeBank().getHours());
        assertEquals(10, result.getTimeBank().getMinutes());
    }

    @Test
    void addTimeToStudent_shouldThrowWhenNotFound() {
        when(studentRepository.findByStudentEmailIgnoreCase("missing")).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> service.addTimeToStudent("missing", 1, 1));
    }

    @Test
    void updateStudents_shouldUpdateOnlyChangedFields() {
        Student existing = new Student();
        existing.setStudentIdNumber("1");
        existing.setFirstName("Old");

        Student updated = new Student();
        updated.setStudentIdNumber("1");
        updated.setFirstName("New");

        when(studentRepository.findById("1")).thenReturn(Optional.of(existing));
        when(studentUtils.isDifferent("Old", "New")).thenReturn(true);

        List<Student> result = service.updateStudents(List.of(updated));

        assertEquals(1, result.size());
        assertEquals("New", result.get(0).getFirstName());

        verify(studentRepository).save(existing);
    }

    @Test
    void updateStudentNotes_shouldAddNewEvent() {
        Student student = new Student();
        student.setStudentIdNumber("1");

        ThreadEvent event = new ThreadEvent();
        event.setEvent("NOTE");
        event.setContent("Test");

        when(studentRepository.findById("1")).thenReturn(Optional.of(student));
        when(studentRepository.save(any())).thenReturn(student);

        Student result = service.updateStudentNotes("1", event);

        assertNotNull(result.getNotesArray());
        assertEquals(1, result.getNotesArray().size());
    }

    @Test
    void updateStudentNotes_shouldReturnNullWhenNotFound() {
        when(studentRepository.findById("1")).thenReturn(Optional.empty());

        Student result = service.updateStudentNotes("1", new ThreadEvent());

        assertNull(result);
    }

    @Test
    void getStudentSchool_shouldReturnSchool() {
        Student student = new Student();
        student.setSchool("Test School");

        School school = new School();

        SecurityContextHolder.setContext(securityContext);
        when(securityContext.getAuthentication()).thenReturn(authentication);
        when(authentication.getName()).thenReturn("test@test.com");

        when(studentRepository.findByStudentEmailIgnoreCase("test@test.com")).thenReturn(student);
        when(schoolRepository.findBySchoolNameIgnoreCase("Test School")).thenReturn(Optional.of(school));

        Optional<School> result = service.getStudentSchool();

        assertTrue(result.isPresent());
    }

    @Test
    void massAssignForSchool_shouldFormatPhoneNumbers() {
        Student student = new Student();
        student.setParentPhoneNumber("843-123-4567");

        when(studentRepository.findAll()).thenReturn(List.of(student));
        when(studentRepository.save(student)).thenReturn(student);

        List<Student> result = service.massAssignForSchool();

        assertEquals("+18431234567", result.get(0).getParentPhoneNumber());
    }
}