package com.reps.demogcloud.data;

import com.reps.demogcloud.models.school.School;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SchoolRepository extends MongoRepository<School, String> {
    School findSchoolBySchoolName (String schoolName);
}
