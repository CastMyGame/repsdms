package com.reps.demogcloud.data;

import com.reps.demogcloud.models.officeReferral.OfficeReferral;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OfficeReferralRepository extends MongoRepository<OfficeReferral, String> {
}
