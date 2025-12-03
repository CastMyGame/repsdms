package com.reps.demogcloud.security.services;

import lombok.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory token store for proof-of-concept Gmail sending.
 * In production you should persist these tokens securely (encrypted at rest).
 */
@Component
public class GoogleOAuthTokenStore {

    private final Map<String, GoogleOAuthToken> tokensByUsername = new ConcurrentHashMap<>();

    public void storeToken(String username, GoogleOAuthToken token) {
        tokensByUsername.put(username, token);
    }

    public Optional<GoogleOAuthToken> getToken(String username) {
        return Optional.ofNullable(tokensByUsername.get(username));
    }

    @Value
    public static class GoogleOAuthToken {
        String accessToken;
        Instant accessTokenExpiresAt;
        String refreshToken;
    }
}

