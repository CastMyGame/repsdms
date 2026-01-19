package com.reps.demogcloud.security.models.stripe;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface RegistrationIntentRepository extends MongoRepository<RegistrationIntent, String> {
    Optional<RegistrationIntent> findByStripeCheckoutSessionId(String stripeCheckoutSessionId);
    Optional<RegistrationIntent> findByEmailIgnoreCaseAndStatus(String email, String status);
}
