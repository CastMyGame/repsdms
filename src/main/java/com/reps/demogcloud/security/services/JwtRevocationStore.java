package com.reps.demogcloud.security.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.*;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import java.util.HexFormat;

/** Shared, fail-closed revocation. Only token digests and expiry timestamps are persisted. */
@Service
@RequiredArgsConstructor
public class JwtRevocationStore {
    public static final String COLLECTION = "jwt_token_revocations";
    private final MongoTemplate mongo;

    public boolean contains(String token) {
        return mongo.exists(Query.query(Criteria.where("_id").is(digest(token))), COLLECTION);
    }

    /** Atomic claim: exactly one renewal may consume a token, across all instances. */
    public boolean revoke(String token, Date expiresAt) {
        try {
            return mongo.upsert(Query.query(Criteria.where("_id").is(digest(token))),
                    new Update().setOnInsert("expiresAt", expiresAt), COLLECTION).getUpsertedId() != null;
        } catch (DuplicateKeyException concurrentClaim) { return false; }
    }

    private static String digest(String token) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 is unavailable"); }
    }
}
