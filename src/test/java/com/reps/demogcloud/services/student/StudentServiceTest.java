package com.reps.demogcloud.services.student;

import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.punishment.ThreadEvent;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.models.student.StudentRequest;
import com.reps.demogcloud.models.student.StudentResponse;
import com.reps.demogcloud.models.student.UpdateSpottersRequest;
import com.reps.demogcloud.services.StudentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StudentServiceTest {

    @Mock
    private StudentQueryService studentQueryService;

    @Mock
    private StudentMutationService studentMutationService;

    @Mock
    private StudentPointService studentPointService;

    @Mock
    private StudentSpotterService studentSpotterService;

    @Mock
    private StudentAdminService studentAdminService;

    @InjectMocks
    private StudentService studentService;

    @Test
    void findStudentByParentEmail_shouldReturnStudents() throws Exception {
        List<Student> students = List.of(new Student());

        when(studentQueryService.findStudentByParentEmail("parent@test.com")).thenReturn(students);

        List<Student> result = studentService.findStudentByParentEmail("parent@test.com");

        assertSame(students, result);
        verify(studentQueryService).findStudentByParentEmail("parent@test.com");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findStudentByParentEmail_shouldPropagateException() throws Exception {
        when(studentQueryService.findStudentByParentEmail("parent@test.com"))
                .thenThrow(new ResourceNotFoundException("not found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentService.findStudentByParentEmail("parent@test.com")
        );

        assertEquals("not found", exception.getMessage());
        verify(studentQueryService).findStudentByParentEmail("parent@test.com");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByStudentLastName_shouldReturnStudents() throws Exception {
        List<Student> students = List.of(new Student());

        when(studentQueryService.findByStudentLastName("Doe")).thenReturn(students);

        List<Student> result = studentService.findByStudentLastName("Doe");

        assertSame(students, result);
        verify(studentQueryService).findByStudentLastName("Doe");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByStudentLastName_shouldPropagateException() throws Exception {
        when(studentQueryService.findByStudentLastName("Doe"))
                .thenThrow(new ResourceNotFoundException("not found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentService.findByStudentLastName("Doe")
        );

        assertEquals("not found", exception.getMessage());
        verify(studentQueryService).findByStudentLastName("Doe");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByStudentEmail_shouldReturnStudent() throws Exception {
        Student student = new Student();

        when(studentQueryService.findByStudentEmail("student@test.com")).thenReturn(student);

        Student result = studentService.findByStudentEmail("student@test.com");

        assertSame(student, result);
        verify(studentQueryService).findByStudentEmail("student@test.com");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByStudentEmail_shouldPropagateException() throws Exception {
        when(studentQueryService.findByStudentEmail("student@test.com"))
                .thenThrow(new Exception("student not found"));

        Exception exception = assertThrows(
                Exception.class,
                () -> studentService.findByStudentEmail("student@test.com")
        );

        assertEquals("student not found", exception.getMessage());
        verify(studentQueryService).findByStudentEmail("student@test.com");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByStudentEmailList_shouldReturnStudents() throws Exception {
        List<String> emails = List.of("student1@test.com", "student2@test.com");
        List<Student> students = List.of(new Student(), new Student());

        when(studentQueryService.findByStudentEmailList(emails)).thenReturn(students);

        List<Student> result = studentService.findByStudentEmailList(emails);

        assertSame(students, result);
        verify(studentQueryService).findByStudentEmailList(emails);
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByStudentEmailList_shouldPropagateException() throws Exception {
        List<String> emails = List.of("student1@test.com");

        when(studentQueryService.findByStudentEmailList(emails))
                .thenThrow(new Exception("students not found"));

        Exception exception = assertThrows(
                Exception.class,
                () -> studentService.findByStudentEmailList(emails)
        );

        assertEquals("students not found", exception.getMessage());
        verify(studentQueryService).findByStudentEmailList(emails);
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByLoggedInStudent_shouldReturnStudent() throws Exception {
        Student student = new Student();

        when(studentQueryService.findByLoggedInStudent()).thenReturn(student);

        Student result = studentService.findByLoggedInStudent();

        assertSame(student, result);
        verify(studentQueryService).findByLoggedInStudent();
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByLoggedInStudent_shouldPropagateException() throws Exception {
        when(studentQueryService.findByLoggedInStudent())
                .thenThrow(new Exception("logged in student not found"));

        Exception exception = assertThrows(
                Exception.class,
                () -> studentService.findByLoggedInStudent()
        );

        assertEquals("logged in student not found", exception.getMessage());
        verify(studentQueryService).findByLoggedInStudent();
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void getAllStudents_shouldReturnStudents() {
        List<Student> students = List.of(new Student());

        when(studentQueryService.getAllStudents(false)).thenReturn(students);

        List<Student> result = studentService.getAllStudents(false);

        assertSame(students, result);
        verify(studentQueryService).getAllStudents(false);
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByStudentId_shouldReturnStudent() throws Exception {
        Student student = new Student();

        when(studentQueryService.findByStudentId("S1")).thenReturn(student);

        Student result = studentService.findByStudentId("S1");

        assertSame(student, result);
        verify(studentQueryService).findByStudentId("S1");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findByStudentId_shouldPropagateException() throws Exception {
        when(studentQueryService.findByStudentId("S1"))
                .thenThrow(new ResourceNotFoundException("not found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentService.findByStudentId("S1")
        );

        assertEquals("not found", exception.getMessage());
        verify(studentQueryService).findByStudentId("S1");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findAllStudentArchived_shouldReturnStudents() throws Exception {
        List<Student> students = List.of(new Student());

        when(studentQueryService.findAllStudentArchived(true)).thenReturn(students);

        List<Student> result = studentService.findAllStudentArchived(true);

        assertSame(students, result);
        verify(studentQueryService).findAllStudentArchived(true);
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findAllStudentArchived_shouldPropagateException() throws Exception {
        when(studentQueryService.findAllStudentArchived(true))
                .thenThrow(new ResourceNotFoundException("archived not found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentService.findAllStudentArchived(true)
        );

        assertEquals("archived not found", exception.getMessage());
        verify(studentQueryService).findAllStudentArchived(true);
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void getDetentionList_shouldReturnDtos() {
        List<PunishmentDTO> dtos = List.of(mock(PunishmentDTO.class));

        when(studentQueryService.getDetentionList("Burke High")).thenReturn(dtos);

        List<PunishmentDTO> result = studentService.getDetentionList("Burke High");

        assertSame(dtos, result);
        verify(studentQueryService).getDetentionList("Burke High");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void getIssList_shouldReturnDtos() {
        List<PunishmentDTO> dtos = List.of(mock(PunishmentDTO.class));

        when(studentQueryService.getIssList("Burke High")).thenReturn(dtos);

        List<PunishmentDTO> result = studentService.getIssList("Burke High");

        assertSame(dtos, result);
        verify(studentQueryService).getIssList("Burke High");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void findBySchool_shouldReturnStudents() {
        List<Student> students = List.of(new Student());

        when(studentQueryService.findBySchool("Burke High")).thenReturn(students);

        List<Student> result = studentService.findBySchool("Burke High");

        assertSame(students, result);
        verify(studentQueryService).findBySchool("Burke High");
        verifyNoMoreInteractions(studentQueryService);
        verifyNoInteractions(studentMutationService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void createNewStudent_shouldReturnResponse() {
        Student student = new Student();
        StudentResponse response = new StudentResponse();

        when(studentMutationService.createNewStudent(student)).thenReturn(response);

        StudentResponse result = studentService.createNewStudent(student);

        assertSame(response, result);
        verify(studentMutationService).createNewStudent(student);
        verifyNoMoreInteractions(studentMutationService);
        verifyNoInteractions(studentQueryService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void deleteStudent_shouldReturnMessage() throws Exception {
        StudentRequest request = new StudentRequest();

        when(studentMutationService.deleteStudent(request)).thenReturn("deleted");

        String result = studentService.deleteStudent(request);

        assertEquals("deleted", result);
        verify(studentMutationService).deleteStudent(request);
        verifyNoMoreInteractions(studentMutationService);
        verifyNoInteractions(studentQueryService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void deleteStudent_shouldPropagateException() throws Exception {
        StudentRequest request = new StudentRequest();

        when(studentMutationService.deleteStudent(request))
                .thenThrow(new Exception("delete failed"));

        Exception exception = assertThrows(
                Exception.class,
                () -> studentService.deleteStudent(request)
        );

        assertEquals("delete failed", exception.getMessage());
        verify(studentMutationService).deleteStudent(request);
        verifyNoMoreInteractions(studentMutationService);
        verifyNoInteractions(studentQueryService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void archiveRecord_shouldReturnStudent() {
        Student student = new Student();

        when(studentMutationService.archiveRecord("S1")).thenReturn(student);

        Student result = studentService.archiveRecord("S1");

        assertSame(student, result);
        verify(studentMutationService).archiveRecord("S1");
        verifyNoMoreInteractions(studentMutationService);
        verifyNoInteractions(studentQueryService, studentPointService, studentSpotterService, studentAdminService);
    }

    @Test
    void addPoints_shouldReturnStudent() throws Exception {
        Student student = new Student();

        when(studentPointService.addPoints("student@test.com", 5)).thenReturn(student);

        Student result = studentService.addPoints("student@test.com", 5);

        assertSame(student, result);
        verify(studentPointService).addPoints("student@test.com", 5);
        verifyNoMoreInteractions(studentPointService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentSpotterService, studentAdminService);
    }

    @Test
    void addPoints_shouldPropagateException() throws Exception {
        when(studentPointService.addPoints("student@test.com", 5))
                .thenThrow(new ResourceNotFoundException("points error"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentService.addPoints("student@test.com", 5)
        );

        assertEquals("points error", exception.getMessage());
        verify(studentPointService).addPoints("student@test.com", 5);
        verifyNoMoreInteractions(studentPointService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentSpotterService, studentAdminService);
    }

    @Test
    void deletePoints_shouldReturnStudent() throws Exception {
        Student student = new Student();

        when(studentPointService.deletePoints("student@test.com", 3)).thenReturn(student);

        Student result = studentService.deletePoints("student@test.com", 3);

        assertSame(student, result);
        verify(studentPointService).deletePoints("student@test.com", 3);
        verifyNoMoreInteractions(studentPointService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentSpotterService, studentAdminService);
    }

    @Test
    void deletePoints_shouldPropagateException() throws Exception {
        when(studentPointService.deletePoints("student@test.com", 3))
                .thenThrow(new ResourceNotFoundException("delete points error"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentService.deletePoints("student@test.com", 3)
        );

        assertEquals("delete points error", exception.getMessage());
        verify(studentPointService).deletePoints("student@test.com", 3);
        verifyNoMoreInteractions(studentPointService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentSpotterService, studentAdminService);
    }

    @Test
    void transferPoints_shouldReturnStudents() throws Exception {
        List<Student> students = List.of(new Student(), new Student());

        when(studentPointService.transferPoints("giver@test.com", "receiver@test.com", 10)).thenReturn(students);

        List<Student> result = studentService.transferPoints("giver@test.com", "receiver@test.com", 10);

        assertSame(students, result);
        verify(studentPointService).transferPoints("giver@test.com", "receiver@test.com", 10);
        verifyNoMoreInteractions(studentPointService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentSpotterService, studentAdminService);
    }

    @Test
    void transferPoints_shouldPropagateException() throws Exception {
        when(studentPointService.transferPoints("giver@test.com", "receiver@test.com", 10))
                .thenThrow(new ResourceNotFoundException("transfer error"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> studentService.transferPoints("giver@test.com", "receiver@test.com", 10)
        );

        assertEquals("transfer error", exception.getMessage());
        verify(studentPointService).transferPoints("giver@test.com", "receiver@test.com", 10);
        verifyNoMoreInteractions(studentPointService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentSpotterService, studentAdminService);
    }

    @Test
    void massAssignForSchool_shouldReturnStudents() {
        List<Student> students = List.of(new Student());

        when(studentAdminService.massAssignForSchool()).thenReturn(students);

        List<Student> result = studentService.massAssignForSchool();

        assertSame(students, result);
        verify(studentAdminService).massAssignForSchool();
        verifyNoMoreInteractions(studentAdminService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentSpotterService);
    }

    @Test
    void getStudentSchool_shouldReturnOptionalSchool() {
        School school = new School();
        Optional<School> schoolOptional = Optional.of(school);

        when(studentAdminService.getStudentSchool()).thenReturn(schoolOptional);

        Optional<School> result = studentService.getStudentSchool();

        assertSame(schoolOptional, result);
        verify(studentAdminService).getStudentSchool();
        verifyNoMoreInteractions(studentAdminService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentSpotterService);
    }

    @Test
    void getStudentSchool_shouldReturnEmptyOptional() {
        when(studentAdminService.getStudentSchool()).thenReturn(Optional.empty());

        Optional<School> result = studentService.getStudentSchool();

        assertTrue(result.isEmpty());
        verify(studentAdminService).getStudentSchool();
        verifyNoMoreInteractions(studentAdminService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentSpotterService);
    }

    @Test
    void updateStudentNotes_shouldReturnStudent() {
        Student student = new Student();
        ThreadEvent event = new ThreadEvent();

        when(studentAdminService.updateStudentNotes("S1", event)).thenReturn(student);

        Student result = studentService.updateStudentNotes("S1", event);

        assertSame(student, result);
        verify(studentAdminService).updateStudentNotes("S1", event);
        verifyNoMoreInteractions(studentAdminService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentSpotterService);
    }

    @Test
    void addTimeToStudent_shouldReturnStudent() {
        Student student = new Student();

        when(studentAdminService.addTimeToStudent("student@test.com", 1, 30)).thenReturn(student);

        Student result = studentService.addTimeToStudent("student@test.com", 1, 30);

        assertSame(student, result);
        verify(studentAdminService).addTimeToStudent("student@test.com", 1, 30);
        verifyNoMoreInteractions(studentAdminService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentSpotterService);
    }

    @Test
    void updateStudents_shouldReturnStudents() {
        List<Student> students = List.of(new Student());

        when(studentAdminService.updateStudents(students)).thenReturn(students);

        List<Student> result = studentService.updateStudents(students);

        assertSame(students, result);
        verify(studentAdminService).updateStudents(students);
        verifyNoMoreInteractions(studentAdminService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentSpotterService);
    }

    @Test
    void addAsSpotter_shouldReturnStudents() {
        UpdateSpottersRequest request = new UpdateSpottersRequest();
        List<Student> students = List.of(new Student());

        when(studentSpotterService.addAsSpotter(request)).thenReturn(students);

        List<Student> result = studentService.addAsSpotter(request);

        assertSame(students, result);
        verify(studentSpotterService).addAsSpotter(request);
        verifyNoMoreInteractions(studentSpotterService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentAdminService);
    }

    @Test
    void deleteSpotters_shouldReturnStudents() {
        UpdateSpottersRequest request = new UpdateSpottersRequest();
        List<Student> students = List.of(new Student());

        when(studentSpotterService.deleteSpotters(request)).thenReturn(students);

        List<Student> result = studentService.deleteSpotters(request);

        assertSame(students, result);
        verify(studentSpotterService).deleteSpotters(request);
        verifyNoMoreInteractions(studentSpotterService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentAdminService);
    }

    @Test
    void removeSpotterByEmail_shouldReturnStudent() {
        Student student = new Student();

        when(studentSpotterService.removeSpotterByEmail("spotter@test.com", student)).thenReturn(student);

        Student result = studentService.removeSpotterByEmail("spotter@test.com", student);

        assertSame(student, result);
        verify(studentSpotterService).removeSpotterByEmail("spotter@test.com", student);
        verifyNoMoreInteractions(studentSpotterService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentAdminService);
    }

    @Test
    void findBySpotter_shouldReturnStudents() {
        List<Student> students = List.of(new Student());

        when(studentSpotterService.findBySpotter("spotter@test.com")).thenReturn(students);

        List<Student> result = studentService.findBySpotter("spotter@test.com");

        assertSame(students, result);
        verify(studentSpotterService).findBySpotter("spotter@test.com");
        verifyNoMoreInteractions(studentSpotterService);
        verifyNoInteractions(studentQueryService, studentMutationService, studentPointService, studentAdminService);
    }
}
