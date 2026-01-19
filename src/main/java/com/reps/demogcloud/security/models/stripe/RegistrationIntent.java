package com.reps.demogcloud.security.models.stripe;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Document(collection = "registration_intents")
public class RegistrationIntent {

    @Id
    private String id;

    // TTL cleanup: auto-delete after N seconds (optional, see note below)
    // If you enable this, make sure your MongoDB deployment supports TTL indexes.
    @Indexed(expireAfterSeconds = 60 * 60 * 24) // 24 hours
    private Instant createdAt;

    private String status; // "PENDING", "COMPLETED", "EXPIRED"

    // School info
    private String schoolIdNumber;
    private String schoolName;
    private String currencyName;

    // Teacher/Employee info
    private String firstName;
    private String lastName;
    private String email;

    // Plan
    private String priceId;

    // Stripe fields (filled later)
    private String stripeCheckoutSessionId;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
}

