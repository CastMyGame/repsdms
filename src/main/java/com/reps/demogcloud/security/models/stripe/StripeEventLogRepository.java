package com.reps.demogcloud.security.models.stripe;

import com.reps.demogcloud.security.models.stripe.StripeEventLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StripeEventLogRepository extends MongoRepository<StripeEventLog, String> {
}
