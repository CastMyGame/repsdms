package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.PunishmentService;
import com.reps.demogcloud.services.StudentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PunishmentDtoServiceTest {

    @Mock
    private PunishmentService punishmentService;

    @Mock
    private StudentService studentService;

    @InjectMocks
    private PunishmentDtoService punishmentDtoService;

    @Test
    void getDTOPunishments_shouldReturnMappedDTOs() throws Exception {
        Punishment p1 = new Punishment();
        p1.setStudentEmail("student1@test.com");

        Punishment p2 = new Punishment();
        p2.setStudentEmail("student2@test.com");

        Student s1 = new Student();
        s1.setFirstName("John");
        s1.setLastName("Doe");

        Student s2 = new Student();
        s2.setFirstName("Jane");
        s2.setLastName("Smith");

        when(punishmentService.findAllSchool()).thenReturn(List.of(p1, p2));
        when(studentService.findByStudentEmail("student1@test.com")).thenReturn(s1);
        when(studentService.findByStudentEmail("student2@test.com")).thenReturn(s2);

        List<PunishmentDTO> result = punishmentDtoService.getDTOPunishments();

        assertEquals(2, result.size());

        assertEquals("student1@test.com", result.get(0).getStudentEmail());
        assertEquals("John", result.get(0).getStudentFirstName());
        assertEquals("Doe", result.get(0).getStudentLastName());

        assertEquals("student2@test.com", result.get(1).getStudentEmail());
        assertEquals("Jane", result.get(1).getStudentFirstName());
        assertEquals("Smith", result.get(1).getStudentLastName());

        verify(punishmentService).findAllSchool();
        verify(studentService).findByStudentEmail("student1@test.com");
        verify(studentService).findByStudentEmail("student2@test.com");
    }

    @Test
    void getDTOPunishments_shouldHandleNullStudent() throws Exception {
        Punishment p1 = new Punishment();
        p1.setStudentEmail("student1@test.com");

        when(punishmentService.findAllSchool()).thenReturn(List.of(p1));
        when(studentService.findByStudentEmail("student1@test.com")).thenReturn(null);

        List<PunishmentDTO> result = punishmentDtoService.getDTOPunishments();

        assertEquals(1, result.size());
        assertEquals("student1@test.com", result.get(0).getStudentEmail());
        assertNull(result.get(0).getStudentFirstName());
        assertNull(result.get(0).getStudentLastName());
    }

    @Test
    void getDTOPunishments_shouldHandleNullStudentEmail() throws Exception {
        Punishment p1 = new Punishment();
        p1.setStudentEmail(null);

        when(punishmentService.findAllSchool()).thenReturn(List.of(p1));

        List<PunishmentDTO> result = punishmentDtoService.getDTOPunishments();

        assertEquals(1, result.size());
        assertNull(result.get(0).getStudentEmail());

        verify(studentService, never()).findByStudentEmail(any());
    }

    @Test
    void getDTOPunishments_shouldReturnEmptyList_whenNoPunishments() throws Exception {
        when(punishmentService.findAllSchool()).thenReturn(List.of());

        List<PunishmentDTO> result = punishmentDtoService.getDTOPunishments();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(punishmentService).findAllSchool();
        verifyNoInteractions(studentService);
    }

    @Test
    void getDTOPunishments_shouldPropagateRuntimeException() throws Exception {
        RuntimeException expected = new RuntimeException("failure");

        when(punishmentService.findAllSchool()).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> punishmentDtoService.getDTOPunishments()
        );

        assertSame(expected, actual);
    }
}