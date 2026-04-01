package com.reps.demogcloud.services.infraction;

import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.services.InfractionService;
import com.reps.demogcloud.services.infraction.InfractionMutationService;
import com.reps.demogcloud.services.infraction.InfractionQueryService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfractionServiceTest {

    @Mock
    private InfractionMutationService infractionMutationService;

    @Mock
    private InfractionQueryService infractionQueryService;

    @InjectMocks
    private InfractionService infractionService;

    @Test
    void findInfractionByInfractionName_shouldReturnInfraction_whenFound() throws ResourceNotFoundException {
        String infractionName = "Tardy";

        Infraction infraction = new Infraction();
        infraction.setInfractionName(infractionName);

        when(infractionQueryService.findInfractionByInfractionName(infractionName)).thenReturn(infraction);

        Infraction result = infractionService.findInfractionByInfractionName(infractionName);

        assertNotNull(result);
        assertEquals(infraction, result);
        assertEquals("Tardy", result.getInfractionName());

        verify(infractionQueryService, times(1)).findInfractionByInfractionName(infractionName);
        verifyNoInteractions(infractionMutationService);
        verifyNoMoreInteractions(infractionQueryService);
    }

    @Test
    void findInfractionByInfractionName_shouldThrowResourceNotFoundException_whenNotFound() throws ResourceNotFoundException {
        String infractionName = "Missing Infraction";

        when(infractionQueryService.findInfractionByInfractionName(infractionName))
                .thenThrow(new ResourceNotFoundException("Infraction not found"));

        ResourceNotFoundException thrown = assertThrows(
                ResourceNotFoundException.class,
                () -> infractionService.findInfractionByInfractionName(infractionName)
        );

        assertEquals("Infraction not found", thrown.getMessage());

        verify(infractionQueryService, times(1)).findInfractionByInfractionName(infractionName);
        verifyNoInteractions(infractionMutationService);
        verifyNoMoreInteractions(infractionQueryService);
    }

    @Test
    void findByInfractionId_shouldReturnInfraction_whenFound() throws ResourceNotFoundException {
        String infractionId = "INF-001";

        Infraction infraction = new Infraction();
        infraction.setInfractionId(infractionId);

        when(infractionQueryService.findByInfractionId(infractionId)).thenReturn(infraction);

        Infraction result = infractionService.findByInfractionId(infractionId);

        assertNotNull(result);
        assertEquals(infraction, result);
        assertEquals("INF-001", result.getInfractionId());

        verify(infractionQueryService, times(1)).findByInfractionId(infractionId);
        verifyNoInteractions(infractionMutationService);
        verifyNoMoreInteractions(infractionQueryService);
    }

    @Test
    void findByInfractionId_shouldThrowResourceNotFoundException_whenNotFound() throws ResourceNotFoundException {
        String infractionId = "BAD-ID";

        when(infractionQueryService.findByInfractionId(infractionId))
                .thenThrow(new ResourceNotFoundException("Infraction id not found"));

        ResourceNotFoundException thrown = assertThrows(
                ResourceNotFoundException.class,
                () -> infractionService.findByInfractionId(infractionId)
        );

        assertEquals("Infraction id not found", thrown.getMessage());

        verify(infractionQueryService, times(1)).findByInfractionId(infractionId);
        verifyNoInteractions(infractionMutationService);
        verifyNoMoreInteractions(infractionQueryService);
    }

    @Test
    void findAllInfractions_shouldReturnAllInfractions() {
        Infraction infraction1 = new Infraction();
        infraction1.setInfractionId("INF-001");
        infraction1.setInfractionName("Tardy");

        Infraction infraction2 = new Infraction();
        infraction2.setInfractionId("INF-002");
        infraction2.setInfractionName("Disrespect");

        List<Infraction> infractions = List.of(infraction1, infraction2);

        when(infractionQueryService.findAllInfractions()).thenReturn(infractions);

        List<Infraction> result = infractionService.findAllInfractions();

        assertNotNull(result);
        assertEquals(2, result.size());
        assertEquals(infractions, result);
        assertEquals("INF-001", result.get(0).getInfractionId());
        assertEquals("Tardy", result.get(0).getInfractionName());
        assertEquals("INF-002", result.get(1).getInfractionId());
        assertEquals("Disrespect", result.get(1).getInfractionName());

        verify(infractionQueryService, times(1)).findAllInfractions();
        verifyNoInteractions(infractionMutationService);
        verifyNoMoreInteractions(infractionQueryService);
    }

    @Test
    void findAllInfractions_shouldReturnEmptyList_whenNoneExist() {
        when(infractionQueryService.findAllInfractions()).thenReturn(List.of());

        List<Infraction> result = infractionService.findAllInfractions();

        assertNotNull(result);
        assertTrue(result.isEmpty());

        verify(infractionQueryService, times(1)).findAllInfractions();
        verifyNoInteractions(infractionMutationService);
        verifyNoMoreInteractions(infractionQueryService);
    }

    @Test
    void createNewInfraction_shouldReturnCreatedInfraction() {
        Infraction infraction = new Infraction();
        infraction.setInfractionName("Fighting");

        Infraction savedInfraction = new Infraction();
        savedInfraction.setInfractionId("INF-003");
        savedInfraction.setInfractionName("Fighting");

        when(infractionMutationService.createNewInfraction(infraction)).thenReturn(savedInfraction);

        Infraction result = infractionService.createNewInfraction(infraction);

        assertNotNull(result);
        assertEquals(savedInfraction, result);
        assertEquals("INF-003", result.getInfractionId());
        assertEquals("Fighting", result.getInfractionName());

        verify(infractionMutationService, times(1)).createNewInfraction(infraction);
        verifyNoInteractions(infractionQueryService);
        verifyNoMoreInteractions(infractionMutationService);
    }

    @Test
    void createNewInfraction_shouldReturnNull_whenMutationServiceReturnsNull() {
        Infraction infraction = new Infraction();
        infraction.setInfractionName("Fighting");

        when(infractionMutationService.createNewInfraction(infraction)).thenReturn(null);

        Infraction result = infractionService.createNewInfraction(infraction);

        assertNull(result);

        verify(infractionMutationService, times(1)).createNewInfraction(infraction);
        verifyNoInteractions(infractionQueryService);
        verifyNoMoreInteractions(infractionMutationService);
    }

    @Test
    void deleteInfraction_shouldReturnSuccessMessage() throws ResourceNotFoundException {
        Infraction infraction = new Infraction();
        infraction.setInfractionId("INF-004");
        infraction.setInfractionName("Bullying");

        when(infractionMutationService.deleteInfraction(infraction))
                .thenReturn("Infraction deleted successfully");

        String result = infractionService.deleteInfraction(infraction);

        assertNotNull(result);
        assertEquals("Infraction deleted successfully", result);

        verify(infractionMutationService, times(1)).deleteInfraction(infraction);
        verifyNoInteractions(infractionQueryService);
        verifyNoMoreInteractions(infractionMutationService);
    }

    @Test
    void deleteInfraction_shouldThrowResourceNotFoundException_whenInfractionDoesNotExist() throws ResourceNotFoundException {
        Infraction infraction = new Infraction();
        infraction.setInfractionId("BAD-ID");

        when(infractionMutationService.deleteInfraction(infraction))
                .thenThrow(new ResourceNotFoundException("Infraction not found for deletion"));

        ResourceNotFoundException thrown = assertThrows(
                ResourceNotFoundException.class,
                () -> infractionService.deleteInfraction(infraction)
        );

        assertEquals("Infraction not found for deletion", thrown.getMessage());

        verify(infractionMutationService, times(1)).deleteInfraction(infraction);
        verifyNoInteractions(infractionQueryService);
        verifyNoMoreInteractions(infractionMutationService);
    }
}
