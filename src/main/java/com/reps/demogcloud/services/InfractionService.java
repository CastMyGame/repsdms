package com.reps.demogcloud.services;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.models.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@Slf4j
public class InfractionService {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final InfractionRepository repository;

    public InfractionService(InfractionRepository repository) {
        this.repository = repository;
    }

    public Infraction findInfractionByInfractionName (String infractionName) throws ResourceNotFoundException {
        var findMe = repository.findByInfractionName(infractionName);

        if (findMe == null) {
            throw new ResourceNotFoundException("No infraction with that name exists");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }

    public Infraction findByInfractionId (String infractionId) throws ResourceNotFoundException {
        var findMe = repository.findByInfractionId(infractionId);

        if (findMe == null) {
            throw new ResourceNotFoundException("No infraction with that ID exists");
        }
        logger.debug(String.valueOf(findMe));
        return findMe;
    }

    public Infraction createNewInfraction (Infraction infraction ) {
        return repository.save(infraction);
    }
    public String deleteInfraction ( Infraction infraction ) throws ResourceNotFoundException {
        try{repository.delete(infraction);}
        catch (Exception e) {
            throw new ResourceNotFoundException("That infraction does not exist");
        } return "${infraction} has been deleted";
    }

    public List<Infraction> findAllInfractions() {
        return repository.findAll();
    }
}
