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
        boolean isolated=java.util.Arrays.asList(environment.getActiveProfiles()).contains("tenant-isolated-verification")
                || java.util.List.of("core","domain","currency","email").stream().anyMatch(part->"true".equals(environment.getProperty("reps.tenancy."+part+"-preparation-enabled")))
                || "true".equals(environment.getProperty("reps.tenancy.isolated-test-activation"));
        if(isolated && java.util.Arrays.stream(environment.getActiveProfiles()).anyMatch(profile->java.util.Set.of("dev","prod","production").contains(profile)))
            throw new IllegalStateException("Canonical preparation cannot be combined with deployed application profiles");
        if(isolated && java.util.List.of("GAE_ENV","GAE_APPLICATION","GAE_SERVICE","K_SERVICE","K_REVISION").stream()
                .anyMatch(name->{String value=environment.getProperty(name);return value!=null&&!value.isBlank();}))
            throw new IllegalStateException("Isolated canonical verification is prohibited in hosted environments");
        if (!"false".equals(environment.getProperty(PROPERTY, "false")))
            throw new IllegalStateException("A7 ID enforcement is not deployable in preparation phase. Keep it disabled until writer protection, reviewed backfill and coordinated cutover are approved.");
    }
}
