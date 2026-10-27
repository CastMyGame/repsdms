package com.reps.demogcloud.security.models;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Set;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Document(collection = "users")
public class UserModel {

    @Id
    private String id;
    //UserName is the email address
    private String username;
    @com.fasterxml.jackson.annotation.JsonProperty(access = com.fasterxml.jackson.annotation.JsonProperty.Access.WRITE_ONLY)
    @lombok.ToString.Exclude
    private String password;
    private String firstName;
    private String lastName;
    private String school;
    /** Stored membership only. Ordinary HTTP payloads cannot establish or transfer a tenant. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String tenantSchoolId;
    // Older user documents store this membership under schoolName.
    // Keep it separate so it cannot override an explicitly stored school.
    @org.springframework.data.mongodb.core.mapping.Field("schoolName")
    @com.fasterxml.jackson.annotation.JsonIgnore
    private String legacySchoolName;

    public String getSchool() {
        return school != null ? school : legacySchoolName;
    }

    private Set<RoleModel> roles;
    private boolean enabled = true; // Default to true for existing users, can be set to false to disable access
    // Missing fields preserve existing accounts. Never serialize bearer setup authority.
    private boolean passwordSetupRequired;
    private long credentialVersion;
    private String invitationStatus;
    private java.time.Instant invitationExpiresAt;
    private java.time.Instant setupCompletedAt;
    private boolean privateEmailSetupAllowed;
    @com.fasterxml.jackson.annotation.JsonIgnore @lombok.ToString.Exclude
    private AccessTicket accessTicket;
    @Data @NoArgsConstructor
    public static class AccessTicket {
        private String digest;
        private String purpose;
        private String environment;
        private String issueId;
        private java.time.Instant expiresAt;
        private int attempts;
    }
    private boolean paid = false;
    private java.time.Instant accessEndsAt;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private String stripePriceId;
    private String stripeStatus;
    private java.time.Instant lastPaymentAt;


}
