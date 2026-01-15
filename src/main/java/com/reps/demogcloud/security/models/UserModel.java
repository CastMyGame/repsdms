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
    private String password;
    private String firstName;
    private String lastName;
    private String school;
    private Set<RoleModel> roles;
    private boolean enabled = true; // Default to true for existing users, can be set to false to disable access
    private boolean paid = false;
    private java.time.Instant accessEndsAt;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private String stripePriceId;
    private String stripeStatus;
    private java.time.Instant lastPaymentAt;


}