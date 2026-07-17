package com.reps.demogcloud.security.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Service
public class JwtUtils {
    private final long expirationTimeMillis;
    private final SecretKey secretKey;

    public JwtUtils(
            @Value("${security.jwt.secret}") String base64Secret,
            @Value("${security.jwt.access-token-minutes:15}") long accessTokenMinutes
    ) {
        byte[] keyBytes;
        try {
            keyBytes = Decoders.BASE64.decode(base64Secret);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("security.jwt.secret must be a Base64-encoded key", exception);
        }

        if (keyBytes.length < 32) {
            throw new IllegalStateException("security.jwt.secret must decode to at least 256 bits");
        }
        if (accessTokenMinutes <= 0) {
            throw new IllegalStateException("security.jwt.access-token-minutes must be positive");
        }

        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
        this.expirationTimeMillis = accessTokenMinutes * 60 * 1000;
    }

    public String generateToken(UserDetails userDetails) {
        Map<String, Object> claims = new HashMap<>();
        return createToken(claims, userDetails.getUsername());
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        String userName = extractUserName(token);
        return userName.equals(userDetails.getUsername()) && !isTokenExpired(token) && !isTokenBlacklisted(token);
    }


    private String createToken(Map<String, Object> claims, String subject) {
        Date now = new Date();
        Date expiration = new Date(now.getTime() + expirationTimeMillis);

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(now)
                .setExpiration(expiration)
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser().setSigningKey(secretKey).parseClaimsJws(token).getBody();
    }

    /**
     * Compatibility endpoint for the existing web client. New clients must use
     * a persisted refresh token through /auth/refresh instead.
     */
    @Deprecated
    public String renewTokenWithBlacklist(String oldToken) {
        if (!isTokenExpired(oldToken)) {
            String username = extractUserName(oldToken);
            Map<String, Object> claims = new HashMap<>();
            return createToken(claims, username);
        } else {
            throw new RuntimeException("Token is expired");
        }
    }



    public String extractUserName(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    /**
     * Access-token revocation is intentionally handled through persisted refresh
     * token revocation. This method remains temporarily for callers compiled
     * against the old API.
     */
    @Deprecated
    public void blacklistToken(String token) {
        // No-op: access tokens are short lived and refresh sessions are revoked persistently.
    }

    @Deprecated
    public boolean isTokenBlacklisted(String token) {
        return false;
    }


    public TokenStatus getTokenStatus(String token) {
        Date expirationDate = extractExpiration(token);
        long timeUntilExpiration = expirationDate.getTime() - new Date().getTime();
        boolean isExpired = timeUntilExpiration <= 0;

        return new TokenStatus(isExpired, isExpired ? 0 : timeUntilExpiration);
    }
}

