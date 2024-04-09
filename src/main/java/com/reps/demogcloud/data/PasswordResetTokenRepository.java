package com.reps.demogcloud.data;

import com.reps.demogcloud.security.models.PasswordResetToken;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface PasswordResetTokenRepository extends MongoRepository<PasswordResetToken, String> {

    // Find a password reset token by token value
    PasswordResetToken findByToken(String token);

    // Additional custom queries can be added if needed
    // For example, find tokens by user ID, delete expired tokens, etc.
}