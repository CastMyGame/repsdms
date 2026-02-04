package com.reps.demogcloud.security.services.stripe;

import com.reps.demogcloud.security.models.stripe.RegistrationIntent;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class RegistrationIntentLockService {

    private final MongoTemplate mongoTemplate;

    /**
     * Atomically transitions an intent to PROCESSING if it is not already COMPLETED/PROCESSING.
     * Returns true if this thread "won" the lock, false otherwise.
     */
    public boolean claimForProcessing(String intentId) {
        Query q = new Query(
                new Criteria().andOperator(
                        Criteria.where("_id").is(intentId),
                        Criteria.where("status").ne("COMPLETED"),
                        Criteria.where("status").ne("PROCESSING")
                )
        );

        Update u = new Update()
                .set("status", "PROCESSING");

        RegistrationIntent updated = mongoTemplate.findAndModify(
                q,
                u,
                FindAndModifyOptions.options().returnNew(true),
                RegistrationIntent.class
        );

        return updated != null;
    }

    /**
     * If something fails during provisioning, revert PROCESSING -> PENDING so Stripe retries can work.
     * Returns true if it changed something.
     */
    public boolean releaseProcessing(String intentId) {
        Query q = new Query(
                new Criteria().andOperator(
                        Criteria.where("_id").is(intentId),
                        Criteria.where("status").is("PROCESSING")
                )
        );

        Update u = new Update().set("status", "PENDING");

        RegistrationIntent updated = mongoTemplate.findAndModify(
                q,
                u,
                FindAndModifyOptions.options().returnNew(true),
                RegistrationIntent.class
        );

        return updated != null;
    }

    /**
     * Mark PROCESSING -> COMPLETED.
     * Returns true if it changed something.
     */
    public boolean markCompleted(String intentId, String stripeCheckoutSessionId, String stripeCustomerId, String stripeSubscriptionId) {
        Query q = new Query(
                new Criteria().andOperator(
                        Criteria.where("_id").is(intentId),
                        Criteria.where("status").is("PROCESSING")
                )
        );

        Update u = new Update()
                .set("status", "COMPLETED")
                .set("stripeCheckoutSessionId", stripeCheckoutSessionId)
                .set("stripeCustomerId", stripeCustomerId)
                .set("stripeSubscriptionId", stripeSubscriptionId);

        RegistrationIntent updated = mongoTemplate.findAndModify(
                q,
                u,
                FindAndModifyOptions.options().returnNew(true),
                RegistrationIntent.class
        );

        return updated != null;
    }
}
