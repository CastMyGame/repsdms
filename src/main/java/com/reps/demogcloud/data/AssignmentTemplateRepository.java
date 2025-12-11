package com.reps.demogcloud.data;

import com.reps.demogcloud.models.assignments.AssignmentTemplate;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AssignmentTemplateRepository extends MongoRepository<AssignmentTemplate, String> {

    List<AssignmentTemplate> findByInfractionNameAndLevel(String infractionName, int level);

    List<AssignmentTemplate> findByInfractionNameAndLevelAndScopeAndActiveTrue(
            String infractionName,
            int level,
            AssignmentTemplate.Scope scope
    );

    List<AssignmentTemplate> findByInfractionNameAndLevelAndScopeAndCreatedByUserIdAndActiveTrue(
            String infractionName,
            int level,
            AssignmentTemplate.Scope scope,
            String createdByUserId
    );

    List<AssignmentTemplate> findByInfractionNameAndLevelAndScopeAndSchoolIdAndActiveTrue(
            String infractionName,
            int level,
            AssignmentTemplate.Scope scope,
            String schoolId
    );
}