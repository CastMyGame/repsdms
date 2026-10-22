package com.reps.demogcloud.models.email;

import lombok.Data;
import java.time.Instant;
import java.util.*;

/** Internal embedded obligation, never accepted from or serialized to case clients. */
@Data
@lombok.ToString(onlyExplicitlyIncluded=true)
public class RequiredNotification {
    private String notificationId;
    private String actionId;
    private String event;
    private String actor;
    private boolean system;
    private String state = "PENDING";
    private boolean retrySafe;
    private Instant createdAt = Instant.now();
    private Instant claimedAt;
    private String attemptId;
    private int attempts;
    private String reason;
    private String provider;
    private String providerMessageId;
    private String gmailThreadId;
    private String rfcMessageId;
    private String inReplyTo;
    private String references;
    private String body;
    private Map<String,String> content;
    private List<String> recipients;
    private Map<String,String> recipientOutcomes;
    private String resolvedBy;
    private String resolution;
}
