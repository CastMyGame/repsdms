package com.reps.demogcloud.security.models;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document(collection = "refreshTokens")
public class RefreshToken {

    @Id
    private String id;

    @Indexed
    private String username;

    @Indexed(unique = true)
    private String tokenHash;

    @Indexed
    private Instant expiresAt;

    private Instant createdAt;
    private Instant lastUsedAt;
    private Instant revokedAt;
    private String replacedByTokenId;
    private String deviceName;

    public boolean isExpired(Instant now) {
        return expiresAt == null || !expiresAt.isAfter(now);
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }
}
