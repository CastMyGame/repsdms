package com.reps.demogcloud.security.config;
import org.springframework.test.context.TestPropertySource;
/** The same real-chain authorization matrix is unaffected by Email MVP flags. */
@TestPropertySource(properties={"auth.sso.google.enabled=false","auth.sso.only=false","reps.email.routing.phase1-enabled=true","reps.email.gmail.connection-enabled=true","reps.email.notifications.enabled=true","reps.email.notifications.dispatch-enabled=true"})
class RosterSecurityEmailEnabledTest extends UserAdministrationSecurityTest {}
