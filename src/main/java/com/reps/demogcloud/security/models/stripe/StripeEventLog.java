package com.reps.demogcloud.security.models.stripe;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document("stripe_event_log")
public class StripeEventLog {
    @Id
    private String id;
    private Instant processedAt;
    private String type;

    public static StripeEventLog processed(String eventId, String type) {
        return new StripeEventLog(eventId, Instant.now(), type);
    }
}
