package com.reps.demogcloud.data;

import com.reps.demogcloud.security.models.RefreshToken;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface RefreshTokenRepository extends MongoRepository<RefreshToken, String> {
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findByUsernameAndRevokedAtIsNull(String username);

    long deleteByExpiresAtBefore(Instant expiration);
}
