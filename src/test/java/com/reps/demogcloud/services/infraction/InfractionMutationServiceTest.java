package com.reps.demogcloud.services.infraction;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InfractionMutationServiceTest {

    @Mock
    private InfractionRepository repository;

    @InjectMocks
    private InfractionMutationService infractionMutationService;

    @Test
    void createNewInfraction_shouldReturnSavedInfraction() {
        Infraction infraction = new Infraction("INF-001", "Tardy", "1");

        when(repository.save(infraction)).thenReturn(infraction);

        Infraction result = infractionMutationService.createNewInfraction(infraction);

        assertNotNull(result);
        assertEquals("INF-001", result.getInfractionId());
        assertEquals("Tardy", result.getInfractionName());
        assertEquals("1", result.getInfractionLevel());

        verify(repository, times(1)).save(infraction);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void createNewInfraction_shouldReturnNull_whenRepositoryReturnsNull() {
        Infraction infraction = new Infraction("INF-001", "Tardy", "1");

        when(repository.save(infraction)).thenReturn(null);

        Infraction result = infractionMutationService.createNewInfraction(infraction);

        assertNull(result);

        verify(repository, times(1)).save(infraction);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deleteInfraction_shouldReturnSuccessMessage_whenDeleteSucceeds() throws ResourceNotFoundException {
        Infraction infraction = new Infraction("INF-002", "Disrespect", "2");

        doNothing().when(repository).delete(infraction);

        String result = infractionMutationService.deleteInfraction(infraction);

        assertNotNull(result);
        assertEquals("Disrespect has been deleted", result);

        verify(repository, times(1)).delete(infraction);
        verifyNoMoreInteractions(repository);
    }

    @Test
    void deleteInfraction_shouldThrowResourceNotFoundException_whenRepositoryThrowsException() {
        Infraction infraction = new Infraction("INF-003", "Bullying", "3");

        doThrow(new RuntimeException("Delete failed")).when(repository).delete(infraction);

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> infractionMutationService.deleteInfraction(infraction)
        );

        assertEquals("That infraction does not exist", exception.getMessage());

        verify(repository, times(1)).delete(infraction);
        verifyNoMoreInteractions(repository);
    }
}