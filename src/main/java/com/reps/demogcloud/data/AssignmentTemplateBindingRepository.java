package com.reps.demogcloud.data;

import com.reps.demogcloud.models.assignments.AssignmentTemplateBinding;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface AssignmentTemplateBindingRepository
        extends MongoRepository<AssignmentTemplateBinding, String> {

    Optional<AssignmentTemplateBinding> findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrue(
            String teacherEmail,
            String infractionName,
            int level
    );

    Optional<AssignmentTemplateBinding> findFirstByTeacherEmailIsNullAndSchoolIdAndInfractionNameAndLevelAndActiveTrue(
            String schoolId,
            String infractionName,
            int level
    );

    List<AssignmentTemplateBinding> findByTeacherEmailAndActiveTrue(String teacherEmail);

    List<AssignmentTemplateBinding> findByAssignmentTemplateIdAndActiveTrue(String assignmentTemplateId);

    Optional<AssignmentTemplateBinding> findFirstByTeacherEmailAndInfractionNameAndLevelAndActiveTrueOrderByCreatedAtDesc(String teacherEmail, String infractionName, int level);
}
