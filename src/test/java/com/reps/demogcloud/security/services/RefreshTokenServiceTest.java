package com.reps.demogcloud.security.services;

import com.reps.demogcloud.data.RefreshTokenRepository;
import com.reps.demogcloud.security.models.RefreshToken;
import com.reps.demogcloud.security.models.UserModel;
import com.reps.demogcloud.security.models.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RefreshTokenServiceTest {

    private RefreshTokenRepository refreshTokenRepository;
    private UserRepository userRepository;
    private RefreshTokenService refreshTokenService;
    private UserModel user;

    @BeforeEach
    void setUp() {
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        userRepository = mock(UserRepository.class);
        refreshTokenService = new RefreshTokenService(refreshTokenRepository, userRepository);
        ReflectionTestUtils.setField(refreshTokenService, "refreshTokenDays", 30L);

        user = new UserModel();
        user.setUsername("teacher@test.com");
        user.setEnabled(true);
    }

    @Test
    void issue_persistsOnlyAHashOfTheRefreshToken() {
        String rawToken = refreshTokenService.issue(user, "REPS Mobile");

        ArgumentCaptor<RefreshToken> captor = ArgumentCaptor.forClass(RefreshToken.class);
        org.mockito.Mockito.verify(refreshTokenRepository).save(captor.capture());

        RefreshToken saved = captor.getValue();
        assertEquals("teacher@test.com", saved.getUsername());
        assertNotEquals(rawToken, saved.getTokenHash());
        assertNotNull(saved.getExpiresAt());
        assertEquals("REPS Mobile", saved.getDeviceName());
    }

    @Test
    void rotate_revokesTheOriginalTokenAndIssuesANewOne() {
        RefreshToken original = new RefreshToken();
        original.setId("original-id");
        original.setUsername("teacher@test.com");
        original.setExpiresAt(Instant.now().plusSeconds(3600));

        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(original));
        when(userRepository.findByUsername("teacher@test.com")).thenReturn(user);
        when(refreshTokenRepository.save(any(RefreshToken.class))).thenAnswer(invocation -> {
            RefreshToken saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId("replacement-id");
            }
            return saved;
        });

        RefreshTokenService.Rotation rotation = refreshTokenService.rotate("raw-token", "REPS Mobile");

        assertEquals(user, rotation.user());
        assertNotEquals("raw-token", rotation.rawRefreshToken());
        assertNotNull(original.getRevokedAt());
        assertEquals("replacement-id", original.getReplacedByTokenId());
    }

    @Test
    void rotate_rejectsAnExpiredOrRevokedToken() {
        RefreshToken expired = new RefreshToken();
        expired.setUsername("teacher@test.com");
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(expired));

        assertThrows(IllegalArgumentException.class,
                () -> refreshTokenService.rotate("expired-token", "REPS Mobile"));
    }
}
