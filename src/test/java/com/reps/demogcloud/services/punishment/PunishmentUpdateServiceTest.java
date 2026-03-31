package com.reps.demogcloud.services.punishment;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.data.PunishRepository;
import com.reps.demogcloud.data.StudentRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.models.punishment.Punishment;
import com.reps.demogcloud.models.student.Student;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PunishmentUpdateServiceTest {

    @Mock
    private PunishRepository punishRepository;

    @Mock
    private InfractionRepository infractionRepository;

    @Mock
    private StudentRepository studentRepository;

    @InjectMocks
    private PunishmentUpdateService punishmentUpdateService;

    @Test
    void updateMapIndex_shouldUpdateAndSavePunishment_whenPunishmentExists() {
        Punishment punishment = new Punishment();
        punishment.setPunishmentId("P-1");
        punishment.setMapIndex(1);

        Punishment savedPunishment = new Punishment();
        savedPunishment.setPunishmentId("P-1");
        savedPunishment.setMapIndex(5);

        when(punishRepository.findByPunishmentId("P-1")).thenReturn(punishment);
        when(punishRepository.save(punishment)).thenReturn(savedPunishment);

        Punishment result = punishmentUpdateService.updateMapIndex("P-1", 5);

        assertNotNull(result);
        assertEquals("P-1", result.getPunishmentId());
        assertEquals(5, result.getMapIndex());
        assertEquals(5, punishment.getMapIndex());

        verify(punishRepository, times(1)).findByPunishmentId("P-1");
        verify(punishRepository, times(1)).save(punishment);
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateMapIndex_shouldThrowResourceNotFoundException_whenPunishmentDoesNotExist() {
        when(punishRepository.findByPunishmentId("MISSING")).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> punishmentUpdateService.updateMapIndex("MISSING", 3)
        );

        assertEquals("No Punishment with Id MISSING number exist", exception.getMessage());

        verify(punishRepository, times(1)).findByPunishmentId("MISSING");
        verify(punishRepository, never()).save(any(Punishment.class));
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateTimeCreated_shouldArchiveAndSaveMatchingInfractionsOnly() {
        Punishment tardy = new Punishment();
        tardy.setPunishmentId("1");
        tardy.setInfractionName("Tardy");
        tardy.setArchived(false);

        Punishment horseplay = new Punishment();
        horseplay.setPunishmentId("2");
        horseplay.setInfractionName("Horseplay");
        horseplay.setArchived(false);

        Punishment nonMatching = new Punishment();
        nonMatching.setPunishmentId("3");
        nonMatching.setInfractionName("Fighting");
        nonMatching.setArchived(false);

        when(punishRepository.findByArchived(false)).thenReturn(List.of(tardy, horseplay, nonMatching));
        when(punishRepository.save(tardy)).thenReturn(tardy);
        when(punishRepository.save(horseplay)).thenReturn(horseplay);

        List<Punishment> result = punishmentUpdateService.updateTimeCreated();

        assertNotNull(result);
        assertEquals(2, result.size());

        assertTrue(tardy.isArchived());
        assertEquals("repsdiscipline@gmail.com", tardy.getArchivedBy());
        assertEquals(" Tardy Sweep 5/10", tardy.getArchivedExplanation());
        assertEquals(LocalDate.now(), tardy.getArchivedOn());

        assertTrue(horseplay.isArchived());
        assertEquals("repsdiscipline@gmail.com", horseplay.getArchivedBy());
        assertEquals(" Tardy Sweep 5/10", horseplay.getArchivedExplanation());
        assertEquals(LocalDate.now(), horseplay.getArchivedOn());

        assertFalse(nonMatching.isArchived());

        verify(punishRepository, times(1)).findByArchived(false);
        verify(punishRepository, times(1)).save(tardy);
        verify(punishRepository, times(1)).save(horseplay);
        verify(punishRepository, never()).save(nonMatching);
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateTimeCreated_shouldReturnEmptyList_whenNoInfractionsMatch() {
        Punishment nonMatching = new Punishment();
        nonMatching.setPunishmentId("3");
        nonMatching.setInfractionName("Fighting");
        nonMatching.setArchived(false);

        when(punishRepository.findByArchived(false)).thenReturn(List.of(nonMatching));

        List<Punishment> result = punishmentUpdateService.updateTimeCreated();

        assertNotNull(result);
        assertTrue(result.isEmpty());
        assertFalse(nonMatching.isArchived());

        verify(punishRepository, times(1)).findByArchived(false);
        verify(punishRepository, never()).save(any(Punishment.class));
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateDescriptions_shouldRemoveFirstDescriptionAndSave_whenMoreThanOneDescriptionExists() {
        Punishment punishment = new Punishment();
        punishment.setPunishmentId("P-1");
        punishment.setInfractionDescription(new ArrayList<>(List.of("remove-me", "keep-me")));

        Punishment singleDescription = new Punishment();
        singleDescription.setPunishmentId("P-2");
        singleDescription.setInfractionDescription(new ArrayList<>(List.of("only-one")));

        when(punishRepository.findAll()).thenReturn(List.of(punishment, singleDescription));
        when(punishRepository.save(punishment)).thenReturn(punishment);

        List<Punishment> result = punishmentUpdateService.updateDescriptions();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(List.of("keep-me"), punishment.getInfractionDescription());

        verify(punishRepository, times(1)).findAll();
        verify(punishRepository, times(1)).save(punishment);
        verify(punishRepository, never()).save(singleDescription);
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateDescriptions_shouldReturnEmptyList_whenNoPunishmentsNeedUpdating() {
        Punishment punishment = new Punishment();
        punishment.setPunishmentId("P-1");
        punishment.setInfractionDescription(new ArrayList<>(List.of("only-one")));

        when(punishRepository.findAll()).thenReturn(List.of(punishment));

        List<Punishment> result = punishmentUpdateService.updateDescriptions();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(punishRepository, times(1)).findAll();
        verify(punishRepository, never()).save(any(Punishment.class));
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateStudentEmails_shouldSaveAllPunishments() {
        Punishment punishment1 = new Punishment();
        punishment1.setPunishmentId("P-1");
        punishment1.setStudentEmail("student1@school.com");

        Punishment punishment2 = new Punishment();
        punishment2.setPunishmentId("P-2");
        punishment2.setStudentEmail("student2@school.com");

        when(punishRepository.findAll()).thenReturn(List.of(punishment1, punishment2));
        when(punishRepository.save(punishment1)).thenReturn(punishment1);
        when(punishRepository.save(punishment2)).thenReturn(punishment2);

        List<Punishment> result = punishmentUpdateService.updateStudentEmails();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals("student1@school.com", result.get(0).getStudentEmail());
        assertEquals("student2@school.com", result.get(1).getStudentEmail());

        verify(punishRepository, times(1)).findAll();
        verify(punishRepository, times(1)).save(punishment1);
        verify(punishRepository, times(1)).save(punishment2);
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateStudentEmails_shouldReturnEmptyList_whenNoPunishmentsExist() {
        when(punishRepository.findAll()).thenReturn(List.of());

        List<Punishment> result = punishmentUpdateService.updateStudentEmails();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(punishRepository, times(1)).findAll();
        verify(punishRepository, never()).save(any(Punishment.class));
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateInfractionName_shouldPopulateAndSaveAllPunishments() {
        Punishment punishment = new Punishment();
        punishment.setPunishmentId("P-1");
        punishment.setInfractionId("I-1");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("I-1");
        infraction.setInfractionName("Tardy");

        when(punishRepository.findAll()).thenReturn(List.of(punishment));
        when(infractionRepository.findByInfractionId("I-1")).thenReturn(infraction);
        when(punishRepository.save(punishment)).thenReturn(punishment);

        List<Punishment> result = punishmentUpdateService.updateInfractionName();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Tardy", punishment.getInfractionName());

        verify(punishRepository, times(1)).findAll();
        verify(infractionRepository, times(1)).findByInfractionId("I-1");
        verify(punishRepository, times(1)).save(punishment);
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateInfractionLevel_shouldPopulateAndSaveAllPunishments() {
        Punishment punishment = new Punishment();
        punishment.setPunishmentId("P-1");
        punishment.setInfractionId("I-1");

        Infraction infraction = new Infraction();
        infraction.setInfractionId("I-1");
        infraction.setInfractionLevel("3");

        when(punishRepository.findAll()).thenReturn(List.of(punishment));
        when(infractionRepository.findByInfractionId("I-1")).thenReturn(infraction);
        when(punishRepository.save(punishment)).thenReturn(punishment);

        List<Punishment> result = punishmentUpdateService.updateInfractionLevel();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("3", punishment.getInfractionLevel());

        verify(punishRepository, times(1)).findAll();
        verify(infractionRepository, times(1)).findByInfractionId("I-1");
        verify(punishRepository, times(1)).save(punishment);
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateSchools_shouldPopulateSchoolFromStudentAndSaveAllPunishments() {
        Punishment punishment = new Punishment();
        punishment.setPunishmentId("P-1");
        punishment.setStudentEmail("student@school.com");

        Student student = new Student();
        student.setStudentEmail("student@school.com");
        student.setSchool("Test School");

        when(punishRepository.findAll()).thenReturn(List.of(punishment));
        when(studentRepository.findByStudentEmailIgnoreCase("student@school.com")).thenReturn(student);
        when(punishRepository.save(punishment)).thenReturn(punishment);

        List<Punishment> result = punishmentUpdateService.updateSchools();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("Test School", punishment.getSchool());

        verify(punishRepository, times(1)).findAll();
        verify(studentRepository, times(1)).findByStudentEmailIgnoreCase("student@school.com");
        verify(punishRepository, times(1)).save(punishment);
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }

    @Test
    void updateSchools_shouldReturnEmptyList_whenNoPunishmentsExist() {
        when(punishRepository.findAll()).thenReturn(List.of());

        List<Punishment> result = punishmentUpdateService.updateSchools();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(punishRepository, times(1)).findAll();
        verify(studentRepository, never()).findByStudentEmailIgnoreCase(anyString());
        verify(punishRepository, never()).save(any(Punishment.class));
        verifyNoMoreInteractions(punishRepository, infractionRepository, studentRepository);
    }
}