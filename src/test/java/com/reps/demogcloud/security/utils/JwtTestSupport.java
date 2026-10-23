package com.reps.demogcloud.security.utils;

import com.reps.demogcloud.security.services.JwtRevocationStore;
import org.springframework.mock.env.MockEnvironment;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.charset.StandardCharsets;
import static org.mockito.Mockito.*;

/** Clearly synthetic fixtures, never runtime signing configuration or real credentials. */
public final class JwtTestSupport {
    public static String key() { return Base64.getEncoder().encodeToString("test".repeat(8).getBytes(StandardCharsets.UTF_8)); }
    public static MockEnvironment environment() { return new MockEnvironment().withProperty("reps.auth.jwt.signing-key", key()); }
    public static JwtRevocationStore store() {
        var store = mock(JwtRevocationStore.class); var consumed = ConcurrentHashMap.<String>newKeySet();
        when(store.contains(anyString())).thenAnswer(i -> consumed.contains(i.getArgument(0)));
        when(store.revoke(anyString(),any(Date.class))).thenAnswer(i -> consumed.add(i.getArgument(0)));
        return store;
    }
    public static JwtUtils jwt() { return new JwtUtils(environment(),store()); }
}
