package com.reps.demogcloud.services.dto;

import com.reps.demogcloud.models.dto.StudentOverviewDTO;
import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.school.School;
import com.reps.demogcloud.models.student.Student;
import com.reps.demogcloud.services.OfficeReferralService;
import com.reps.demogcloud.services.PunishmentService;
import com.reps.demogcloud.services.SchoolService;
import com.reps.demogcloud.services.StudentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StudentDtoServiceTest {

    @Mock
    private PunishmentService punishmentService;

    @Mock
    private StudentService studentService;

    @Mock
    private OfficeReferralService officeReferralService;

    @Mock
    private SchoolService schoolService;

    @InjectMocks
    private StudentDtoService studentDtoService;

    @Test
    void getLoggedInStudentOverData_shouldReturnOverview() throws Exception {
        List<Punishment> punishmentList = List.of(new Punishment(), new Punishment());
        List<OfficeReferral> referralList = List.of(new OfficeReferral());

        Student student = new Student();
        student.setSchool("Test School");

        School school = new School();

        when(punishmentService.findAllPunishmentsByStudentEmail()).thenReturn(punishmentList);
        when(officeReferralService.findByLoggedInStudent()).thenReturn(referralList);
        when(studentService.findByLoggedInStudent()).thenReturn(student);
        when(studentService.getStudentSchool()).thenReturn(Optional.of(school));

        StudentOverviewDTO result = studentDtoService.getLoggedInStudentOverData();

        assertNotNull(result);
        assertSame(student, result.getStudent());
        assertSame(school, result.getSchool());
        assertEquals(punishmentList, result.getPunishments());
        assertEquals(referralList, result.getOfficeReferrals());

        verify(punishmentService).findAllPunishmentsByStudentEmail();
        verify(officeReferralService).findByLoggedInStudent();
        verify(studentService).findByLoggedInStudent();
        verify(studentService).getStudentSchool();
    }

    @Test
    void getLoggedInStudentOverData_shouldThrow_whenLoggedInStudentIsNull() throws Exception {
        when(punishmentService.findAllPunishmentsByStudentEmail()).thenReturn(List.of());
        when(officeReferralService.findByLoggedInStudent()).thenReturn(List.of());
        when(studentService.findByLoggedInStudent()).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> studentDtoService.getLoggedInStudentOverData()
        );

        assertEquals("Logged in student not found", ex.getMessage());

        verify(punishmentService).findAllPunishmentsByStudentEmail();
        verify(officeReferralService).findByLoggedInStudent();
        verify(studentService).findByLoggedInStudent();
    }

    @Test
    void getLoggedInStudentOverData_shouldThrow_whenSchoolMissing() throws Exception {
        Student student = new Student();
        student.setSchool("Missing School");

        when(punishmentService.findAllPunishmentsByStudentEmail()).thenReturn(List.of());
        when(officeReferralService.findByLoggedInStudent()).thenReturn(List.of());
        when(studentService.findByLoggedInStudent()).thenReturn(student);
        when(studentService.getStudentSchool()).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> studentDtoService.getLoggedInStudentOverData()
        );

        assertEquals("School not found: Missing School", ex.getMessage());

        verify(studentService).getStudentSchool();
    }

    @Test
    void getLoggedInStudentOverData_shouldPropagateRuntimeException() throws Exception {
        RuntimeException expected = new RuntimeException("punishment failure");
        when(punishmentService.findAllPunishmentsByStudentEmail()).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> studentDtoService.getLoggedInStudentOverData()
        );

        assertSame(expected, actual);
    }

    @Test
    void getStudentOverData_shouldReturnOverview() throws Exception {
        String studentEmail = "student@test.com";

        List<Punishment> punishmentList = List.of(new Punishment());
        List<OfficeReferral> referralList = List.of(new OfficeReferral(), new OfficeReferral());

        Student student = new Student();
        student.setSchool("Test School");

        School school = new School();

        when(punishmentService.getAllPunishmentByStudentEmail(studentEmail)).thenReturn(punishmentList);
        when(officeReferralService.findByStudentEmail(studentEmail)).thenReturn(referralList);
        when(studentService.findByStudentEmail(studentEmail)).thenReturn(student);
        when(schoolService.findSchoolByName("Test School")).thenReturn(Optional.of(school));

        StudentOverviewDTO result = studentDtoService.getStudentOverData(studentEmail);

        assertNotNull(result);
        assertSame(student, result.getStudent());
        assertSame(school, result.getSchool());
        assertEquals(punishmentList, result.getPunishments());
        assertEquals(referralList, result.getOfficeReferrals());

        verify(punishmentService).getAllPunishmentByStudentEmail(studentEmail);
        verify(officeReferralService).findByStudentEmail(studentEmail);
        verify(studentService).findByStudentEmail(studentEmail);
        verify(schoolService).findSchoolByName("Test School");
    }

    @Test
    void getStudentOverData_shouldThrow_whenStudentIsNull() throws Exception {
        String studentEmail = "student@test.com";

        when(punishmentService.getAllPunishmentByStudentEmail(studentEmail)).thenReturn(List.of());
        when(officeReferralService.findByStudentEmail(studentEmail)).thenReturn(List.of());
        when(studentService.findByStudentEmail(studentEmail)).thenReturn(null);

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> studentDtoService.getStudentOverData(studentEmail)
        );

        assertEquals("Student not found: student@test.com", ex.getMessage());
    }

    @Test
    void getStudentOverData_shouldThrow_whenSchoolMissing() throws Exception {
        String studentEmail = "student@test.com";

        Student student = new Student();
        student.setSchool("Missing School");

        when(punishmentService.getAllPunishmentByStudentEmail(studentEmail)).thenReturn(List.of());
        when(officeReferralService.findByStudentEmail(studentEmail)).thenReturn(List.of());
        when(studentService.findByStudentEmail(studentEmail)).thenReturn(student);
        when(schoolService.findSchoolByName("Missing School")).thenReturn(Optional.empty());

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> studentDtoService.getStudentOverData(studentEmail)
        );

        assertEquals("School not found: Missing School", ex.getMessage());
    }

    @Test
    void getStudentOverData_shouldPropagateRuntimeException() throws Exception {
        String studentEmail = "student@test.com";

        RuntimeException expected = new RuntimeException("student punishment failure");
        when(punishmentService.getAllPunishmentByStudentEmail(studentEmail)).thenThrow(expected);

        RuntimeException actual = assertThrows(
                RuntimeException.class,
                () -> studentDtoService.getStudentOverData(studentEmail)
        );

        assertSame(expected, actual);
    }
}
