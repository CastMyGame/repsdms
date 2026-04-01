package com.reps.demogcloud.services.student;

import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.UpdateSpottersRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentSpotterServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private StudentSpotterService studentSpotterService;

    @Test
    void addAsSpotter_shouldAddSpottersToStudentsWithExistingSpotters() {
        Student student1 = new Student();
        student1.setStudentEmail("student1@test.com");
        student1.setSpotters(new ArrayList<>(List.of("existing@test.com")));

        Student student2 = new Student();
        student2.setStudentEmail("student2@test.com");
        student2.setSpotters(new ArrayList<>(List.of("old@test.com")));

        UpdateSpottersRequest request = new UpdateSpottersRequest();
        request.setStudentEmail(List.of("student1@test.com", "student2@test.com"));
        request.setSpotters(List.of("new1@test.com", "new2@test.com"));

        when(studentRepository.findByStudentEmailIgnoreCase("student1@test.com")).thenReturn(student1);
        when(studentRepository.findByStudentEmailIgnoreCase("student2@test.com")).thenReturn(student2);
        when(studentRepository.save(student1)).thenReturn(student1);
        when(studentRepository.save(student2)).thenReturn(student2);

        List<Student> result = studentSpotterService.addAsSpotter(request);

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals(
                List.of("existing@test.com", "new1@test.com", "new2@test.com"),
                student1.getSpotters()
        );
        assertEquals(
                List.of("old@test.com", "new1@test.com", "new2@test.com"),
                student2.getSpotters()
        );

        verify(studentRepository).findByStudentEmailIgnoreCase("student1@test.com");
        verify(studentRepository).findByStudentEmailIgnoreCase("student2@test.com");
        verify(studentRepository).save(student1);
        verify(studentRepository).save(student2);
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void addAsSpotter_shouldAddSpottersWhenStudentHasNoExistingSpotters() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setSpotters(null);

        UpdateSpottersRequest request = new UpdateSpottersRequest();
        request.setStudentEmail(List.of("student@test.com"));
        request.setSpotters(List.of("spotter1@test.com", "spotter2@test.com"));

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(studentRepository.save(student)).thenReturn(student);

        List<Student> result = studentSpotterService.addAsSpotter(request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(List.of("spotter1@test.com", "spotter2@test.com"), student.getSpotters());

        verify(studentRepository).findByStudentEmailIgnoreCase("student@test.com");
        verify(studentRepository).save(student);
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void addAsSpotter_shouldReturnEmptyListWhenNoStudentEmailsProvided() {
        UpdateSpottersRequest request = new UpdateSpottersRequest();
        request.setStudentEmail(List.of());
        request.setSpotters(List.of("spotter@test.com"));

        List<Student> result = studentSpotterService.addAsSpotter(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verifyNoInteractions(studentRepository);
    }

    @Test
    void deleteSpotters_shouldRemoveRequestedSpottersFromStudents() {
        Student student1 = new Student();
        student1.setStudentEmail("student1@test.com");
        student1.setSpotters(new ArrayList<>(List.of("keep@test.com", "remove1@test.com", "remove2@test.com")));

        Student student2 = new Student();
        student2.setStudentEmail("student2@test.com");
        student2.setSpotters(new ArrayList<>(List.of("remove1@test.com", "keep2@test.com")));

        UpdateSpottersRequest request = new UpdateSpottersRequest();
        request.setStudentEmail(List.of("student1@test.com", "student2@test.com"));
        request.setSpotters(List.of("remove1@test.com", "remove2@test.com"));

        when(studentRepository.findByStudentEmailIgnoreCase("student1@test.com")).thenReturn(student1);
        when(studentRepository.findByStudentEmailIgnoreCase("student2@test.com")).thenReturn(student2);
        when(studentRepository.save(student1)).thenReturn(student1);
        when(studentRepository.save(student2)).thenReturn(student2);

        List<Student> result = studentSpotterService.deleteSpotters(request);

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals(List.of("keep@test.com"), student1.getSpotters());
        assertEquals(List.of("keep2@test.com"), student2.getSpotters());

        verify(studentRepository).findByStudentEmailIgnoreCase("student1@test.com");
        verify(studentRepository).findByStudentEmailIgnoreCase("student2@test.com");
        verify(studentRepository).save(student1);
        verify(studentRepository).save(student2);
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void deleteSpotters_shouldHandleNullExistingSpotters() {
        Student student = new Student();
        student.setStudentEmail("student@test.com");
        student.setSpotters(null);

        UpdateSpottersRequest request = new UpdateSpottersRequest();
        request.setStudentEmail(List.of("student@test.com"));
        request.setSpotters(List.of("remove@test.com"));

        when(studentRepository.findByStudentEmailIgnoreCase("student@test.com")).thenReturn(student);
        when(studentRepository.save(student)).thenReturn(student);

        List<Student> result = studentSpotterService.deleteSpotters(request);

        assertNotNull(result);
        assertEquals(1, result.size());
        assertNotNull(student.getSpotters());
        assertTrue(student.getSpotters().isEmpty());

        verify(studentRepository).findByStudentEmailIgnoreCase("student@test.com");
        verify(studentRepository).save(student);
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void deleteSpotters_shouldReturnEmptyListWhenNoStudentEmailsProvided() {
        UpdateSpottersRequest request = new UpdateSpottersRequest();
        request.setStudentEmail(List.of());
        request.setSpotters(List.of("remove@test.com"));

        List<Student> result = studentSpotterService.deleteSpotters(request);

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verifyNoInteractions(studentRepository);
    }

    @Test
    void removeSpotterByEmail_shouldRemoveSpotterAndSaveStudent() {
        Student inputStudent = new Student();
        inputStudent.setStudentIdNumber("S-1");

        Student existingStudent = new Student();
        existingStudent.setStudentIdNumber("S-1");
        existingStudent.setSpotters(new ArrayList<>(List.of("keep@test.com", "remove@test.com")));

        when(studentRepository.findByStudentIdNumber("S-1")).thenReturn(existingStudent);
        when(studentRepository.save(existingStudent)).thenReturn(existingStudent);

        Student result = studentSpotterService.removeSpotterByEmail("remove@test.com", inputStudent);

        assertNotNull(result);
        assertEquals(List.of("keep@test.com"), existingStudent.getSpotters());

        verify(studentRepository).findByStudentIdNumber("S-1");
        verify(studentRepository).save(existingStudent);
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void removeSpotterByEmail_shouldLeaveSpottersUnchangedWhenEmailNotPresent() {
        Student inputStudent = new Student();
        inputStudent.setStudentIdNumber("S-1");

        Student existingStudent = new Student();
        existingStudent.setStudentIdNumber("S-1");
        existingStudent.setSpotters(new ArrayList<>(List.of("keep@test.com")));

        when(studentRepository.findByStudentIdNumber("S-1")).thenReturn(existingStudent);
        when(studentRepository.save(existingStudent)).thenReturn(existingStudent);

        Student result = studentSpotterService.removeSpotterByEmail("missing@test.com", inputStudent);

        assertNotNull(result);
        assertEquals(List.of("keep@test.com"), existingStudent.getSpotters());

        verify(studentRepository).findByStudentIdNumber("S-1");
        verify(studentRepository).save(existingStudent);
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void findBySpotter_shouldDelegateToRepository() {
        List<Student> students = List.of(new Student(), new Student());

        when(studentRepository.findBySpottersContainsIgnoreCase("spotter@test.com")).thenReturn(students);

        List<Student> result = studentSpotterService.findBySpotter("spotter@test.com");

        assertSame(students, result);

        verify(studentRepository).findBySpottersContainsIgnoreCase("spotter@test.com");
        verifyNoMoreInteractions(studentRepository);
    }

    @Test
    void findBySpotter_shouldReturnEmptyListWhenRepositoryReturnsEmpty() {
        when(studentRepository.findBySpottersContainsIgnoreCase("spotter@test.com")).thenReturn(List.of());

        List<Student> result = studentSpotterService.findBySpotter("spotter@test.com");

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(studentRepository).findBySpottersContainsIgnoreCase("spotter@test.com");
        verifyNoMoreInteractions(studentRepository);
    }
}
