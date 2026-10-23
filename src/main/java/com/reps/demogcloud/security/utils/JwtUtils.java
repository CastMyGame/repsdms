package com.reps.demogcloud.security.utils;

import com.reps.demogcloud.security.services.JwtRevocationStore;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import javax.crypto.SecretKey;
import java.time.Clock;
import java.util.*;
import java.util.function.Function;

@Service
public class JwtUtils {
    private static final long EXPIRATION_TIME = 20 * 60 * 1000;
    private final SecretKey secretKey;
    private final JwtRevocationStore revocations;
    private final Clock clock;

    @Autowired
    public JwtUtils(Environment environment, JwtRevocationStore revocations) {
        this(environment, revocations, Clock.systemUTC());
    }

    JwtUtils(Environment environment, JwtRevocationStore revocations, Clock clock) {
        // Never allow binding/conversion exceptions to include the supplied secret in startup logs.
        byte[] decoded = null;
        try {
            if (!environment.getProperty("reps.auth.jwt.signing-key-version", "1").matches("[1-9][0-9]*")) throw new IllegalArgumentException();
            String configured = environment.getProperty("reps.auth.jwt.signing-key", "");
            decoded = Base64.getDecoder().decode(configured);
            if (decoded.length != 32 || !Base64.getEncoder().encodeToString(decoded).equals(configured)) throw new IllegalArgumentException();
            secretKey = Keys.hmacShaKeyFor(decoded);
        } catch (RuntimeException invalid) {
            throw new IllegalStateException("JWT signing configuration is required: use a numeric secret version and canonical standard Base64 encoding of exactly 32 random bytes");
        } finally { if (decoded != null) Arrays.fill(decoded, (byte) 0); }
        this.revocations = Objects.requireNonNull(revocations);
        this.clock = Objects.requireNonNull(clock);
    }

    public String generateToken(UserDetails userDetails) { return generateToken(userDetails, "PASSWORD"); }
    public String generateToken(UserDetails userDetails, String loginMethod) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("loginMethod", loginMethod);
        if (userDetails instanceof com.reps.demogcloud.security.models.AccountPrincipal account) claims.put("credentialVersion", account.credentialVersion);
        return createToken(claims, userDetails.getUsername());
    }
    public boolean validateToken(String token, UserDetails userDetails) {
        Claims claims = extractAllClaims(token);
        if (userDetails instanceof com.reps.demogcloud.security.models.AccountPrincipal account) {
            Number version = claims.get("credentialVersion", Number.class);
            if ((version == null ? 0 : version.longValue()) != account.credentialVersion) return false;
        }
        return userDetails.isEnabled() && userDetails.isAccountNonLocked() && userDetails.isAccountNonExpired()
                && userDetails.isCredentialsNonExpired() && claims.getExpiration().after(Date.from(clock.instant()))
                && userDetails.getUsername().equals(claims.getSubject())
                && !isTokenBlacklisted(token);
    }
    private String createToken(Map<String, Object> claims, String subject) {
        Date now = Date.from(clock.instant());
        return Jwts.builder().setClaims(claims).setId(UUID.randomUUID().toString()).setSubject(subject).setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + EXPIRATION_TIME)).signWith(secretKey, SignatureAlgorithm.HS256).compact();
    }
    public <T> T extractClaim(String token, Function<Claims, T> resolver) { return resolver.apply(extractAllClaims(token)); }
    private Claims extractAllClaims(String token) {
        var parsed = Jwts.parserBuilder().setSigningKey(secretKey).setClock(() -> Date.from(clock.instant())).build().parseClaimsJws(token);
        if (!"HS256".equals(parsed.getHeader().getAlgorithm())) throw new JwtException("Unsupported token algorithm");
        if (parsed.getBody().getExpiration() == null || parsed.getBody().getSubject() == null || parsed.getBody().getSubject().isBlank())
            throw new JwtException("Required token claims are missing");
        return parsed.getBody();
    }
    public String renewTokenWithBlacklist(String oldToken) {
        var claims = extractAllClaims(oldToken); // Expired/unknown signatures are never accepted for renewal.
        if (!claims.getExpiration().after(Date.from(clock.instant()))) throw new JwtException("Token has expired");
        Map<String, Object> renewed = new HashMap<>();
        renewed.put("loginMethod", claims.getOrDefault("loginMethod", "PASSWORD"));
        renewed.put("credentialVersion", claims.get("credentialVersion", Number.class));
        // Construct first, then atomically consume. No valid token is returned if storage/claim fails.
        String token = createToken(renewed, claims.getSubject());
        if (!revocations.revoke(oldToken, claims.getExpiration())) throw new JwtException("Token has already been revoked");
        return token;
    }
    public String extractUserName(String token) { return extractClaim(token, Claims::getSubject); }
    public String extractLoginMethod(String token) {
        String method = extractClaim(token, c -> c.get("loginMethod", String.class));
        return method == null || method.isBlank() ? "PASSWORD" : method;
    }
    public Date extractExpiration(String token) { return extractClaim(token, Claims::getExpiration); }
    public boolean isTokenExpired(String token) { return !extractExpiration(token).after(Date.from(clock.instant())); }
    public void blacklistToken(String token) { revocations.revoke(token, extractExpiration(token)); }
    public boolean isTokenBlacklisted(String token) { return revocations.contains(token); }
    public TokenStatus getTokenStatus(String token) {
        long remaining = extractExpiration(token).getTime() - clock.millis();
        return new TokenStatus(remaining <= 0, Math.max(0, remaining));
    }
}
