package com.reps.demogcloud.services.infraction;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class InfractionQueryService {
    private final InfractionRepository repository;

    public Infraction findInfractionByInfractionName(String infractionName) throws ResourceNotFoundException {
        var infraction = repository.findByInfractionName(infractionName);
        if (infraction == null) {
            throw new ResourceNotFoundException("No infraction with that name exists");
        }
        log.debug("Found infraction: {}", infraction);
        return infraction;
    }

    public Infraction findByInfractionId(String infractionId) throws ResourceNotFoundException {
        var infraction = repository.findByInfractionId(infractionId);
        if (infraction == null) {
            throw new ResourceNotFoundException("No infraction with that ID exists");
        }
        log.debug("Found infraction: {}", infraction);
        return infraction;
    }

    public List<Infraction> findAllInfractions() {
        return repository.findAll();
    }
}
