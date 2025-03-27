package com.reps.demogcloud.data;

import com.reps.demogcloud.models.guidance.GuidanceReferral;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface GuidanceRepository extends MongoRepository<GuidanceReferral, String> {
    List<GuidanceReferral> findAllByStatus(String status);
}
