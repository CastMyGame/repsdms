package com.reps.demogcloud.services;

import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import com.reps.demogcloud.services.infraction.InfractionMutationService;
import com.reps.demogcloud.services.infraction.InfractionQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class InfractionService {
    private final InfractionMutationService infractionMutationService;
    private final InfractionQueryService infractionQueryService;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public Infraction findInfractionByInfractionName (String infractionName) throws ResourceNotFoundException {
        return infractionQueryService.findInfractionByInfractionName(infractionName);
    }

    public Infraction findByInfractionId (String infractionId) throws ResourceNotFoundException {
        return infractionQueryService.findByInfractionId(infractionId);
    }

    public List<Infraction> findAllInfractions() {
        return infractionQueryService.findAllInfractions();
    }

    public Infraction createNewInfraction (Infraction infraction ) {
        return infractionMutationService.createNewInfraction(infraction);
    }
    public String deleteInfraction ( Infraction infraction ) throws ResourceNotFoundException {
        return infractionMutationService.deleteInfraction(infraction);
    }
}
