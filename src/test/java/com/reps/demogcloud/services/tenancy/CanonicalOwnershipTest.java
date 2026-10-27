package com.reps.demogcloud.services.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.mock.env.MockEnvironment;
import java.util.Set;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalOwnershipTest {
    final Set<String> schools = Set.of("school-A", "school-B");
    CanonicalOwnership.StoredOwner owner(String id) { return new CanonicalOwnership.StoredOwner(id); }
    @Test void samePersistedIdAccepted() { assertDoesNotThrow(() -> CanonicalOwnership.requireSameTenant(owner("school-A"),owner("school-A"),schools)); }
    @Test void differentSchoolsNeverMatch() { assertThrows(AccessDeniedException.class,()->CanonicalOwnership.requireSameTenant(owner("school-A"),owner("school-B"),schools)); }
    @Test void missingUnknownAndNormalizedIdsDenied() {
        for (String id : new String[]{null,""," ","school-a"," school-A","unknown"}) {
            assertThrows(AccessDeniedException.class,()->CanonicalOwnership.requireSameTenant(owner(id),owner(id),schools));
            assertThrows(AccessDeniedException.class,()->CanonicalOwnership.requireSameTenant(owner("school-A"),owner(id),schools));
        }
    }
    @Test void immutableOwnershipRejectsTransferAndClearing() {
        assertThrows(AccessDeniedException.class,()->CanonicalOwnership.requireUnchanged(owner("school-A"),owner("school-B"),schools));
        assertThrows(AccessDeniedException.class,()->CanonicalOwnership.requireUnchanged(owner("school-A"),owner(null),schools));
    }
    @Test void defaultGuardDoesNotChangeExistingStartup() { assertDoesNotThrow(()->new TenantPreparationGuard(new MockEnvironment()).rejectPrematureActivation()); }
    @Test void enabledOrMalformedActivationFailsWithoutEchoingSuppliedValue() {
        for(String value : new String[]{"true","TRUE","invalid-sensitive-value",""}) {
            var failure=assertThrows(IllegalStateException.class,()->new TenantPreparationGuard(new MockEnvironment().withProperty(TenantPreparationGuard.PROPERTY,value)).rejectPrematureActivation());
            assertFalse(failure.getMessage().contains(value.isEmpty()?"\"\"":value));
        }
    }
    @Test void realSpringStartupRejectsPrematureActivation() {
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(TenantPreparationGuard.class)
                .withPropertyValues(TenantPreparationGuard.PROPERTY + "=true")
                .run(context -> assertNotNull(context.getStartupFailure()));
        new org.springframework.boot.test.context.runner.ApplicationContextRunner()
                .withBean(TenantPreparationGuard.class)
                .run(context -> assertNull(context.getStartupFailure()));
    }
}
