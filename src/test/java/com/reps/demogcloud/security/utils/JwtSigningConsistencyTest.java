package com.reps.demogcloud.security.utils;

import com.reps.demogcloud.security.services.JwtRevocationStore;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.core.userdetails.User;
import io.jsonwebtoken.JwtException;
import java.time.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class JwtSigningConsistencyTest {
    private final JwtRevocationStore store = JwtTestSupport.store();
    private final org.springframework.security.core.userdetails.UserDetails user = User.withUsername("synthetic@example.test").password("unused").roles("TEACHER").build();
    @Test void instancesAndRestartUseIdenticalConfiguredSigningMaterial() {
        var a=new JwtUtils(JwtTestSupport.environment(),store); var b=new JwtUtils(JwtTestSupport.environment(),store);
        String token=a.generateToken(user,"GOOGLE");assertTrue(b.validateToken(token,user));
        assertTrue(new JwtUtils(JwtTestSupport.environment(),store).validateToken(token,user));
        assertEquals("GOOGLE",b.extractLoginMethod(token));
        long ttl=b.extractExpiration(token).getTime()-b.extractClaim(token,c->c.getIssuedAt()).getTime();assertEquals(20*60*1000,ttl);
    }
    @Test void unknownSigningKeyIsRejectedForValidationAndRenewal() {
        String token=new JwtUtils(JwtTestSupport.environment(),store).generateToken(user);
        var other=new MockEnvironment().withProperty("reps.auth.jwt.signing-key",Base64.getEncoder().encodeToString("diff".repeat(8).getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        var jwt=new JwtUtils(other,store);assertThrows(JwtException.class,()->jwt.validateToken(token,user));assertThrows(JwtException.class,()->jwt.renewTokenWithBlacklist(token));
    }
    @Test void expiredTokensCannotValidateOrRenew() {
        var issued=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
        String token=new JwtUtils(JwtTestSupport.environment(),store,issued).generateToken(user);
        var later=new JwtUtils(JwtTestSupport.environment(),store,Clock.offset(issued,Duration.ofMinutes(21)));
        assertThrows(JwtException.class,()->later.validateToken(token,user));assertThrows(JwtException.class,()->later.renewTokenWithBlacklist(token));
    }
    @Test void renewalAndLogoutAreSharedAcrossInstancesAndRestart() {
        var a=new JwtUtils(JwtTestSupport.environment(),store);var b=new JwtUtils(JwtTestSupport.environment(),store);
        String old=a.generateToken(user,"GOOGLE");String renewed=b.renewTokenWithBlacklist(old);assertNotEquals(old,renewed);
        assertFalse(a.validateToken(old,user));assertThrows(JwtException.class,()->a.renewTokenWithBlacklist(old));assertTrue(a.validateToken(renewed,user));
        assertEquals("GOOGLE",a.extractLoginMethod(renewed));a.blacklistToken(renewed);
        assertFalse(new JwtUtils(JwtTestSupport.environment(),store).validateToken(renewed,user));
    }
    @Test void disabledUsersAndDifferentSubjectsAreRejected() {
        var jwt=new JwtUtils(JwtTestSupport.environment(),store);String token=jwt.generateToken(user);
        assertFalse(jwt.validateToken(token,User.withUserDetails(user).disabled(true).build()));
        assertFalse(jwt.validateToken(token,User.withUsername("another@example.test").password("unused").roles("ADMIN").build()));
    }
    @Test void missingMalformedNonCanonicalOrWeakConfigurationFailsWithoutExposingValue() {
        for(String bad:List.of("", "not-base64!", "test", JwtTestSupport.key()+"\n",Base64.getEncoder().encodeToString(new byte[31]),Base64.getEncoder().encodeToString(new byte[33]))) {
            var environment=new MockEnvironment().withProperty("reps.auth.jwt.signing-key",bad);
            var error=assertThrows(IllegalStateException.class,()->new JwtUtils(environment,store));
            assertNull(error.getCause());if(!bad.isEmpty())assertFalse(error.getMessage().contains(bad));
        }
        assertThrows(IllegalStateException.class,()->new JwtUtils(new MockEnvironment(),store));
    }
    @Test void simultaneousRenewalsHaveOneWinnerEvenWithinTheSameSecond() throws Exception {
        var fixed=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
        var a=new JwtUtils(JwtTestSupport.environment(),store,fixed);var b=new JwtUtils(JwtTestSupport.environment(),store,fixed);
        String token=a.generateToken(user);var pool=java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            var results=pool.invokeAll(List.<java.util.concurrent.Callable<String>>of(
                ()->{try{return a.renewTokenWithBlacklist(token);}catch(JwtException rejected){return null;}},
                ()->{try{return b.renewTokenWithBlacklist(token);}catch(JwtException rejected){return null;}}));
            var values=new ArrayList<String>();for(var result:results)values.add(result.get());
            assertEquals(1,values.stream().filter(Objects::nonNull).count());
            String renewed=values.stream().filter(Objects::nonNull).findFirst().orElseThrow();assertNotEquals(token,renewed);assertTrue(a.validateToken(renewed,user));
        } finally { pool.shutdownNow(); }
    }

}
