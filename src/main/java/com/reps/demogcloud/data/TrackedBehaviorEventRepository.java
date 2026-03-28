package com.reps.demogcloud.data;

import com.reps.demogcloud.models.trackedBehavior.TrackedBehaviorEvent;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface TrackedBehaviorEventRepository extends MongoRepository<TrackedBehaviorEvent, String> {

    List<TrackedBehaviorEvent> findByStudentEmailOrderByTimeCreatedDesc(String studentEmail);

    List<TrackedBehaviorEvent> findByTeacherEmailOrderByTimeCreatedDesc(String teacherEmail);

    List<TrackedBehaviorEvent> findByStudentEmailAndBehaviorCodeOrderByTimeCreatedDesc(String studentEmail, String behaviorCode);

    List<TrackedBehaviorEvent> findByStudentEmailAndTeacherEmailOrderByTimeCreatedDesc(String studentEmail, String teacherEmail);

    List<TrackedBehaviorEvent> findBySchoolOrderByTimeCreatedDesc(String school);

    List<TrackedBehaviorEvent> findByClassPeriodAndTeacherEmailOrderByTimeCreatedDesc(String classPeriod, String teacherEmail);

    List<TrackedBehaviorEvent> findBySchoolAndStudentEmailInOrderByTimeCreatedDesc(String school, List<String> studentEmails);
}