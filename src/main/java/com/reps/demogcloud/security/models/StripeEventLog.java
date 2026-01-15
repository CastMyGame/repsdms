package com.reps.demogcloud.security.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document("stripe_event_log")
public class StripeEventLog {
    @Id
    private String id;      // use eventId as _id
    private java.time.Instant processedAt;
    private String type;
}
