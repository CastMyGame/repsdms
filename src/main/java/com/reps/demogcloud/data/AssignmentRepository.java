package com.reps.demogcloud.data;

import com.reps.demogcloud.models.assignments.Assignment;
import com.reps.demogcloud.models.infraction.Infraction;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AssignmentRepository extends MongoRepository<Assignment, String> {

}
