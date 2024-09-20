package com.reps.demogcloud.security.utils;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.*;
import java.util.function.Function;

@Service
public class JwtUtils {
    private static final long EXPIRATION_TIME = 20 * 60 * 1000; // 10 minutes
    private final SecretKey secretKey = Keys.secretKeyFor(SignatureAlgorithm.HS256);
    private final Set<String> blacklistedTokens = new HashSet<>();

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
        Date expiration = new Date(now.getTime() + EXPIRATION_TIME);

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

    public String renewTokenWithBlacklist(String oldToken) {
        if (!isTokenExpired(oldToken) && !isTokenBlacklisted(oldToken)) {
            String username = extractUserName(oldToken);
            // Blacklist the old token to prevent reuse
            blacklistToken(oldToken);

            Map<String, Object> claims = new HashMap<>();
            return createToken(claims, username);
        } else {
            throw new RuntimeException("Token is either expired or blacklisted");
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

    public void blacklistToken(String token) {
        blacklistedTokens.add(token);
    }

    public boolean isTokenBlacklisted(String token) {
        boolean isBlacklisted = blacklistedTokens.contains(token);
        System.out.println(isBlacklisted);
        System.out.println("list: " +blacklistedTokens);
        System.out.println("incoming: " +token);



        return isBlacklisted;
    }


    public TokenStatus getTokenStatus(String token) {
        Date expirationDate = extractExpiration(token);
        long timeUntilExpiration = expirationDate.getTime() - new Date().getTime();
        boolean isExpired = timeUntilExpiration <= 0;

        return new TokenStatus(isExpired, isExpired ? 0 : timeUntilExpiration);
    }
}

