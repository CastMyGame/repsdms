package com.reps.demogcloud.services.tenancy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;

class CanonicalEmailBoundaryTest {
    @Test void requestIntegrationIsAbsentByDefault(){new ApplicationContextRunner().withUserConfiguration(CanonicalEmailOwnership.class).run(c->{assertNull(c.getStartupFailure());assertTrue(c.getBeansOfType(CanonicalEmailOwnership.class).isEmpty());});}
    @Test void directCallsRequireIsolatedProfile(){var guard=new CanonicalEmailOwnership(null,null,null,new MockEnvironment());assertThrows(IllegalStateException.class,guard::actor);}
    @Test void notificationOwnershipIsNotWritableOrExposedThroughJson()throws Exception{
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var n=mapper.readValue("{\"tenantSchoolId\":\"forged\",\"actorAccountId\":\"forged\"}",com.reps.demogcloud.models.email.RequiredNotification.class);assertNull(n.getTenantSchoolId());assertNull(n.getActorAccountId());
        n.setTenantSchoolId("internal");n.setActorAccountId("internal");n.setCreatedAt(null);assertFalse(mapper.writeValueAsString(n).contains("internal"));
    }
}
