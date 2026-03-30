package com.reps.demogcloud.services.infraction;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfractionQueryServiceTest {

    @Mock
    private InfractionRepository repository;

    @InjectMocks
    private InfractionQueryService infractionQueryService;

    @Test
    void findInfractionByInfractionName_shouldReturnInfraction_whenInfractionExists() throws ResourceNotFoundException {
        String infractionName = "Tardy";
        Infraction infraction = new Infraction("INF-001", "Tardy", "1");

        when(repository.findByInfractionName(infractionName)).thenReturn(infraction);

        Infraction result = infractionQueryService.findInfractionByInfractionName(infractionName);

        assertNotNull(result);
        assertEquals("INF-001", result.getInfractionId());
        assertEquals("Tardy", result.getInfractionName());
        assertEquals("1", result.getInfractionLevel());

        verify(repository, times(1)).findByInfractionName(infractionName);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void findInfractionByInfractionName_shouldThrowResourceNotFoundException_whenInfractionDoesNotExist() {
        String infractionName = "Missing Infraction";

        when(repository.findByInfractionName(infractionName)).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> infractionQueryService.findInfractionByInfractionName(infractionName)
        );

        assertEquals("No infraction with that name exists", exception.getMessage());

        verify(repository, times(1)).findByInfractionName(infractionName);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void findByInfractionId_shouldReturnInfraction_whenInfractionExists() throws ResourceNotFoundException {
        String infractionId = "INF-002";
        Infraction infraction = new Infraction("INF-002", "Disrespect", "2");

        when(repository.findByInfractionId(infractionId)).thenReturn(infraction);

        Infraction result = infractionQueryService.findByInfractionId(infractionId);

        assertNotNull(result);
        assertEquals("INF-002", result.getInfractionId());
        assertEquals("Disrespect", result.getInfractionName());
        assertEquals("2", result.getInfractionLevel());

        verify(repository, times(1)).findByInfractionId(infractionId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void findByInfractionId_shouldThrowResourceNotFoundException_whenInfractionDoesNotExist() {
        String infractionId = "BAD-ID";

        when(repository.findByInfractionId(infractionId)).thenReturn(null);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> infractionQueryService.findByInfractionId(infractionId)
        );

        assertEquals("No infraction with that ID exists", exception.getMessage());

        verify(repository, times(1)).findByInfractionId(infractionId);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void findAllInfractions_shouldReturnAllInfractions_whenInfractionsExist() {
        Infraction infraction1 = new Infraction("INF-001", "Tardy", "1");
        Infraction infraction2 = new Infraction("INF-002", "Disrespect", "2");
        List<Infraction> infractions = List.of(infraction1, infraction2);

        when(repository.findAll()).thenReturn(infractions);

        List<Infraction> result = infractionQueryService.findAllInfractions();

        assertNotNull(result);
        assertEquals(2, result.size());

        assertEquals("INF-001", result.get(0).getInfractionId());
        assertEquals("Tardy", result.get(0).getInfractionName());
        assertEquals("1", result.get(0).getInfractionLevel());

        assertEquals("INF-002", result.get(1).getInfractionId());
        assertEquals("Disrespect", result.get(1).getInfractionName());
        assertEquals("2", result.get(1).getInfractionLevel());

        verify(repository, times(1)).findAll();
        verifyNoMoreInteractions(repository);
    }

    @Test
    void findAllInfractions_shouldReturnEmptyList_whenNoInfractionsExist() {
        when(repository.findAll()).thenReturn(List.of());

        List<Infraction> result = infractionQueryService.findAllInfractions();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(repository, times(1)).findAll();
        verifyNoMoreInteractions(repository);
    }
}
