package com.reps.demogcloud.security.utils;

public class TokenStatus {
    private boolean expired;
    private long timeUntilExpiration;

    // Constructor
    public TokenStatus(boolean expired, long timeUntilExpiration) {
        this.expired = expired;
        this.timeUntilExpiration = timeUntilExpiration;
    }

    // Getters
    public boolean isExpired() {
        return expired;
    }

    public long getTimeUntilExpiration() {
        return timeUntilExpiration;
    }
}
