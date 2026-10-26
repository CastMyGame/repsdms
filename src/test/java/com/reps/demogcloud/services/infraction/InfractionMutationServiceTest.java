package com.reps.demogcloud.services.infraction;

import com.reps.demogcloud.models.infraction.Infraction;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;

class InfractionMutationServiceTest {
    private final InfractionMutationService service = new InfractionMutationService();
    @Test void createOrOverwriteIsDisabledEvenForExistingIds() {
        var failure=assertThrows(ResponseStatusException.class,()->service.createNewInfraction(new Infraction("existing", "Synthetic", "1")));
        assertEquals(405,failure.getStatusCode().value());
        assertEquals("Infraction catalog editing is unavailable for MVP 1.",failure.getReason());
    }
    @Test void deleteIsDisabledEvenWithoutHttpSecurity() {
        var failure=assertThrows(ResponseStatusException.class,()->service.deleteInfraction(new Infraction("existing", "Synthetic", "1")));
        assertEquals(405,failure.getStatusCode().value());
    }
}
