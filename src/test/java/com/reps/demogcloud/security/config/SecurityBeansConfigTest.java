package com.reps.demogcloud.security.config;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class SecurityBeansConfigTest {

    @Test
    void passwordEncoder_shouldReturnBCryptPasswordEncoder() {
        SecurityBeansConfig config = new SecurityBeansConfig();

        PasswordEncoder encoder = config.passwordEncoder();

        assertNotNull(encoder);
        assertTrue(encoder instanceof BCryptPasswordEncoder);
    }

    @Test
    void passwordEncoder_shouldEncodeAndMatchPassword() {
        SecurityBeansConfig config = new SecurityBeansConfig();

        PasswordEncoder encoder = config.passwordEncoder();

        String rawPassword = "myPassword123";
        String encoded = encoder.encode(rawPassword);

        assertNotNull(encoded);
        assertNotEquals(rawPassword, encoded); // should not be plain text
        assertTrue(encoder.matches(rawPassword, encoded));
    }

    @Test
    void passwordEncoder_shouldNotMatchWrongPassword() {
        SecurityBeansConfig config = new SecurityBeansConfig();

        PasswordEncoder encoder = config.passwordEncoder();

        String encoded = encoder.encode("correctPassword");

        assertFalse(encoder.matches("wrongPassword", encoded));
    }
}