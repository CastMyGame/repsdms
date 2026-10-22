package com.reps.demogcloud.security.utils;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class JwtUtilsLoggingTest {
    @Test
    void blacklistChecksDoNotPrintBearerTokens() {
        JwtUtils jwt = new JwtUtils();
        String token = jwt.generateToken(User.withUsername("synthetic@example.test")
                .password("unused").roles("TEACHER").build());
        PrintStream previous = System.out;
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try {
            System.setOut(new PrintStream(output, true, StandardCharsets.UTF_8));
            assertFalse(jwt.isTokenBlacklisted(token));
            jwt.blacklistToken(token);
            assertTrue(jwt.isTokenBlacklisted(token));
        } finally {
            System.setOut(previous);
        }
        assertFalse(output.toString(StandardCharsets.UTF_8).contains(token), "Bearer must never be printed");
    }
}
