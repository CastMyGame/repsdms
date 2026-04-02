package com.reps.demogcloud.utils;

import com.reps.demogcloud.data.EmployeeRepository;
import com.reps.demogcloud.models.dto.TeacherDTO;
import com.reps.demogcloud.models.employee.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DtoUtilsTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private DtoUtils dtoUtils;

    private TeacherDTO shoutOutOld;
    private TeacherDTO shoutOutNew;
    private TeacherDTO nonShoutOut;
    private TeacherDTO nullInfraction;

    @BeforeEach
    void setUp() {
        shoutOutOld = new TeacherDTO();
        shoutOutOld.setInfractionName("Positive Behavior Shout Out!");
        shoutOutOld.setTimeCreated(LocalDate.now().minusDays(3));
        shoutOutOld.setClassPeriod("1st");

        shoutOutNew = new TeacherDTO();
        shoutOutNew.setInfractionName("positive behavior shout out!");
        shoutOutNew.setTimeCreated(LocalDate.now().minusDays(1));
        shoutOutNew.setClassPeriod("2nd");

        nonShoutOut = new TeacherDTO();
        nonShoutOut.setInfractionName("Tardy");
        nonShoutOut.setTimeCreated(LocalDate.now());
        nonShoutOut.setClassPeriod("1st");

        nullInfraction = new TeacherDTO();
        nullInfraction.setInfractionName(null);
        nullInfraction.setTimeCreated(LocalDate.now());
        nullInfraction.setClassPeriod("3rd");
    }

    @Test
    void listOfShoutOuts_shouldReturnOnlyShoutOuts_sortedNewestFirst() {
        List<TeacherDTO> input = List.of(nonShoutOut, shoutOutOld, shoutOutNew, nullInfraction);

        List<TeacherDTO> result = dtoUtils.listOfShoutOuts(input);

        assertEquals(2, result.size());
        assertEquals(shoutOutNew, result.get(0));
        assertEquals(shoutOutOld, result.get(1));
    }

    @Test
    void listOfShoutOuts_shouldReturnEmptyList_whenInputIsNull() {
        List<TeacherDTO> result = dtoUtils.listOfShoutOuts(null);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void listOfShoutOuts_shouldReturnEmptyList_whenNoShoutOutsExist() {
        List<TeacherDTO> input = List.of(nonShoutOut, nullInfraction);

        List<TeacherDTO> result = dtoUtils.listOfShoutOuts(input);

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void updateWeeklyPunishmentsForTeacherClasses_shouldUpdateCountsAndSaveTeacher() {
        Employee teacher = new Employee();
        List<Employee.ClassRoster> rosters = new ArrayList<>();

        Employee.ClassRoster firstPeriod = new Employee.ClassRoster();
        firstPeriod.setClassPeriod("1st");

        Employee.ClassRoster secondPeriod = new Employee.ClassRoster();
        secondPeriod.setClassPeriod("2nd");

        Employee.ClassRoster thirdPeriod = new Employee.ClassRoster();
        thirdPeriod.setClassPeriod("3rd");

        rosters.add(firstPeriod);
        rosters.add(secondPeriod);
        rosters.add(thirdPeriod);
        teacher.setClasses(rosters);

        TeacherDTO dto1 = new TeacherDTO();
        dto1.setClassPeriod("1st");
        dto1.setTimeCreated(LocalDate.now().minusDays(1));

        TeacherDTO dto2 = new TeacherDTO();
        dto2.setClassPeriod("1st");
        dto2.setTimeCreated(LocalDate.now().minusDays(6));

        TeacherDTO dto3 = new TeacherDTO();
        dto3.setClassPeriod("2nd");
        dto3.setTimeCreated(LocalDate.now().minusWeeks(1)); // included

        TeacherDTO dto4 = new TeacherDTO();
        dto4.setClassPeriod("2nd");
        dto4.setTimeCreated(LocalDate.now().minusDays(8)); // excluded

        TeacherDTO dto5 = new TeacherDTO();
        dto5.setClassPeriod("4th");
        dto5.setTimeCreated(LocalDate.now().minusDays(2)); // not on roster

        List<TeacherDTO> punishments = List.of(dto1, dto2, dto3, dto4, dto5);

        when(employeeRepository.save(any(Employee.class))).thenReturn(teacher);

        dtoUtils.updateWeeklyPunishmentsForTeacherClasses(teacher, punishments);

        assertEquals(2, firstPeriod.getPunishmentsThisWeek());
        assertEquals(1, secondPeriod.getPunishmentsThisWeek());
        assertEquals(0, thirdPeriod.getPunishmentsThisWeek());

        verify(employeeRepository, times(1)).save(teacher);
    }

    @Test
    void updateWeeklyPunishmentsForTeacherClasses_shouldHandleNullPunishmentList_andSetAllCountsToZero() {
        Employee teacher = new Employee();
        List<Employee.ClassRoster> rosters = new ArrayList<>();

        Employee.ClassRoster firstPeriod = new Employee.ClassRoster();
        firstPeriod.setClassPeriod("1st");

        Employee.ClassRoster secondPeriod = new Employee.ClassRoster();
        secondPeriod.setClassPeriod("2nd");

        rosters.add(firstPeriod);
        rosters.add(secondPeriod);
        teacher.setClasses(rosters);

        when(employeeRepository.save(any(Employee.class))).thenReturn(teacher);

        dtoUtils.updateWeeklyPunishmentsForTeacherClasses(teacher, null);

        assertEquals(0, firstPeriod.getPunishmentsThisWeek());
        assertEquals(0, secondPeriod.getPunishmentsThisWeek());

        verify(employeeRepository, times(1)).save(teacher);
    }

    @Test
    void updateWeeklyPunishmentsForTeacherClasses_shouldDoNothing_whenTeacherIsNull() {
        dtoUtils.updateWeeklyPunishmentsForTeacherClasses(null, List.of(shoutOutNew));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateWeeklyPunishmentsForTeacherClasses_shouldDoNothing_whenTeacherClassesAreNull() {
        Employee teacher = new Employee();
        teacher.setClasses(null);

        dtoUtils.updateWeeklyPunishmentsForTeacherClasses(teacher, List.of(shoutOutNew));

        verify(employeeRepository, never()).save(any(Employee.class));
    }

    @Test
    void updateWeeklyPunishmentsForTeacherClasses_shouldIgnoreNullDtosAndNullClassPeriods() {
        Employee teacher = new Employee();
        List<Employee.ClassRoster> rosters = new ArrayList<>();

        Employee.ClassRoster firstPeriod = new Employee.ClassRoster();
        firstPeriod.setClassPeriod("1st");
        rosters.add(firstPeriod);

        teacher.setClasses(rosters);

        TeacherDTO validDto = new TeacherDTO();
        validDto.setClassPeriod("1st");
        validDto.setTimeCreated(LocalDate.now().minusDays(2));

        TeacherDTO nullClassPeriodDto = new TeacherDTO();
        nullClassPeriodDto.setClassPeriod(null);
        nullClassPeriodDto.setTimeCreated(LocalDate.now().minusDays(2));

        List<TeacherDTO> punishments = new ArrayList<>();
        punishments.add(null);
        punishments.add(validDto);
        punishments.add(nullClassPeriodDto);

        when(employeeRepository.save(any(Employee.class))).thenReturn(teacher);

        dtoUtils.updateWeeklyPunishmentsForTeacherClasses(teacher, punishments);

        assertEquals(1, firstPeriod.getPunishmentsThisWeek());
        verify(employeeRepository, times(1)).save(teacher);
    }

    @Test
    void updateWeeklyPunishmentsForTeacherClasses_shouldSaveUpdatedTeacherObject() {
        Employee teacher = new Employee();
        List<Employee.ClassRoster> rosters = new ArrayList<>();

        Employee.ClassRoster firstPeriod = new Employee.ClassRoster();
        firstPeriod.setClassPeriod("1st");
        rosters.add(firstPeriod);
        teacher.setClasses(rosters);

        TeacherDTO dto = new TeacherDTO();
        dto.setClassPeriod("1st");
        dto.setTimeCreated(LocalDate.now());

        when(employeeRepository.save(any(Employee.class))).thenReturn(teacher);

        dtoUtils.updateWeeklyPunishmentsForTeacherClasses(teacher, List.of(dto));

        ArgumentCaptor<Employee> captor = ArgumentCaptor.forClass(Employee.class);
        verify(employeeRepository).save(captor.capture());

        Employee savedTeacher = captor.getValue();
        assertNotNull(savedTeacher);
        assertNotNull(savedTeacher.getClasses());
        assertEquals(1, savedTeacher.getClasses().get(0).getPunishmentsThisWeek());
    }
}