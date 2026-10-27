package com.reps.demogcloud.data;

import org.bson.Document;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.core.query.Criteria;

/** Write preservation only, not tenant authorization or a migration mechanism. */
final class TenantOwnershipWriteGuard {
    static final String FIELD = "tenantSchoolId";

    private TenantOwnershipWriteGuard() {}

    static void requireUnownedInsert(Document incoming) {
        if (incoming.get(FIELD) != null) throw conflict();
        // Keep legacy documents absent, not explicitly null. No application-side owner assignment yet.
        incoming.remove(FIELD);
    }

    static Criteria unchangedOwner(Document persisted, Document incoming) {
        Object supplied = incoming.get(FIELD);
        if (!persisted.containsKey(FIELD)) {
            if (supplied != null) throw conflict();
            return Criteria.where(FIELD).exists(false);
        }
        Object owner = persisted.get(FIELD);
        if (!(owner instanceof String id) || id.isBlank() || !id.equals(id.trim())
                || (supplied != null && !owner.equals(supplied))) throw conflict();
        // Missing ownership in a stale profile is allowed, but is NEVER written/unset.
        return Criteria.where(FIELD).is(owner);
    }

    static OptimisticLockingFailureException conflict() {
        return new OptimisticLockingFailureException("Profile ownership changed or requires review. Reload before retrying.");
    }
}
