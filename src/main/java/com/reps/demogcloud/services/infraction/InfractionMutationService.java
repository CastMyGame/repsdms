package com.reps.demogcloud.services.infraction;

import com.reps.demogcloud.models.infraction.Infraction;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** Shared catalog writes are unavailable through application APIs for MVP 1, including internal aliases. */
@Service
public class InfractionMutationService {
    public static ResponseStatusException editingUnavailable() {
        return new ResponseStatusException(HttpStatus.METHOD_NOT_ALLOWED,
                "Infraction catalog editing is unavailable for MVP 1.");
    }
    public Infraction createNewInfraction(Infraction infraction) { throw editingUnavailable(); }
    public String deleteInfraction(Infraction infraction) { throw editingUnavailable(); }
}
