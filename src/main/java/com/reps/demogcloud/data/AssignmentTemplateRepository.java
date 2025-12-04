package com.reps.demogcloud.data;

import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AssignmentTemplateRepository extends MongoRepository<AssignmentTemplate, String> {

    List<AssignmentTemplate> findByInfractionNameAndLevel(String infractionName, int level);
}