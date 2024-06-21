package com.reps.demogcloud.data;

import com.reps.demogcloud.models.assignments.Assignment;
import com.reps.demogcloud.models.guidance.Guidance;
import com.reps.demogcloud.models.punishment.Punishment;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuidanceRepository extends MongoRepository<Guidance, String> {
    List<Guidance> findAllByStatus(String status);
}
