package com.reps.demogcloud.services.infraction;

import com.reps.demogcloud.data.InfractionRepository;
import com.reps.demogcloud.exceptions.ResourceNotFoundException;
import com.reps.demogcloud.models.infraction.Infraction;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class InfractionMutationService {

    private final InfractionRepository repository;

    public Infraction createNewInfraction(Infraction infraction) {
        return repository.save(infraction);
    }

    public String deleteInfraction(Infraction infraction) throws ResourceNotFoundException {
        try {
            repository.delete(infraction);
        } catch (Exception e) {
            throw new ResourceNotFoundException("That infraction does not exist");
        }
        return infraction.getInfractionName() + " has been deleted";
    }
}
