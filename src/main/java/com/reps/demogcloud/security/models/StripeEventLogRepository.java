package com.reps.demogcloud.security.models;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface StripeEventLogRepository extends MongoRepository<StripeEventLog, String> {
}
