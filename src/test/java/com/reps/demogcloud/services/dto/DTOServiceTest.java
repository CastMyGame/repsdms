package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.AdminOverviewDTO;
import com.reps.demogcloud.models.dto.PunishmentDTO;
import com.reps.demogcloud.models.dto.StudentOverviewDTO;
import com.reps.demogcloud.models.dto.TeacherOverviewDTO;
import com.reps.demogcloud.services.DTOService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DTOServiceTest {

    @Mock
    private AdminDtoService adminDtoService;

    @Mock
    private TeacherDtoService teacherDtoService;

    @Mock
    private StudentDtoService studentDtoService;

    @Mock
    private PunishmentDtoService punishmentDtoService;

    @InjectMocks
    private DTOService dtoService;

    @Test
    void getAdminOverData_shouldReturnAdminOverviewDTO() throws Exception {
        AdminOverviewDTO expected = new AdminOverviewDTO();
        when(adminDtoService.getAdminOverData()).thenReturn(expected);

        AdminOverviewDTO result = dtoService.getAdminOverData();

        assertSame(expected, result);
        verify(adminDtoService).getAdminOverData();
        verifyNoInteractions(teacherDtoService, studentDtoService, punishmentDtoService);
        verifyNoMoreInteractions(adminDtoService);
    }

    @Test
    void getAdminOverData_shouldPropagateException() throws Exception {
        Exception expectedException = new Exception("admin failure");
        when(adminDtoService.getAdminOverData()).thenThrow(expectedException);

        Exception actualException = assertThrows(Exception.class, () -> dtoService.getAdminOverData());

        assertSame(expectedException, actualException);
        verify(adminDtoService).getAdminOverData();
        verifyNoInteractions(teacherDtoService, studentDtoService, punishmentDtoService);
        verifyNoMoreInteractions(adminDtoService);
    }

    @Test
    void getTeacherOverData_shouldReturnTeacherOverviewDTO() throws Exception {
        TeacherOverviewDTO expected = new TeacherOverviewDTO();
        when(teacherDtoService.getTeacherOverData()).thenReturn(expected);

        TeacherOverviewDTO result = dtoService.getTeacherOverData();

        assertSame(expected, result);
        verify(teacherDtoService).getTeacherOverData();
        verifyNoInteractions(adminDtoService, studentDtoService, punishmentDtoService);
        verifyNoMoreInteractions(teacherDtoService);
    }

    @Test
    void getTeacherOverData_shouldPropagateException() throws Exception {
        Exception expectedException = new Exception("teacher failure");
        when(teacherDtoService.getTeacherOverData()).thenThrow(expectedException);

        Exception actualException = assertThrows(Exception.class, () -> dtoService.getTeacherOverData());

        assertSame(expectedException, actualException);
        verify(teacherDtoService).getTeacherOverData();
        verifyNoInteractions(adminDtoService, studentDtoService, punishmentDtoService);
        verifyNoMoreInteractions(teacherDtoService);
    }

    @Test
    void getLoggedInStudentOverData_shouldReturnStudentOverviewDTO() throws Exception {
        StudentOverviewDTO expected = new StudentOverviewDTO();
        when(studentDtoService.getLoggedInStudentOverData()).thenReturn(expected);

        StudentOverviewDTO result = dtoService.getLoggedInStudentOverData();

        assertSame(expected, result);
        verify(studentDtoService).getLoggedInStudentOverData();
        verifyNoInteractions(adminDtoService, teacherDtoService, punishmentDtoService);
        verifyNoMoreInteractions(studentDtoService);
    }

    @Test
    void getLoggedInStudentOverData_shouldPropagateException() throws Exception {
        Exception expectedException = new Exception("logged in student failure");
        when(studentDtoService.getLoggedInStudentOverData()).thenThrow(expectedException);

        Exception actualException = assertThrows(Exception.class, () -> dtoService.getLoggedInStudentOverData());

        assertSame(expectedException, actualException);
        verify(studentDtoService).getLoggedInStudentOverData();
        verifyNoInteractions(adminDtoService, teacherDtoService, punishmentDtoService);
        verifyNoMoreInteractions(studentDtoService);
    }

    @Test
    void getStudentOverData_shouldReturnStudentOverviewDTO() throws Exception {
        String studentEmail = "student@test.com";
        StudentOverviewDTO expected = new StudentOverviewDTO();
        when(studentDtoService.getStudentOverData(studentEmail)).thenReturn(expected);

        StudentOverviewDTO result = dtoService.getStudentOverData(studentEmail);

        assertSame(expected, result);
        verify(studentDtoService).getStudentOverData(studentEmail);
        verifyNoInteractions(adminDtoService, teacherDtoService, punishmentDtoService);
        verifyNoMoreInteractions(studentDtoService);
    }

    @Test
    void getStudentOverData_shouldPropagateException() throws Exception {
        String studentEmail = "student@test.com";
        Exception expectedException = new Exception("student failure");
        when(studentDtoService.getStudentOverData(studentEmail)).thenThrow(expectedException);

        Exception actualException = assertThrows(Exception.class, () -> dtoService.getStudentOverData(studentEmail));

        assertSame(expectedException, actualException);
        verify(studentDtoService).getStudentOverData(studentEmail);
        verifyNoInteractions(adminDtoService, teacherDtoService, punishmentDtoService);
        verifyNoMoreInteractions(studentDtoService);
    }

    @Test
    void getDTOPunishments_shouldReturnPunishmentDTOList() throws Exception {
        PunishmentDTO punishmentDTO = new PunishmentDTO();
        List<PunishmentDTO> expected = List.of(punishmentDTO);
        when(punishmentDtoService.getDTOPunishments()).thenReturn(expected);

        List<PunishmentDTO> result = dtoService.getDTOPunishments();

        assertSame(expected, result);
        verify(punishmentDtoService).getDTOPunishments();
        verifyNoInteractions(adminDtoService, teacherDtoService, studentDtoService);
        verifyNoMoreInteractions(punishmentDtoService);
    }

    @Test
    void getDTOPunishments_shouldPropagateException() throws Exception {
        Exception expectedException = new Exception("punishment failure");
        when(punishmentDtoService.getDTOPunishments()).thenThrow(expectedException);

        Exception actualException = assertThrows(Exception.class, () -> dtoService.getDTOPunishments());

        assertSame(expectedException, actualException);
        verify(punishmentDtoService).getDTOPunishments();
        verifyNoInteractions(adminDtoService, teacherDtoService, studentDtoService);
        verifyNoMoreInteractions(punishmentDtoService);
    }
}