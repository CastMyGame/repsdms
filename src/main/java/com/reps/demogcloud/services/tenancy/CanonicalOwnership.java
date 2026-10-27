package com.reps.demogcloud.services.tenancy;

import org.springframework.security.access.AccessDeniedException;
import java.util.Set;

/** Preparation contract only. Callers must supply ownership loaded from trusted persisted records.
 * Not wired into runtime authorization until the coordinated A7 cutover. No name resolution exists here. */
public final class CanonicalOwnership {
    private CanonicalOwnership() {}
    public record StoredOwner(String tenantSchoolId) {}
    public static void requireSameTenant(StoredOwner actor, StoredOwner target, Set<String> existingSchoolIds) {
        if (actor == null || target == null || existingSchoolIds == null
                || !valid(actor.tenantSchoolId()) || !valid(target.tenantSchoolId())
                || !existingSchoolIds.contains(actor.tenantSchoolId())
                || !existingSchoolIds.contains(target.tenantSchoolId())
                || !actor.tenantSchoolId().equals(target.tenantSchoolId())) denied();
    }
    /** Ordinary updates cannot clear, normalize or replace a bound tenant, even with another existing ID. */
    public static void requireUnchanged(StoredOwner before, StoredOwner after, Set<String> existingSchoolIds) {
        requireSameTenant(before, after, existingSchoolIds);
    }
    private static boolean valid(String id) { return id != null && !id.isBlank() && id.equals(id.trim()); }
    private static void denied() { throw new AccessDeniedException("Tenant ownership is unavailable or does not match"); }
}
