package com.reps.demogcloud.security.config;
@org.springframework.test.context.TestPropertySource(properties={"reps.email.routing.phase1-enabled=true","reps.email.notifications.enabled=true","reps.email.notifications.dispatch-enabled=true","reps.email.gmail.connection-enabled=true"})
class SchoolConfigurationSecurityEmailEnabledTest extends SchoolConfigurationSecurityTest {}
