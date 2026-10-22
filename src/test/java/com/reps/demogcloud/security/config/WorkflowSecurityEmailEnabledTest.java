package com.reps.demogcloud.security.config;

import org.springframework.test.context.TestPropertySource;

/** Run the identical real-chain authorization matrix with every Email MVP gate enabled. */
@TestPropertySource(properties = {"reps.email.routing.phase1-enabled=true", "reps.email.gmail.connection-enabled=true",
        "reps.email.notifications.enabled=true", "reps.email.notifications.dispatch-enabled=true"})
class WorkflowSecurityEmailEnabledTest extends WorkflowSecurityTest { }
