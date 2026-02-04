package com.reps.demogcloud.data;

import com.reps.demogcloud.models.school.School;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SchoolRepository extends MongoRepository<School, String> {
    Optional<School> findBySchoolNameIgnoreCase(String schoolName);

    Optional<School> findByNormalizedKey(String normalizedKey);

    List<School> findByCityIgnoreCaseAndStateIgnoreCaseOrderBySchoolNameAsc(String city, String state);

    List<School> findByZip(String zip);


}
