package com.reps.demogcloud.services.tenancy;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** Phase 1 must never be mistaken for an operational ID-enforcement feature flag. */
@Component
@RequiredArgsConstructor
public class TenantPreparationGuard {
    public static final String PROPERTY = "reps.tenancy.id-enforcement-enabled";
    private final Environment environment;
    @PostConstruct public void rejectPrematureActivation() {
        if (!"false".equals(environment.getProperty(PROPERTY, "false")))
            throw new IllegalStateException("A7 ID enforcement is not deployable in preparation phase. Keep it disabled until writer protection, reviewed backfill and coordinated cutover are approved.");
    }
}
