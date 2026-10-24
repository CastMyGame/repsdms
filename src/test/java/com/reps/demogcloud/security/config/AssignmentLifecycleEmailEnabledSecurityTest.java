package com.reps.demogcloud.security.config;
import org.springframework.test.context.TestPropertySource;
@TestPropertySource(properties={"reps.email.routing.phase1-enabled=true","reps.email.gmail.connection-enabled=true","reps.email.notifications.enabled=true","reps.email.notifications.dispatch-enabled=true"})
class AssignmentLifecycleEmailEnabledSecurityTest extends AssignmentLifecycleSecurityTest {
    @Override @org.junit.jupiter.api.Test void authorizationMatrixRunsWithExpectedEmailGates() {
        for(var property:java.util.List.of("reps.email.routing.phase1-enabled","reps.email.gmail.connection-enabled","reps.email.notifications.enabled","reps.email.notifications.dispatch-enabled"))
            org.junit.jupiter.api.Assertions.assertEquals(true,context.getEnvironment().getProperty(property,Boolean.class));
    }
}
