package com.reps.demogcloud.security.services;

import com.reps.demogcloud.data.RefreshTokenRepository;
import com.reps.demogcloud.security.models.RefreshToken;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private static final int TOKEN_BYTES = 64;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    @Value("${security.jwt.refresh-token-days:30}")
    private long refreshTokenDays;

    public String issue(UserModel user, String deviceName) {
        if (refreshTokenDays <= 0) {
            throw new IllegalStateException("security.jwt.refresh-token-days must be positive");
        }

        refreshTokenRepository.deleteByExpiresAtBefore(Instant.now());

        String rawToken = generateRawToken();
        Instant now = Instant.now();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setUsername(user.getUsername());
        refreshToken.setTokenHash(hash(rawToken));
        refreshToken.setCreatedAt(now);
        refreshToken.setExpiresAt(now.plusSeconds(refreshTokenDays * 24 * 60 * 60));
        refreshToken.setDeviceName(normalizeDeviceName(deviceName));
        refreshTokenRepository.save(refreshToken);

        return rawToken;
    }

    public Rotation rotate(String rawToken, String deviceName) {
        RefreshToken current = findValidToken(rawToken);
        Instant now = Instant.now();

        UserModel user = userRepository.findByUsername(current.getUsername());
        if (user == null || !user.isEnabled()) {
            revoke(current, now);
            throw new IllegalArgumentException("Refresh token user is no longer active");
        }

        String replacementRawToken = generateRawToken();
        RefreshToken replacement = new RefreshToken();
        replacement.setUsername(user.getUsername());
        replacement.setTokenHash(hash(replacementRawToken));
        replacement.setCreatedAt(now);
        replacement.setExpiresAt(now.plusSeconds(refreshTokenDays * 24 * 60 * 60));
        replacement.setDeviceName(normalizeDeviceName(deviceName));
        refreshTokenRepository.save(replacement);

        current.setLastUsedAt(now);
        current.setRevokedAt(now);
        current.setReplacedByTokenId(replacement.getId());
        refreshTokenRepository.save(current);

        return new Rotation(user, replacementRawToken);
    }

    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }

        refreshTokenRepository.findByTokenHash(hash(rawToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> revoke(token, Instant.now()));
    }

    public void revokeAllForUser(String username) {
        Instant now = Instant.now();
        refreshTokenRepository.findByUsernameAndRevokedAtIsNull(username)
                .forEach(token -> revoke(token, now));
    }

    private RefreshToken findValidToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("Refresh token is required");
        }

        RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
                .orElseThrow(() -> new IllegalArgumentException("Invalid refresh token"));
        Instant now = Instant.now();
        if (token.isRevoked() || token.isExpired(now)) {
            throw new IllegalArgumentException("Expired or revoked refresh token");
        }
        return token;
    }

    private void revoke(RefreshToken token, Instant now) {
        token.setRevokedAt(now);
        refreshTokenRepository.save(token);
    }

    private String generateRawToken() {
        byte[] tokenBytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizeDeviceName(String deviceName) {
        if (deviceName == null || deviceName.isBlank()) {
            return "unknown";
        }
        return deviceName.trim().substring(0, Math.min(deviceName.trim().length(), 120));
    }

    public record Rotation(UserModel user, String rawRefreshToken) {
    }
}
